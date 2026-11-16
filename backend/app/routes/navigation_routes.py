"""
Navigation routes — route calculation, alternative routes, emergency routes, history.
Blueprint: 'navigation', registered at /api/navigation and /api/routes
"""
from flask import request
from flask_login import login_required, current_user

from backend.app.routes.navigation import navigation_bp
from backend.app.models.report import NavigationHistory
from backend.app.models.location import NavigationNode
from backend.app.services.navigation_service import NavigationService
from backend.app.utils.response_helpers import success_response, error_response


@navigation_bp.route('/calculate', methods=['POST'])
@navigation_bp.route('/route', methods=['POST'])
def calculate_route():
    """
    POST /api/navigation/calculate or /api/routes/calculate
    Payload:
    {
        "from_node": "n001" (or "from_node_key"),
        "to_node": "n005" (or "to_node_key"),
        "mode": "shortest" | "fastest" | "accessible",
        "algorithm": "astar" | "dijkstra",
        "accessible_only": false
    }
    """
    data = request.get_json(silent=True) or {}
    from_key = data.get('from_node') or data.get('from_node_key')
    to_key = data.get('to_node') or data.get('to_node_key')
    mode = data.get('mode', 'shortest').lower()
    algorithm = data.get('algorithm', 'astar').lower()
    accessible_only = bool(data.get('accessible_only', False)) or (mode == 'accessible')

    if not from_key or not to_key:
        return error_response('from_node and to_node parameters are required.', status_code=400)

    user_id = current_user.id if current_user.is_authenticated else None

    result = NavigationService.calculate_route(
        from_node_key=from_key,
        to_node_key=to_key,
        mode=mode,
        algorithm=algorithm,
        accessible_only=accessible_only,
        user_id=user_id
    )

    if not result.get('success'):
        return error_response(result.get('message', 'Routing failed.'), status_code=404)

    return success_response(result)


@navigation_bp.route('/alternative', methods=['POST'])
def alternative_route():
    """
    POST /api/navigation/alternative or /api/routes/alternative
    Payload:
    {
        "from_node": "n001",
        "to_node": "n005",
        "primary_path_nodes": ["n001", "n002", "n005"],
        "mode": "shortest",
        "algorithm": "astar"
    }
    """
    data = request.get_json(silent=True) or {}
    from_key = data.get('from_node') or data.get('from_node_key')
    to_key = data.get('to_node') or data.get('to_node_key')
    primary_nodes = data.get('primary_path_nodes') or []
    mode = data.get('mode', 'shortest').lower()
    algorithm = data.get('algorithm', 'astar').lower()
    accessible_only = bool(data.get('accessible_only', False)) or (mode == 'accessible')

    if not from_key or not to_key:
        return error_response('from_node and to_node parameters are required.', status_code=400)

    result = NavigationService.calculate_alternative_route(
        from_node_key=from_key,
        to_node_key=to_key,
        primary_path_nodes=primary_nodes,
        mode=mode,
        algorithm=algorithm,
        accessible_only=accessible_only
    )

    if not result.get('success'):
        return error_response(result.get('message', 'No alternative route found.'), status_code=404)

    return success_response(result)


@navigation_bp.route('/emergency', methods=['POST', 'GET'])
def emergency_route():
    """
    POST /api/navigation/emergency or /api/routes/emergency
    GET /api/navigation/emergency — returns list of facilities
    POST Payload:
    {
        "from_node": "n001",
        "facility_type": "MEDICAL" | "SECURITY" | "FIRE" (optional),
        "algorithm": "astar"
    }
    """
    from backend.app.models.emergency import EmergencyFacility
    if request.method == 'GET':
        facilities = EmergencyFacility.query.filter_by(is_active=True).all()
        return success_response([f.to_dict() for f in facilities])

    data = request.get_json(silent=True) or {}
    from_key = data.get('from_node') or data.get('from_node_key')
    facility_type = data.get('facility_type')
    algorithm = data.get('algorithm', 'astar').lower()

    if not from_key:
        return error_response('from_node parameter is required for emergency navigation.', status_code=400)

    result = NavigationService.find_emergency_route(
        from_node_key=from_key,
        facility_type=facility_type,
        algorithm=algorithm
    )

    if not result.get('success'):
        return error_response(result.get('message', 'Emergency route calculation failed.'), status_code=404)

    return success_response(result)


@navigation_bp.route('/history', methods=['GET'])
@login_required
def navigation_history():
    """GET /api/navigation/history or /api/routes/history — retrieve user's routing history."""
    history = NavigationHistory.query.filter_by(user_id=current_user.id)\
        .order_by(NavigationHistory.timestamp.desc()).limit(50).all()
    results = []
    for h in history:
        d = h.to_dict()
        fn = NavigationNode.query.get(h.from_node_id)
        tn = NavigationNode.query.get(h.to_node_id)
        d['from_node_key'] = fn.node_key if fn else None
        d['to_node_key'] = tn.node_key if tn else None
        results.append(d)
    return success_response(results)


@navigation_bp.route('/nearest-node', methods=['GET', 'POST'])
def nearest_node():
    """Find the nearest graph node to given latitude/longitude (e.g. from GPS)."""
    if request.method == 'POST':
        data = request.get_json(silent=True) or {}
        lat = data.get('latitude')
        lon = data.get('longitude')
    else:
        lat = request.args.get('lat', type=float)
        lon = request.args.get('lon', type=float)

    if lat is None or lon is None:
        return error_response('latitude and longitude parameters are required.', status_code=400)

    node_key = NavigationService.find_nearest_node(float(lat), float(lon))
    if not node_key:
        return error_response('Could not locate nearby navigation point.', status_code=404)

    node = NavigationNode.query.filter_by(node_key=node_key).first()
    return success_response({
        'node_key': node_key,
        'node': node.to_dict() if node else None
    })
