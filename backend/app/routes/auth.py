from flask import Blueprint

auth_bp = Blueprint('auth', __name__)

from backend.app.routes import auth_routes  # noqa: F401, E402
