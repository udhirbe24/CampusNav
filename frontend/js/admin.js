/**
 * CampusNav Admin Controller
 * Analytics visualizations with Chart.js, pathway closure controls, user reports.
 */

class AdminController {
  constructor() {
    this.analyticsData = null;
    this.usersChart = null;
    this.modesChart = null;
    this.destChart = null;
    this.selectedPathwayId = null;
  }

  async init() {
    // Verify admin authentication before loading admin dashboard
    try {
      const userRes = await window.api.getCurrentUser();
      if (!userRes.success || !userRes.data || userRes.data.role !== 'admin') {
        window.Utils.showToast('Please sign in with administrator credentials.', 'warning');
        setTimeout(() => { window.location.href = '/auth'; }, 700);
        return;
      }
    } catch (e) {
      window.Utils.showToast('Please sign in with administrator credentials.', 'warning');
      setTimeout(() => { window.location.href = '/auth'; }, 700);
      return;
    }

    this.setupNavigation();
    await this.loadAnalytics();
    await this.loadPathwaysTable();
    await this.loadReportsTable();
    await this.loadUsersTable();
    this.setupClosureModal();
  }

  setupNavigation() {
    const navItems = document.querySelectorAll('.admin-nav-item');
    const sections = document.querySelectorAll('.admin-section');

    navItems.forEach(item => {
      item.addEventListener('click', () => {
        navItems.forEach(n => n.classList.remove('active'));
        sections.forEach(s => s.classList.remove('active'));

        item.classList.add('active');
        const targetSection = document.getElementById(item.dataset.target);
        if (targetSection) targetSection.classList.add('active');
      });
    });
  }

  async loadAnalytics() {
    try {
      const res = await window.api.getAdminAnalytics();
      if (!res.success || !res.data) return;

      this.analyticsData = res.data;
      const overview = res.data.overview || {};

      // Update KPI cards
      this.updateElement('stat-total-users', overview.total_users || 0);
      this.updateElement('stat-total-buildings', overview.total_buildings || 0);
      this.updateElement('stat-total-pathways', overview.total_pathways || 0);
      this.updateElement('stat-blocked-pathways', overview.blocked_pathways || 0);
      this.updateElement('stat-pending-reports', overview.pending_reports || 0);
      this.updateElement('stat-total-queries', overview.total_navigation_queries || 0);

      // Render Charts with Chart.js
      this.renderCharts(res.data);
    } catch (err) {
      console.error('Failed to load admin analytics:', err);
      if (err.status === 401 || err.status === 403) {
        window.Utils.showToast('Administrator privileges required. Please log in as admin.', 'error');
        setTimeout(() => { window.location.href = '/auth'; }, 1200);
      }
    }
  }

