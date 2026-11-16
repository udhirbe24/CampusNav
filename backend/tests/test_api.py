"""
Integration tests for CampusNav REST APIs:
- Locations API
- Navigation API (Dijkstra, A*, shortest, fastest, accessible, alternative, emergency)
- Pathways API & Closure Reporting
- Admin Analytics & Role Access Controls
"""
import pytest
from backend.app.models.user import User
from backend.app.models.location import Building, NavigationNode
from backend.app.models.pathway import Pathway
from backend.app.models.emergency import EmergencyFacility


@pytest.fixture(autouse=True)
def seed_test_data(db):
    """Seed minimal campus network for API testing idempotently."""
    # Seed Admin User
    admin = User.query.filter_by(email='admin@thapar.edu').first()
    if not admin:
        admin = User(
            email='admin@thapar.edu',
            first_name='Admin',
            last_name='User',
            role='admin',
            is_active=True
        )
        admin.set_password('AdminPassword123!')
        db.session.add(admin)

    # Seed Buildings if not present
    b1 = Building.query.filter_by(short_code='AB1').first()
    if not b1:
        b1 = Building(name='Academic Block A', short_code='AB1', category='ACADEMIC', latitude=30.3528, longitude=76.3634, campus_id='thapar')
        db.session.add(b1)

    b2 = Building.query.filter_by(short_code='LIB').first()
    if not b2:
        b2 = Building(name='Chern Library', short_code='LIB', category='LIBRARY', latitude=30.3522, longitude=76.3627, campus_id='thapar')
        db.session.add(b2)

    b3 = Building.query.filter_by(short_code='MED').first()
    if not b3:
        b3 = Building(name='Medical Centre', short_code='MED', category='MEDICAL', latitude=30.3518, longitude=76.3638, campus_id='thapar')
        db.session.add(b3)

    db.session.flush()

    # Seed Nodes if not present
    n1 = NavigationNode.query.filter_by(node_key='n_ab1').first()
    if not n1:
        n1 = NavigationNode(node_key='n_ab1', latitude=30.3528, longitude=76.3634, is_entrance=True, building_id=b1.id, campus_id='thapar')
        db.session.add(n1)

    n2 = NavigationNode.query.filter_by(node_key='n_inter').first()
    if not n2:
        n2 = NavigationNode(node_key='n_inter', latitude=30.3525, longitude=76.3630, is_entrance=False, node_type='intersection', campus_id='thapar')
        db.session.add(n2)

    n3 = NavigationNode.query.filter_by(node_key='n_lib').first()
    if not n3:
        n3 = NavigationNode(node_key='n_lib', latitude=30.3522, longitude=76.3627, is_entrance=True, building_id=b2.id, campus_id='thapar')
        db.session.add(n3)

    n4 = NavigationNode.query.filter_by(node_key='n_med').first()
    if not n4:
        n4 = NavigationNode(node_key='n_med', latitude=30.3518, longitude=76.3638, is_entrance=True, building_id=b3.id, campus_id='thapar')
        db.session.add(n4)

    db.session.flush()

    # Seed Pathways if not present
    p1 = Pathway.query.filter_by(from_node_id=n1.id, to_node_id=n2.id).first()
    if not p1:
        p1 = Pathway(from_node_id=n1.id, to_node_id=n2.id, distance_meters=60.0, estimated_seconds=43.0, is_bidirectional=True, is_accessible=True, campus_id='thapar')
        db.session.add(p1)

    p2 = Pathway.query.filter_by(from_node_id=n2.id, to_node_id=n3.id).first()
    if not p2:
        p2 = Pathway(from_node_id=n2.id, to_node_id=n3.id, distance_meters=50.0, estimated_seconds=36.0, is_bidirectional=True, is_accessible=True, campus_id='thapar')
        db.session.add(p2)

    p3 = Pathway.query.filter_by(from_node_id=n2.id, to_node_id=n4.id).first()
    if not p3:
        p3 = Pathway(from_node_id=n2.id, to_node_id=n4.id, distance_meters=90.0, estimated_seconds=64.0, is_bidirectional=True, is_accessible=True, campus_id='thapar')
        db.session.add(p3)

    # Seed Emergency Facility
    ef = EmergencyFacility.query.filter_by(name='Campus Health Clinic').first()
    if not ef:
        ef = EmergencyFacility(name='Campus Health Clinic', facility_type='MEDICAL', latitude=30.3518, longitude=76.3638, node_id=n4.id, is_active=True, campus_id='thapar')
        db.session.add(ef)

    db.session.commit()

    # Ensure NavigationService reloads the graph
    from backend.app.services.navigation_service import NavigationService
    NavigationService.invalidate_graph()


def test_health_endpoint(client):
    res = client.get('/api/health')
    assert res.status_code == 200
    data = res.get_json()
    assert data['status'] == 'healthy'
    assert data['campus'] == 'thapar'


def test_get_locations(client):
    res = client.get('/api/locations')
    assert res.status_code == 200
    data = res.get_json()
    assert data['success'] is True
    assert len(data['data']) >= 3


def test_search_locations(client):
    res = client.get('/api/locations/search?q=Library')
    assert res.status_code == 200
    data = res.get_json()
    assert len(data['data']) >= 1
    assert 'Library' in data['data'][0]['name']


def test_route_calculation_dijkstra(client):
    payload = {
        'from_node': 'n_ab1',
        'to_node': 'n_lib',
        'algorithm': 'dijkstra',
        'mode': 'shortest'
    }
    res = client.post('/api/routes/calculate', json=payload)
    assert res.status_code == 200
    data = res.get_json()
    assert data['success'] is True
    assert data['data']['path_nodes'] == ['n_ab1', 'n_inter', 'n_lib']
    assert data['data']['summary']['total_distance_meters'] == 110.0
    assert len(data['data']['steps']) >= 2


def test_route_calculation_astar(client):
    payload = {
        'from_node': 'n_ab1',
        'to_node': 'n_lib',
        'algorithm': 'astar',
        'mode': 'shortest'
    }
    res = client.post('/api/routes/calculate', json=payload)
    assert res.status_code == 200
    data = res.get_json()
    assert data['success'] is True
    assert data['data']['summary']['algorithm'] == 'astar'


def test_emergency_navigation(client):
    payload = {
        'from_node': 'n_ab1',
        'facility_type': 'MEDICAL'
    }
    res = client.post('/api/routes/emergency', json=payload)
    assert res.status_code == 200
    data = res.get_json()
    assert data['success'] is True
    assert data['data']['emergency_facility']['name'] == 'Campus Health Clinic'


def test_pathway_report(client):
    payload = {
        'report_type': 'CONSTRUCTION',
        'description': 'Water pipe maintenance near Library entrance.'
    }
    res = client.post('/api/pathways/report', json=payload)
    assert res.status_code == 201
    data = res.get_json()
    assert data['success'] is True


def test_admin_analytics_requires_auth(client):
    res = client.get('/api/admin/analytics')
    assert res.status_code in (401, 403)


def test_admin_analytics_with_admin_login(client):
    # Log in as admin
    login_res = client.post('/api/auth/login', json={
        'email': 'admin@thapar.edu',
        'password': 'AdminPassword123!'
    })
    assert login_res.status_code == 200

    res = client.get('/api/admin/analytics')
    assert res.status_code == 200
    data = res.get_json()
    assert data['success'] is True
    assert 'overview' in data['data']
    assert data['data']['overview']['total_buildings'] >= 3
