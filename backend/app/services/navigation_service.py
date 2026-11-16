"""
Navigation Service for CampusNav.
Handles graph caching, route requests, emergency routing, and history logging.
"""
from typing import Dict, List, Optional, Any
from datetime import datetime, timezone

from backend.app import db
from backend.app.models.location import NavigationNode, Building
from backend.app.models.pathway import Pathway, PathwayClosure
from backend.app.models.emergency import EmergencyFacility
from backend.app.models.report import NavigationHistory
from backend.app.algorithms.graph import CampusGraph, GraphNode, GraphEdge
from backend.app.algorithms.route_optimizer import RouteOptimizer


class NavigationService:
    """Singleton-style service for campus navigation graph and routing."""

    _cached_graph: Optional[CampusGraph] = None
    _optimizer = RouteOptimizer()

    @classmethod
    def invalidate_graph(cls):
        """Clear cached graph so it gets rebuilt on the next query."""
        cls._cached_graph = None

    @classmethod
    def get_graph(cls, campus_id: str = 'thapar', force_reload: bool = False) -> CampusGraph:
        """Fetch cached graph or construct fresh one from database."""
        if cls._cached_graph is not None and not force_reload:
            return cls._cached_graph

        graph = CampusGraph()

        # Load all nodes
        nodes = NavigationNode.query.filter_by(campus_id=campus_id).all()
        for n in nodes:
            graph.add_node(GraphNode(
                node_key=n.node_key,
                latitude=n.latitude,
                longitude=n.longitude,
                is_accessible=n.is_accessible,
                node_type=n.node_type,
                building_id=str(n.building_id) if n.building_id else None
            ))

        # Load pathways
        pathways = Pathway.query.filter_by(campus_id=campus_id).all()
        # Find actively closed pathway IDs
        active_closures = PathwayClosure.query.filter_by(is_active=True).all()
        closed_ids = {c.pathway_id for c in active_closures}

        for p in pathways:
            from_node = NavigationNode.query.get(p.from_node_id)
            to_node = NavigationNode.query.get(p.to_node_id)
            if not from_node or not to_node:
                continue

            is_blocked = p.is_blocked or (p.id in closed_ids)

            edge = GraphEdge(
                from_key=from_node.node_key,
                to_key=to_node.node_key,
                distance_meters=p.distance_meters,
                estimated_seconds=p.estimated_seconds,
                has_stairs=p.has_stairs,
                has_ramp=p.has_ramp,
                is_accessible=p.is_accessible,
                is_blocked=is_blocked,
                surface_type=p.surface_type or 'concrete',
                pathway_id=p.id
            )
            graph.add_edge(edge, bidirectional=p.is_bidirectional)

        cls._cached_graph = graph
        return graph

    @classmethod
    def calculate_route(
        cls,
        from_node_key: str,
        to_node_key: str,
        mode: str = 'shortest',
        algorithm: str = 'astar',
        accessible_only: bool = False,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        """Calculate primary route between two navigation nodes."""
        graph = cls.get_graph()

        result = cls._optimizer.calculate_route(
            graph=graph,
            source=from_node_key,
            target=to_node_key,
            mode=mode,
            algorithm=algorithm,
            accessible_only=accessible_only
        )

        if not result:
            return {'success': False, 'message': 'No route found between selected points.'}

        # Log navigation history if user is authenticated
        if user_id:
            try:
                from_node = NavigationNode.query.filter_by(node_key=from_node_key).first()
                to_node = NavigationNode.query.filter_by(node_key=to_node_key).first()
                if from_node and to_node:
                    history = NavigationHistory(
                        user_id=user_id,
                        from_node_id=from_node.id,
                        to_node_id=to_node.id,
                        algorithm_used=algorithm,
                        route_mode=mode,
                        distance_meters=result['summary']['total_distance_meters'],
                        duration_seconds=result['summary']['total_time_seconds'],
                        timestamp=datetime.now(timezone.utc)
                    )
                    db.session.add(history)
                    db.session.commit()
            except Exception as e:
                db.session.rollback()

        result['success'] = True
        return result

    @classmethod
    def calculate_alternative_route(
        cls,
        from_node_key: str,
        to_node_key: str,
        primary_path_nodes: List[str],
        mode: str = 'shortest',
        algorithm: str = 'astar',
        accessible_only: bool = False
    ) -> Dict[str, Any]:
        """Find an alternative route avoiding primary path nodes/edges."""
        graph = cls.get_graph()

        result = cls._optimizer.alternative_route(
            graph=graph,
            source=from_node_key,
            target=to_node_key,
            primary_path_nodes=primary_path_nodes,
            mode=mode,
            algorithm=algorithm,
            accessible_only=accessible_only
        )

        if not result:
            return {'success': False, 'message': 'No viable alternative route found.'}

        result['success'] = True
        return result

    @classmethod
    def find_emergency_route(
        cls,
        from_node_key: str,
        facility_type: Optional[str] = None,
        algorithm: str = 'astar'
    ) -> Dict[str, Any]:
        """Find fastest route to nearest emergency facility."""
        graph = cls.get_graph()

        query = EmergencyFacility.query.filter_by(is_active=True)
        if facility_type:
            query = query.filter_by(facility_type=facility_type.upper())

        facilities = query.all()
        if not facilities:
            return {'success': False, 'message': 'No active emergency facilities found.'}

        facility_nodes = []
        for f in facilities:
            if f.node_id:
                node = NavigationNode.query.get(f.node_id)
                if node:
                    facility_nodes.append({
                        'node_key': node.node_key,
                        'facility': f.to_dict()
                    })

        if not facility_nodes:
            return {'success': False, 'message': 'Emergency facilities have no mapped navigation points.'}

        result = cls._optimizer.emergency_route(
            graph=graph,
            source=from_node_key,
            emergency_facility_nodes=facility_nodes,
            algorithm=algorithm
        )

        if not result:
            return {'success': False, 'message': 'Could not calculate route to any emergency facility.'}

        result['success'] = True
        return result

    @classmethod
    def find_nearest_node(cls, latitude: float, longitude: float, accessible_only: bool = False) -> Optional[str]:
        """Find the closest navigation node to arbitrary coordinates (e.g. user GPS)."""
        graph = cls.get_graph()
        return graph.find_nearest_node(latitude, longitude, accessible_only=accessible_only)
