/**
 * CampusNav Routing Controller
 * Orchestrates multi-criteria route optimization, steps rendering, and emergency routing with Google Maps UI.
 */

class RoutingController {
  constructor() {
    this.currentMode = 'shortest';
    this.accessibleOnly = false;
    this.currentRouteData = null;
    this.alternativeRouteData = null;
  }

  init() {
    this.setupModePills();
    this.setupGeolocation();
  }

  setupModePills() {
    const pills = document.querySelectorAll('.mode-pill, .gmap-mode-tab');
    pills.forEach(pill => {
      pill.addEventListener('click', e => {
        pills.forEach(p => p.classList.remove('active'));
        pill.classList.add('active');

        this.currentMode = pill.dataset.mode;
        this.accessibleOnly = (this.currentMode === 'accessible');

        // If emergency mode clicked, route immediately to nearest medical/security
        if (this.currentMode === 'emergency') {
          this.triggerEmergencyRouting();
          return;
        }

        // Recalculate if points exist
        if (window.searchController.startNodeKey && window.searchController.destNodeKey) {
          this.calculateRoute();
        }
      });
    });
  }

  setupGeolocation() {
    const gpsBtn = document.getElementById('use-current-location-btn');
    if (gpsBtn) {
      gpsBtn.addEventListener('click', () => {
        if (!navigator.geolocation) {
          window.Utils.showToast('Geolocation is not supported by your browser.', 'warning');
          return;
        }

        window.Utils.showToast('Acquiring campus GPS location...', 'info');
        navigator.geolocation.getCurrentPosition(
          async position => {
            const lat = position.coords.latitude;
            const lon = position.coords.longitude;

            window.campusMap.setUserLocation(lat, lon);

            try {
              const res = await window.api.findNearestNode(lat, lon);
              if (res.success && res.data && res.data.node_key) {
                window.Utils.showToast('Mapped to nearest campus walkway!', 'success');
                window.searchController.selectLocation('start', res.data.node_key, 'Current Location (GPS)');
              }
            } catch (err) {
              window.Utils.showToast('Could not link GPS to campus walkway.', 'warning');
            }
          },
          error => {
            console.error('Geolocation error:', error);
            // Default to Gate 1 (Main Gate) for demo if GPS unavailable or outside
            window.searchController.startNodeKey = 'n_gate1';
            window.searchController.startLabel = 'Gate 1 (Main Gate)';
            const input = document.getElementById('start-location-input');
            if (input) input.value = 'Gate 1 (Main Gate)';
            window.Utils.showToast('GPS unavailable. Start set to Gate 1 (Main Gate).', 'info');
          },
          { enableHighAccuracy: true, timeout: 6000 }
        );
      });
    }
  }

  async calculateRoute() {
    if (this.currentMode === 'emergency') {
      return this.triggerEmergencyRouting();
    }
    const fromNode = window.searchController.startNodeKey;
    const toNode = window.searchController.destNodeKey;

    if (!fromNode || !toNode) {
      window.Utils.showToast('Please select both starting point and destination.', 'warning');
      return;
    }

    const detailsDrawer = document.getElementById('route-details-panel');

    try {
      const res = await window.api.calculateRoute({
        fromNode,
        toNode,
        mode: this.currentMode,
        accessibleOnly: this.accessibleOnly
      });

      if (!res.success || !res.data) {
        window.Utils.showToast(res.message || 'No walkable path found between points.', 'error');
        return;
      }

      this.stopWalk();
      this.currentRouteData = res.data;
      this.alternativeRouteData = null;
      this.hideDetourInfo();
      window.campusMap.clearAlternative();

      // Draw primary route on Leaflet map (Google walking blue)
      window.campusMap.drawRoute(res.data.geojson, false);
      this.updateUrl();

      // Render summary metrics (Time, Distance, Attributes)
      this.renderRouteSummary(res.data.summary);

      // Render turn-by-turn steps
      this.renderTurnSteps(res.data.steps);

      if (detailsDrawer) detailsDrawer.style.display = 'block';

      // Show alternative route button
      const altBtn = document.getElementById('calc-alternative-btn');
      if (altBtn) altBtn.style.display = 'inline-flex';

    } catch (err) {
      window.Utils.showToast(err.message || 'Route calculation failed.', 'error');
    }
  }

