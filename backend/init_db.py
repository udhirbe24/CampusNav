#!/usr/bin/env python3
"""
init_db.py — Initialise the CampusNav database with schema and seed data.

Usage:
    python backend/init_db.py [--reset]

Options:
    --reset   Drop all tables before recreating them (default behaviour).
"""
import os
import sys
import json

# Allow running from project root
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.app import create_app, db
from backend.app.models.user import User
from backend.app.models.location import Building, NavigationNode
from backend.app.models.pathway import Pathway
from backend.app.models.emergency import EmergencyFacility


DATA_DIR = os.path.join(os.path.dirname(__file__), 'data')


def load_json(filename):
    path = os.path.join(DATA_DIR, filename)
    if not os.path.exists(path):
        print(f'  [WARN] Data file not found: {path}')
        return None
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def seed_buildings(data):
    """Insert buildings from thapar_locations.json."""
    buildings_data = data.get('buildings', [])
    building_map = {}  # external id -> db id
    for b in buildings_data:
        existing = Building.query.filter_by(short_code=b['short_code']).first()
        if existing:
            building_map[b['id']] = existing.id
            continue
        building = Building(
            name=b['name'],
            short_code=b['short_code'],
            category=b['category'],
            description=b.get('description', ''),
            latitude=b['latitude'],
            longitude=b['longitude'],
            campus_id=data.get('campus_id', 'thapar'),
        )
        db.session.add(building)
        db.session.flush()
        building_map[b['id']] = building.id
    db.session.commit()
    print(f'  Buildings: {len(buildings_data)} processed, {Building.query.count()} in DB.')
    return building_map


def seed_nodes(geojson, building_map):
    """Insert NavigationNodes from thapar_nodes.geojson."""
    features = geojson.get('features', [])
    node_map = {}  # node_key -> db id
    for f in features:
        props = f.get('properties', {})
        coords = f['geometry']['coordinates']  # [lon, lat]
        lon, lat = coords[0], coords[1]
        node_key = props['node_key']
        existing = NavigationNode.query.filter_by(node_key=node_key).first()
        if existing:
            node_map[node_key] = existing.id
            continue

        ext_building_id = props.get('building_id')
        db_building_id = building_map.get(ext_building_id) if ext_building_id else None

        node = NavigationNode(
            node_key=node_key,
            latitude=lat,
            longitude=lon,
            is_entrance=props.get('node_type') == 'entrance',
            building_id=db_building_id,
            is_accessible=props.get('is_accessible', True),
            node_type=props.get('node_type', 'intersection'),
            description=props.get('description', ''),
            campus_id='thapar',
        )
        db.session.add(node)
        db.session.flush()
        node_map[node_key] = node.id
    db.session.commit()
    print(f'  Nodes: {len(features)} processed, {NavigationNode.query.count()} in DB.')
    return node_map


def seed_pathways(geojson, node_map):
    """Insert Pathways from thapar_pathways.geojson."""
    features = geojson.get('features', [])
    inserted = 0
    for f in features:
        props = f.get('properties', {})
        from_key = props['from_node']
        to_key = props['to_node']
        from_id = node_map.get(from_key)
        to_id = node_map.get(to_key)
        if not from_id or not to_id:
            print(f'  [WARN] Skipping pathway {props.get("pathway_key")}: missing node {from_key} or {to_key}')
            continue
        distance = float(props.get('distance_meters', 50.0))
        estimated = distance / 1.4
        pathway = Pathway(
            from_node_id=from_id,
            to_node_id=to_id,
            distance_meters=distance,
            estimated_seconds=estimated,
            is_bidirectional=props.get('is_bidirectional', True),
            has_stairs=props.get('has_stairs', False),
            has_ramp=props.get('has_ramp', False),
            is_accessible=props.get('is_accessible', True),
            surface_type=props.get('surface_type', 'concrete'),
            campus_id='thapar',
        )
        db.session.add(pathway)
        inserted += 1
    db.session.commit()
    print(f'  Pathways: {inserted} inserted, {Pathway.query.count()} in DB.')


