"""
Dijkstra's Algorithm for CampusNav Pathfinding.
Finds shortest path by distance or time using a min-heap priority queue.
"""
import heapq
from typing import Dict, List, Optional, Tuple, Set

from .graph import CampusGraph, GraphEdge


def dijkstra(
    graph: CampusGraph,
    source: str,
    target: str,
    weight: str = 'distance',  # 'distance' or 'time'
    accessible_only: bool = False,
    blocked_pathways: Optional[List[int]] = None,
    excluded_edges: Optional[Set[Tuple[str, str]]] = None
) -> Tuple[Optional[List[str]], float, Dict]:
    """
    Dijkstra's shortest/fastest path.
    Returns: (path_node_keys, total_cost, metadata)
    path_node_keys is None if no path exists.
    """
    if not graph.has_node(source) or not graph.has_node(target):
        return None, float('inf'), {'error': f'Source ({source}) or target ({target}) node not found in graph.'}

    if source == target:
        node = graph.nodes.get(source)
        return [source], 0.0, {
            'algorithm': 'dijkstra',
            'weight': weight,
            'total_distance_meters': 0.0,
            'total_time_seconds': 0.0,
            'node_count': 1,
            'has_stairs': False,
            'accessible_only': accessible_only
        }

    blocked_set = set(blocked_pathways or [])
    excluded = excluded_edges or set()

    # Priority queue: (cost, node_key)
    pq: List[Tuple[float, str]] = [(0.0, source)]
    dist: Dict[str, float] = {source: 0.0}
    prev: Dict[str, Optional[str]] = {source: None}
    edge_used: Dict[str, Optional[GraphEdge]] = {source: None}
    visited: Set[str] = set()

    while pq:
        cost, u = heapq.heappop(pq)

        if u in visited:
            continue
        visited.add(u)

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
            if v in visited:
                continue

            w = edge.estimated_seconds if weight == 'time' else edge.distance_meters
            new_cost = cost + w

            if v not in dist or new_cost < dist[v]:
                dist[v] = new_cost
                prev[v] = u
                edge_used[v] = edge
                heapq.heappush(pq, (new_cost, v))

    if target not in dist:
        return None, float('inf'), {'error': 'No path found between locations (destination unreachable).'}

    # Reconstruct path
    path = []
    cur = target
    while cur is not None:
        path.append(cur)
        cur = prev.get(cur)
    path.reverse()

    # Compute distance and travel time from edges
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
        'algorithm': 'dijkstra',
        'weight': weight,
        'total_distance_meters': round(total_dist, 1),
        'total_time_seconds': round(total_time, 1),
        'node_count': len(path),
        'has_stairs': has_stairs,
        'accessible_only': accessible_only,
        'pathway_ids': [pid for pid in edges_traversed if pid is not None]
    }

    return path, dist[target], metadata
