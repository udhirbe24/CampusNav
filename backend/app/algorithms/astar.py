"""
A* Search Algorithm for CampusNav Pathfinding.
Uses Haversine distance as an admissible and consistent heuristic.
"""
import heapq
from typing import Dict, List, Optional, Tuple, Set

from .graph import CampusGraph, GraphEdge

try:
    from backend.app.utils.geo_utils import haversine_distance
except ImportError:
    from app.utils.geo_utils import haversine_distance


def heuristic(graph: CampusGraph, node_key: str, target_key: str, weight: str = 'distance') -> float:
    """
    Admissible heuristic function:
    - For distance weight: straight-line Haversine distance (meters). Admissible because straight line <= path distance.
    - For time weight: Haversine distance / 1.5 m/s max walking speed. Admissible because time cannot be less than straight line at max speed.
    """
    n = graph.nodes.get(node_key)
    t = graph.nodes.get(target_key)
    if not n or not t:
        return 0.0

    dist = haversine_distance(n.latitude, n.longitude, t.latitude, t.longitude)
    if weight == 'time':
        return dist / 1.5  # Max walking speed 1.5 m/s ensures admissibility
    return dist


def astar(
    graph: CampusGraph,
    source: str,
    target: str,
    weight: str = 'distance',  # 'distance' or 'time'
    accessible_only: bool = False,
    blocked_pathways: Optional[List[int]] = None,
    excluded_edges: Optional[Set[Tuple[str, str]]] = None
) -> Tuple[Optional[List[str]], float, Dict]:
    """
    A* pathfinding algorithm.
    Returns: (path_node_keys, total_cost, metadata)
    path_node_keys is None if no path exists.
    """
    if not graph.has_node(source) or not graph.has_node(target):
        return None, float('inf'), {'error': f'Source ({source}) or target ({target}) node not found in graph.'}

    if source == target:
        return [source], 0.0, {
            'algorithm': 'astar',
            'weight': weight,
            'total_distance_meters': 0.0,
            'total_time_seconds': 0.0,
            'node_count': 1,
            'has_stairs': False,
            'accessible_only': accessible_only
        }

    blocked_set = set(blocked_pathways or [])
    excluded = excluded_edges or set()

    h0 = heuristic(graph, source, target, weight)
    # Heap stores tuples: (f_score, g_score, node_key)
    pq: List[Tuple[float, float, str]] = [(h0, 0.0, source)]

    g_score: Dict[str, float] = {source: 0.0}
    prev: Dict[str, Optional[str]] = {source: None}
    edge_used: Dict[str, Optional[GraphEdge]] = {source: None}
    closed: Set[str] = set()

    while pq:
        f, g, u = heapq.heappop(pq)

        if u in closed:
            continue
        closed.add(u)

        if u == target:
            break

        for edge in graph.get_neighbors(u, accessible_only=accessible_only):
            if edge.is_blocked:
                continue
            if blocked_set and edge.pathway_id in blocked_set:
                continue
            if (edge.from_key, edge.to_key) in excluded or (edge.to_key, edge.from_key) in excluded:
                continue

            v = edge.to_key
            if v in closed:
                continue

            edge_weight = edge.estimated_seconds if weight == 'time' else edge.distance_meters
            tentative_g = g + edge_weight

            if v not in g_score or tentative_g < g_score[v]:
                g_score[v] = tentative_g
                prev[v] = u
                edge_used[v] = edge
                h = heuristic(graph, v, target, weight)
                f_score = tentative_g + h
                heapq.heappush(pq, (f_score, tentative_g, v))

    if target not in g_score:
        return None, float('inf'), {'error': 'No path found between locations (destination unreachable).'}

    # Reconstruct path
    path = []
    cur = target
    while cur is not None:
        path.append(cur)
        cur = prev.get(cur)
    path.reverse()

    total_dist = 0.0
    total_time = 0.0
    has_stairs = False
    edges_traversed = []

    for i in range(len(path) - 1):
        u_key, v_key = path[i], path[i + 1]
        edge = edge_used.get(v_key)
        if edge:
            total_dist += edge.distance_meters
            total_time += edge.estimated_seconds
            if edge.has_stairs:
                has_stairs = True
            edges_traversed.append(edge.pathway_id)

    metadata = {
        'algorithm': 'astar',
        'weight': weight,
        'total_distance_meters': round(total_dist, 1),
        'total_time_seconds': round(total_time, 1),
        'node_count': len(path),
        'has_stairs': has_stairs,
        'accessible_only': accessible_only,
        'pathway_ids': [pid for pid in edges_traversed if pid is not None]
    }

    return path, g_score[target], metadata
