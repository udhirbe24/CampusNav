"""
test_models.py — basic sanity tests for CampusNav models.
"""
from backend.app.models.user import User
from backend.app.models.location import Building, NavigationNode


def test_user_password_hashing(db):
    u = User(email='test@thapar.edu', first_name='Test', last_name='User', role='student')
    u.set_password('Secret@123')
    assert u.check_password('Secret@123')
    assert not u.check_password('wrongpassword')


def test_user_to_dict(db):
    u = User(email='dict@thapar.edu', first_name='Dict', last_name='User', role='visitor')
    u.set_password('Pass@word1')
    db.session.add(u)
    db.session.commit()
    d = u.to_dict()
    assert d['email'] == 'dict@thapar.edu'
    assert 'password_hash' not in d


def test_building_to_dict(db):
    b = Building(
        name='Test Building',
        short_code='TST',
        category='ACADEMIC',
        latitude=30.3528,
        longitude=76.3634,
        campus_id='thapar',
    )
    db.session.add(b)
    db.session.commit()
    d = b.to_dict()
    assert d['short_code'] == 'TST'
    assert d['campus_id'] == 'thapar'
