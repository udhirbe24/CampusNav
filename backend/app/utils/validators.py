import re


EMAIL_REGEX = re.compile(r'^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$')


def validate_email(email):
    """Return (is_valid: bool, error_message: str|None)."""
    if not email or not isinstance(email, str):
        return False, 'Email is required.'
    email = email.strip()
    if len(email) > 255:
        return False, 'Email must be 255 characters or fewer.'
    if not EMAIL_REGEX.match(email):
        return False, 'Invalid email format.'
    return True, None


def validate_password_strength(password):
    """
    Return (is_valid: bool, error_message: str|None).
    Requirements: at least 8 characters, one uppercase, one lowercase, one digit.
    """
    if not password or not isinstance(password, str):
        return False, 'Password is required.'
    if len(password) < 8:
        return False, 'Password must be at least 8 characters long.'
    if not re.search(r'[A-Z]', password):
        return False, 'Password must contain at least one uppercase letter.'
    if not re.search(r'[a-z]', password):
        return False, 'Password must contain at least one lowercase letter.'
    if not re.search(r'\d', password):
        return False, 'Password must contain at least one digit.'
    return True, None


def validate_coordinates(lat, lon):
    """
    Return (is_valid: bool, error_message: str|None).
    Validates latitude (-90..90) and longitude (-180..180), and that the
    coordinates are within or near Thapar campus bounds.
    """
    try:
        lat = float(lat)
        lon = float(lon)
    except (TypeError, ValueError):
        return False, 'Latitude and longitude must be numeric values.'
    if not (-90 <= lat <= 90):
        return False, 'Latitude must be between -90 and 90.'
    if not (-180 <= lon <= 180):
        return False, 'Longitude must be between -180 and 180.'
    return True, None


def validate_node_key(node_key):
    """Return (is_valid: bool, error_message: str|None)."""
    if not node_key or not isinstance(node_key, str):
        return False, 'Node key is required.'
    if not re.match(r'^[a-zA-Z0-9_\-]{1,50}$', node_key):
        return False, 'Node key must be alphanumeric (underscores and hyphens allowed), max 50 chars.'
    return True, None
