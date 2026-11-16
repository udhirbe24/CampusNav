from datetime import datetime, timezone
from backend.app import db


class Building(db.Model):
    __tablename__ = 'buildings'

    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(200), nullable=False)
    short_code = db.Column(db.String(20), nullable=False, unique=True)
    category = db.Column(db.String(50), nullable=False)
    # categories: ACADEMIC, ADMIN, LIBRARY, HOSTEL, CAFETERIA, SPORTS, MEDICAL, PARKING, OTHER
    description = db.Column(db.Text, nullable=True)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    campus_id = db.Column(db.String(50), nullable=False, default='thapar')
    is_active = db.Column(db.Boolean, default=True, nullable=False)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    # Relationships
    locations = db.relationship('CampusLocation', back_populates='building', lazy='dynamic')
    nodes = db.relationship('NavigationNode', back_populates='building', lazy='dynamic')

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'short_code': self.short_code,
            'category': self.category,
            'description': self.description,
            'latitude': self.latitude,
            'longitude': self.longitude,
            'campus_id': self.campus_id,
            'is_active': self.is_active,
        }

    def __repr__(self):
        return f'<Building {self.short_code}: {self.name}>'


class CampusLocation(db.Model):
    __tablename__ = 'campus_locations'

    id = db.Column(db.Integer, primary_key=True)
    building_id = db.Column(db.Integer, db.ForeignKey('buildings.id'), nullable=False)
    name = db.Column(db.String(200), nullable=False)
    floor = db.Column(db.Integer, nullable=True, default=0)
    room_number = db.Column(db.String(20), nullable=True)
    description = db.Column(db.Text, nullable=True)
    category = db.Column(db.String(50), nullable=True)
    latitude = db.Column(db.Float, nullable=True)
    longitude = db.Column(db.Float, nullable=True)
    is_active = db.Column(db.Boolean, default=True, nullable=False)

    # Relationships
    building = db.relationship('Building', back_populates='locations')

    def to_dict(self):
        return {
            'id': self.id,
            'building_id': self.building_id,
            'building_name': self.building.name if self.building else None,
            'name': self.name,
            'floor': self.floor,
            'room_number': self.room_number,
            'description': self.description,
            'category': self.category,
            'latitude': self.latitude,
            'longitude': self.longitude,
            'is_active': self.is_active,
        }

    def __repr__(self):
        return f'<CampusLocation {self.name}>'


class NavigationNode(db.Model):
    __tablename__ = 'navigation_nodes'

    id = db.Column(db.Integer, primary_key=True)
    node_key = db.Column(db.String(50), unique=True, nullable=False, index=True)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    is_entrance = db.Column(db.Boolean, default=False, nullable=False)
    building_id = db.Column(db.Integer, db.ForeignKey('buildings.id'), nullable=True)
    is_accessible = db.Column(db.Boolean, default=True, nullable=False)
    node_type = db.Column(db.String(30), nullable=False, default='intersection')
    # node_type: 'entrance' | 'intersection' | 'landmark' | 'emergency'
    description = db.Column(db.String(200), nullable=True)
    campus_id = db.Column(db.String(50), nullable=False, default='thapar')

    # Relationships
    building = db.relationship('Building', back_populates='nodes')
    outgoing_pathways = db.relationship('Pathway', foreign_keys='Pathway.from_node_id',
                                        back_populates='from_node', lazy='dynamic')
    incoming_pathways = db.relationship('Pathway', foreign_keys='Pathway.to_node_id',
                                        back_populates='to_node', lazy='dynamic')

    def to_dict(self):
        return {
            'id': self.id,
            'node_key': self.node_key,
            'latitude': self.latitude,
            'longitude': self.longitude,
            'is_entrance': self.is_entrance,
            'building_id': self.building_id,
            'building_name': self.building.name if self.building else None,
            'is_accessible': self.is_accessible,
            'node_type': self.node_type,
            'description': self.description,
            'campus_id': self.campus_id,
        }

    def __repr__(self):
        return f'<NavigationNode {self.node_key}>'
