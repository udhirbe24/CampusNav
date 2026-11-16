from backend.app import db


class EmergencyFacility(db.Model):
    __tablename__ = 'emergency_facilities'

    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(200), nullable=False)
    facility_type = db.Column(db.String(30), nullable=False)
    # facility_type: 'MEDICAL' | 'SECURITY' | 'FIRE' | 'ASSEMBLY'
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    contact_number = db.Column(db.String(30), nullable=True)
    description = db.Column(db.Text, nullable=True)
    node_id = db.Column(db.Integer, db.ForeignKey('navigation_nodes.id'), nullable=True)
    is_active = db.Column(db.Boolean, default=True, nullable=False)
    campus_id = db.Column(db.String(50), nullable=False, default='thapar')

    # Relationships
    node = db.relationship('NavigationNode', backref='emergency_facility', uselist=False)

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'facility_type': self.facility_type,
            'latitude': self.latitude,
            'longitude': self.longitude,
            'contact_number': self.contact_number,
            'description': self.description,
            'node_id': self.node_id,
            'is_active': self.is_active,
            'campus_id': self.campus_id,
        }

    def __repr__(self):
        return f'<EmergencyFacility {self.name} ({self.facility_type})>'
