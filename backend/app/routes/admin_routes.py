"""
Admin routes — campus location management, pathway management, closures, reports, and analytics.
Blueprint: 'admin', registered at /api/admin
"""
from functools import wraps
from datetime import datetime, timezone
from flask import request
from flask_login import login_required, current_user

from backend.app.routes.admin import admin_bp
from backend.app.models.user import User
from backend.app.models.report import UserReport
from backend.app.models.location import Building, NavigationNode, CampusLocation
from backend.app.models.pathway import Pathway, PathwayClosure
from backend.app.models.emergency import EmergencyFacility
from backend.app.services.campus_data_service import CampusDataService
from backend.app.services.navigation_service import NavigationService
from backend.app import db
from backend.app.utils.response_helpers import success_response, error_response


def admin_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not current_user.is_authenticated or current_user.role != 'admin':
            return error_response('Administrator privileges required.', status_code=403)
        return f(*args, **kwargs)
    return decorated


@admin_bp.route('/analytics', methods=['GET'])
@login_required
@admin_required
def analytics():
    """GET /api/admin/analytics — comprehensive real database analytics."""
    campus_id = request.args.get('campus_id', 'thapar')
    data = CampusDataService.get_admin_analytics(campus_id=campus_id)
    return success_response(data)


@admin_bp.route('/stats', methods=['GET'])
@login_required
@admin_required
def stats():
    """GET /api/admin/stats — quick overview counts."""
    campus_id = request.args.get('campus_id', 'thapar')
    data = CampusDataService.get_admin_analytics(campus_id=campus_id)
    return success_response(data.get('overview', {}))


# --- Building & Location Management ---

@admin_bp.route('/locations', methods=['POST'])
@login_required
@admin_required
def create_location():
    """POST /api/admin/locations — create a new building or facility."""
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    short_code = (data.get('short_code') or '').strip().upper()
    category = (data.get('category') or 'OTHER').upper()
    lat = data.get('latitude')
    lon = data.get('longitude')
    desc = data.get('description', '')

    if not name or not short_code or lat is None or lon is None:
        return error_response('name, short_code, latitude, and longitude are required.', status_code=400)

    building = Building(
        name=name,
        short_code=short_code,
        category=category,
        description=desc,
        latitude=float(lat),
        longitude=float(lon),
        campus_id=data.get('campus_id', 'thapar')
    )
    db.session.add(building)
    db.session.commit()

    # Automatically create a default entrance NavigationNode for this building
    node_key = f"n_bldg_{building.id}"
    node = NavigationNode(
        node_key=node_key,
        latitude=float(lat),
        longitude=float(lon),
        is_entrance=True,
        building_id=building.id,
        is_accessible=True,
        node_type='entrance',
        description=f"Entrance to {building.name}",
        campus_id=building.campus_id
    )
    db.session.add(node)
    db.session.commit()

    NavigationService.invalidate_graph()
    return success_response(building.to_dict(), message='Building and entrance node created successfully.', status_code=201)


@admin_bp.route('/locations/<int:building_id>', methods=['PUT'])
@login_required
@admin_required
def update_location(building_id):
    """PUT /api/admin/locations/<id> — update building properties."""
    building = Building.query.get_or_404(building_id)
    data = request.get_json(silent=True) or {}

    if 'name' in data:
        building.name = data['name'].strip()
    if 'short_code' in data:
        building.short_code = data['short_code'].strip().upper()
    if 'category' in data:
        building.category = data['category'].strip().upper()
    if 'description' in data:
        building.description = data['description']
    if 'latitude' in data:
        building.latitude = float(data['latitude'])
    if 'longitude' in data:
        building.longitude = float(data['longitude'])
    if 'is_active' in data:
        building.is_active = bool(data['is_active'])

    db.session.commit()
    NavigationService.invalidate_graph()
    return success_response(building.to_dict(), message='Building updated.')


@admin_bp.route('/locations/<int:building_id>', methods=['DELETE'])
@login_required
@admin_required
def delete_location(building_id):
    """DELETE /api/admin/locations/<id> — soft delete building."""
    building = Building.query.get_or_404(building_id)
    building.is_active = False
    db.session.commit()
    NavigationService.invalidate_graph()
    return success_response(message='Building deactivated.')


# --- Pathway & Closure Management ---

@admin_bp.route('/pathways', methods=['POST'])
@login_required
@admin_required
def create_pathway():
    """POST /api/admin/pathways — add new pathway between two nodes."""
    data = request.get_json(silent=True) or {}
    from_key = data.get('from_node_key')
    to_key = data.get('to_node_key')

    if not from_key or not to_key:
        return error_response('from_node_key and to_node_key are required.', status_code=400)

    from_node = NavigationNode.query.filter_by(node_key=from_key).first()
    to_node = NavigationNode.query.filter_by(node_key=to_key).first()

    if not from_node or not to_node:
        return error_response('Specified from_node or to_node does not exist.', status_code=404)

    # Compute distance
    from backend.app.utils.geo_utils import haversine_distance
    calc_dist = haversine_distance(from_node.latitude, from_node.longitude, to_node.latitude, to_node.longitude)
    dist = float(data.get('distance_meters') or calc_dist)
    est_sec = float(data.get('estimated_seconds') or (dist / 1.4))

    pathway = Pathway(
        from_node_id=from_node.id,
        to_node_id=to_node.id,
        distance_meters=round(dist, 1),
        estimated_seconds=round(est_sec, 1),
        is_bidirectional=bool(data.get('is_bidirectional', True)),
        has_stairs=bool(data.get('has_stairs', False)),
        has_ramp=bool(data.get('has_ramp', False)),
        is_accessible=bool(data.get('is_accessible', True)),
        surface_type=data.get('surface_type', 'concrete'),
        width_meters=float(data.get('width_meters', 2.5)),
        is_blocked=False,
        campus_id=data.get('campus_id', 'thapar')
    )
    db.session.add(pathway)
    db.session.commit()

    NavigationService.invalidate_graph()
    return success_response(pathway.to_dict(), message='Pathway created.', status_code=201)


