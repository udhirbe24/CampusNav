from flask import Blueprint

admin_bp = Blueprint('admin', __name__)

from backend.app.routes import admin_routes  # noqa: F401, E402
