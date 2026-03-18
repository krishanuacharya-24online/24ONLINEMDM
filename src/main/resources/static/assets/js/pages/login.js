import { calculatePasswordStrength, checkPasswordBreach } from '../password-strength.js';

function readCookie(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

async function apiLogin(username, password) {
  const csrfToken = readCookie("XSRF-TOKEN");
  const headers = { "Content-Type": "application/json" };
  if (csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }
  const response = await fetch("/auth/login", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || "Invalid username or password");
  }
  return response.json();
}

const form = document.getElementById("loginForm");
const button = document.getElementById("loginBtn");
const errorEl = document.getElementById("loginError");
const breachedWarningEl = document.getElementById("breachedWarning");
const breachCountEl = document.getElementById("breachCount");
const passwordInput = document.getElementById("password");

// Track if we've already shown breached warning to avoid repeated checks
let breachedCheckDone = false;
let lastBreachedResult = null;

// Password strength meter for login
function setupPasswordStrengthMeter() {
  if (!passwordInput) return;

  // Create strength meter elements
  const meterContainer = document.createElement('div');
  meterContainer.className = 'password-strength-meter';
  meterContainer.innerHTML = `
    <div class="strength-bar">
      <div class="strength-fill"></div>
    </div>
    <div class="strength-label"></div>
  `;

  const wrapper = passwordInput.parentElement;
  if (wrapper) {
    wrapper.appendChild(meterContainer);
  }

  const strengthFill = meterContainer.querySelector('.strength-fill');
  const strengthLabel = meterContainer.querySelector('.strength-label');

  // Update strength on input
  passwordInput.addEventListener('input', () => {
    const password = passwordInput.value;
    const result = calculatePasswordStrength(password);

    const percentage = (result.score / 4) * 100;
    strengthFill.style.width = `${percentage}%`;
    strengthFill.style.backgroundColor = result.level.color;
    strengthLabel.textContent = password ? result.level.label : '';
    strengthLabel.style.color = result.level.color;
  });
}

// Check breached password with debounce
let breachCheckTimeout = null;
async function checkBreachedPassword(password) {
  if (!password || password.length === 0) {
    return;
  }

  // Debounce the check
  if (breachCheckTimeout) {
    clearTimeout(breachCheckTimeout);
  }

  breachCheckTimeout = setTimeout(async () => {
    try {
      const result = await checkPasswordBreach(password);
      lastBreachedResult = result;

      if (result.breached) {
        breachedWarningEl.hidden = false;
        breachedWarningEl.classList.add('visible');
        if (result.count > 0) {
          breachCountEl.textContent = `This password has been seen ${result.count.toLocaleString()} times in breach databases.`;
        }
      } else {
        breachedWarningEl.hidden = true;
        breachedWarningEl.classList.remove('visible');
      }
    } catch (error) {
      console.warn('Failed to check password breach:', error);
    }
  }, 500); // Wait 500ms after typing stops
}

// Setup breached password check
function setupBreachedPasswordCheck() {
  if (!passwordInput) return;

  passwordInput.addEventListener('input', (e) => {
    checkBreachedPassword(e.target.value);
  });

  passwordInput.addEventListener('blur', (e) => {
    if (breachCheckTimeout) {
      clearTimeout(breachCheckTimeout);
    }
    checkBreachedPassword(e.target.value);
  });
}

form?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!button || !errorEl) {
    return;
  }
  errorEl.hidden = true;
  breachedWarningEl.hidden = true;
  button.disabled = true;
  button.textContent = "Signing in...";

  const username = document.getElementById("username")?.value.trim() ?? "";
  const password = document.getElementById("password")?.value ?? "";
  try {
    const me = await apiLogin(username, password);
    
    // Show breached password warning if applicable
    if (me.passwordBreached) {
      breachedWarningEl.hidden = false;
      breachedWarningEl.classList.add('visible');
      breachCountEl.textContent = 'Our security check found this password in known data breaches.';
      
      // Add a note that they should change it
      const changePasswordNote = document.createElement('div');
      changePasswordNote.style.marginTop = '0.5rem';
      changePasswordNote.style.fontSize = '0.75rem';
      changePasswordNote.style.color = '#c62828';
      changePasswordNote.textContent = '⚠ You should change your password immediately after login.';
      breachedWarningEl.appendChild(changePasswordNote);
    }
    
    const role = String(me?.role || "").trim().toUpperCase();
    window.location.href = role === "TENANT_USER" ? "/ui/enrollments" : "/ui";
  } catch (error) {
    const message = error instanceof Error ? error.message : "Login failed";
    errorEl.textContent = message || "Login failed";
    errorEl.hidden = false;
    button.disabled = false;
    button.textContent = "Login";
  }
});

// Initialize on page load
setupPasswordStrengthMeter();
setupBreachedPasswordCheck();