  async calculateAlternativeRoute() {
    if (!this.currentRouteData || !this.currentRouteData.path_nodes) return;

    try {
      const res = await window.api.getAlternativeRoute({
        fromNode: window.searchController.startNodeKey,
        toNode: window.searchController.destNodeKey,
        primaryPathNodes: this.currentRouteData.path_nodes,
        mode: this.currentMode,
        accessibleOnly: this.accessibleOnly
      });

      if (!res.success || !res.data) {
        window.Utils.showToast('No secondary alternative route available.', 'warning');
        return;
      }

      this.alternativeRouteData = res.data;
      // Draw alternative route on map in amber
      window.campusMap.drawRoute(res.data.geojson, true);
      this.showDetourInfo(this.currentRouteData.summary, res.data.summary);

    } catch (err) {
      window.Utils.showToast(err.message || 'No different route exists between these two places.', 'warning');
    }
  }

  async triggerEmergencyRouting() {
    const startKey = window.searchController.startNodeKey;
    if (!startKey) {
      window.Utils.showToast('Where are you? Choose your current location (or tap ⌖ for GPS) to find the nearest help.', 'warning', 5000);
      document.getElementById('start-location-input')?.focus();
      return;
    }

    try {
      const res = await window.api.getEmergencyRoute({ fromNode: startKey });

      if (!res.success || !res.data) {
        window.Utils.showToast('Could not route to emergency facility.', 'error');
        return;
      }

      this.stopWalk();
      this.currentRouteData = res.data;
      this.alternativeRouteData = null;
      this.hideDetourInfo();
      window.campusMap.clearAlternative();
      window.campusMap.drawRoute(res.data.geojson, false);

      const facility = res.data.emergency_facility || {};
      const destLabel = `${facility.name || 'Emergency Facility'} (${facility.facility_type || 'MEDICAL'})`;
      const destInput = document.getElementById('dest-location-input');
      if (destInput) destInput.value = destLabel;
      // Remember the facility as the destination so switching mode re-routes to it
      window.searchController.destNodeKey = res.data.path_nodes[res.data.path_nodes.length - 1];
      window.searchController.destLabel = destLabel;

      this.renderRouteSummary(res.data.summary);
      this.renderTurnSteps(res.data.steps);

      const detailsDrawer = document.getElementById('route-details-panel');
      if (detailsDrawer) detailsDrawer.style.display = 'block';

      window.Utils.showToast(`Emergency Route: ${facility.name} (Contact: ${facility.contact_number || '112'})`, 'success', 5000);

    } catch (err) {
      window.Utils.showToast('Emergency routing failed.', 'error');
    }
  }

  renderRouteSummary(summary) {
    const distEl = document.getElementById('summary-distance');
    const timeEl = document.getElementById('summary-time');
    const badgesEl = document.getElementById('summary-badges');

    if (distEl) distEl.textContent = summary.total_distance_text;
    if (timeEl) timeEl.textContent = summary.total_time_text;

    const modeLabels = { shortest: 'Shortest walk', fastest: 'Fastest', accessible: 'Step-free', emergency: 'Emergency' };
    if (badgesEl) {
      let badgesHtml = `
        <span class="badge badge-sage">${summary.algorithm}</span>
        <span class="badge badge-info">${modeLabels[summary.mode] || summary.mode}</span>
      `;
      if (summary.has_stairs) {
        badgesHtml += `<span class="badge badge-warning">⚠️ Includes Stairs</span>`;
      } else {
        badgesHtml += `<span class="badge badge-success">♿ Step-Free Accessible</span>`;
      }
      badgesEl.innerHTML = badgesHtml;
    }

    // Explain which algorithm the engine picked automatically, and why
    const engineEl = document.getElementById('summary-engine');
    if (engineEl) {
      engineEl.textContent = summary.algorithm_reason
        ? `Auto-selected ${summary.algorithm}: ${summary.algorithm_reason} (${summary.nodes_explored} points explored)`
        : '';
    }
  }

