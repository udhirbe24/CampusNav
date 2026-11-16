"""
Campus Data Service for CampusNav.
Provides CRUD and query operations for buildings, navigation nodes, pathways, closures, and analytics.
"""
from typing import Dict, List, Optional, Any
from sqlalchemy import or_, desc, func

from backend.app import db
from backend.app.models.user import User
from backend.app.models.location import Building, CampusLocation, NavigationNode
from backend.app.models.pathway import Pathway, PathwayClosure
from backend.app.models.emergency import EmergencyFacility
from backend.app.models.report import UserReport, NavigationHistory


class CampusDataService:
    """Service layer for querying and managing campus data."""

    @classmethod
    def get_buildings(
        cls,
        campus_id: str = 'thapar',
        category: Optional[str] = None,
        search_query: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """Fetch active buildings with their associated navigation entrances."""
        query = Building.query.filter_by(campus_id=campus_id, is_active=True)

        if category and category.upper() != 'ALL':
            query = query.filter_by(category=category.upper())

        if search_query:
            term = f"%{search_query.strip()}%"
            query = query.filter(
                or_(
                    Building.name.ilike(term),
                    Building.short_code.ilike(term),
                    Building.description.ilike(term)
                )
            )

        buildings = query.order_by(Building.name.asc()).all()
        results = []
        for b in buildings:
            b_dict = b.to_dict()
            # Fetch entrance nodes
            entrances = NavigationNode.query.filter_by(
                building_id=b.id,
                is_entrance=True
            ).all()
            b_dict['entrances'] = [e.to_dict() for e in entrances]
            results.append(b_dict)

        return results

    @classmethod
    def get_building_by_id(cls, building_id: int) -> Optional[Dict[str, Any]]:
        """Retrieve single building details with rooms and entrances."""
        b = Building.query.get(building_id)
        if not b:
            return None

        b_dict = b.to_dict()
        entrances = NavigationNode.query.filter_by(building_id=b.id).all()
        rooms = CampusLocation.query.filter_by(building_id=b.id, is_active=True).all()
        b_dict['nodes'] = [e.to_dict() for e in entrances]
        b_dict['rooms'] = [r.to_dict() for r in rooms]
        return b_dict

    @classmethod
    def get_all_nodes(cls, campus_id: str = 'thapar') -> List[Dict[str, Any]]:
        """Fetch all navigation nodes with attached building names."""
        nodes = NavigationNode.query.filter_by(campus_id=campus_id).all()
        results = []
        for n in nodes:
            d = n.to_dict()
            if n.building_id:
                b = Building.query.get(n.building_id)
                if b:
                    d['building_name'] = b.name
                    d['building_code'] = b.short_code
            results.append(d)
        return results

    @classmethod
    def get_all_pathways(cls, campus_id: str = 'thapar') -> List[Dict[str, Any]]:
        """Fetch all pathways with coordinates and status."""
        pathways = Pathway.query.filter_by(campus_id=campus_id).all()
        results = []
        for p in pathways:
            d = p.to_dict()
            from_n = NavigationNode.query.get(p.from_node_id)
            to_n = NavigationNode.query.get(p.to_node_id)
            if from_n and to_n:
                d['from_node_key'] = from_n.node_key
                d['to_node_key'] = to_n.node_key
                d['coordinates'] = [
                    [from_n.longitude, from_n.latitude],
                    [to_n.longitude, to_n.latitude]
                ]
            results.append(d)
        return results

    @classmethod
    def get_active_closures(cls, campus_id: str = 'thapar') -> List[Dict[str, Any]]:
        """Fetch currently active pathway closures."""
        closures = PathwayClosure.query.filter_by(is_active=True).all()
        results = []
        for c in closures:
            d = c.to_dict()
            p = Pathway.query.get(c.pathway_id)
            if p and p.campus_id == campus_id:
                from_n = NavigationNode.query.get(p.from_node_id)
                to_n = NavigationNode.query.get(p.to_node_id)
                d['pathway'] = {
                    'id': p.id,
                    'from_node_key': from_n.node_key if from_n else None,
                    'to_node_key': to_n.node_key if to_n else None,
                    'distance_meters': p.distance_meters
                }
                results.append(d)
        return results

    @classmethod
    def get_admin_analytics(cls, campus_id: str = 'thapar') -> Dict[str, Any]:
        """Aggregate authentic database analytics for the administration dashboard."""
        total_users = User.query.count()
        role_counts = dict(db.session.query(User.role, func.count(User.id)).group_by(User.role).all())

        total_buildings = Building.query.filter_by(campus_id=campus_id).count()
        total_nodes = NavigationNode.query.filter_by(campus_id=campus_id).count()
        total_pathways = Pathway.query.filter_by(campus_id=campus_id).count()
        blocked_pathways = Pathway.query.filter_by(campus_id=campus_id, is_blocked=True).count()
        active_closures = PathwayClosure.query.filter_by(is_active=True).count()
        pending_reports = UserReport.query.filter_by(status='PENDING').count()
        total_reports = UserReport.query.count()

        # Popular destinations from NavigationHistory
        popular_dest_ids = db.session.query(
            NavigationHistory.to_node_id,
            func.count(NavigationHistory.id).label('route_count')
        ).group_by(NavigationHistory.to_node_id).order_by(desc('route_count')).limit(5).all()

        popular_destinations = []
        for node_id, count in popular_dest_ids:
            node = NavigationNode.query.get(node_id)
            if node:
                label = node.node_key
                if node.building_id:
                    b = Building.query.get(node.building_id)
                    if b:
                        label = f"{b.name} ({b.short_code})"
                popular_destinations.append({
                    'node_key': node.node_key,
                    'name': label,
                    'count': count
                })

        # Recent navigation routes
        recent_routes_q = NavigationHistory.query.order_by(NavigationHistory.timestamp.desc()).limit(10).all()
        recent_routes = [r.to_dict() for r in recent_routes_q]

        # Mode usage breakdown
        mode_counts = dict(db.session.query(
            NavigationHistory.route_mode,
            func.count(NavigationHistory.id)
        ).group_by(NavigationHistory.route_mode).all())

        return {
            'overview': {
                'total_users': total_users,
                'users_by_role': role_counts,
                'total_buildings': total_buildings,
                'total_nodes': total_nodes,
                'total_pathways': total_pathways,
                'blocked_pathways': blocked_pathways,
                'active_closures': active_closures,
                'pending_reports': pending_reports,
                'total_reports': total_reports,
                'total_navigation_queries': NavigationHistory.query.count()
            },
            'popular_destinations': popular_destinations,
            'recent_routes': recent_routes,
            'mode_breakdown': mode_counts
        }
