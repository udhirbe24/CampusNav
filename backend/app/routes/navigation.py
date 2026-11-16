from flask import Blueprint

navigation_bp = Blueprint('navigation', __name__)

from backend.app.routes import navigation_routes  # noqa: F401, E402
