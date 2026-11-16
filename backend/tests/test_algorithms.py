"""
Test suite for CampusNav Routing Algorithms:
- Dijkstra's Algorithm
- A* Search Algorithm
- Multi-criteria optimization (shortest vs fastest)
- Wheelchair accessibility constraints (stairs avoidance)
- Dynamic pathway blockages
- Alternative route generation
- Emergency route to nearest facility
"""
import pytest
import os
from backend.app.algorithms.graph import CampusGraph, GraphNode, GraphEdge, build_graph_from_geojson
from backend.app.algorithms.dijkstra import dijkstra
from backend.app.algorithms.astar import astar
from backend.app.algorithms.route_optimizer import RouteOptimizer

DATA_DIR = os.path.join(os.path.dirname(__file__), '..', 'data')
NODES_PATH = os.path.join(DATA_DIR, 'thapar_nodes.geojson')
PATHWAYS_PATH = os.path.join(DATA_DIR, 'thapar_pathways.geojson')


@pytest.fixture
def sample_campus_graph():
    """
    Synthetic graph for unit testing:
        (A) --[100m, 71s]-- (B) --[120m, 85s]-- (D)
         |                   |                   |
      [200m]              [150m]               [80m]
         |                   |                   |
        (E) --[100m, 71s]-- (C) -----------------+
    """
    g = CampusGraph()
    # Add nodes with mock coordinates (Patiala vicinity)
    g.add_node(GraphNode('A', 30.3520, 76.3630, is_accessible=True))
    g.add_node(GraphNode('B', 30.3530, 76.3630, is_accessible=True))
    g.add_node(GraphNode('C', 30.3530, 76.3645, is_accessible=True))
    g.add_node(GraphNode('D', 30.3520, 76.3645, is_accessible=True))
    g.add_node(GraphNode('E', 30.3510, 76.3635, is_accessible=True))

    # Add edges
    g.add_edge(GraphEdge('A', 'B', distance_meters=100.0, estimated_seconds=71.0, pathway_id=1))
    g.add_edge(GraphEdge('B', 'D', distance_meters=120.0, estimated_seconds=85.0, pathway_id=2))
    g.add_edge(GraphEdge('B', 'C', distance_meters=150.0, estimated_seconds=107.0, pathway_id=3))
    g.add_edge(GraphEdge('C', 'D', distance_meters=80.0, estimated_seconds=57.0, pathway_id=4))
    g.add_edge(GraphEdge('A', 'E', distance_meters=200.0, estimated_seconds=142.0, pathway_id=5))
    g.add_edge(GraphEdge('E', 'C', distance_meters=100.0, estimated_seconds=71.0, pathway_id=6))
    return g


@pytest.fixture
def real_thapar_graph():
    if os.path.exists(NODES_PATH) and os.path.exists(PATHWAYS_PATH):
        return build_graph_from_geojson(NODES_PATH, PATHWAYS_PATH)
    return None


def test_dijkstra_finds_optimal_path(sample_campus_graph):
    # Shortest path A -> D: A -> B -> D (100 + 120 = 220m)
    path, cost, meta = dijkstra(sample_campus_graph, 'A', 'D', weight='distance')
    assert path == ['A', 'B', 'D']
    assert abs(cost - 220.0) < 0.1
    assert meta['total_distance_meters'] == 220.0


def test_astar_finds_optimal_path(sample_campus_graph):
    path, cost, meta = astar(sample_campus_graph, 'A', 'D', weight='distance')
    assert path == ['A', 'B', 'D']
    assert abs(cost - 220.0) < 0.1
    assert meta['algorithm'] == 'astar'


def test_astar_matches_dijkstra_cost(sample_campus_graph):
    path_d, cost_d, _ = dijkstra(sample_campus_graph, 'A', 'D')
    path_a, cost_a, _ = astar(sample_campus_graph, 'A', 'D')
    assert abs(cost_d - cost_a) < 0.001


def test_unreachable_node_handling(sample_campus_graph):
    # Add an isolated node
    sample_campus_graph.add_node(GraphNode('ISOLATED', 30.3600, 76.3700))
    path, cost, meta = dijkstra(sample_campus_graph, 'A', 'ISOLATED')
    assert path is None
    assert cost == float('inf')
    assert 'error' in meta


def test_dynamic_blocked_pathway(sample_campus_graph):
    # Block pathway A <-> B (pathway_id=1)
    sample_campus_graph.block_pathway('A', 'B')
    path, cost, meta = dijkstra(sample_campus_graph, 'A', 'D')
    # Path must now detour via E: A -> E -> C -> D (200 + 100 + 80 = 380m)
    assert path == ['A', 'E', 'C', 'D']
    assert abs(cost - 380.0) < 0.1


def test_accessibility_stairs_avoidance(sample_campus_graph):
    # Make edge B -> D have stairs and inaccessible
    for edge in sample_campus_graph.adjacency['B']:
        if edge.to_key == 'D':
            edge.has_stairs = True
            edge.is_accessible = False

    # Accessible route A -> D must avoid B -> D
    path, cost, meta = dijkstra(sample_campus_graph, 'A', 'D', accessible_only=True)
    assert path is not None
    # Cannot be ['A', 'B', 'D']
    assert path != ['A', 'B', 'D']
    assert meta['accessible_only'] is True


def test_alternative_route_generation(sample_campus_graph):
    optimizer = RouteOptimizer()
    primary = ['A', 'B', 'D']
    alt = optimizer.alternative_route(sample_campus_graph, 'A', 'D', primary_path_nodes=primary)
    assert alt is not None
    assert alt['path_nodes'] != primary
    assert alt['path_nodes'][0] == 'A'
    assert alt['path_nodes'][-1] == 'D'


def test_emergency_routing(sample_campus_graph):
    optimizer = RouteOptimizer()
    emergency_nodes = [
        {'node_key': 'D', 'facility': {'name': 'Health Center', 'type': 'MEDICAL'}},
        {'node_key': 'E', 'facility': {'name': 'Security Gate', 'type': 'SECURITY'}}
    ]
    route = optimizer.emergency_route(sample_campus_graph, 'A', emergency_nodes)
    assert route is not None
    assert route['emergency_facility'] is not None


def test_real_thapar_graph_connectivity(real_thapar_graph):
    if real_thapar_graph is None:
        pytest.skip("GeoJSON data files not found.")

    nodes = list(real_thapar_graph.nodes.keys())
    assert len(nodes) >= 30, f"Expected at least 30 nodes, found {len(nodes)}"

    # Test pathfinding across major landmark nodes on the real Thapar map
    source = 'n_gate3'  # Gate 3 (Western Gate)
    targets = ['n_gate1', 'n_cricket', 'n_new_lib', 'n_sports_ctr', 'n_ha']

    for target in targets:
        if real_thapar_graph.has_node(target):
            path, cost, meta = astar(real_thapar_graph, source, target)
            assert path is not None, f"Could not route from {source} to {target}"
            assert path[0] == source
            assert path[-1] == target
            assert cost > 0
