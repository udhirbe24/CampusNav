import math

THAPAR_BOUNDS = {
    'min_lat': 30.344,
    'max_lat': 30.362,
    'min_lon': 76.350,
    'max_lon': 76.380
}


def haversine_distance(lat1, lon1, lat2, lon2):
    """Calculate the great-circle distance in meters between two points on Earth."""
    R = 6371000  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def bearing(lat1, lon1, lat2, lon2):
    """Calculate the initial bearing (degrees) from point 1 to point 2."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    x = math.sin(dl) * math.cos(phi2)
    y = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dl)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def is_within_campus_bounds(lat, lon):
    """Return True if the given coordinates fall within Thapar campus bounds."""
    b = THAPAR_BOUNDS
    return b['min_lat'] <= lat <= b['max_lat'] and b['min_lon'] <= lon <= b['max_lon']


def coords_to_geojson_point(lat, lon):
    """Return a GeoJSON Point object for the given coordinates."""
    return {'type': 'Point', 'coordinates': [lon, lat]}


def walking_time_seconds(distance_meters, speed_mps=1.4):
    """Estimate walking time in seconds given distance and walking speed."""
    return distance_meters / speed_mps


def cardinal_direction(deg):
    """Convert a bearing in degrees to a cardinal direction string."""
    directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']
    idx = round(deg / 45) % 8
    return directions[idx]