def seed_admin():
    """Create or update the admin user."""
    admin_email = os.environ.get('ADMIN_EMAIL', 'admin@thapar.edu')
    admin_password = os.environ.get('ADMIN_PASSWORD', 'AdminPassword123!')
    existing = User.query.filter_by(email=admin_email).first()
    if existing:
        existing.set_password(admin_password)
        db.session.commit()
        print(f'  Admin user updated with current password: {admin_email}')
        return
    admin = User(
        email=admin_email,
        first_name='Campus',
        last_name='Admin',
        role='admin',
    )
    admin.set_password(admin_password)
    db.session.add(admin)
    db.session.commit()
    print(f'  Admin user created: {admin_email}')


def seed_emergency_facilities(node_map):
    """Seed basic emergency facilities."""
    facilities = [
        {
            'name': 'Medical Centre',
            'facility_type': 'MEDICAL',
            'latitude': 30.3518,
            'longitude': 76.3638,
            'contact_number': '+91-175-2393021',
            'description': 'Campus health centre and first aid.',
            'node_key': 'n009',
        },
        {
            'name': 'Security Office (Main Gate)',
            'facility_type': 'SECURITY',
            'latitude': 30.3539,
            'longitude': 76.3648,
            'contact_number': '+91-175-2393000',
            'description': 'Campus security and visitor management.',
            'node_key': 'n020',
        },
        {
            'name': 'Assembly Point A',
            'facility_type': 'ASSEMBLY',
            'latitude': 30.3526,
            'longitude': 76.3630,
            'contact_number': None,
            'description': 'Emergency assembly point near auditorium.',
            'node_key': 'n016',
        },
        {
            'name': 'Fire Point - Academic Zone',
            'facility_type': 'FIRE',
            'latitude': 30.3528,
            'longitude': 76.3634,
            'contact_number': '101',
            'description': 'Fire extinguisher and hose reel station.',
            'node_key': 'n001',
        },
    ]
    for ef in facilities:
        if EmergencyFacility.query.filter_by(name=ef['name']).first():
            continue
        node_id = node_map.get(ef.pop('node_key'))
        facility = EmergencyFacility(
            node_id=node_id,
            campus_id='thapar',
            **ef
        )
        db.session.add(facility)
    db.session.commit()
    print(f'  Emergency facilities: {EmergencyFacility.query.count()} in DB.')


def main():
    app = create_app('development')
    with app.app_context():
        print('=== CampusNav Database Initialisation ===')
        print('Dropping and recreating all tables...')
        db.drop_all()
        db.create_all()
        print('Tables created.')

        # Admin
        print('\n[1/5] Seeding admin user...')
        seed_admin()

        # Buildings
        print('\n[2/5] Seeding buildings...')
        locations_data = load_json('thapar_locations.json')
        building_map = {}
        if locations_data:
            building_map = seed_buildings(locations_data)

        # Nodes
        print('\n[3/5] Seeding navigation nodes...')
        nodes_geojson = load_json('thapar_nodes.geojson')
        node_map = {}
        if nodes_geojson:
            node_map = seed_nodes(nodes_geojson, building_map)

        # Pathways
        print('\n[4/5] Seeding pathways...')
        pathways_geojson = load_json('thapar_pathways.geojson')
        if pathways_geojson:
            seed_pathways(pathways_geojson, node_map)

        # Emergency
        print('\n[5/5] Seeding emergency facilities...')
        seed_emergency_facilities(node_map)

        print('\n=== Initialisation complete! ===')
        print(f'  Users:       {User.query.count()}')
        print(f'  Buildings:   {Building.query.count()}')
        print(f'  Nodes:       {NavigationNode.query.count()}')
        print(f'  Pathways:    {Pathway.query.count()}')
        print(f'  Emergency:   {EmergencyFacility.query.count()}')


if __name__ == '__main__':
    main()
