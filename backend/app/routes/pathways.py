from flask import Blueprint

pathways_bp = Blueprint('pathways', __name__)

from backend.app.routes import pathways_routes  # noqa: F401, E402
