"""
Campus Graph Builder
Constructs weighted graph from database or GeoJSON for routing algorithms.
"""
import heapq
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Set
try:
    from backend.app.utils.geo_utils import haversine_distance, walking_time_seconds
except ImportError:
    from app.utils.geo_utils import haversine_distance, walking_time_seconds


@dataclass
class GraphNode:
    node_key: str
    latitude: float
    longitude: float
    is_accessible: bool = True
    node_type: str = 'intersection'
    building_id: Optional[str] = None


@dataclass
class GraphEdge:
    from_key: str
    to_key: str
    distance_meters: float
    estimated_seconds: float
    has_stairs: bool = False
    has_ramp: bool = False
    is_accessible: bool = True
    is_blocked: bool = False
    surface_type: str = 'concrete'
    pathway_id: Optional[int] = None


class CampusGraph:
    """Weighted undirected/directed graph of campus navigation network."""

    def __init__(self):
        self.nodes: Dict[str, GraphNode] = {}
        self.adjacency: Dict[str, List[GraphEdge]] = {}   # node_key → [edges]
        self._edge_index: Dict[Tuple[str, str], GraphEdge] = {}

    def add_node(self, node: GraphNode):
        self.nodes[node.node_key] = node
        if node.node_key not in self.adjacency:
            self.adjacency[node.node_key] = []

    def add_edge(self, edge: GraphEdge, bidirectional: bool = True):
        self._add_directed_edge(edge)
        if bidirectional:
            reverse = GraphEdge(
                from_key=edge.to_key, to_key=edge.from_key,
                distance_meters=edge.distance_meters,
                estimated_seconds=edge.estimated_seconds,
                has_stairs=edge.has_stairs, has_ramp=edge.has_ramp,
                is_accessible=edge.is_accessible, is_blocked=edge.is_blocked,
                surface_type=edge.surface_type, pathway_id=edge.pathway_id
            )
            self._add_directed_edge(reverse)

    def _add_directed_edge(self, edge: GraphEdge):
        if edge.from_key not in self.adjacency:
            self.adjacency[edge.from_key] = []
        self.adjacency[edge.from_key].append(edge)
        self._edge_index[(edge.from_key, edge.to_key)] = edge

    def get_neighbors(self, node_key: str, accessible_only: bool = False,
                      include_blocked: bool = False) -> List[GraphEdge]:
        edges = self.adjacency.get(node_key, [])
        if not include_blocked:
            edges = [e for e in edges if not e.is_blocked]
        if accessible_only:
            edges = [e for e in edges if e.is_accessible and not e.has_stairs]
        return edges

    def block_pathway(self, from_key: str, to_key: str):
        """Mark a pathway as blocked in both directions."""
        for key in [(from_key, to_key), (to_key, from_key)]:
            if key in self._edge_index:
                self._edge_index[key].is_blocked = True

    def unblock_pathway(self, from_key: str, to_key: str):
        for key in [(from_key, to_key), (to_key, from_key)]:
            if key in self._edge_index:
                self._edge_index[key].is_blocked = False

    def node_count(self) -> int:
        return len(self.nodes)

    def edge_count(self) -> int:
        return len(self._edge_index)

    def has_node(self, node_key: str) -> bool:
        return node_key in self.nodes

    def find_nearest_node(self, lat: float, lon: float,
                          accessible_only: bool = False) -> Optional[str]:
        """Find nearest graph node to given coordinates."""
        best_key, best_dist = None, float('inf')
        for key, node in self.nodes.items():
            if accessible_only and not node.is_accessible:
                continue
            d = haversine_distance(lat, lon, node.latitude, node.longitude)
            if d < best_dist:
                best_dist, best_key = d, key
        return best_key


def build_graph_from_db(campus_id: str = 'thapar',
                        accessible_only: bool = False) -> CampusGraph:
    """Build CampusGraph from SQLAlchemy database models."""
    try:
        from backend.app.models.location import NavigationNode
        from backend.app.models.pathway import Pathway
    except ImportError:
        from app.models.location import NavigationNode
        from app.models.pathway import Pathway

    graph = CampusGraph()

    nodes = NavigationNode.query.filter_by(campus_id=campus_id).all()
    for n in nodes:
        graph.add_node(GraphNode(
            node_key=n.node_key, latitude=n.latitude, longitude=n.longitude,
            is_accessible=n.is_accessible, node_type=n.node_type,
            building_id=str(n.building_id) if n.building_id else None
        ))

    pathways = Pathway.query.filter_by(campus_id=campus_id).all()
    for p in pathways:
        from_node = NavigationNode.query.get(p.from_node_id)
        to_node = NavigationNode.query.get(p.to_node_id)
        if not from_node or not to_node:
            continue
        edge = GraphEdge(
            from_key=from_node.node_key, to_key=to_node.node_key,
            distance_meters=p.distance_meters,
            estimated_seconds=p.estimated_seconds,
            has_stairs=p.has_stairs, has_ramp=p.has_ramp,
            is_accessible=p.is_accessible, is_blocked=p.is_blocked,
            surface_type=p.surface_type or 'concrete',
            pathway_id=p.id
        )
        graph.add_edge(edge, bidirectional=p.is_bidirectional)

    return graph


def build_graph_from_geojson(nodes_path: str, pathways_path: str) -> CampusGraph:
    """Build CampusGraph from GeoJSON files (for testing/bootstrapping)."""
    import json
    graph = CampusGraph()

    with open(nodes_path) as f:
        nodes_data = json.load(f)
    for feature in nodes_data['features']:
        props = feature['properties']
        coords = feature['geometry']['coordinates']
        graph.add_node(GraphNode(
            node_key=props['node_key'],
            latitude=coords[1], longitude=coords[0],
            is_accessible=props.get('is_accessible', True),
            node_type=props.get('node_type', 'intersection'),
            building_id=props.get('building_id')
        ))

    with open(pathways_path) as f:
        pathways_data = json.load(f)
    for feature in pathways_data['features']:
        props = feature['properties']
        edge = GraphEdge(
            from_key=props['from_node'], to_key=props['to_node'],
            distance_meters=props.get('distance_meters', 50.0),
            estimated_seconds=props.get('estimated_seconds', 36.0),
            has_stairs=props.get('has_stairs', False),
            has_ramp=props.get('has_ramp', False),
            is_accessible=props.get('is_accessible', True),
            is_blocked=False,
            surface_type=props.get('surface_type', 'concrete')
        )
        graph.add_edge(edge, bidirectional=props.get('is_bidirectional', True))

    return graph
