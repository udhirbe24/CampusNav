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
      window.Utils.showToast('Calculating walking route...', 'info', 1000);
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

      this.currentRouteData = res.data;

      // Draw primary route on Leaflet map (Google walking blue)
      window.campusMap.drawRoute(res.data.geojson, false);

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
      window.Utils.showToast('Finding alternative walking route...', 'info', 1500);
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
      window.Utils.showToast('Alternative detour route displayed on map!', 'success');

    } catch (err) {
      window.Utils.showToast('Alternative route not found.', 'warning');
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
      window.Utils.showToast('Locating nearest campus emergency facility...', 'info');
      const res = await window.api.getEmergencyRoute({ fromNode: startKey });

      if (!res.success || !res.data) {
        window.Utils.showToast('Could not route to emergency facility.', 'error');
        return;
      }

      this.currentRouteData = res.data;
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

    container.innerHTML = steps.map(s => {
      const icon = window.Utils.getStepIcon(s.instruction);
      return `
        <div class="gmap-step-item">
          <div class="gmap-step-arrow">${icon}</div>
          <div class="gmap-step-text">
            <div style="font-weight: 500;">${s.instruction}</div>
            ${s.distance_meters > 0 ? `<div class="gmap-step-dist">${s.distance_text}</div>` : ''}
          </div>
        </div>
      `;
    }).join('');
  }

  selectStart(nodeKey, label) {
    window.searchController.selectLocation('start', nodeKey, label);
  }

  selectDestination(nodeKey, label) {
    window.searchController.selectLocation('dest', nodeKey, label);
  }
}

window.routingController = new RoutingController();
