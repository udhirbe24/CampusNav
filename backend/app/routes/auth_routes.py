"""
Auth routes — register, login, logout, profile.
Blueprint: 'auth', registered at /api/auth
"""
from flask import request
from flask_login import login_user, logout_user, login_required, current_user
from datetime import datetime, timezone

from backend.app.routes.auth import auth_bp
from backend.app.models.user import User
from backend.app import db
from backend.app.utils.response_helpers import success_response, error_response
from backend.app.utils.validators import validate_email, validate_password_strength


@auth_bp.route('/register', methods=['POST'])
def register():
    """POST /api/auth/register — create a new user account."""
    data = request.get_json(silent=True) or {}
    email = (data.get('email') or '').strip().lower()
    password = data.get('password', '')
    first_name = (data.get('first_name') or '').strip()
    last_name = (data.get('last_name') or '').strip()
    role = data.get('role', 'visitor')
    student_id = data.get('student_id')

    # Validate inputs
    ok, err = validate_email(email)
    if not ok:
        return error_response(err, status_code=400)

    ok, err = validate_password_strength(password)
    if not ok:
        return error_response(err, status_code=400)

    if not first_name or not last_name:
        return error_response('First name and last name are required.', status_code=400)

    if role not in ('student', 'faculty', 'visitor', 'admin'):
        role = 'visitor'

    # Check uniqueness
    if User.query.filter_by(email=email).first():
        return error_response('An account with this email already exists.', status_code=400)

    user = User(
        email=email,
        first_name=first_name,
        last_name=last_name,
        role=role,
        student_id=student_id,
        department=data.get('department'),
    )
    user.set_password(password)
    db.session.add(user)
    db.session.commit()
    return success_response(user.to_dict(), message='Account created successfully.', status_code=201)


@auth_bp.route('/login', methods=['POST'])
def login():
    """POST /api/auth/login — authenticate and start session."""
    data = request.get_json(silent=True) or {}
    email = (data.get('email') or '').strip().lower()
    password = data.get('password', '')

    user = User.query.filter_by(email=email).first()
    if not user or not user.check_password(password):
        return error_response('Invalid email or password.', status_code=401)
    if not user.is_active:
        return error_response('Account is deactivated. Contact admin.', status_code=403)

    login_user(user)
    user.last_login = datetime.now(timezone.utc)
    db.session.commit()
    return success_response(user.to_dict(), message='Logged in successfully.')


@auth_bp.route('/logout', methods=['POST'])
@login_required
def logout():
    """POST /api/auth/logout — end the current session."""
    logout_user()
    return success_response(message='Logged out successfully.')


@auth_bp.route('/me', methods=['GET'])
@login_required
def me():
    """GET /api/auth/me — return current authenticated user."""
    return success_response(current_user.to_dict())


@auth_bp.route('/me', methods=['PUT'])
@login_required
def update_profile():
    """PUT /api/auth/me — update current user's profile fields."""
    data = request.get_json(silent=True) or {}
    current_user.first_name = (data.get('first_name') or current_user.first_name).strip()
    current_user.last_name = (data.get('last_name') or current_user.last_name).strip()
    if 'department' in data:
        current_user.department = data['department']
    db.session.commit()
    return success_response(current_user.to_dict(), message='Profile updated.')
