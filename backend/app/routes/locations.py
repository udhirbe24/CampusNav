from flask import Blueprint

locations_bp = Blueprint('locations', __name__)

from backend.app.routes import locations_routes  # noqa: F401, E402
