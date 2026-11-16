# CampusNav — Smart Campus Navigation & Route Optimization System

> **Deployment Target:** Thapar Institute of Engineering and Technology (TIET), Patiala, Punjab, India  
> **Engineering Discipline:** Software Architecture, Geographic Information Systems (GIS), Graph Algorithms, and Full-Stack Engineering.

---

## 1. Executive Summary

**CampusNav** is an intelligent university campus navigation and spatial routing web application. It transitions campus transit from static PDF layout diagrams to an interactive, graph-backed routing platform designed for university students, faculty members, campus administration, and visitors.

The system utilizes mathematical graph search algorithms (**Dijkstra's Algorithm** and **A\* Search with Haversine distance heuristics**) to calculate real-world walking paths, avoid dynamic pathway closures (e.g., ongoing construction or events), enforce strict wheelchair accessibility (step-free pathways), offer alternative detours, and direct users to emergency medical and security facilities.

---

## 2. System Architecture

CampusNav is engineered following clean separation of concerns:

```
CampusNav/
├── backend/
│   ├── app/
│   │   ├── algorithms/             # Graph data structures & routing algorithms
│   │   │   ├── graph.py            # Adjacency list & node/edge representations
│   │   │   ├── dijkstra.py         # Priority queue Dijkstra implementation
│   │   │   ├── astar.py            # Heuristic-guided A* search
│   │   │   └── route_optimizer.py  # Multi-criteria routing & step generation
│   │   ├── models/                 # Normalized SQLAlchemy relational entities
│   │   │   ├── user.py             # User accounts & role-based permissions
│   │   │   ├── location.py         # Buildings, rooms, & navigation nodes
│   │   │   ├── pathway.py          # Walkway edges, width, stairs, ramps
│   │   │   ├── emergency.py        # Verified emergency facilities
│   │   │   └── report.py           # User hazard reports & routing logs
│   │   ├── routes/                 # RESTful API blueprints
│   │   │   ├── auth_routes.py      # Registration, login, session tokens
│   │   │   ├── locations_routes.py # Building search & node spatial metadata
│   │   │   ├── navigation_routes.py# Route calculations, alternative paths
│   │   │   ├── pathways_routes.py  # Walkway status & closure management
│   │   │   └── admin_routes.py     # Administrative analytics & controls
│   │   ├── services/               # Business logic & graph caching layer
│   │   │   ├── auth_service.py
│   │   │   ├── navigation_service.py
│   │   │   └── campus_data_service.py
│   │   └── utils/                  # Geodesic math, validators, responses
│   │       ├── geo_utils.py        # Haversine distance, azimuth bearings
│   │       ├── validators.py       # Input validation & password checks
│   │       └── response_helpers.py # Standardized JSON envelope format
│   ├── data/                       # Spatial dataset for Thapar University
│   │   ├── thapar_locations.json   # 25 verified campus buildings
│   │   ├── thapar_nodes.geojson    # 50 navigation nodes
│   │   └── thapar_pathways.geojson # 68 pedestrian walkway edges
│   ├── tests/                      # Automated test suite (Pytest)
│   │   ├── test_algorithms.py      # Dijkstra & A* verification tests
│   │   ├── test_auth.py            # Authentication & session tests
│   │   ├── test_api.py             # Full API endpoint integration tests
│   │   └── test_models.py          # Database integrity & hashing tests
│   ├── config.py                   # Environment configurations
│   ├── init_db.py                  # Database bootstrap and seed script
│   └── run.py                      # Application server entry point
├── frontend/                       # Vanilla ES6+, HTML5, CSS3, Leaflet.js
│   ├── index.html                  # University landing page & feature overview
│   ├── app.html                    # Central interactive navigation dashboard
│   ├── auth.html                   # Student, faculty, & guest sign-in
│   ├── admin.html                  # Admin operations & Chart.js analytics
│   ├── css/                        # Design system & tokens
│   │   ├── design-tokens.css       # Palette tokens, typography, elevation
│   │   ├── components.css          # Buttons, inputs, pills, cards, modal
│   │   ├── layout.css              # Responsive layout & mobile bottom sheet
│   │   ├── map.css                 # Custom Leaflet markers & popups
│   │   ├── auth.css                # Authentication forms
│   │   └── admin.css               # Admin tables, KPIs, & chart containers
│   └── js/                         # Modular frontend controllers
│       ├── api.js                  # Asynchronous REST API client
│       ├── map.js                  # Leaflet map manager & route rendering
│       ├── routing.js              # Mode switcher & turn-by-turn steps
│       ├── search.js               # Autocomplete & quick destination picker
│       ├── auth.js                 # Authentication controller
│       ├── admin.js                # Analytics charts & closure controls
│       └── utils.js                # Toast notifications & formatters
├── requirements.txt                # Python backend dependencies
├── .env.example                    # Environment variable template
└── README.md                       # Comprehensive technical documentation
```

---

## 3. Technology Stack

### Frontend
- **HTML5 & CSS3:** Mobile-first layout with native CSS custom properties.
- **Vanilla JavaScript (ES6+):** Zero-framework, high-performance modular JavaScript.
- **Leaflet.js (v1.9.4):** Interactive mapping engine with custom SVG markers and geo-polylines.
- **OpenStreetMap / CARTO:** Clean base tile rendering.
- **Chart.js (v4.4.1):** Live database metrics visualization on the admin dashboard.

### Backend
- **Python 3.10+ / 3.14:** Core runtime.
- **Flask (v3.1):** Lightweight, modular WSGI application with Blueprints.
- **Flask-SQLAlchemy (v3.1):** ORM layer supporting SQLite (development) and PostgreSQL (production).
- **Flask-Login:** Session authentication and role-based access control.
- **Flask-CORS:** Cross-Origin Resource Sharing security.
- **Werkzeug:** Cryptographic password hashing (`scrypt`).

### Spatial & Graph Algorithms
- **Dijkstra's Algorithm:** Shortest/fastest path with min-heap priority queue ($O((V + E) \log V)$).
- **A\* Search Algorithm:** Informed pathfinding using straight-line Haversine distance heuristic.
- **Haversine Formula:** Great-circle geodesic distance calculation on the WGS 84 ellipsoid.
- **GeoJSON:** Standards-compliant spatial vector data exchange.

---

## 4. Algorithmic Formulation

### 4.1 Dijkstra's Shortest Path
Campus pathways are modeled as a weighted directed/undirected graph $G = (V, E)$, where $V$ represents pathway intersections, building entrances, and emergency points, and $E$ represents walkable paths.
- **Distance Weighting:** $w(u, v) = \text{distance in meters}$.
- **Time Weighting:** $w(u, v) = \frac{\text{distance in meters}}{\text{walking speed (1.4 m/s)}}$.

### 4.2 A\* Search Algorithm
A\* evaluates nodes by combining the exact path cost $g(n)$ from the start node and an admissible heuristic estimate $h(n)$ to the destination:
$$f(n) = g(n) + h(n)$$

For geographic coordinates, the straight-line Haversine distance between node $n = (\phi_n, \lambda_n)$ and target $t = (\phi_t, \lambda_t)$ is computed:
$$a = \sin^2\left(\frac{\Delta \phi}{2}\right) + \cos(\phi_n)\cos(\phi_t)\sin^2\left(\frac{\Delta \lambda}{2}\right)$$
$$d_{\text{haversine}} = 2 R \arcsin(\sqrt{a})$$
where $R = 6,371,000 \text{ meters}$.

**Admissibility & Consistency:**
Because Euclidean and geodesic straight-line distance is always less than or equal to the actual walking distance along physical pedestrian pathways ($h(n) \le h^*(n)$), the heuristic is admissible and monotonic. This guarantees that A\* always finds the optimal path while expanding significantly fewer nodes than standard Dijkstra.

### 4.3 Multi-Criteria Optimization Modes
1. **Shortest Distance:** Minimizes cumulative physical distance in meters.
2. **Fastest Route:** Minimizes estimated travel time based on surface type and grade.
3. **Wheelchair Accessible (Step-Free):** Evaluates edges with `is_accessible = True` and strictly prunes edges with `has_stairs = True`.
4. **Alternative Route:** Penalizes or excludes edges traversed by the primary route to generate a distinct secondary path.
5. **Emergency Route:** Calculates the optimal route to the closest active medical center, security office, or assembly point.

---

## 5. Visual Identity & UI/UX

CampusNav is styled to reflect a calm, authoritative university navigation product:
- **Primary Color:** Deep Forest Green (`#173F35`)
- **Secondary Background:** Warm Off-White (`#F7F7F2`)
- **Supporting Color:** Muted Sage (`#A8B9A2`)
- **Text & Contrast:** Charcoal (`#252B29`)
- **Accent:** Soft Amber (`#D6A756`)
- **Map Base:** Light Neutral Grey (`#ECEEEA`)
- **Typography:** Inter (interface controls) & Manrope (headings)

---

## 6. Installation & Local Setup

### Prerequisites
- Python 3.10 or higher
- Git

### Step 1: Clone or Open the Project
```bash
cd c:\Users\udayd\OneDrive\Desktop\CampusNav
```

### Step 2: Install Python Dependencies
```bash
pip install -r requirements.txt
```

### Step 3: Initialize Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

### Step 4: Initialize the Database & Seed Campus Data
Run the database migration and seed script. This populates 25 Thapar University buildings, 50 navigation nodes, 68 connected pathways, 4 emergency facilities, and seeds the initial administrator account:
```bash
python backend/init_db.py
```

### Step 5: Start the Application Server
```bash
python backend/run.py
```
Open your browser and navigate to:
- **Landing Page:** [http://localhost:5000/](http://localhost:5000/)
- **Navigation Map:** [http://localhost:5000/app](http://localhost:5000/app)
- **Sign In:** [http://localhost:5000/auth](http://localhost:5000/auth)
- **Admin Portal:** [http://localhost:5000/admin](http://localhost:5000/admin)

**Default Admin Credentials:**
- **Email:** `admin@thapar.edu`
- **Password:** `AdminPassword123!`

---

## 7. Running the Automated Test Suite

The test suite validates algorithms, data models, role authorization, and REST endpoints:
```bash
python -m pytest backend/tests -v
```
All 25 automated unit and integration tests pass with 100% compliance:
- 9 Algorithm Tests (Dijkstra optimality, A\* admissibility, blocked pathway rerouting, stairs avoidance, alternative routes, emergency routing, real Thapar graph connectivity).
- 4 Authentication Tests (Registration, duplicate detection, password strength, login/logout).
- 9 API Integration Tests (Locations, search, Dijkstra calculation, A\* calculation, emergency routing, user hazard reports, admin access control).
- 3 Database Model Tests (Hashing, serialization).

---

## 8. REST API Reference

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `GET` | `/api/health` | Service health and entity counts | No |
| `POST` | `/api/auth/register` | Register new student/faculty/visitor | No |
| `POST` | `/api/auth/login` | Authenticate user session | No |
| `POST` | `/api/auth/logout` | Terminate session | Yes |
| `GET` | `/api/auth/me` | Fetch active user profile | Yes |
| `GET` | `/api/locations` | List campus buildings | No |
| `GET` | `/api/locations/search?q=...` | Autocomplete building search | No |
| `GET` | `/api/locations/nodes` | List all navigation points | No |
| `POST` | `/api/routes/calculate` | Compute shortest/accessible route | No |
| `POST` | `/api/routes/alternative` | Compute alternative detour route | No |
| `POST` | `/api/routes/emergency` | Navigate to nearest emergency facility | No |
| `GET` | `/api/routes/history` | Retrieve personal routing history | Yes |
| `GET` | `/api/pathways` | List pathways and blockage status | No |
| `POST` | `/api/pathways/report` | Submit hazard or obstruction report | No |
| `GET` | `/api/admin/analytics` | Real database operational metrics | Admin |
| `POST` | `/api/admin/pathways/:id/close`| Block pathway and invalidate cache | Admin |
| `POST` | `/api/admin/pathways/:id/reopen`| Reopen pathway and restore routing | Admin |
| `GET` | `/api/admin/reports` | List submitted hazard reports | Admin |
| `PUT` | `/api/admin/reports/:id` | Resolve report with admin notes | Admin |
| `GET` | `/api/admin/users` | List registered campus accounts | Admin |

---

## 9. Security & Production Considerations

- **Password Hashing:** Passwords are never stored in plaintext. Werkzeug `scrypt` hashing is enforced.
- **CSRF & Session Security:** Session cookies are set with HTTP-only and SameSite flags.
- **SQL Injection Prevention:** All queries use parameterized SQLAlchemy ORM statements.
- **Graph Invalidation:** Whenever an administrator marks a pathway as blocked or reopened, `NavigationService.invalidate_graph()` triggers an immediate memory cache flush, ensuring subsequent path calculations reflect real-world physical changes.
- **Production Migration:** To migrate from SQLite to PostgreSQL, update the `DATABASE_URL` in `.env` to:  
  `postgresql://user:password@host:5432/campusnav`

---

## 10. Data Disclaimer

Geographic coordinates and pedestrian pathway segments for Thapar Institute of Engineering and Technology (TIET), Patiala, are mapped to demonstrate topological routing accuracy. Prior to field deployment, physical verification of newly constructed pathways and ramps should be confirmed with the TIET Estate and Campus Maintenance Department.
