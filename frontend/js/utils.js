/**
 * CampusNav Utilities
 * UI helpers, toast notifications, modals, and formatters.
 */

const Utils = {
  // --- Toast Notifications ---
  showToast(message, type = 'info', duration = 3500) {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    // Icon based on type
    let icon = 'ℹ️';
    if (type === 'success') icon = '✓';
    if (type === 'error') icon = '✕';
    if (type === 'warning') icon = '⚠';

    toast.innerHTML = `
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-weight: bold;">${icon}</span>
        <span>${message}</span>
      </div>
      <button style="background: none; border: none; color: inherit; cursor: pointer; font-size: 1rem; opacity: 0.7;" onclick="this.parentElement.remove()">✕</button>
    `;

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
    }, duration);
  },

  // --- Modals ---
  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
      modal.classList.add('active');
    }
  },

  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
      modal.classList.remove('active');
    }
  },

  // --- Debounce ---
  debounce(func, wait = 250) {
    let timeout;
    return function (...args) {
      clearTimeout(timeout);
      timeout = setTimeout(() => func.apply(this, args), wait);
    };
  },

  // --- Formatters ---
  formatDistance(meters) {
    if (meters === undefined || meters === null) return '0 m';
    if (meters < 1000) return `${Math.round(meters)} m`;
    return `${(meters / 1000).toFixed(2)} km`;
  },

  formatTime(seconds) {
    if (seconds === undefined || seconds === null) return '< 1 min';
    const minutes = Math.ceil(seconds / 60);
    if (minutes < 1) return '< 1 min';
    if (minutes >= 60) {
      const hrs = Math.floor(minutes / 60);
      const remMin = minutes % 60;
      return `${hrs}h ${remMin}m`;
    }
    return `${minutes} min`;
  },

  // Turn step icon mapper
  getStepIcon(instruction) {
    const text = instruction.toLowerCase();
    if (text.includes('arrive')) return '🏁';
    if (text.includes('sharp right')) return '↱';
    if (text.includes('right')) return '→';
    if (text.includes('sharp left')) return '↰';
    if (text.includes('left')) return '←';
    if (text.includes('straight')) return '↑';
    return '🧭';
  }
};

window.Utils = Utils;