@admin_bp.route('/pathways/<int:pathway_id>', methods=['PUT'])
@login_required
@admin_required
def update_pathway(pathway_id):
    """PUT /api/admin/pathways/<id> — update pathway accessibility or attributes."""
    pathway = Pathway.query.get_or_404(pathway_id)
    data = request.get_json(silent=True) or {}

    if 'is_accessible' in data:
        pathway.is_accessible = bool(data['is_accessible'])
    if 'has_stairs' in data:
        pathway.has_stairs = bool(data['has_stairs'])
    if 'has_ramp' in data:
        pathway.has_ramp = bool(data['has_ramp'])
    if 'surface_type' in data:
        pathway.surface_type = data['surface_type']
    if 'width_meters' in data:
        pathway.width_meters = float(data['width_meters'])
    if 'is_bidirectional' in data:
        pathway.is_bidirectional = bool(data['is_bidirectional'])

    db.session.commit()
    NavigationService.invalidate_graph()
    return success_response(pathway.to_dict(), message='Pathway updated.')


@admin_bp.route('/pathways/<int:pathway_id>/close', methods=['POST'])
@login_required
@admin_required
def close_pathway_admin(pathway_id):
    """POST /api/admin/pathways/<id>/close — block pathway."""
    from backend.app.routes.pathways_routes import close_pathway
    return close_pathway(pathway_id)


@admin_bp.route('/pathways/<int:pathway_id>/reopen', methods=['POST'])
@login_required
@admin_required
def reopen_pathway_admin(pathway_id):
    """POST /api/admin/pathways/<id>/reopen — unblock pathway."""
    from backend.app.routes.pathways_routes import reopen_pathway
    return reopen_pathway(pathway_id)


# --- Reports Management ---

@admin_bp.route('/reports', methods=['GET'])
@login_required
@admin_required
def get_reports():
    """GET /api/admin/reports — list user reports."""
    status = request.args.get('status')
    q = UserReport.query
    if status and status.upper() != 'ALL':
        q = q.filter_by(status=status.upper())
    reports = q.order_by(UserReport.submitted_at.desc()).all()
    results = []
    for r in reports:
        d = r.to_dict()
        if r.user_id:
            u = User.query.get(r.user_id)
            d['user_name'] = f"{u.first_name} {u.last_name}" if u else "Unknown"
            d['user_email'] = u.email if u else None
        else:
            d['user_name'] = 'Anonymous Visitor'
        results.append(d)
    return success_response(results)


@admin_bp.route('/reports/<int:report_id>', methods=['PUT'])
@login_required
@admin_required
def update_report(report_id):
    """PUT /api/admin/reports/<id> — update report status and admin notes."""
    report = UserReport.query.get_or_404(report_id)
    data = request.get_json(silent=True) or {}

    if 'status' in data:
        report.status = data['status'].upper()
    if 'admin_notes' in data:
        report.admin_notes = data['admin_notes']

    db.session.commit()
    return success_response(report.to_dict(), message='Report status updated.')


# --- Emergency Facilities Management ---

@admin_bp.route('/emergency', methods=['GET'])
@login_required
@admin_required
def get_emergency():
    """GET /api/admin/emergency — list emergency facilities."""
    facilities = EmergencyFacility.query.all()
    return success_response([f.to_dict() for f in facilities])


@admin_bp.route('/emergency', methods=['POST'])
@login_required
@admin_required
def create_emergency():
    """POST /api/admin/emergency — configure emergency facility."""
    data = request.get_json(silent=True) or {}
    name = (data.get('name') or '').strip()
    ftype = (data.get('facility_type') or 'MEDICAL').upper()
    lat = data.get('latitude')
    lon = data.get('longitude')
    contact = data.get('contact_number', '')
    desc = data.get('description', '')
    node_id = data.get('node_id')

    if not name or lat is None or lon is None:
        return error_response('name, latitude, and longitude are required.', status_code=400)

    facility = EmergencyFacility(
        name=name,
        facility_type=ftype,
        latitude=float(lat),
        longitude=float(lon),
        contact_number=contact,
        description=desc,
        node_id=node_id,
        is_active=True,
        campus_id='thapar'
    )
    db.session.add(facility)
    db.session.commit()
    return success_response(facility.to_dict(), message='Emergency facility added.', status_code=201)


# --- User Management ---

@admin_bp.route('/users', methods=['GET'])
@login_required
@admin_required
def get_users():
    """GET /api/admin/users — list registered users."""
    role = request.args.get('role')
    q = User.query
    if role and role.lower() != 'all':
        q = q.filter_by(role=role.lower())
    users = q.order_by(User.created_at.desc()).all()
    return success_response([u.to_dict() for u in users])


@admin_bp.route('/users/<int:user_id>/role', methods=['PUT'])
@login_required
@admin_required
def update_user_role(user_id):
    """PUT /api/admin/users/<id>/role — change user role."""
    user = User.query.get_or_404(user_id)
    data = request.get_json(silent=True) or {}
    new_role = (data.get('role') or '').lower()

    if new_role not in ('student', 'faculty', 'visitor', 'admin'):
        return error_response('Invalid role specified.', status_code=400)

    user.role = new_role
    db.session.commit()
    return success_response(user.to_dict(), message=f'User role updated to {new_role}.')