  renderTurnSteps(steps) {
    const container = document.getElementById('route-steps-list');
    if (!container) return;

    if (!steps || steps.length === 0) {
      container.innerHTML = '<p style="color: #70757A; font-size: 12px; padding: 6px 0;">Straight walking path.</p>';
      return;
    }

    container.innerHTML = steps.map((s, i) => {
      const icon = window.Utils.getStepIcon(s.instruction);
      return `
        <div class="gmap-step-item" data-step="${i}" onclick="window.routingController.goToStep(${i})">
          <div class="gmap-step-arrow">${icon}</div>
          <div class="gmap-step-text">
            <div style="font-weight: 500;">${s.instruction}</div>
            ${s.distance_meters > 0 ? `<div class="gmap-step-dist">${s.distance_text}</div>` : ''}
          </div>
        </div>
      `;
    }).join('');
  }

  // ------------------------------------------------------------------ detour comparison

  showDetourInfo(primary, detour) {
    const el = document.getElementById('detour-info');
    if (!el) return;
    const extraMeters = Math.round(detour.total_distance_meters - primary.total_distance_meters);
    const extraMinutes = Math.round((detour.total_time_seconds - primary.total_time_seconds) / 60);
    const extra = extraMeters > 0
      ? `${extraMeters} m${extraMinutes > 0 ? ` / ${extraMinutes} min` : ''} longer`
      : 'about the same length';
    el.innerHTML = `<span class="gmap-detour-swatch"></span>
      <span><strong>Detour:</strong> ${detour.total_time_text} · ${detour.total_distance_text} (${extra}) — shown dashed in amber.</span>`;
    el.style.display = 'flex';
  }

  hideDetourInfo() {
    const el = document.getElementById('detour-info');
    if (el) el.style.display = 'none';
  }

  // ------------------------------------------------------------------ guided walk

  /** Walk the route one step at a time, highlighting each stretch on the map. */
  startWalk() {
    if (!this.currentRouteData || !this.currentRouteData.steps) return;
    this.walkIndex = 0;
    document.getElementById('walk-controls').style.display = 'block';
    document.getElementById('route-actions').style.display = 'none';
    this.renderWalkStep();
    this.watchGps();
  }

  walkStep(delta) {
    const steps = this.currentRouteData ? this.currentRouteData.steps : [];
    const next = (this.walkIndex ?? 0) + delta;
    if (next < 0 || next >= steps.length) return;
    this.walkIndex = next;
    this.renderWalkStep();
  }

  goToStep(index) {
    if (this.walkIndex === undefined || this.walkIndex === null) {
      this.startWalk();
    }
    this.walkIndex = index;
    this.renderWalkStep();
  }

  stopWalk() {
    this.walkIndex = null;
    const controls = document.getElementById('walk-controls');
    if (controls) controls.style.display = 'none';
    const actions = document.getElementById('route-actions');
    if (actions) actions.style.display = 'flex';
    document.querySelectorAll('.gmap-step-item.active').forEach(el => el.classList.remove('active'));
    window.campusMap.clearHighlight();
    if (this.gpsWatchId != null) {
      navigator.geolocation.clearWatch(this.gpsWatchId);
      this.gpsWatchId = null;
    }
  }

