"""
Route Optimizer for CampusNav.
Provides multi-criteria route optimization:
- Shortest route (minimize distance)
- Fastest route (minimize walking time)
- Accessible route (wheelchair-safe, avoids stairs)
- Alternative route (secondary route avoiding primary path)
- Emergency route (nearest emergency facility)
- Human-readable step-by-step turn instructions
- GeoJSON route feature generation
"""
import math
from typing import Dict, List, Optional, Tuple, Set, Any

from .graph import CampusGraph
from .dijkstra import dijkstra
from .astar import astar

try:
    from backend.app.utils.geo_utils import haversine_distance, bearing
except ImportError:
    from app.utils.geo_utils import haversine_distance, bearing


def get_cardinal_direction(degrees: float) -> str:
    """Convert azimuth angle in degrees (0-360) into compass direction."""
    directions = [
        "North", "North-East", "East", "South-East",
        "South", "South-West", "West", "North-West"
    ]
    idx = round(degrees / 45.0) % 8
    return directions[idx]


def get_turn_instruction(bearing_change: float) -> str:
    """Determine turn action based on change in bearing."""
    # Normalize to -180 to 180
    change = (bearing_change + 180) % 360 - 180
    if abs(change) < 20:
        return "Continue straight"
    elif 20 <= change < 60:
        return "Turn slight right"
    elif 60 <= change < 120:
        return "Turn right"
    elif change >= 120:
        return "Turn sharp right"
    elif -60 < change <= -20:
        return "Turn slight left"
    elif -120 < change <= -60:
        return "Turn left"
    else:
        return "Turn sharp left"


