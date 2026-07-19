/* ============================================================
   VideoHub public site — browse, search, watch, like.
   Uses classic numbered pages (10 videos per page).
   Uploading lives in the login-protected /admin area.
   ============================================================ */
'use strict';

const API = '/api/videos';
const LIKED_KEY = 'vh_liked';
const PAGE_SIZE_KEY = 'vh_page_size';
const PAGE_SIZE_OPTIONS = [10, 50, 100];
const DEFAULT_PAGE_SIZE = 10;

function loadPageSize() {
    const saved = parseInt(localStorage.getItem(PAGE_SIZE_KEY), 10);
    return PAGE_SIZE_OPTIONS.includes(saved) ? saved : DEFAULT_PAGE_SIZE;
}

const state = { page: 0, pageSize: loadPageSize(), search: '', totalPages: 0, totalElements: 0, loading: false };

const $ = (sel) => document.querySelector(sel);
const el = (tag, cls) => { const n = document.createElement(tag); if (cls) n.className = cls; return n; };

/* ---------- formatting ---------- */
function formatCount(n) {
    n = Number(n) || 0;
    if (n >= 1e9) return (n / 1e9).toFixed(1).replace(/\.0$/, '') + 'B';
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (n >= 1e3) return (n / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return String(n);
}
function formatBytes(bytes) {
    bytes = Number(bytes) || 0;
    if (!bytes) return '';
    const u = ['B', 'KB', 'MB', 'GB', 'TB'];
    let i = 0;
    while (bytes >= 1024 && i < u.length - 1) { bytes /= 1024; i++; }
    return bytes.toFixed(bytes < 10 && i > 0 ? 1 : 0) + ' ' + u[i];
}
function formatDuration(sec) {
    if (!sec || sec < 0) return null;
    sec = Math.round(sec);
    const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    const pad = (x) => String(x).padStart(2, '0');
    return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}
function timeAgo(iso) {
    const then = new Date(iso).getTime();
    if (isNaN(then)) return '';
    const secs = Math.max(1, Math.floor((Date.now() - then) / 1000));
    const units = [['year', 31536000], ['month', 2592000], ['week', 604800],
                   ['day', 86400], ['hour', 3600], ['minute', 60]];
    for (const [name, s] of units) {
        const v = Math.floor(secs / s);
        if (v >= 1) return `${v} ${name}${v > 1 ? 's' : ''} ago`;
    }
    return 'just now';
}
function metaHtml(v) {
    return `${formatCount(v.views)} view${v.views === 1 ? '' : 's'}`
        + `<span class="dot">•</span>${formatCount(v.likes)} like${v.likes === 1 ? '' : 's'}`
        + `<span class="dot">•</span>${timeAgo(v.uploadedAt)}`;
}

/* ---------- likes remembered per browser ---------- */
function likedSet() {
    try { return new Set(JSON.parse(localStorage.getItem(LIKED_KEY) || '[]')); }
    catch { return new Set(); }
}
function isLiked(id) { return likedSet().has(id); }
function setLiked(id, liked) {
    const s = likedSet();
    if (liked) s.add(id); else s.delete(id);
    localStorage.setItem(LIKED_KEY, JSON.stringify([...s]));
}

/* ---------- toast ---------- */
let toastTimer;
function toast(msg, kind = '') {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast show ' + kind;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.hidden = true, 300); }, 3000);
}

/* ---------- lazy thumbnails ---------- */
const thumbObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const video = entry.target;
        if (!video.dataset.src) { thumbObserver.unobserve(video); continue; }
        video.src = video.dataset.src + '#t=1';
        video.load();
        delete video.dataset.src;
        thumbObserver.unobserve(video);
    }
}, { rootMargin: '400px 0px' });

/* ============================================================
   Cards
   ============================================================ */
