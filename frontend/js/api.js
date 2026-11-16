/**
 * CampusNav API Client
 * Unified asynchronous HTTP client with authentication and error handling.
 */

const API_BASE_URL = window.location.origin + '/api';

class ApiClient {
  constructor(baseUrl = API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;
    const defaultHeaders = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };

    const config = {
      ...options,
      headers: {
        ...defaultHeaders,
        ...(options.headers || {})
      },
      credentials: 'same-origin' // Send and receive session cookies
    };

    if (config.body && typeof config.body === 'object' && !(config.body instanceof FormData)) {
      config.body = JSON.stringify(config.body);
    }

    try {
      const response = await fetch(url, config);
      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        const error = new Error(data.message || `Request failed with status ${response.status}`);
        error.status = response.status;
        error.data = data;
        throw error;
      }

      return data;
    } catch (err) {
      console.error(`API Error [${options.method || 'GET'} ${endpoint}]:`, err);
      throw err;
    }
  }

  // --- Auth Endpoints ---
  async login(email, password) {
    return this.request('/auth/login', {
      method: 'POST',
      body: { email, password }
    });
  }

  async register(userData) {
    return this.request('/auth/register', {
      method: 'POST',
      body: userData
    });
  }

  async logout() {
    return this.request('/auth/logout', { method: 'POST' });
  }

  async getCurrentUser() {
    return this.request('/auth/me');
  }

  // --- Locations Endpoints ---
  async getBuildings(category = null, search = null) {
    let query = '?campus_id=thapar';
    if (category && category !== 'ALL') query += `&category=${encodeURIComponent(category)}`;
    if (search) query += `&q=${encodeURIComponent(search)}`;
    return this.request(`/locations${query}`);
  }

  async searchLocations(q) {
    return this.request(`/locations/search?q=${encodeURIComponent(q)}&campus_id=thapar`);
  }

  async getNodes() {
    return this.request('/locations/nodes?campus_id=thapar');
  }

  async getCategories() {
    return this.request('/locations/categories');
  }

  // --- Navigation & Routing Endpoints ---
  async calculateRoute({ fromNode, toNode, mode = 'shortest', algorithm = 'astar', accessibleOnly = false }) {
    return this.request('/routes/calculate', {
      method: 'POST',
      body: {
        from_node: fromNode,
        to_node: toNode,
        mode,
        algorithm,
        accessible_only: accessibleOnly
      }
    });
  }

  async getAlternativeRoute({ fromNode, toNode, primaryPathNodes, mode = 'shortest', algorithm = 'astar' }) {
    return this.request('/routes/alternative', {
      method: 'POST',
      body: {
        from_node: fromNode,
        to_node: toNode,
        primary_path_nodes: primaryPathNodes,
        mode,
        algorithm
      }
    });
  }

  async getEmergencyRoute({ fromNode, facilityType = null, algorithm = 'astar' }) {
    return this.request('/routes/emergency', {
      method: 'POST',
      body: {
        from_node: fromNode,
        facility_type: facilityType,
        algorithm
      }
    });
  }

  async getEmergencyFacilities() {
    return this.request('/routes/emergency');
  }

  async findNearestNode(lat, lon) {
    return this.request('/routes/nearest-node', {
      method: 'POST',
      body: { latitude: lat, longitude: lon }
    });
  }

  async getNavigationHistory() {
    return this.request('/routes/history');
  }

  // --- Pathways & Reporting Endpoints ---
  async getPathways() {
    return this.request('/pathways');
  }

  async getClosures() {
    return this.request('/pathways/closures');
  }

  async reportPathway(reportData) {
    return this.request('/pathways/report', {
      method: 'POST',
      body: reportData
    });
  }

  // --- Administration Endpoints ---
  async getAdminAnalytics() {
    return this.request('/admin/analytics');
  }

  async closePathway(pathwayId, reason, description) {
    return this.request(`/admin/pathways/${pathwayId}/close`, {
      method: 'POST',
      body: { reason, description }
    });
  }

  async reopenPathway(pathwayId) {
    return this.request(`/admin/pathways/${pathwayId}/reopen`, {
      method: 'POST'
    });
  }

  async getAdminReports(status = null) {
    let q = '';
    if (status) q = `?status=${encodeURIComponent(status)}`;
    return this.request(`/admin/reports${q}`);
  }

  async updateReport(reportId, updateData) {
    return this.request(`/admin/reports/${reportId}`, {
      method: 'PUT',
      body: updateData
    });
  }

  async getAdminUsers(role = null) {
    let q = '';
    if (role) q = `?role=${encodeURIComponent(role)}`;
    return this.request(`/admin/users${q}`);
  }

  async updateUserRole(userId, role) {
    return this.request(`/admin/users/${userId}/role`, {
      method: 'PUT',
      body: { role }
    });
  }
}

// Export singleton instance
window.api = new ApiClient();
