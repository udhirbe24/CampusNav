"""
Pathways routes — list pathways, view active closures, report pathway issues, close/reopen.
Blueprint: 'pathways', registered at /api/pathways
"""
from flask import request
from flask_login import login_required, current_user
from datetime import datetime, timezone

from backend.app.routes.pathways import pathways_bp
from backend.app.models.pathway import Pathway, PathwayClosure
from backend.app.models.report import UserReport
from backend.app.services.campus_data_service import CampusDataService
from backend.app.services.navigation_service import NavigationService
from backend.app import db
from backend.app.utils.response_helpers import success_response, error_response


@pathways_bp.route('', methods=['GET'])
@pathways_bp.route('/', methods=['GET'])
def get_pathways():
    """GET /api/pathways — list all pathways with connectivity and status."""
    campus_id = request.args.get('campus_id', 'thapar')
    pathways = CampusDataService.get_all_pathways(campus_id=campus_id)
    return success_response(pathways)


@pathways_bp.route('/<int:pathway_id>', methods=['GET'])
def get_pathway(pathway_id):
    """GET /api/pathways/<id> — retrieve single pathway details."""
    pathway = Pathway.query.get_or_404(pathway_id)
    return success_response(pathway.to_dict())


@pathways_bp.route('/closures', methods=['GET'])
def get_closures():
    """GET /api/pathways/closures — list currently active pathway closures."""
    campus_id = request.args.get('campus_id', 'thapar')
    closures = CampusDataService.get_active_closures(campus_id=campus_id)
    return success_response(closures)


@pathways_bp.route('/report', methods=['POST'])
def report_pathway():
    """
    POST /api/pathways/report — submit an issue or blockage report.
    Can be called by logged-in users or visitors.
    Payload: { pathway_id (optional), report_type, description, location_details }
    """
    data = request.get_json(silent=True) or {}
    report_type = data.get('report_type', 'OBSTRUCTION')
    description = (data.get('description') or '').strip()
    pathway_id = data.get('pathway_id')

    if not description:
        return error_response('Description is required for user reports.', status_code=400)

    user_id = current_user.id if current_user.is_authenticated else None

    report = UserReport(
        user_id=user_id,
        pathway_id=pathway_id,
        report_type=report_type,
        description=description,
        status='PENDING',
        submitted_at=datetime.now(timezone.utc)
    )

    try:
        db.session.add(report)
        db.session.commit()
        return success_response(report.to_dict(), message='Report submitted successfully. Thank you for keeping campus pathways safe!', status_code=201)
    except Exception as e:
        db.session.rollback()
        return error_response(f'Could not submit report: {str(e)}', status_code=500)


@pathways_bp.route('/<int:pathway_id>/close', methods=['POST'])
@login_required
def close_pathway(pathway_id):
    """POST /api/pathways/<id>/close — mark pathway closed and invalidate routing cache."""
    if current_user.role != 'admin':
        return error_response('Administrator privileges required.', status_code=403)

    pathway = Pathway.query.get_or_404(pathway_id)
    data = request.get_json(silent=True) or {}
    reason = data.get('reason', 'Temporary Maintenance')
    description = data.get('description', '')

    closure = PathwayClosure(
        pathway_id=pathway.id,
        reason=reason,
        description=description,
        closed_by_id=current_user.id,
        closed_at=datetime.now(timezone.utc),
        is_active=True
    )
    pathway.is_blocked = True
    pathway.closure_reason = reason

    db.session.add(closure)
    db.session.commit()

    # CRITICAL: Invalidate the graph cache so new route calculations avoid this pathway
    NavigationService.invalidate_graph()

    return success_response(closure.to_dict(), message='Pathway has been marked as blocked. Routing engine updated.')


@pathways_bp.route('/<int:pathway_id>/reopen', methods=['POST'])
@login_required
def reopen_pathway(pathway_id):
    """POST /api/pathways/<id>/reopen — clear closure and restore pathway in routing graph."""
    if current_user.role != 'admin':
        return error_response('Administrator privileges required.', status_code=403)

    pathway = Pathway.query.get_or_404(pathway_id)
    pathway.is_blocked = False
    pathway.closure_reason = None

    active_closures = PathwayClosure.query.filter_by(pathway_id=pathway_id, is_active=True).all()
    for c in active_closures:
        c.is_active = False
        c.actual_reopen = datetime.now(timezone.utc)

    db.session.commit()

    # Invalidate graph cache
    NavigationService.invalidate_graph()

    return success_response(pathway.to_dict(), message='Pathway restored to active service. Routing engine updated.')
