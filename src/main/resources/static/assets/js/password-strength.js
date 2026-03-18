/**
 * Password Strength Meter
 * Provides real-time password strength evaluation and visual feedback
 * Includes checks for:
 * - Length requirements
 * - Character variety (upper, lower, number, special)
 * - Common patterns
 * - Breached password check (optional, via API)
 */

// Password strength levels
const STRENGTH_LEVELS = {
  VERY_WEAK: { value: 0, label: 'Very Weak', class: 'strength-very-weak', color: '#d32f2f' },
  WEAK: { value: 1, label: 'Weak', class: 'strength-weak', color: '#f57c00' },
  MEDIUM: { value: 2, label: 'Medium', class: 'strength-medium', color: '#fbc02d' },
  STRONG: { value: 3, label: 'Strong', class: 'strength-strong', color: '#7cb342' },
  VERY_STRONG: { value: 4, label: 'Very Strong', class: 'strength-very-strong', color: '#388e3c' }
};

// Common weak patterns
const WEAK_PATTERNS = [
  /^(.)\1+$/, // Repeated characters (aaa, 111)
  /^(012|123|234|345|456|567|678|789|890)$/, // Sequential numbers
  /^(abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)$/i, // Sequential letters
  /^(qwerty|password|passw0rd|admin|admin123|welcome|letmein|123456|123456789|12345678)$/i,
  /\d{3,}/, // 3+ consecutive digits
  /[a-zA-Z]{3,}/ // 3+ consecutive letters without mixing
];

