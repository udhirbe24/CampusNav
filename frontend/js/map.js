/**
 * CampusNav Map Controller
 * Dedicated Thapar University Campus Map with Google Maps UI & Strict Boundary Lockdown.
 * Ensures the user stays strictly inside Thapar University with an outer boundary mask.
 */

class CampusMap {
  constructor(containerId = 'campus-map') {
    this.containerId = containerId;
    this.map = null;
    this.outerMaskLayer = null;
    this.campusGroundsLayer = null;
    this.markersLayer = null;
    this.pathwaysLayer = null;
    this.routeLayer = null;
    this.alternativeRouteLayer = null;
    this.userLocationMarker = null;
    this.startMarker = null;
    this.destMarker = null;

    // Strict Thapar University Coordinates
    this.thaparCenter = [30.3552, 76.3642];
    this.thaparBounds = L.latLngBounds(
      [30.3496, 76.3552],  // Southwest limit (outside Gate 3 / Hostel D)
      [30.3625, 76.3735]   // Northeast limit (outside Gate 4 / Gate 1)
    );

    // Exact Thapar University Physical Campus Boundary Polygon
    this.thaparPerimeter = [
      [30.3542, 76.3562], // Western Gate (Gate 3)
      [30.3575, 76.3565], // Faculty Area (West boundary)
      [30.3600, 76.3568], // Northwest Corner (Faculty Housing)
      [30.3610, 76.3615], // Hostel K area (North boundary)
      [30.3618, 76.3650], // Gate 4 (Polytechnic North Gate)
      [30.3605, 76.3690], // Polytechnic College Curve
      [30.3592, 76.3712], // Old Library / Northeast Corner
      [30.3575, 76.3725], // Gate 2 (East Gate)
      [30.3540, 76.3728], // Gate 1 (Main Gate - Bhadson Rd)
      [30.3520, 76.3715], // Main Entrance Parking / South-East corner
      [30.3508, 76.3670], // South Boundary (PG Hostel / SBOP)
      [30.3506, 76.3630], // South Nirvana Edge
      [30.3508, 76.3578], // Hostel D (Neeram Hall) / South-West corner
      [30.3525, 76.3562]  // Return to Gate 3
    ];
  }

