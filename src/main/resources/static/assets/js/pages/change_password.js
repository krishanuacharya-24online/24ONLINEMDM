function readCookie(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

const form = document.getElementById("changePasswordForm");
const messageEl = document.getElementById("changePasswordMessage");

form?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!messageEl) {
    return;
  }

  messageEl.style.color = "#475569";
  messageEl.textContent = "";

  const currentPassword = document.getElementById("currentPassword")?.value ?? "";
  const newPassword = document.getElementById("newPassword")?.value ?? "";
  const confirmPassword = document.getElementById("confirmPassword")?.value ?? "";

  if (newPassword !== confirmPassword) {
    messageEl.style.color = "#b91c1c";
    messageEl.textContent = "New password and confirmation do not match.";
    return;
  }
  if (newPassword.length < 12) {
    messageEl.style.color = "#b91c1c";
    messageEl.textContent = "Password must be at least 12 characters.";
    return;
  }

  const csrfToken = readCookie("XSRF-TOKEN");
  const headers = { "Content-Type": "application/json" };
  if (csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch("/auth/change-password", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
  });

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    messageEl.style.color = "#b91c1c";
    messageEl.textContent = body.message || "Failed to update password.";
    return;
  }

  form.reset();
  messageEl.style.color = "#166534";
  messageEl.textContent = body.message || "Password updated successfully.";
});
