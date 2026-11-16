"""
Locations routes — buildings, campus locations, navigation nodes, search.
Blueprint: 'locations', registered at /api/locations
"""
from flask import request
from backend.app.routes.locations import locations_bp
from backend.app.models.location import Building, CampusLocation, NavigationNode
from backend.app.services.campus_data_service import CampusDataService
from backend.app.utils.response_helpers import success_response, error_response


@locations_bp.route('', methods=['GET'])
@locations_bp.route('/', methods=['GET'])
@locations_bp.route('/buildings', methods=['GET'])
def get_buildings():
    """GET /api/locations or /api/locations/buildings — list buildings."""
    campus_id = request.args.get('campus_id', 'thapar')
    category = request.args.get('category')
    search_q = request.args.get('q')
    buildings = CampusDataService.get_buildings(campus_id=campus_id, category=category, search_query=search_q)
    return success_response(buildings)


@locations_bp.route('/<int:building_id>', methods=['GET'])
@locations_bp.route('/buildings/<int:building_id>', methods=['GET'])
def get_building(building_id):
    """GET /api/locations/<id> — retrieve single building details."""
    building = CampusDataService.get_building_by_id(building_id)
    if not building:
        return error_response(f'Building #{building_id} not found.', status_code=404)
    return success_response(building)


@locations_bp.route('/search', methods=['GET'])
def search_locations():
    """GET /api/locations/search?q=... — search buildings and locations."""
    query = request.args.get('q', '').strip()
    campus_id = request.args.get('campus_id', 'thapar')
    category = request.args.get('category')
    if not query:
        return error_response('Query parameter "q" is required.', status_code=400)

    results = CampusDataService.get_buildings(campus_id=campus_id, category=category, search_query=query)
    return success_response(results)


@locations_bp.route('/nodes', methods=['GET'])
def get_nodes():
    """GET /api/locations/nodes — list all navigation nodes for map display."""
    campus_id = request.args.get('campus_id', 'thapar')
    nodes = CampusDataService.get_all_nodes(campus_id=campus_id)
    return success_response(nodes)


@locations_bp.route('/nodes/<string:node_key>', methods=['GET'])
def get_node(node_key):
    """GET /api/locations/nodes/<key> — single node details."""
    node = NavigationNode.query.filter_by(node_key=node_key).first()
    if not node:
        return error_response(f'Node "{node_key}" not found.', status_code=404)
    return success_response(node.to_dict())


@locations_bp.route('/categories', methods=['GET'])
def get_categories():
    """GET /api/locations/categories — list available building categories."""
    categories = [
        {"key": "ACADEMIC", "label": "Academic & Classrooms", "icon": "book-open"},
        {"key": "ADMIN", "label": "Administration & Offices", "icon": "briefcase"},
        {"key": "HOSTEL", "label": "Hostels & Residences", "icon": "home"},
        {"key": "LIBRARY", "label": "Libraries", "icon": "bookmark"},
        {"key": "CAFETERIA", "label": "Cafeterias & Food", "icon": "coffee"},
        {"key": "SPORTS", "label": "Sports & Recreation", "icon": "activity"},
        {"key": "MEDICAL", "label": "Medical & Health", "icon": "plus-circle"},
        {"key": "PARKING", "label": "Parking Facilities", "icon": "truck"},
        {"key": "OTHER", "label": "Other Campus Points", "icon": "map-pin"}
    ]
    return success_response(categories)
