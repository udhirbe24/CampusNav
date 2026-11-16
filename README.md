# CampusNav — Smart Campus Navigation & Route Optimization System

> **Deployment Target:** Thapar Institute of Engineering and Technology (TIET), Patiala, Punjab, India
> **Built with:** Java 21 · Spring Boot 3 · Spring Data JPA · H2 · Leaflet.js

---

## 1. Executive Summary

**CampusNav** is a campus navigation and route-optimisation web application. It replaces static PDF campus maps with an interactive, graph-backed routing platform for students, faculty, administrators and visitors.

The routing engine models the campus walkways as a weighted graph and uses **A\* Search** (with a Haversine straight-line heuristic) and **Dijkstra's Algorithm**, choosing between them automatically. It finds the shortest, fastest or wheelchair-accessible (step-free) path, avoids walkways that admins have closed, suggests detours, and guides users to the nearest emergency facility.

---

## 2. Quick Start

### Prerequisites
- **Java 17 or newer** (Java 21 recommended). Maven is *not* required — the included Maven Wrapper downloads it automatically.
  - macOS: `brew install openjdk@21`
  - Windows / Linux: install Temurin 21 from <https://adoptium.net>

### Run it
| OS | Command (from the project folder) |
|---|---|
| macOS / Linux | `./start.sh` |
| Windows (Command Prompt) | `start.bat` |
| Windows (PowerShell) | `.\start.ps1` |

Or manually:
```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The first start downloads dependencies (a minute or two) and loads the Thapar campus map into the database automatically — there is no separate database setup step.

Then open:
- **Navigation map:** <http://localhost:8088/app>
- **Landing page:** <http://localhost:8088/>
- **Sign in / register:** <http://localhost:8088/auth>
- **Admin portal:** <http://localhost:8088/admin>

**Default admin login:** `admin@thapar.edu` / `AdminPassword123!` (change it with the `ADMIN_PASSWORD` environment variable before the first start).

To use another port: `PORT=9090 ./start.sh` (macOS/Linux) or `set PORT=9090` then `start.bat` (Windows).

### Build a standalone jar
```bash
cd backend
./mvnw package
java -jar target/campusnav.jar
```
The jar contains the frontend too, so it runs anywhere Java is installed.

---

## 3. System Architecture

```
CampusNav/
├── backend/                                  # Java 21 + Spring Boot (Maven project)
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd                        # Maven Wrapper (no Maven install needed)
│   └── src/
│       ├── main/java/edu/thapar/campusnav/
│       │   ├── CampusNavApplication.java     # Entry point
│       │   ├── algorithm/                    # Pure Java graph + routing (no Spring)
│       │   │   ├── CampusGraph.java          # Adjacency-list graph of walkways
│       │   │   ├── DijkstraPathFinder.java   # Dijkstra with a binary-heap priority queue
│       │   │   ├── AStarPathFinder.java      # A* with an admissible Haversine heuristic
│       │   │   ├── RouteOptimizer.java       # Auto algorithm choice, modes, detours, directions
│       │   │   ├── SearchOptions.java        # Distance/time weighting, step-free filter
│       │   │   ├── PathResult.java, RouteResult.java, Algorithm.java
│       │   │   └── GeoUtils.java             # Haversine distance, bearings, formatting
│       │   ├── model/                        # JPA entities (User, Building, NavigationNode,
│       │   │                                 #   Pathway, PathwayClosure, EmergencyFacility, ...)
│       │   ├── repository/                   # Spring Data JPA repositories
│       │   ├── service/
│       │   │   ├── GraphService.java         # Builds + caches the graph from the database
│       │   │   ├── NavigationService.java    # Routes, detours, emergency routing, history
│       │   │   ├── CampusDataService.java    # Buildings, nodes, pathways, admin analytics
│       │   │   ├── PathwayService.java       # Close / reopen walkways
│       │   │   ├── AuthService.java          # Registration, login, BCrypt hashing
│       │   │   └── DataSeeder.java           # Loads the campus map on first start
│       │   ├── web/                          # REST controllers, session auth, error handling
│       │   └── config/WebConfig.java         # Serves the frontend
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── data/                         # Thapar campus dataset
│       │       ├── thapar_locations.json     # 35 buildings and places
│       │       ├── thapar_nodes.geojson      # 43 walkway nodes
│       │       └── thapar_pathways.geojson   # 58 walkway edges
│       └── test/java/...                     # JUnit 5 tests
├── frontend/                                 # HTML, CSS, vanilla JavaScript, Leaflet.js
│   ├── index.html, app.html, auth.html, admin.html
│   ├── css/
│   └── js/ (api.js, map.js, routing.js, search.js, auth.js, admin.js, utils.js)
├── start.sh, start.bat, start.ps1            # One-click starters
└── README.md
```

The backend serves the frontend from the same address, so there is a single server to run.

---

## 4. Technology Stack

### Backend
- **Java 21** and **Spring Boot 3.5** (Spring Web MVC)
- **Spring Data JPA / Hibernate** with an embedded **H2** database (a file in `backend/campusnav-data/`)
- **BCrypt** password hashing (`spring-security-crypto`) and HTTP-session login
- **JUnit 5**, **MockMvc** and **AssertJ** for tests

### Frontend
- HTML5, CSS3 and vanilla JavaScript (ES6+), no framework
- **Leaflet.js 1.9.4** with **OpenStreetMap** tiles (no API key needed)
- **Chart.js** on the admin dashboard

---

## 5. Algorithmic Formulation

### 5.1 Dijkstra's Shortest Path
Campus pathways are modeled as a weighted directed/undirected graph $G = (V, E)$, where $V$ represents pathway intersections, building entrances, and emergency points, and $E$ represents walkable paths.
- **Distance Weighting:** $w(u, v) = \text{distance in meters}$.
- **Time Weighting:** $w(u, v) = \frac{\text{distance in meters}}{\text{walking speed (1.4 m/s)}}$.

### 5.2 A\* Search Algorithm
A\* evaluates nodes by combining the exact path cost $g(n)$ from the start node and an admissible heuristic estimate $h(n)$ to the destination:
$$f(n) = g(n) + h(n)$$

For geographic coordinates, the straight-line Haversine distance between node $n = (\phi_n, \lambda_n)$ and target $t = (\phi_t, \lambda_t)$ is computed:
$$a = \sin^2\left(\frac{\Delta \phi}{2}\right) + \cos(\phi_n)\cos(\phi_t)\sin^2\left(\frac{\Delta \lambda}{2}\right)$$
$$d_{\text{haversine}} = 2 R \arcsin(\sqrt{a})$$
where $R = 6,371,000 \text{ meters}$.

**Admissibility & Consistency:**
Because Euclidean and geodesic straight-line distance is always less than or equal to the actual walking distance along physical pedestrian pathways ($h(n) \le h^*(n)$), the heuristic is admissible and monotonic. This guarantees that A\* always finds the optimal path while expanding significantly fewer nodes than standard Dijkstra.

### 5.3 Automatic Algorithm Selection
Users never pick an algorithm — `RouteOptimizer.chooseAlgorithm()` picks the best one for each question and the app shows which one it used and why:

| Question | Algorithm chosen | Why |
|---|---|---|
| One start → one destination | **A\*** | Same optimal answer as Dijkstra, but the straight-line heuristic pulls the search toward the destination, so it explores fewer points. |
| One start → nearest of several places (Emergency) | **Dijkstra** | Expands outward in order of distance, so a single search stops at whichever facility is closest. A\*'s heuristic needs a single target. |
| Any walkway recorded shorter than the straight line between its ends | **Dijkstra** | The A\* heuristic could overestimate, so optimality would no longer be guaranteed. |

For time-based routing the A\* heuristic is the straight-line distance divided by the fastest walking speed found on any walkway, which keeps it admissible.

### 5.4 Multi-Criteria Optimization Modes
1. **Shortest Distance:** Minimizes cumulative physical distance in meters.
2. **Fastest Route:** Minimizes estimated walking time (each walkway stores its own walking time).
3. **Wheelchair Accessible (Step-Free):** Evaluates edges with `is_accessible = True` and strictly prunes edges with `has_stairs = True`.
4. **Alternative Route (Detour):** Makes the primary route's walkways 3× as expensive and searches again, producing a genuinely different path.
5. **Emergency Route:** Calculates the optimal route to the closest active medical center, security office, or assembly point.

---

## 6. Visual Identity & UI/UX

CampusNav is styled to reflect a calm, authoritative university navigation product:
- **Primary Color:** Deep Forest Green (`#173F35`)
- **Secondary Background:** Warm Off-White (`#F7F7F2`)
- **Supporting Color:** Muted Sage (`#A8B9A2`)
- **Text & Contrast:** Charcoal (`#252B29`)
- **Accent:** Soft Amber (`#D6A756`)
- **Map Base:** Light Neutral Grey (`#ECEEEA`)
- **Typography:** Inter (interface controls) & Manrope (headings)

