from datetime import datetime, timezone
from backend.app import db


class Pathway(db.Model):
    __tablename__ = 'pathways'

    id = db.Column(db.Integer, primary_key=True)
    from_node_id = db.Column(db.Integer, db.ForeignKey('navigation_nodes.id'), nullable=False)
    to_node_id = db.Column(db.Integer, db.ForeignKey('navigation_nodes.id'), nullable=False)
    distance_meters = db.Column(db.Float, nullable=False)
    estimated_seconds = db.Column(db.Float, nullable=False)
    is_bidirectional = db.Column(db.Boolean, default=True, nullable=False)
    has_stairs = db.Column(db.Boolean, default=False, nullable=False)
    has_ramp = db.Column(db.Boolean, default=False, nullable=False)
    is_accessible = db.Column(db.Boolean, default=True, nullable=False)
    surface_type = db.Column(db.String(30), default='concrete', nullable=False)
    width_meters = db.Column(db.Float, nullable=True)
    is_blocked = db.Column(db.Boolean, default=False, nullable=False)
    closure_reason = db.Column(db.String(255), nullable=True)
    reopens_at = db.Column(db.DateTime, nullable=True)
    campus_id = db.Column(db.String(50), nullable=False, default='thapar')
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    # Relationships
    from_node = db.relationship('NavigationNode', foreign_keys=[from_node_id], back_populates='outgoing_pathways')
    to_node = db.relationship('NavigationNode', foreign_keys=[to_node_id], back_populates='incoming_pathways')
    closures = db.relationship('PathwayClosure', back_populates='pathway', lazy='dynamic')
    reports = db.relationship('UserReport', foreign_keys='UserReport.pathway_id',
                              back_populates='pathway', lazy='dynamic')

    def to_dict(self):
        return {
            'id': self.id,
            'from_node_id': self.from_node_id,
            'from_node_key': self.from_node.node_key if self.from_node else None,
            'to_node_id': self.to_node_id,
            'to_node_key': self.to_node.node_key if self.to_node else None,
            'distance_meters': self.distance_meters,
            'estimated_seconds': self.estimated_seconds,
            'is_bidirectional': self.is_bidirectional,
            'has_stairs': self.has_stairs,
            'has_ramp': self.has_ramp,
            'is_accessible': self.is_accessible,
            'surface_type': self.surface_type,
            'width_meters': self.width_meters,
            'is_blocked': self.is_blocked,
            'closure_reason': self.closure_reason,
            'reopens_at': self.reopens_at.isoformat() if self.reopens_at else None,
            'campus_id': self.campus_id,
        }

    def __repr__(self):
        return f'<Pathway {self.id}: {self.from_node_id}→{self.to_node_id}>'


class PathwayClosure(db.Model):
    __tablename__ = 'pathway_closures'

    id = db.Column(db.Integer, primary_key=True)
    pathway_id = db.Column(db.Integer, db.ForeignKey('pathways.id'), nullable=False)
    reason = db.Column(db.String(100), nullable=False)
    description = db.Column(db.Text, nullable=True)
    closed_by_id = db.Column(db.Integer, db.ForeignKey('users.id'), nullable=False)
    closed_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    expected_reopen = db.Column(db.DateTime, nullable=True)
    actual_reopen = db.Column(db.DateTime, nullable=True)
    is_active = db.Column(db.Boolean, default=True, nullable=False)

    # Relationships
    pathway = db.relationship('Pathway', back_populates='closures')
    closed_by = db.relationship('User', foreign_keys=[closed_by_id], back_populates='closures_created')

    def to_dict(self):
        return {
            'id': self.id,
            'pathway_id': self.pathway_id,
            'reason': self.reason,
            'description': self.description,
            'closed_by_id': self.closed_by_id,
            'closed_at': self.closed_at.isoformat() if self.closed_at else None,
            'expected_reopen': self.expected_reopen.isoformat() if self.expected_reopen else None,
            'actual_reopen': self.actual_reopen.isoformat() if self.actual_reopen else None,
            'is_active': self.is_active,
        }

    def __repr__(self):
        return f'<PathwayClosure {self.id} pathway={self.pathway_id}>'