// Requirements for strong password
const REQUIREMENTS = [
  { id: 'length', label: 'At least 12 characters', test: (pwd) => pwd.length >= 12 },
  { id: 'uppercase', label: 'Uppercase letter', test: (pwd) => /[A-Z]/.test(pwd) },
  { id: 'lowercase', label: 'Lowercase letter', test: (pwd) => /[a-z]/.test(pwd) },
  { id: 'number', label: 'Number', test: (pwd) => /\d/.test(pwd) },
  { id: 'special', label: 'Special character', test: (pwd) => /[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'`~]/.test(pwd) }
];

/**
 * Calculate password strength score (0-4)
 */
export function calculatePasswordStrength(password) {
  if (!password || password.length === 0) {
    return { score: 0, level: STRENGTH_LEVELS.VERY_WEAK, requirements: [], warnings: [] };
  }

  let score = 0;
  const warnings = [];
  const metRequirements = [];

  // Check length
  if (password.length >= 12) {
    score += 2;
    metRequirements.push('length');
  } else if (password.length >= 8) {
    score += 1;
  } else {
    warnings.push('Password is too short');
  }

  // Check character variety
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasNumber = /\d/.test(password);
  const hasSpecial = /[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\;'`~]/.test(password);

  const varietyCount = [hasUpper, hasLower, hasNumber, hasSpecial].filter(Boolean).length;
  
  if (varietyCount >= 4) {
    score += 2;
    metRequirements.push('uppercase', 'lowercase', 'number', 'special');
  } else if (varietyCount >= 3) {
    score += 1;
    if (hasUpper) metRequirements.push('uppercase');
    if (hasLower) metRequirements.push('lowercase');
    if (hasNumber) metRequirements.push('number');
    if (hasSpecial) metRequirements.push('special');
  } else if (varietyCount >= 2) {
    score += 0.5;
  }

  // Check for weak patterns
  for (const pattern of WEAK_PATTERNS) {
    if (pattern.test(password)) {
      score -= 1;
      warnings.push('Avoid common patterns and sequences');
      break;
    }
  }

  // Check for keyboard patterns
  const keyboardPatterns = ['qwerty', 'asdf', 'zxcv', 'qazwsx', '1qaz', '2wsx'];
  const lowerPwd = password.toLowerCase();
  for (const pattern of keyboardPatterns) {
    if (lowerPwd.includes(pattern)) {
      score -= 0.5;
      warnings.push('Avoid keyboard patterns');
      break;
    }
  }

  // Normalize score to 0-4 range
  score = Math.max(0, Math.min(4, Math.round(score)));

  // Determine strength level
  let level;
  switch (score) {
    case 0:
    case 1:
      level = STRENGTH_LEVELS.VERY_WEAK;
      break;
    case 2:
      level = STRENGTH_LEVELS.WEAK;
      break;
    case 3:
      level = STRENGTH_LEVELS.MEDIUM;
      break;
    case 4:
      level = STRENGTH_LEVELS.STRONG;
      break;
    default:
      level = STRENGTH_LEVELS.VERY_WEAK;
  }

  // Bonus for very strong passwords
  if (password.length >= 16 && varietyCount === 4 && score >= 4) {
    level = STRENGTH_LEVELS.VERY_STRONG;
  }

  return {
    score,
    level,
    requirements: metRequirements,
    warnings,
    allRequirementsMet: metRequirements.length === REQUIREMENTS.length
  };
}

/**
 * Check if password meets minimum requirements
 */
export function isPasswordValid(password) {
  const result = calculatePasswordStrength(password);
  return result.allRequirementsMet && result.score >= 3;
}

/**
 * Get requirement status for UI display
 */
export function getPasswordRequirements(password) {
  return REQUIREMENTS.map(req => ({
    id: req.id,
    label: req.label,
    met: req.test(password || '')
  }));
}

/**
 * Create and attach password strength meter to an input field
 * @param {HTMLInputElement} inputElement - The password input element
 * @param {Object} options - Configuration options
 * @param {HTMLElement} options.container - Container element for the strength meter
 * @param {boolean} options.showRequirements - Whether to show requirements checklist
 * @param {boolean} options.showWarnings - Whether to show warnings
 * @param {Function} options.onStrengthChange - Callback when strength changes
 */
export function createPasswordStrengthMeter(inputElement, options = {}) {
  const {
    container = inputElement.parentElement,
    showRequirements = true,
    showWarnings = true,
    onStrengthChange = null
  } = options;

  // Create strength meter UI
  const meterContainer = document.createElement('div');
  meterContainer.className = 'password-strength-meter';
  meterContainer.innerHTML = `
    <div class="strength-bar">
      <div class="strength-fill"></div>
    </div>
    <div class="strength-label"></div>
    ${showRequirements ? `
      <div class="strength-requirements">
        ${REQUIREMENTS.map(req => `
          <div class="requirement-item" data-requirement="${req.id}">
            <span class="requirement-icon">○</span>
            <span class="requirement-label">${req.label}</span>
          </div>
        `).join('')}
      </div>
    ` : ''}
    ${showWarnings ? '<div class="strength-warnings"></div>' : ''}
  `;

  container.appendChild(meterContainer);

  const strengthFill = meterContainer.querySelector('.strength-fill');
  const strengthLabel = meterContainer.querySelector('.strength-label');
  const warningsContainer = meterContainer.querySelector('.strength-warnings');

  // Update strength display
  function updateStrength() {
    const password = inputElement.value;
    const result = calculatePasswordStrength(password);

    // Update strength bar
    const percentage = (result.score / 4) * 100;
    strengthFill.style.width = `${percentage}%`;
    strengthFill.style.backgroundColor = result.level.color;
    strengthLabel.textContent = password ? result.level.label : '';
    strengthLabel.style.color = result.level.color;

    // Update requirements
    if (showRequirements) {
      const requirements = getPasswordRequirements(password);
      requirements.forEach(req => {
        const item = meterContainer.querySelector(`[data-requirement="${req.id}"]`);
        if (item) {
          item.classList.toggle('met', req.met);
          item.querySelector('.requirement-icon').textContent = req.met ? '✓' : '○';
        }
      });
    }

    // Update warnings
    if (showWarnings && warningsContainer) {
      if (result.warnings.length > 0) {
        warningsContainer.innerHTML = result.warnings
          .map(w => `<div class="warning-item">⚠ ${w}</div>`)
          .join('');
        warningsContainer.style.display = 'block';
      } else {
        warningsContainer.style.display = 'none';
      }
    }

    // Callback
    if (onStrengthChange) {
      onStrengthChange(result);
    }
  }

  // Attach event listener
  inputElement.addEventListener('input', updateStrength);
  inputElement.addEventListener('blur', updateStrength);

  // Initial update
  updateStrength();

  return {
    update: updateStrength,
    destroy: () => {
      meterContainer.remove();
    }
  };
}

/**
 * Async check if password has been breached using HIBP API
 * @param {string} password - Password to check
 * @returns {Promise<{breached: boolean, count: number}>}
 */
export async function checkPasswordBreach(password) {
  if (!password || password.length === 0) {
    return { breached: false, count: 0 };
  }

  try {
    // Calculate SHA-1 hash
    const encoder = new TextEncoder();
    const data = encoder.encode(password.toUpperCase());
    const hashBuffer = await crypto.subtle.digest('SHA-1', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase();

    const prefix = hashHex.substring(0, 5);
    const suffix = hashHex.substring(5);

    // Query HIBP API
    const response = await fetch(`https://api.pwnedpasswords.com/range/${prefix}`);
    if (!response.ok) {
      console.warn('HIBP API request failed');
      return { breached: false, count: 0 };
    }

    const text = await response.text();
    const lines = text.split('\n');

    for (const line of lines) {
      const [hashSuffix, count] = line.trim().split(':');
      if (hashSuffix.toUpperCase() === suffix) {
        return { breached: true, count: parseInt(count, 10) };
      }
    }

    return { breached: false, count: 0 };
  } catch (error) {
    console.warn('Failed to check password breach:', error);
    return { breached: false, count: 0 }; // Fail open
  }
}