  updateElement(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  renderCharts(data) {
    if (typeof Chart === 'undefined') return;

    // 1. Users by Role Doughnut Chart
    const usersCtx = document.getElementById('chart-users-role')?.getContext('2d');
    if (usersCtx) {
      const roles = data.overview.users_by_role || {};
      const labels = Object.keys(roles).map(r => r.charAt(0).toUpperCase() + r.slice(1));
      const counts = Object.values(roles);

      if (this.usersChart) this.usersChart.destroy();
      this.usersChart = new Chart(usersCtx, {
        type: 'doughnut',
        data: {
          labels: labels.length ? labels : ['Visitors', 'Students', 'Faculty', 'Admins'],
          datasets: [{
            data: counts.length ? counts : [1, 0, 0, 1],
            backgroundColor: ['#173F35', '#A8B9A2', '#D6A756', '#4F5A56'],
            borderWidth: 2,
            borderColor: '#FFFFFF'
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { position: 'bottom', labels: { boxWidth: 12, font: { family: 'Inter' } } }
          }
        }
      });
    }

    // 2. Navigation Mode Breakdown Bar Chart
    const modesCtx = document.getElementById('chart-modes-usage')?.getContext('2d');
    if (modesCtx) {
      const modes = data.mode_breakdown || {};
      const labels = ['Shortest', 'Fastest', 'Accessible', 'Emergency'];
      const counts = [
        modes['shortest'] || 0,
        modes['fastest'] || 0,
        modes['accessible'] || 0,
        modes['emergency'] || 0
      ];

      if (this.modesChart) this.modesChart.destroy();
      this.modesChart = new Chart(modesCtx, {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [{
            label: 'Route Queries',
            data: counts,
            backgroundColor: '#173F35',
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            y: { beginAtZero: true, ticks: { precision: 0 } }
          }
        }
      });
    }

    // 3. Popular Destinations Bar Chart
    const destCtx = document.getElementById('chart-popular-dest')?.getContext('2d');
    if (destCtx) {
      const popular = data.popular_destinations || [];
      const labels = popular.map(p => p.name || p.node_key);
      const counts = popular.map(p => p.count);

      if (this.destChart) this.destChart.destroy();
      this.destChart = new Chart(destCtx, {
        type: 'bar',
        data: {
          labels: labels.length ? labels : ['Academic Block A', 'Chern Library', 'Medical Centre'],
          datasets: [{
            label: 'Visits',
            data: counts.length ? counts : [12, 8, 5],
            backgroundColor: '#D6A756',
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          indexAxis: 'y',
          plugins: { legend: { display: false } },
          scales: {
            x: { beginAtZero: true, ticks: { precision: 0 } }
          }
        }
      });
    }
  }

  async loadPathwaysTable() {
    const tbody = document.getElementById('pathways-table-body');
    if (!tbody) return;

    try {
      const res = await window.api.getPathways();
      if (!res.success || !res.data) return;

      tbody.innerHTML = res.data.map(p => {
        const isBlocked = p.is_blocked;
        const statusBadge = isBlocked
          ? `<span class="badge badge-danger">Blocked</span>`
          : `<span class="badge badge-success">Open</span>`;

        const actionBtn = isBlocked
          ? `<button class="btn btn-secondary btn-sm" onclick="window.adminController.reopenPathway(${p.id})">Reopen</button>`
          : `<button class="btn btn-danger btn-sm" onclick="window.adminController.openClosureModal(${p.id})">Block Pathway</button>`;

        return `
          <tr>
            <td><strong>#${p.id}</strong></td>
            <td>${p.from_node_key || `Node ${p.from_node_id}`} &rarr; ${p.to_node_key || `Node ${p.to_node_id}`}</td>
            <td>${Math.round(p.distance_meters)} m</td>
            <td>${p.has_stairs ? '⚠️ Yes (Stairs)' : '♿ Yes (Ramp/Flat)'}</td>
            <td>${statusBadge}</td>
            <td>${p.closure_reason || '—'}</td>
            <td>${actionBtn}</td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      console.error('Error loading pathways:', err);
    }
  }

  openClosureModal(pathwayId) {
    this.selectedPathwayId = pathwayId;
    window.Utils.openModal('modal-close-pathway');
  }

  setupClosureModal() {
    const form = document.getElementById('form-close-pathway');
    if (form) {
      form.addEventListener('submit', async e => {
        e.preventDefault();
        const reason = document.getElementById('closure-reason').value;
        const desc = document.getElementById('closure-desc').value;

        if (!this.selectedPathwayId) return;

        try {
          const res = await window.api.closePathway(this.selectedPathwayId, reason, desc);
          if (res.success) {
            window.Utils.showToast('Pathway marked as blocked! Routing engine updated.', 'success');
            window.Utils.closeModal('modal-close-pathway');
            await this.loadPathwaysTable();
            await this.loadAnalytics();
          }
        } catch (err) {
          window.Utils.showToast(err.message || 'Failed to update pathway status.', 'error');
        }
      });
    }
  }

  async reopenPathway(pathwayId) {
    try {
      const res = await window.api.reopenPathway(pathwayId);
      if (res.success) {
        window.Utils.showToast('Pathway reopened and restored in routing graph!', 'success');
        await this.loadPathwaysTable();
        await this.loadAnalytics();
      }
    } catch (err) {
      window.Utils.showToast(err.message || 'Failed to reopen pathway.', 'error');
    }
  }

  async loadReportsTable() {
    const tbody = document.getElementById('reports-table-body');
    if (!tbody) return;

    try {
      const res = await window.api.getAdminReports();
      if (!res.success || !res.data) return;

      if (res.data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--color-text-muted); padding: 24px;">No active reports found. All campus pathways are clear!</td></tr>`;
        return;
      }

      tbody.innerHTML = res.data.map(r => {
        const isResolved = r.status === 'RESOLVED';
        const statusBadge = isResolved
          ? `<span class="badge badge-success">Resolved</span>`
          : `<span class="badge badge-warning">Pending</span>`;

        const actionBtn = isResolved
          ? '—'
          : `<button class="btn btn-primary btn-sm" onclick="window.adminController.resolveReport(${r.id})">Mark Resolved</button>`;

        return `
          <tr>
            <td><strong>#${r.id}</strong></td>
            <td><span class="badge badge-sage">${r.report_type}</span></td>
            <td>${r.description}</td>
            <td>${r.user_name || 'Anonymous'}</td>
            <td>${statusBadge}</td>
            <td>${actionBtn}</td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      console.error('Error loading reports:', err);
    }
  }

  async resolveReport(reportId) {
    try {
      const res = await window.api.updateReport(reportId, {
        status: 'RESOLVED',
        admin_notes: 'Reviewed and resolved by campus facility administrator.'
      });
      if (res.success) {
        window.Utils.showToast('Report marked as resolved!', 'success');
        await this.loadReportsTable();
        await this.loadAnalytics();
      }
    } catch (err) {
      window.Utils.showToast('Could not resolve report.', 'error');
    }
  }

  async loadUsersTable() {
    const tbody = document.getElementById('users-table-body');
    if (!tbody) return;

    try {
      const res = await window.api.getAdminUsers();
      if (!res.success || !res.data) return;

      tbody.innerHTML = res.data.map(u => {
        const roleBadge = u.role === 'admin'
          ? `<span class="badge badge-accent">Admin</span>`
          : `<span class="badge badge-sage">${u.role.toUpperCase()}</span>`;

        return `
          <tr>
            <td><strong>#${u.id}</strong></td>
            <td>${u.first_name} ${u.last_name}</td>
            <td>${u.email}</td>
            <td>${u.student_id || '—'}</td>
            <td>${roleBadge}</td>
            <td>${u.created_at ? new Date(u.created_at).toLocaleDateString() : '—'}</td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      console.error('Error loading users:', err);
    }
  }
}

window.adminController = new AdminController();
