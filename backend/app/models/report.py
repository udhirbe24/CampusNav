from datetime import datetime, timezone
from backend.app import db


class UserReport(db.Model):
    __tablename__ = 'user_reports'

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'), nullable=True)
    pathway_id = db.Column(db.Integer, db.ForeignKey('pathways.id'), nullable=True)
    report_type = db.Column(db.String(50), nullable=False)
    # report_type: 'OBSTRUCTION' | 'DAMAGE' | 'CLOSURE' | 'HAZARD' | 'OTHER'
    description = db.Column(db.Text, nullable=False)
    submitted_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    status = db.Column(db.String(20), default='PENDING', nullable=False)
    # status: 'PENDING' | 'REVIEWED' | 'RESOLVED'
    admin_notes = db.Column(db.Text, nullable=True)

    # Relationships
    user = db.relationship('User', foreign_keys=[user_id], back_populates='reports')
    pathway = db.relationship('Pathway', foreign_keys=[pathway_id], back_populates='reports')

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'user_email': self.user.email if self.user else None,
            'pathway_id': self.pathway_id,
            'report_type': self.report_type,
            'description': self.description,
            'submitted_at': self.submitted_at.isoformat() if self.submitted_at else None,
            'status': self.status,
            'admin_notes': self.admin_notes,
        }

    def __repr__(self):
        return f'<UserReport {self.id} type={self.report_type} status={self.status}>'


class NavigationHistory(db.Model):
    __tablename__ = 'navigation_history'

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'), nullable=True)
    from_node_id = db.Column(db.Integer, db.ForeignKey('navigation_nodes.id'), nullable=False)
    to_node_id = db.Column(db.Integer, db.ForeignKey('navigation_nodes.id'), nullable=False)
    algorithm_used = db.Column(db.String(50), nullable=False, default='dijkstra')
    route_mode = db.Column(db.String(30), nullable=False, default='shortest')
    # route_mode: 'shortest' | 'accessible' | 'scenic' | 'indoor'
    distance_meters = db.Column(db.Float, nullable=True)
    duration_seconds = db.Column(db.Float, nullable=True)
    timestamp = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    # Relationships
    user = db.relationship('User', foreign_keys=[user_id], back_populates='navigation_history')
    from_node = db.relationship('NavigationNode', foreign_keys=[from_node_id])
    to_node = db.relationship('NavigationNode', foreign_keys=[to_node_id])

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'from_node_id': self.from_node_id,
            'from_node_key': self.from_node.node_key if self.from_node else None,
            'to_node_id': self.to_node_id,
            'to_node_key': self.to_node.node_key if self.to_node else None,
            'algorithm_used': self.algorithm_used,
            'route_mode': self.route_mode,
            'distance_meters': self.distance_meters,
            'duration_seconds': self.duration_seconds,
            'timestamp': self.timestamp.isoformat() if self.timestamp else None,
        }

    def __repr__(self):
        return f'<NavigationHistory {self.id} user={self.user_id}>'
