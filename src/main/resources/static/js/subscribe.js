/* ============================================================
   VideoHub subscribe page — shows the pass price / your status
   and starts a Stripe checkout.
   ============================================================ */
'use strict';

const statusEl = document.getElementById('subStatus');
const errEl = document.getElementById('subError');

function showError(msg) {
    errEl.textContent = msg;
    errEl.hidden = false;
}

/** 9900 + "inr" -> "₹99" */
function money(minor, currency) {
    const amount = (Number(minor) || 0) / 100;
    const symbols = { inr: '₹', usd: '$', eur: '€', gbp: '£' };
    const symbol = symbols[(currency || '').toLowerCase()];
    const text = amount.toFixed(amount % 1 === 0 ? 0 : 2);
    return symbol ? symbol + text : `${text} ${(currency || '').toUpperCase()}`;
}

async function startCheckout(e) {
    const btn = e.currentTarget;
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Redirecting…';
    errEl.hidden = true;
    try {
        const res = await fetch('/api/subscription/checkout', { method: 'POST' });
        const data = await res.json().catch(() => ({}));
        if (res.ok && data.url) {
            window.location.href = data.url; // off to Stripe's secure page
            return;
        }
        showError(data.error || 'Could not start checkout. Please try again.');
    } catch {
        showError('Could not reach the server. Please try again.');
    }
    btn.disabled = false;
    btn.textContent = original;
}

async function load() {
    if (new URLSearchParams(window.location.search).get('canceled') === '1') {
        showError('Checkout was cancelled — you have not been charged.');
    }

    let s;
    try {
        const res = await fetch('/api/subscription/me');
        s = await res.json();
    } catch {
        statusEl.textContent = 'Could not load subscription details.';
        return;
    }

    statusEl.innerHTML = '';

    if (!s.loggedIn) {
        statusEl.innerHTML =
            '<p class="auth-alt">You need an account first — ' +
            '<a href="/login">log in</a> or <a href="/signup">sign up</a>.</p>';
        return;
    }

    if (s.active) {
        const until = s.until ? new Date(s.until).toLocaleDateString() : '';
        const p = document.createElement('p');
        p.className = 'sub-active';
        p.textContent = `★ You're a member until ${until}`;
        const renew = document.createElement('button');
        renew.className = 'btn btn-ghost auth-submit';
        renew.textContent = `Extend by ${s.days} more days`;
        renew.addEventListener('click', startCheckout);
        statusEl.append(p, renew);
        return;
    }

    const price = document.createElement('p');
    price.className = 'sub-price';
    price.innerHTML = `${money(s.priceMinor, s.currency)} <span>for ${s.days} days</span>`;
    const pay = document.createElement('button');
    pay.className = 'btn btn-primary auth-submit';
    pay.textContent = `Get ${s.days}-day pass`;
    pay.addEventListener('click', startCheckout);
    statusEl.append(price, pay);
}

load();