  renderWalkStep() {
    const steps = this.currentRouteData.steps;
    const step = steps[this.walkIndex];
    document.getElementById('walk-progress').textContent = `Step ${this.walkIndex + 1} of ${steps.length}`;
    document.getElementById('walk-instruction').textContent =
      step.instruction + (step.distance_meters > 0 ? ` (${step.distance_text})` : '');
    document.getElementById('walk-prev-btn').disabled = this.walkIndex === 0;
    document.getElementById('walk-next-btn').disabled = this.walkIndex === steps.length - 1;

    document.querySelectorAll('.gmap-step-item').forEach(el => {
      el.classList.toggle('active', Number(el.dataset.step) === this.walkIndex);
    });
    document.querySelector('.gmap-step-item.active')?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });

    window.campusMap.highlightSegment(this.segmentLatLngs(step));
  }

  /** Coordinates of the route between a step's start and end points. */
  segmentLatLngs(step) {
    const nodes = this.currentRouteData.node_details;
    const from = nodes.findIndex(n => n.node_key === step.from_node);
    const to = nodes.findIndex((n, i) => i >= from && n.node_key === step.to_node);
    if (from < 0 || to < 0) return null;
    if (from === to) {
      const n = nodes[to];
      return [[n.latitude, n.longitude], [n.latitude + 0.00001, n.longitude + 0.00001]];
    }
    return nodes.slice(from, to + 1).map(n => [n.latitude, n.longitude]);
  }

  /** On campus with GPS: show where you are and move on when you reach the end of a step. */
  watchGps() {
    if (!navigator.geolocation || this.gpsWatchId != null) return;
    this.gpsWatchId = navigator.geolocation.watchPosition(pos => {
      if (this.walkIndex == null) return;
      const { latitude, longitude } = pos.coords;
      if (!window.campusMap.thaparBounds.contains([latitude, longitude])) return; // not on campus
      window.campusMap.showGpsDot(latitude, longitude);
      const step = this.currentRouteData.steps[this.walkIndex];
      const end = this.currentRouteData.node_details.find(n => n.node_key === step.to_node);
      if (end && window.campusMap.map.distance([latitude, longitude], [end.latitude, end.longitude]) < 15) {
        this.walkStep(1);
      }
    }, () => {}, { enableHighAccuracy: true, maximumAge: 5000 });
  }

  // ------------------------------------------------------------------ sharing and reset

  /** Puts the current route in the address bar so it can be bookmarked or shared. */
  updateUrl() {
    const s = window.searchController;
    if (!s.startNodeKey || !s.destNodeKey) return;
    const params = new URLSearchParams({ from: s.startNodeKey, to: s.destNodeKey, mode: this.currentMode });
    history.replaceState(null, '', `${location.pathname}?${params}`);
  }

  async shareRoute() {
    this.updateUrl();
    try {
      await navigator.clipboard.writeText(location.href);
      window.Utils.showToast('Link to this route copied.', 'success');
    } catch (e) {
      window.prompt('Copy this link:', location.href);
    }
  }

  /** Opens the route described in the address bar (?from=...&to=...&mode=...). */
  restoreFromUrl() {
    const params = new URLSearchParams(location.search);
    const from = params.get('from');
    const to = params.get('to');
    if (!from || !to) return;
    const mode = params.get('mode');
    if (mode && mode !== 'emergency') {
      const pill = document.querySelector(`.gmap-mode-tab[data-mode="${mode}"]`);
      if (pill) {
        document.querySelectorAll('.gmap-mode-tab').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.currentMode = mode;
        this.accessibleOnly = mode === 'accessible';
      }
    }
    const nameOf = key => {
      const b = window.searchController.allBuildings.find(x => x.node_key === key);
      return b ? b.name : key;
    };
    window.searchController.startNodeKey = from;
    window.searchController.startLabel = nameOf(from);
    document.getElementById('start-location-input').value = nameOf(from);
    window.searchController.selectLocation('dest', to, nameOf(to));
  }

  clearRoute() {
    this.stopWalk();
    this.currentRouteData = null;
    this.alternativeRouteData = null;
    this.hideDetourInfo();
    window.campusMap.clearRoutes();
    document.getElementById('route-details-panel').style.display = 'none';
    const s = window.searchController;
    s.startNodeKey = s.destNodeKey = s.startLabel = s.destLabel = null;
    document.getElementById('start-location-input').value = '';
    document.getElementById('dest-location-input').value = '';
    history.replaceState(null, '', location.pathname);
  }

  selectStart(nodeKey, label) {
    window.searchController.selectLocation('start', nodeKey, label);
  }

  selectDestination(nodeKey, label) {
    window.searchController.selectLocation('dest', nodeKey, label);
  }
}

window.routingController = new RoutingController();
