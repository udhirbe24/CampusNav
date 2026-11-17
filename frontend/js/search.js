/**
 * CampusNav Search Controller
 * Handles location search, autocomplete dropdowns, and point selection with Google Maps UI.
 */

class SearchController {
  constructor() {
    this.allBuildings = [];
    this.startNodeKey = null;
    this.startLabel = null;
    this.destNodeKey = null;
    this.destLabel = null;
  }

  async init() {
    await this.fetchBuildings();
    this.setupEventListeners();
  }

  async fetchBuildings() {
    try {
      const res = await window.api.getBuildings();
      if (res.success && res.data) {
        this.allBuildings = res.data;
      }
    } catch (err) {
      console.error('Error fetching buildings list:', err);
    }
  }

  setupEventListeners() {
    const startInput = document.getElementById('start-location-input');
    const destInput = document.getElementById('dest-location-input');
    const swapBtn = document.getElementById('swap-locations-btn');

    if (startInput) {
      startInput.addEventListener('input', window.Utils.debounce(e => {
        this.handleSearch(e.target.value, 'start-dropdown', 'start');
      }, 150));

      startInput.addEventListener('focus', () => {
        this.showDropdown('start-dropdown', this.allBuildings.slice(0, 10), 'start');
      });
    }

    if (destInput) {
      destInput.addEventListener('input', window.Utils.debounce(e => {
        this.handleSearch(e.target.value, 'dest-dropdown', 'dest');
      }, 150));

      destInput.addEventListener('focus', () => {
        this.showDropdown('dest-dropdown', this.allBuildings.slice(0, 10), 'dest');
      });
    }

    if (swapBtn) {
      swapBtn.addEventListener('click', () => this.swapLocations());
    }

    // Hide dropdowns when clicking outside
    document.addEventListener('click', e => {
      if (!e.target.closest('.location-inputs') && !e.target.closest('.gmap-inputs-box') && !e.target.closest('.gmap-direction-card')) {
        document.querySelectorAll('.autocomplete-dropdown, .gmap-dropdown').forEach(d => d.classList.remove('active'));
      }
    });
  }

  handleSearch(query, dropdownId, type) {
    const term = query.trim().toLowerCase();
    if (!term) {
      this.showDropdown(dropdownId, this.allBuildings.slice(0, 10), type);
      return;
    }

    // Rank matches: name starts with the term > short code > name contains it > category
    const rank = b => {
      const name = b.name.toLowerCase();
      const mainName = name.split('(')[0].trim();
      if (mainName.startsWith(term)) return 0;
      if ((b.short_code || '').toLowerCase() === term) return 1;
      if (mainName.includes(term)) return 2;
      if (name.includes(term) || (b.short_code || '').toLowerCase().includes(term)) return 3;
      if (b.category && b.category.toLowerCase().includes(term)) return 4;
      return -1;
    };

    const filtered = this.allBuildings
      .map(b => ({ b, r: rank(b) }))
      .filter(x => x.r >= 0)
      .sort((x, y) => x.r - y.r || x.b.name.localeCompare(y.b.name))
      .map(x => x.b);

    this.showDropdown(dropdownId, filtered, type);
  }

  showDropdown(dropdownId, items, type) {
    const dropdown = document.getElementById(dropdownId);
    if (!dropdown) return;

    if (!items || items.length === 0) {
      dropdown.innerHTML = `<div class="gmap-dropdown-item" style="color: #70757A;">No campus locations found</div>`;
      dropdown.classList.add('active');
      return;
    }

    dropdown.innerHTML = items.filter(b => b.node_key).map(b => {
      const entrance = b.node_key;
      let icon = '🏛️';
      if (b.short_code && b.short_code.startsWith('GATE')) icon = '🚪';
      else if (b.category === 'HOSTEL') icon = '🏠';
      else if (b.category === 'SPORTS') icon = '⚽';
      else if (b.category === 'LIBRARY') icon = '📚';
      else if (b.category === 'MEDICAL') icon = '🏥';

      return `
        <div class="gmap-dropdown-item" onclick="window.searchController.selectLocation('${type}', '${entrance}', '${b.name.replace(/'/g, "\\'")}')">
          <span style="font-size: 15px;">${icon}</span>
          <div style="flex-grow: 1;">
            <div style="font-weight: 500; color: #202124;">${b.name}</div>
            <div style="font-size: 11px; color: #70757A;">${b.category} • ${b.short_code}</div>
          </div>
        </div>
      `;
    }).join('');

    dropdown.classList.add('active');
  }

  selectLocation(type, nodeKey, name) {
    if (type === 'start') {
      this.startNodeKey = nodeKey;
      this.startLabel = name;
      const input = document.getElementById('start-location-input');
      if (input) input.value = name;
      document.getElementById('start-dropdown')?.classList.remove('active');
    } else {
      this.destNodeKey = nodeKey;
      this.destLabel = name;
      const input = document.getElementById('dest-location-input');
      if (input) input.value = name;
      document.getElementById('dest-dropdown')?.classList.remove('active');
    }

    // In Emergency mode only the starting point matters
    if (window.routingController.currentMode === 'emergency' && this.startNodeKey) {
      window.routingController.triggerEmergencyRouting();
      return;
    }

    // Auto calculate if both start and destination are selected
    if (this.startNodeKey && this.destNodeKey) {
      window.routingController.calculateRoute();
    }
  }

  swapLocations() {
    const tempKey = this.startNodeKey;
    const tempLabel = this.startLabel;

    this.startNodeKey = this.destNodeKey;
    this.startLabel = this.destLabel;

    this.destNodeKey = tempKey;
    this.destLabel = tempLabel;

    const startInput = document.getElementById('start-location-input');
    const destInput = document.getElementById('dest-location-input');

    if (startInput) startInput.value = this.startLabel || '';
    if (destInput) destInput.value = this.destLabel || '';

    if (this.startNodeKey && this.destNodeKey) {
      window.routingController.calculateRoute();
    }
  }
}

window.searchController = new SearchController();