function createCard(v) {
    const card = el('div', 'card');
    card.dataset.id = v.id;
    // Locked (premium & not subscribed) cards send the viewer to the subscribe page.
    card.addEventListener('click', () => { if (v.locked) window.location.href = '/subscribe'; else openWatch(v); });

    const thumb = el('div', 'thumb');
    const fallback = el('div', 'thumb-fallback');
    fallback.textContent = (v.title || v.originalFilename || '?').trim().charAt(0).toUpperCase();
    thumb.appendChild(fallback);

    const vid = el('video');
    vid.muted = true; vid.playsInline = true; vid.preload = 'none';
    vid.dataset.src = v.streamUrl;
    thumb.appendChild(vid);
    if (!v.locked) thumbObserver.observe(vid); // a locked video can't be streamed for a preview

    if (v.locked) {
        card.classList.add('locked');
        const lock = el('div', 'lock-badge');
        lock.innerHTML = '<svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" d="M6 10V7a6 6 0 1112 0v3M5 10h14v10H5z"/></svg> Premium';
        thumb.appendChild(lock);
    } else {
        const play = el('div', 'play-badge');
        play.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M8 5v14l11-7z" fill="currentColor"/></svg>';
        thumb.appendChild(play);
    }

    const dur = formatDuration(v.durationSeconds);
    if (dur) { const b = el('span', 'duration-badge'); b.textContent = dur; thumb.appendChild(b); }

    const body = el('div', 'card-body');
    const avatar = el('div', 'card-avatar');
    avatar.textContent = (v.title || v.originalFilename || '?').trim().charAt(0).toUpperCase();
    const info = el('div', 'card-info');
    const title = el('div', 'card-title');
    title.textContent = v.title || v.originalFilename || 'Untitled';
    const meta = el('div', 'card-meta');
    meta.innerHTML = metaHtml(v);
    info.append(title, meta);
    body.append(avatar, info);

    card.append(thumb, body);
    return card;
}

function renderSkeletons(count) {
    const grid = $('#grid');
    for (let i = 0; i < count; i++) {
        const sk = el('div', 'card skeleton');
        sk.innerHTML = '<div class="thumb"></div><div class="sk-line"></div><div class="sk-line short"></div>';
        grid.appendChild(sk);
    }
}

/* ============================================================
   Load ONE page (replaces the grid) and draw the pager
   ============================================================ */