class RouteOptimizer:
    """Multi-criteria campus route optimization engine."""

    def __init__(self, algorithm: str = 'astar'):
        self.default_algorithm = algorithm

    def _run_algorithm(
        self,
        graph: CampusGraph,
        source: str,
        target: str,
        weight: str = 'distance',
        accessible_only: bool = False,
        blocked_pathways: Optional[List[int]] = None,
        excluded_edges: Optional[Set[Tuple[str, str]]] = None,
        algorithm: Optional[str] = None
    ) -> Tuple[Optional[List[str]], float, Dict]:
        algo = (algorithm or self.default_algorithm).lower()
        if algo == 'dijkstra':
            return dijkstra(
                graph=graph,
                source=source,
                target=target,
                weight=weight,
                accessible_only=accessible_only,
                blocked_pathways=blocked_pathways,
                excluded_edges=excluded_edges
            )
        else:
            return astar(
                graph=graph,
                source=source,
                target=target,
                weight=weight,
                accessible_only=accessible_only,
                blocked_pathways=blocked_pathways,
                excluded_edges=excluded_edges
            )

    def calculate_route(
        self,
        graph: CampusGraph,
        source: str,
        target: str,
        mode: str = 'shortest',  # 'shortest', 'fastest', 'accessible'
        algorithm: str = 'astar',
        accessible_only: bool = False,
        blocked_pathways: Optional[List[int]] = None
    ) -> Optional[Dict[str, Any]]:
        """Calculate primary route matching user preferences."""
        weight = 'time' if mode == 'fastest' else 'distance'
        is_accessible = accessible_only or (mode == 'accessible')

        path_nodes, cost, meta = self._run_algorithm(
            graph=graph,
            source=source,
            target=target,
            weight=weight,
            accessible_only=is_accessible,
            blocked_pathways=blocked_pathways,
            algorithm=algorithm
        )

        if not path_nodes:
            return None

        return self.format_route_response(
            graph=graph,
            path_nodes=path_nodes,
            metadata=meta,
            mode=mode,
            algorithm=algorithm
        )

    def alternative_route(
        self,
        graph: CampusGraph,
        source: str,
        target: str,
        primary_path_nodes: List[str],
        mode: str = 'shortest',
        algorithm: str = 'astar',
        accessible_only: bool = False,
        blocked_pathways: Optional[List[int]] = None
    ) -> Optional[Dict[str, Any]]:
        """
        Find a genuine alternative route by penalizing or excluding the primary path's edges.
        """
        if not primary_path_nodes or len(primary_path_nodes) < 2:
            return None

        weight = 'time' if mode == 'fastest' else 'distance'
        is_accessible = accessible_only or (mode == 'accessible')

        # Exclude edges used in the primary path
        excluded_edges: Set[Tuple[str, str]] = set()
        for i in range(len(primary_path_nodes) - 1):
            u, v = primary_path_nodes[i], primary_path_nodes[i + 1]
            excluded_edges.add((u, v))
            excluded_edges.add((v, u))

        alt_nodes, cost, meta = self._run_algorithm(
            graph=graph,
            source=source,
            target=target,
            weight=weight,
            accessible_only=is_accessible,
            blocked_pathways=blocked_pathways,
            excluded_edges=excluded_edges,
            algorithm=algorithm
        )

        # If completely excluding all edges found no path, try excluding only intermediate edges
        if not alt_nodes and len(primary_path_nodes) > 3:
            relaxed_excluded: Set[Tuple[str, str]] = set()
            mid = len(primary_path_nodes) // 2
            u, v = primary_path_nodes[mid], primary_path_nodes[mid + 1]
            relaxed_excluded.add((u, v))
            relaxed_excluded.add((v, u))

            alt_nodes, cost, meta = self._run_algorithm(
                graph=graph,
                source=source,
                target=target,
                weight=weight,
                accessible_only=is_accessible,
                blocked_pathways=blocked_pathways,
                excluded_edges=relaxed_excluded,
                algorithm=algorithm
            )

        if not alt_nodes or alt_nodes == primary_path_nodes:
            return None

        meta['is_alternative'] = True
        return self.format_route_response(
            graph=graph,
            path_nodes=alt_nodes,
            metadata=meta,
            mode=mode,
            algorithm=algorithm
        )

    def emergency_route(
        self,
        graph: CampusGraph,
        source: str,
        emergency_facility_nodes: List[Dict[str, Any]],
        algorithm: str = 'astar',
        blocked_pathways: Optional[List[int]] = None
    ) -> Optional[Dict[str, Any]]:
        """
        Find route to the closest accessible emergency facility.
        emergency_facility_nodes: list of dicts with {'node_key', 'facility_info'}
        """
        best_route = None
        best_cost = float('inf')
        selected_facility = None

        for item in emergency_facility_nodes:
            target_key = item.get('node_key')
            if not target_key or not graph.has_node(target_key):
                continue

            path_nodes, cost, meta = self._run_algorithm(
                graph=graph,
                source=source,
                target=target_key,
                weight='distance',
                accessible_only=False,
                blocked_pathways=blocked_pathways,
                algorithm=algorithm
            )

            if path_nodes and cost < best_cost:
                best_cost = cost
                selected_facility = item.get('facility')
                meta['emergency_facility'] = selected_facility
                best_route = self.format_route_response(
                    graph=graph,
                    path_nodes=path_nodes,
                    metadata=meta,
                    mode='emergency',
                    algorithm=algorithm
                )
                if best_route:
                    best_route['emergency_facility'] = selected_facility

        return best_route

    def generate_navigation_steps(self, path_nodes: List[str], graph: CampusGraph) -> List[Dict[str, Any]]:
        """Generate human-readable, turn-by-turn navigation steps."""
        if not path_nodes:
            return []

        if len(path_nodes) == 1:
            node = graph.nodes.get(path_nodes[0])
            desc = node.node_key if node else path_nodes[0]
            return [{
                'step_number': 1,
                'instruction': f"You are already at {desc}.",
                'distance_meters': 0.0,
                'distance_text': "0 m",
                'bearing': 0.0,
                'from_node': path_nodes[0],
                'to_node': path_nodes[0]
            }]

        steps = []
        last_bearing = None

        for i in range(len(path_nodes) - 1):
            u_key = path_nodes[i]
            v_key = path_nodes[i + 1]

            u_node = graph.nodes.get(u_key)
            v_node = graph.nodes.get(v_key)

            if not u_node or not v_node:
                continue

            seg_dist = haversine_distance(u_node.latitude, u_node.longitude, v_node.latitude, v_node.longitude)
            cur_bearing = bearing(u_node.latitude, u_node.longitude, v_node.latitude, v_node.longitude)
            cardinal = get_cardinal_direction(cur_bearing)

            # Determine turn instruction
            if i == 0:
                instruction = f"Head {cardinal} toward {v_key}"
            else:
                bearing_change = cur_bearing - last_bearing
                turn_action = get_turn_instruction(bearing_change)
                instruction = f"{turn_action} toward {v_key}"

            # Check edge attributes like stairs or ramps
            edge = graph._edge_index.get((u_key, v_key))
            has_stairs = edge.has_stairs if edge else False
            has_ramp = edge.has_ramp if edge else False

            if has_stairs:
                instruction += " (Includes stairs)"
            elif has_ramp:
                instruction += " (Ramp available)"

            dist_text = f"{round(seg_dist)} m" if seg_dist < 1000 else f"{round(seg_dist/1000, 2)} km"

            steps.append({
                'step_number': i + 1,
                'instruction': instruction,
                'distance_meters': round(seg_dist, 1),
                'distance_text': dist_text,
                'bearing': round(cur_bearing, 1),
                'from_node': u_key,
                'to_node': v_key,
                'has_stairs': has_stairs,
                'has_ramp': has_ramp
            })

            last_bearing = cur_bearing

        # Final arrival step
        dest_node = graph.nodes.get(path_nodes[-1])
        dest_label = dest_node.building_id or dest_node.node_key if dest_node else path_nodes[-1]
        steps.append({
            'step_number': len(steps) + 1,
            'instruction': f"Arrive at destination: {dest_label}",
            'distance_meters': 0.0,
            'distance_text': "0 m",
            'bearing': round(last_bearing, 1) if last_bearing is not None else 0.0,
            'from_node': path_nodes[-1],
            'to_node': path_nodes[-1],
            'is_arrival': True
        })

        return steps

    def format_route_response(
        self,
        graph: CampusGraph,
        path_nodes: List[str],
        metadata: Dict[str, Any],
        mode: str = 'shortest',
        algorithm: str = 'astar'
    ) -> Dict[str, Any]:
        """Produce the standardized route output object with GeoJSON line and steps."""
        coordinates = []
        node_details = []

        for key in path_nodes:
            node = graph.nodes.get(key)
            if node:
                coordinates.append([node.longitude, node.latitude])
                node_details.append({
                    'node_key': node.node_key,
                    'latitude': node.latitude,
                    'longitude': node.longitude,
                    'node_type': node.node_type,
                    'is_accessible': node.is_accessible,
                    'building_id': node.building_id
                })

        dist_m = metadata.get('total_distance_meters', 0.0)
        time_s = metadata.get('total_time_seconds', 0.0)

        dist_text = f"{round(dist_m)} m" if dist_m < 1000 else f"{round(dist_m/1000, 2)} km"
        minutes = math.ceil(time_s / 60)
        time_text = f"{minutes} min" if minutes >= 1 else "< 1 min"

        steps = self.generate_navigation_steps(path_nodes, graph)

        geojson_feature = {
            'type': 'Feature',
            'geometry': {
                'type': 'LineString',
                'coordinates': coordinates
            },
            'properties': {
                'mode': mode,
                'algorithm': algorithm,
                'distance_meters': dist_m,
                'distance_text': dist_text,
                'duration_seconds': time_s,
                'duration_text': time_text,
                'has_stairs': metadata.get('has_stairs', False),
                'is_accessible': metadata.get('accessible_only', False)
            }
        }

        return {
            'path_nodes': path_nodes,
            'node_details': node_details,
            'geojson': geojson_feature,
            'steps': steps,
            'summary': {
                'total_distance_meters': dist_m,
                'total_distance_text': dist_text,
                'total_time_seconds': round(time_s),
                'total_time_text': time_text,
                'node_count': len(path_nodes),
                'algorithm': algorithm,
                'mode': mode,
                'has_stairs': metadata.get('has_stairs', False),
                'is_accessible': not metadata.get('has_stairs', False)
            }
        }