---

## 7. Running the Tests

```bash
cd backend
./mvnw test
```

26 JUnit tests cover:
- **Algorithms (14):** Dijkstra optimality, A\* matches Dijkstra on every pair, A\* explores no more nodes than Dijkstra, step-free routes avoid stairs, closed walkways are avoided, unreachable destinations, automatic algorithm choice (including the fallback when the heuristic could overestimate), nearest-facility search, detours, turn instructions, Haversine accuracy.
- **API (12), on the real Thapar map:** every building is routable, every pair of points is reachable in every mode with A\* and Dijkstra agreeing, CS Block → Cricket Ground (regression), emergency routing, detours, closing and reopening a walkway, registration rules, login, admin-only access, hazard reports, page serving.

---

## 8. REST API Reference

All responses use the envelope `{"success": true|false, "message": "...", "data": ...}`. Route requests send only `from_node`, `to_node` and `mode` (`shortest`, `fastest` or `accessible`); the server chooses the algorithm and returns it in `data.summary.algorithm` along with `algorithm_reason` and `nodes_explored`.


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

## 9. Security Notes

- Passwords are stored as **BCrypt** hashes, never in plain text.
- Logging in issues a new session id (prevents session fixation); session cookies are HTTP-only and SameSite=Lax.
- Nobody can make themselves an admin by registering — only an existing admin can change roles, and an admin cannot remove their own admin role.
- All database access goes through JPA parameterised queries (no SQL injection).
- Closing or reopening a walkway immediately rebuilds the cached routing graph, so the next route reflects the change.
- The sign-in page's demo **Auto-Fill** button shows the default admin password. Remove it and set `ADMIN_PASSWORD` before deploying anywhere public.

---

## 10. Data Disclaimer

Geographic coordinates and pedestrian pathway segments for Thapar Institute of Engineering and Technology (TIET), Patiala, are mapped to demonstrate topological routing accuracy. Prior to field deployment, physical verification of newly constructed pathways and ramps should be confirmed with the TIET Estate and Campus Maintenance Department.