async function loadPage(page, opts = {}) {
    if (state.loading) return;
    state.loading = true;

    $('#grid').innerHTML = '';
    renderSkeletons(state.pageSize);
    $('#emptyState').hidden = true;
    $('#pager').innerHTML = '';
    $('#pageSizeBar').hidden = true;
    $('#listStatus').textContent = '';

    try {
        const params = new URLSearchParams({ page, size: state.pageSize });
        if (state.search) params.set('search', state.search);
        const res = await fetch(`${API}?${params}`);
        if (!res.ok) throw new Error('Failed to load videos');
        const data = await res.json();

        $('#grid').innerHTML = '';
        const frag = document.createDocumentFragment();
        data.content.forEach((v) => frag.appendChild(createCard(v)));
        $('#grid').appendChild(frag);

        state.page = data.page;
        state.totalPages = data.totalPages;
        state.totalElements = data.totalElements;

        $('#sectionCount').textContent = data.totalElements ? `${data.totalElements.toLocaleString()} total` : '';

        if (data.totalElements === 0) {
            $('#emptyState').hidden = false;
        } else {
            const start = data.page * data.size + 1;
            const end = data.page * data.size + data.content.length;
            $('#listStatus').textContent = `Showing ${start}–${end} of ${data.totalElements.toLocaleString()}`;
            renderPager();
            $('#pageSizeBar').hidden = false;
        }

        if (opts.scroll) $('.section-head').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (err) {
        $('#grid').innerHTML = '';
        $('#listStatus').textContent = 'Could not load videos. Is the server running?';
        console.error(err);
    } finally {
        state.loading = false;
    }
}

function goToPage(page) {
    if (page < 0 || page >= state.totalPages || page === state.page) return;
    loadPage(page, { scroll: true });
}

/* Which page buttons to show, with … for gaps. Works on 1-indexed numbers. */
function pageItems(current, total) {
    const wanted = new Set([1, total, current, current - 1, current + 1]);
    const pages = [...wanted].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
    const out = [];
    let prev = 0;
    for (const p of pages) {
        if (p - prev > 1) out.push('…');
        out.push(p);
        prev = p;
    }
    return out;
}

function renderPager() {
    const pager = $('#pager');
    pager.innerHTML = '';
    if (state.totalPages <= 1) return;

    const current = state.page + 1; // 1-indexed for display
    const total = state.totalPages;

    const mkBtn = (label, targetPage, { active = false, disabled = false, aria } = {}) => {
        const b = el('button', active ? 'active' : '');
        b.textContent = label;
        if (aria) b.setAttribute('aria-label', aria);
        if (active) b.setAttribute('aria-current', 'page');
        if (disabled) b.disabled = true;
        else b.addEventListener('click', () => goToPage(targetPage));
        return b;
    };

    pager.appendChild(mkBtn('‹', state.page - 1, { disabled: state.page === 0, aria: 'Previous page' }));
    for (const item of pageItems(current, total)) {
        if (item === '…') {
            const s = el('span', 'ellipsis'); s.textContent = '…'; pager.appendChild(s);
        } else {
            pager.appendChild(mkBtn(String(item), item - 1, { active: item === current, aria: `Page ${item}` }));
        }
    }
    pager.appendChild(mkBtn('›', state.page + 1, { disabled: state.page >= total - 1, aria: 'Next page' }));
}

/* ============================================================
   Stats
   ============================================================ */
async function loadStats() {
    try {
        const res = await fetch(`${API}/stats`);
        if (!res.ok) return;
        const s = await res.json();
        animateNumber($('#statVideos'), s.totalVideos);
        animateNumber($('#statViews'), s.totalViews);
    } catch { /* stats are non-critical */ }
}
function animateNumber(node, target) {
    target = Number(target) || 0;
    const start = performance.now(), dur = 700;
    function tick(now) {
        const p = Math.min(1, (now - start) / dur);
        const eased = 1 - Math.pow(1 - p, 3);
        node.textContent = formatCount(Math.round(target * eased));
        if (p < 1) requestAnimationFrame(tick); else node.textContent = formatCount(target);
    }
    requestAnimationFrame(tick);
}

/* ============================================================
   Search (debounced) — jumps back to page 1
   ============================================================ */
let searchTimer;
$('#searchInput').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    const q = e.target.value.trim();
    searchTimer = setTimeout(() => {
        state.search = q;
        $('#sectionTitle').textContent = q ? `Results for “${q}”` : 'Recent uploads';
        loadPage(0);
    }, 350);
});

/* ============================================================
   Modal + watch + like
   ============================================================ */
function openModal(id) { $(`#${id}`).hidden = false; document.body.style.overflow = 'hidden'; }
function closeModal(id) {
    $(`#${id}`).hidden = true;
    document.body.style.overflow = '';
    if (id === 'watchModal') { const p = $('#player'); p.pause(); p.removeAttribute('src'); p.load(); }
}
document.addEventListener('click', (e) => {
    const closer = e.target.closest('[data-close]');
    if (closer) closeModal(closer.dataset.close);
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !$('#watchModal').hidden) closeModal('watchModal');
});

let currentVideo = null;
async function openWatch(v) {
    currentVideo = v;
    $('#watchTitle').textContent = v.title || v.originalFilename || 'Untitled';
    $('#watchDesc').textContent = v.description || '';
    const player = $('#player');
    player.src = v.streamUrl;
    player.play().catch(() => { /* autoplay may be blocked; user can press play */ });
    updateLikeUI(isLiked(v.id), v.likes);
    updateWatchStats(v, v.views + 1); // optimistic
    openModal('watchModal');

    try {
        const res = await fetch(`${API}/${v.id}/view`, { method: 'POST' });
        if (res.ok) { const u = await res.json(); currentVideo = u; updateWatchStats(u, u.views); loadStats(); }
    } catch { /* non-critical */ }
}
function updateWatchStats(v, views) {
    const parts = [`${formatCount(views)} view${views === 1 ? '' : 's'}`, timeAgo(v.uploadedAt)];
    if (v.sizeBytes) parts.push(formatBytes(v.sizeBytes));
    $('#watchStats').textContent = parts.join('  •  ');
}
function updateLikeUI(liked, count) {
    const btn = $('#likeBtn');
    btn.classList.toggle('liked', liked);
    btn.setAttribute('aria-pressed', liked ? 'true' : 'false');
    $('#likeCount').textContent = formatCount(count);
}

