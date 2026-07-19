/* ============================================================
   VideoHub viewer auth — powers the /signup and /login pages.
   Posts to /api/auth/*, shows any error, and returns to the
   home page on success (the visitor is now logged in).
   ============================================================ */
'use strict';

const errorEl = document.getElementById('authError');
const submitBtn = document.getElementById('submitBtn');

function showError(msg) {
    errorEl.textContent = msg;
    errorEl.hidden = false;
}

async function submitAuth(url) {
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;

    errorEl.hidden = true;
    submitBtn.disabled = true;
    const original = submitBtn.textContent;
    submitBtn.textContent = 'Please wait…';

    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
        if (res.ok) {
            window.location.href = '/'; // logged in — back to the site
            return;
        }
        let msg = 'Something went wrong. Please try again.';
        try { const data = await res.json(); if (data && data.error) msg = data.error; } catch { /* ignore */ }
        showError(msg);
    } catch {
        showError('Could not reach the server. Please try again.');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = original;
    }
}

const signupForm = document.getElementById('signupForm');
if (signupForm) {
    signupForm.addEventListener('submit', (e) => { e.preventDefault(); submitAuth('/api/auth/signup'); });
}

const loginForm = document.getElementById('loginForm');
if (loginForm) {
    loginForm.addEventListener('submit', (e) => { e.preventDefault(); submitAuth('/api/auth/login'); });
}