  init() {
    if (!document.getElementById(this.containerId)) return;

    // Initialize Leaflet map with STRICT campus boundary lockdown
    this.map = L.map(this.containerId, {
      zoomControl: false,
      attributionControl: false,
      maxBounds: this.thaparBounds,
      maxBoundsViscosity: 1.0, // 100% rigid wall — cannot drag outside Thapar University
      minZoom: 16.0,           // User cannot zoom out to see outside city/country
      maxZoom: 19.5
    }).fitBounds(this.thaparBounds);

    // Clean Google Maps-style base map tiles (Voyager)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      maxZoom: 20,
      minZoom: 15,
      bounds: this.thaparBounds
    }).addTo(this.map);

    // Layer groups in visual z-index order
    this.outerMaskLayer = L.layerGroup().addTo(this.map);
    this.campusGroundsLayer = L.layerGroup().addTo(this.map);
    this.pathwaysLayer = L.layerGroup().addTo(this.map);
    this.markersLayer = L.layerGroup().addTo(this.map);
    this.alternativeRouteLayer = L.layerGroup().addTo(this.map);
    this.routeLayer = L.layerGroup().addTo(this.map);

    // 1. Mask out everything outside Thapar University boundary
    this.renderOutsideMask();

    // 2. Render dedicated Thapar grounds, parks, and tracks
    this.renderCampusGrounds();

    // 3. Load pathways and building markers from database
    this.loadCampusPathways();
    this.loadCampusBuildings();
  }

  /**
   * Masks out the entire outside world with a clean Google-style neutral overlay,
   * cutting out a hole strictly for Thapar University.
   */
  renderOutsideMask() {
    this.outerMaskLayer.clearLayers();

    // Inverted donut polygon: outer box covers world, inner ring is Thapar campus
    const worldOuterRing = [
      [-85, -180],
      [-85, 180],
      [85, 180],
      [85, -180]
    ];

    // Create the masking polygon
    L.polygon([worldOuterRing, this.thaparPerimeter], {
      color: '#173F35',
      weight: 3.5,
      opacity: 0.85,
      dashArray: '6, 6',
      fillColor: '#EDF2F7',  // Clean subtle grey-blue tone that hides external city
      fillOpacity: 0.95,     // Dims and masks external roads completely
      interactive: false
    }).addTo(this.outerMaskLayer);

    // Add a solid boundary casing line
    L.polygon(this.thaparPerimeter, {
      color: '#1A73E8',
      weight: 2,
      opacity: 0.7,
      fill: false,
      interactive: false
    }).addTo(this.outerMaskLayer);
  }

  /**
   * Renders real campus landmarks: Nirvana Park, Cricket Ground, Football Track, etc.
   */
  renderCampusGrounds() {
    this.campusGroundsLayer.clearLayers();

    // 1. Campus Lawn Base Fill (inside perimeter)
    L.polygon(this.thaparPerimeter, {
      fillColor: '#EBF5EB',
      fillOpacity: 0.35,
      weight: 0,
      interactive: false
    }).addTo(this.campusGroundsLayer);

    // 2. Nirvana Central Park & Lake (Green Area)
    L.polygon([
      [30.3548, 76.3642],
      [30.3548, 76.3662],
      [30.3528, 76.3662],
      [30.3528, 76.3642]
    ], {
      color: '#81C995',
      weight: 1.5,
      fillColor: '#CEEAD6',
      fillOpacity: 0.85
    }).addTo(this.campusGroundsLayer).bindTooltip('🌳 Nirvana Central Park & Lake', { sticky: true });

    // 3. Cricket Ground & Pavilion (Green Oval)
    L.circle([30.3578, 76.3622], {
      radius: 65,
      color: '#81C995',
      weight: 2,
      fillColor: '#A8DAB5',
      fillOpacity: 0.85
    }).addTo(this.campusGroundsLayer).bindTooltip('🏏 Cricket Field & Pavilion', { sticky: true });

    // 4. Football & Synthetic Athletics Track (Red Oval with Green Turf)
    L.polygon([
      [30.3570, 76.3590],
      [30.3570, 76.3600],
      [30.3550, 76.3600],
      [30.3550, 76.3590]
    ], {
      color: '#EA4335',
      weight: 3.5,
      fillColor: '#A8DAB5',
      fillOpacity: 0.85
    }).addTo(this.campusGroundsLayer).bindTooltip('⚽ Football & Synthetic Track', { sticky: true });

    // 5. Tennis Courts & Fete Play Ground
    L.polygon([
      [30.3568, 76.3615],
      [30.3568, 76.3630],
      [30.3554, 76.3630],
      [30.3554, 76.3615]
    ], {
      color: '#81C995',
      weight: 1.5,
      fillColor: '#CEEAD6',
      fillOpacity: 0.75
    }).addTo(this.campusGroundsLayer).bindTooltip('🎾 Tennis Courts & Play Area', { sticky: true });
  }

  async loadCampusBuildings() {
    try {
      const res = await window.api.getBuildings();
      if (!res.success || !res.data) return;

      this.markersLayer.clearLayers();

      res.data.forEach(bldg => {
        const marker = this.createBuildingMarker(bldg);
        marker.addTo(this.markersLayer);
      });
    } catch (err) {
      console.error('Failed to load campus buildings onto map:', err);
    }
  }

  createBuildingMarker(bldg) {
    let iconClass = 'gmap-icon-academic';
    let iconSymbol = '🏛️';

    if (bldg.short_code && bldg.short_code.startsWith('GATE')) {
      iconClass = 'gmap-icon-gate';
      iconSymbol = '🚪';
    } else if (bldg.category === 'HOSTEL') {
      iconClass = 'gmap-icon-hostel';
      iconSymbol = '🏠';
    } else if (bldg.category === 'SPORTS') {
      iconClass = 'gmap-icon-sports';
      iconSymbol = '⚽';
    } else if (bldg.category === 'LIBRARY') {
      iconClass = 'gmap-icon-academic';
      iconSymbol = '📚';
    } else if (bldg.category === 'MEDICAL') {
      iconClass = 'gmap-icon-medical';
      iconSymbol = '🏥';
    }

    const html = `
      <div class="gmap-place-marker" title="${bldg.name}">
        <span class="gmap-place-icon ${iconClass}">${iconSymbol}</span>
        <span>${bldg.name.split('(')[0].trim()}</span>
      </div>
    `;

    const customIcon = L.divIcon({
      html: html,
      className: '',
      iconSize: [120, 24],
      iconAnchor: [60, 12]
    });

    const marker = L.marker([bldg.latitude, bldg.longitude], { icon: customIcon });

    const entranceKey = (bldg.entrances && bldg.entrances.length > 0) ? bldg.entrances[0].node_key : `n_${bldg.id}`;

    const popupContent = `
      <div class="gmap-popup-card">
        <h4 class="gmap-popup-title">${bldg.name}</h4>
        <span class="gmap-popup-category">${bldg.category} • Thapar Institute</span>
        <p style="font-size: 12px; color: #5F6368; line-height: 1.35; margin: 4px 0;">${bldg.description || 'Campus Facility'}</p>
        <div class="gmap-popup-actions">
          <button class="gmap-popup-btn primary" onclick="window.routingController.selectDestination('${entranceKey}', '${bldg.name.replace(/'/g, "\\'")}')">
            📍 Directions
          </button>
          <button class="gmap-popup-btn" onclick="window.routingController.selectStart('${entranceKey}', '${bldg.name.replace(/'/g, "\\'")}')">
            Start Here
          </button>
        </div>
      </div>
    `;

    marker.bindPopup(popupContent);
    return marker;
  }

  async loadCampusPathways() {
    try {
      const res = await window.api.getPathways();
      if (!res.success || !res.data) return;

      this.pathwaysLayer.clearLayers();

      res.data.forEach(p => {
        if (!p.coordinates || p.coordinates.length < 2) return;

        const latlngs = [
          [p.coordinates[0][1], p.coordinates[0][0]],
          [p.coordinates[1][1], p.coordinates[1][0]]
        ];

        // Google Maps campus pedestrian walkways style: Crisp white with subtle grey border
        let strokeColor = '#FFFFFF';
        let borderColor = '#BDC1C6';
        let weight = 4.5;
        let dashArray = null;

        if (p.is_blocked) {
          strokeColor = '#EA4335';
          borderColor = '#B31412';
          dashArray = '5, 5';
          weight = 5;
        }

        // Pathway casing border
        L.polyline(latlngs, {
          color: borderColor,
          weight: weight + 2,
          opacity: 0.85,
          lineCap: 'round',
          lineJoin: 'round'
        }).addTo(this.pathwaysLayer);

        // Main pathway stroke
        const line = L.polyline(latlngs, {
          color: strokeColor,
          weight: weight,
          opacity: 0.95,
          dashArray: dashArray,
          lineCap: 'round',
          lineJoin: 'round'
        });

        line.bindTooltip(p.is_blocked ? `🚧 Closed: ${p.closure_reason || 'Maintenance'}` : 'Campus Walkway', {
          sticky: true
        });

        line.addTo(this.pathwaysLayer);
      });
    } catch (err) {
      console.error('Failed to load pathways:', err);
    }
  }

  drawRoute(geojsonFeature, isAlternative = false) {
    if (!geojsonFeature || !geojsonFeature.geometry) return;

    const layerToUse = isAlternative ? this.alternativeRouteLayer : this.routeLayer;
    layerToUse.clearLayers();

    const latlngs = geojsonFeature.geometry.coordinates.map(c => [c[1], c[0]]);

    // Google Maps Walking Route: Solid Google Blue (#1A73E8)
    const color = isAlternative ? '#FBBC04' : '#1A73E8';
    const weight = isAlternative ? 5 : 7;
    const dashArray = isAlternative ? '8, 8' : null;

    // White shadow casing underneath (Google Maps style)
    L.polyline(latlngs, {
      color: '#FFFFFF',
      weight: weight + 4,
      opacity: 0.9,
      lineCap: 'round',
      lineJoin: 'round'
    }).addTo(layerToUse);

    // Primary route line
    const polyline = L.polyline(latlngs, {
      color: color,
      weight: weight,
      opacity: 1.0,
      dashArray: dashArray,
      lineCap: 'round',
      lineJoin: 'round'
    }).addTo(layerToUse);

    // Set starting and ending teardrop pins
    if (!isAlternative && latlngs.length >= 2) {
      this.setRouteEndpoints(latlngs[0], latlngs[latlngs.length - 1]);
      this.map.fitBounds(polyline.getBounds(), { padding: [100, 100], maxZoom: 18.5 });
    }
  }

  setRouteEndpoints(startLatLng, destLatLng) {
    if (this.startMarker) this.map.removeLayer(this.startMarker);
    if (this.destMarker) this.map.removeLayer(this.destMarker);

    // Start Pin (Green)
    const startIcon = L.divIcon({
      html: '<div class="gmap-pin-start"></div>',
      className: '',
      iconSize: [32, 42],
      iconAnchor: [16, 42]
    });
    this.startMarker = L.marker(startLatLng, { icon: startIcon }).addTo(this.routeLayer);

    // Destination Pin (Red)
    const destIcon = L.divIcon({
      html: '<div class="gmap-pin-dest"></div>',
      className: '',
      iconSize: [32, 42],
      iconAnchor: [16, 42]
    });
    this.destMarker = L.marker(destLatLng, { icon: destIcon }).addTo(this.routeLayer);
  }

  clearRoutes() {
    if (this.routeLayer) this.routeLayer.clearLayers();
    if (this.alternativeRouteLayer) this.alternativeRouteLayer.clearLayers();
    this.startMarker = null;
    this.destMarker = null;
  }

  setUserLocation(lat, lon) {
    if (this.userLocationMarker) {
      this.map.removeLayer(this.userLocationMarker);
    }

    const gpsIcon = L.divIcon({
      html: '<div class="gmap-user-gps"></div>',
      className: '',
      iconSize: [16, 16],
      iconAnchor: [8, 8]
    });

    this.userLocationMarker = L.marker([lat, lon], { icon: gpsIcon }).addTo(this.map);
    this.userLocationMarker.bindPopup('<b>Your Current Location</b>').openPopup();
    this.map.setView([lat, lon], 17.5);
  }

  resetView() {
    this.map.fitBounds(this.thaparBounds);
  }

  zoomIn() {
    this.map.zoomIn();
  }

  zoomOut() {
    this.map.zoomOut();
  }
}

window.campusMap = new CampusMap();