$('#likeBtn').addEventListener('click', async () => {
    if (!currentVideo) return;
    const liked = isLiked(currentVideo.id);
    const willLike = !liked;
    setLiked(currentVideo.id, willLike);
    updateLikeUI(willLike, (currentVideo.likes || 0) + (willLike ? 1 : -1));
    try {
        const res = await fetch(`${API}/${currentVideo.id}/like`, { method: willLike ? 'POST' : 'DELETE' });
        if (res.ok) {
            const u = await res.json();
            currentVideo.likes = u.likes;
            updateLikeUI(willLike, u.likes);
            const cardMeta = $(`#grid .card[data-id="${u.id}"] .card-meta`);
            if (cardMeta) cardMeta.innerHTML = metaHtml(u);
        }
    } catch {
        setLiked(currentVideo.id, liked);
        updateLikeUI(liked, currentVideo.likes || 0);
        toast('Could not save your like', 'err');
    }
});

/* ============================================================
   Videos per page — remembered per browser, jumps back to page 1
   ============================================================ */
const pageSizeSelect = $('#pageSizeSelect');
pageSizeSelect.value = String(state.pageSize);
pageSizeSelect.addEventListener('change', (e) => {
    const next = parseInt(e.target.value, 10);
    if (!PAGE_SIZE_OPTIONS.includes(next) || next === state.pageSize) return;
    state.pageSize = next;
    localStorage.setItem(PAGE_SIZE_KEY, String(next));
    loadPage(0, { scroll: true });
});

/* ============================================================
   Header auth state — shows Log in / Sign up, or the user + Log out
   ============================================================ */
async function renderAuth() {
    const box = $('#navAuth');
    if (!box) return;
    try {
        const res = await fetch('/api/auth/me');
        box.innerHTML = '';
        if (res.ok) {
            const u = await res.json();
            // Show membership status: a "Member" badge, or a "Subscribe" button.
            let sub = null;
            try { const s = await fetch('/api/subscription/me'); if (s.ok) sub = await s.json(); } catch { /* ignore */ }
            if (sub && sub.active) {
                const member = el('span', 'nav-member'); member.textContent = '★ Member';
                box.append(member);
            } else {
                const subscribe = el('a', 'btn btn-primary'); subscribe.href = '/subscribe'; subscribe.textContent = 'Subscribe';
                box.append(subscribe);
            }
            const hi = el('span', 'nav-user');
            hi.textContent = u.displayName || u.email;
            const out = el('button', 'btn btn-ghost');
            out.textContent = 'Log out';
            out.addEventListener('click', async () => {
                try { await fetch('/api/auth/logout', { method: 'POST' }); } catch { /* ignore */ }
                window.location.reload();
            });
            box.append(hi, out);
        } else {
            const login = el('a', 'btn btn-ghost'); login.href = '/login'; login.textContent = 'Log in';
            const signup = el('a', 'btn btn-primary'); signup.href = '/signup'; signup.textContent = 'Sign up';
            box.append(login, signup);
        }
    } catch { /* header auth is non-critical */ }
}

/* ---------- boot ---------- */
if (new URLSearchParams(window.location.search).get('subscribed') === '1') {
    toast('🎉 You are now a member — enjoy all videos!', 'ok');
    window.history.replaceState({}, '', '/');
}
renderAuth();
loadStats();
loadPage(0);
