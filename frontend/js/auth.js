/**
 * CampusNav Auth Controller
 * Handles user login, registration, role selection, and session management.
 */

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');
  const tabLogin = document.getElementById('tab-login');
  const tabRegister = document.getElementById('tab-register');
  const loginSection = document.getElementById('login-section');
  const registerSection = document.getElementById('register-section');

  // Tab switching
  if (tabLogin && tabRegister) {
    tabLogin.addEventListener('click', () => {
      tabLogin.classList.add('active');
      tabRegister.classList.remove('active');
      loginSection.style.display = 'block';
      registerSection.style.display = 'none';
    });

    tabRegister.addEventListener('click', () => {
      tabRegister.classList.add('active');
      tabLogin.classList.remove('active');
      registerSection.style.display = 'block';
      loginSection.style.display = 'none';
    });
  }

  // Handle Login Submission
  if (loginForm) {
    loginForm.addEventListener('submit', async e => {
      e.preventDefault();
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      const submitBtn = loginForm.querySelector('button[type="submit"]');

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<div class="spinner"></div> Signing In...';

      try {
        const res = await window.api.login(email, password);
        if (res.success) {
          window.Utils.showToast('Login successful! Redirecting...', 'success');
          setTimeout(() => {
            if (res.data.role === 'admin') {
              window.location.href = '/admin';
            } else {
              window.location.href = '/app';
            }
          }, 600);
        }
      } catch (err) {
        window.Utils.showToast(err.message || 'Invalid email or password.', 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Sign In';
      }
    });
  }

  // Handle Register Submission
  if (registerForm) {
    registerForm.addEventListener('submit', async e => {
      e.preventDefault();
      const email = document.getElementById('reg-email').value.trim();
      const password = document.getElementById('reg-password').value;
      const firstName = document.getElementById('reg-firstname').value.trim();
      const lastName = document.getElementById('reg-lastname').value.trim();
      const role = document.getElementById('reg-role').value;
      const studentId = document.getElementById('reg-studentid').value.trim();
      const submitBtn = registerForm.querySelector('button[type="submit"]');

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<div class="spinner"></div> Creating Account...';

      try {
        const res = await window.api.register({
          email,
          password,
          first_name: firstName,
          last_name: lastName,
          role,
          student_id: studentId || null
        });

        if (res.success) {
          window.Utils.showToast('Account created successfully! Logging you in...', 'success');
          // Auto login
          await window.api.login(email, password);
          setTimeout(() => {
            window.location.href = '/app';
          }, 600);
        }
      } catch (err) {
        window.Utils.showToast(err.message || 'Registration failed. Check inputs.', 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Create Account';
      }
    });
  }
});
