"""
Authentication Service for CampusNav.
Handles user registration, authentication, role authorization, and session verification.
"""
from typing import Dict, Optional, Tuple, Any
from datetime import datetime, timezone

from backend.app import db
from backend.app.models.user import User
from backend.app.utils.validators import validate_email, validate_password_strength


class AuthService:
    """Manages user authentication and account lifecycle."""

    @classmethod
    def register_user(cls, data: Dict[str, Any]) -> Tuple[bool, Any]:
        """
        Register a new user.
        data: {email, password, first_name, last_name, role, student_id, department}
        Returns: (success: bool, user_or_error_dict)
        """
        email = (data.get('email') or '').strip().lower()
        password = data.get('password') or ''
        first_name = (data.get('first_name') or '').strip()
        last_name = (data.get('last_name') or '').strip()
        role = (data.get('role') or 'visitor').strip().lower()
        student_id = data.get('student_id')
        department = data.get('department')

        if not email:
            return False, {'email': 'Email address is required.'}
        if not validate_email(email):
            return False, {'email': 'Please enter a valid email address.'}

        # Check existing email
        if User.query.filter_by(email=email).first():
            return False, {'email': 'An account with this email address already exists.'}

        # Validate password strength
        is_strong, msg = validate_password_strength(password)
        if not is_strong:
            return False, {'password': msg}

        # Role validation
        allowed_roles = {'student', 'faculty', 'visitor', 'admin'}
        if role not in allowed_roles:
            role = 'visitor'

        user = User(
            email=email,
            first_name=first_name,
            last_name=last_name,
            role=role,
            student_id=student_id,
            department=department,
            created_at=datetime.now(timezone.utc),
            is_active=True
        )
        user.set_password(password)

        try:
            db.session.add(user)
            db.session.commit()
            return True, user
        except Exception as e:
            db.session.rollback()
            return False, {'general': f'Registration failed: {str(e)}'}

    @classmethod
    def authenticate_user(cls, email: str, password: str) -> Tuple[bool, Optional[User], str]:
        """
        Authenticate user with email and password.
        Returns: (success: bool, user: Optional[User], error_message: str)
        """
        email = (email or '').strip().lower()
        if not email or not password:
            return False, None, 'Email and password are required.'

        user = User.query.filter_by(email=email).first()
        if not user:
            return False, None, 'Invalid email or password.'

        if not user.is_active:
            return False, None, 'This account has been deactivated. Please contact an administrator.'

        if not user.check_password(password):
            return False, None, 'Invalid email or password.'

        # Update last login timestamp
        try:
            user.last_login = datetime.now(timezone.utc)
            db.session.commit()
        except Exception:
            db.session.rollback()

        return True, user, 'Authentication successful.'
