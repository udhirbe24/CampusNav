import os
from flask import Flask, jsonify, send_from_directory, render_template_string
from flask_sqlalchemy import SQLAlchemy
from flask_login import LoginManager
from flask_cors import CORS

from backend.config import config

db = SQLAlchemy()
login_manager = LoginManager()


def create_app(config_name='development'):
    frontend_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'frontend'))
    app = Flask(
        __name__,
        instance_relative_config=True,
        static_folder=frontend_dir,
        static_url_path=''
    )

    # Load configuration
    app.config.from_object(config[config_name])

    # Ensure instance folder exists
    os.makedirs(app.instance_path, exist_ok=True)

    # Initialize extensions
    db.init_app(app)
    login_manager.init_app(app)
    login_manager.login_view = 'auth.login'
    login_manager.login_message_category = 'info'

    @login_manager.unauthorized_handler
    def unauthorized_callback():
        return jsonify({'success': False, 'message': 'Authentication required. Please log in.'}), 401

    CORS(app, origins=app.config.get('CORS_ORIGINS', '*'), supports_credentials=True)

    # Import and register blueprints
    from backend.app.routes.auth import auth_bp
    from backend.app.routes.locations import locations_bp
    from backend.app.routes.navigation import navigation_bp
    from backend.app.routes.pathways import pathways_bp
    from backend.app.routes.admin import admin_bp

    app.register_blueprint(auth_bp, url_prefix='/api/auth')
    app.register_blueprint(locations_bp, url_prefix='/api/locations')
    app.register_blueprint(navigation_bp, url_prefix='/api/navigation')
    app.register_blueprint(navigation_bp, url_prefix='/api/routes', name='routes_alias_bp')
    app.register_blueprint(pathways_bp, url_prefix='/api/pathways')
    app.register_blueprint(admin_bp, url_prefix='/api/admin')

    # Register user loader
    from backend.app.models.user import User

    @login_manager.user_loader
    def load_user(user_id):
        return User.query.get(int(user_id))

    # Health check endpoint
    @app.route('/api/health', methods=['GET'])
    def health():
        from backend.app.models.location import Building, NavigationNode
        from backend.app.models.pathway import Pathway
        return jsonify({
            'status': 'healthy',
            'campus': 'thapar',
            'buildings': Building.query.count(),
            'nodes': NavigationNode.query.count(),
            'pathways': Pathway.query.count()
        }), 200

    # Serve Frontend Pages directly
    @app.route('/')
    def serve_index():
        return send_from_directory(frontend_dir, 'index.html')

    @app.route('/app')
    @app.route('/navigate')
    def serve_app():
        return send_from_directory(frontend_dir, 'app.html')

    @app.route('/auth')
    @app.route('/login')
    @app.route('/register')
    def serve_auth():
        return send_from_directory(frontend_dir, 'auth.html')

    @app.route('/admin')
    def serve_admin():
        return send_from_directory(frontend_dir, 'admin.html')

    # JSON error handlers for API
    @app.errorhandler(400)
    def bad_request(e):
        return jsonify({'success': False, 'message': 'Bad request', 'error': str(e)}), 400

    @app.errorhandler(401)
    def unauthorized(e):
        return jsonify({'success': False, 'message': 'Unauthorized. Please log in.', 'error': str(e)}), 401

    @app.errorhandler(403)
    def forbidden(e):
        return jsonify({'success': False, 'message': 'Forbidden. Insufficient permissions.', 'error': str(e)}), 403

    @app.errorhandler(404)
    def not_found(e):
        return jsonify({'success': False, 'message': 'Resource not found', 'error': str(e)}), 404

    @app.errorhandler(500)
    def internal_error(e):
        db.session.rollback()
        return jsonify({'success': False, 'message': 'Internal server error', 'error': str(e)}), 500

    return app
