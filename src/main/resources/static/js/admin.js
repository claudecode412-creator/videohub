/* ============================================================
   VideoHub admin dashboard — upload + manage videos.
   This page is only reachable after logging in at /admin/login.
   ============================================================ */
'use strict';

const API = '/api/videos';
const PAGE_SIZE = 10;

const $ = (s) => document.querySelector(s);
const el = (t, c) => { const n = document.createElement(t); if (c) n.className = c; return n; };

/* ---------- formatting ---------- */
function formatCount(n) {
    n = Number(n) || 0;
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

let toastTimer;
function toast(msg, kind = '') {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast show ' + kind;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.hidden = true, 300); }, 3000);
}

/* ============================================================
   Manage list (paginated)
   ============================================================ */
const manage = { page: 0, last: false, loading: false };

async function loadManage() {
    if (manage.loading || manage.last) return;
    manage.loading = true;
    $('#manageStatus').innerHTML = '<span class="spinner"></span>';
    try {
        const res = await fetch(`${API}?page=${manage.page}&size=${PAGE_SIZE}`);
        if (!res.ok) throw new Error();
        const data = await res.json();
        const frag = document.createDocumentFragment();
        data.content.forEach((v) => frag.appendChild(manageRow(v)));
        $('#manageList').appendChild(frag);
        manage.last = data.last;
        manage.page += 1;
        $('#manageCount').textContent = data.totalElements ? `${data.totalElements.toLocaleString()} total` : '';
        $('#manageStatus').textContent = data.totalElements === 0 ? 'No videos yet. Upload some above.' : '';
        $('#loadMore').hidden = manage.last;
    } catch {
        $('#manageStatus').textContent = 'Could not load videos.';
    } finally {
        manage.loading = false;
    }
}

function manageRow(v) {
    const row = el('div', 'manage-row');
    row.dataset.id = v.id;

    const thumb = el('div', 'manage-thumb');
    const vid = el('video');
    vid.muted = true; vid.preload = 'metadata'; vid.src = v.streamUrl + '#t=1';
    thumb.appendChild(vid);

    const info = el('div', 'manage-info');
    const title = el('div', 'manage-title');
    title.textContent = v.title || v.originalFilename || 'Untitled';
    const meta = el('div', 'manage-meta');
    const plural = (n, word) => `${formatCount(n)} ${word}${n === 1 ? '' : 's'}`;
    meta.textContent = `${plural(v.views, 'view')}  •  ${plural(v.likes, 'like')}  •  ${formatBytes(v.sizeBytes)}  •  ${timeAgo(v.uploadedAt)}`;
    info.append(title, meta);

    const del = el('button', 'btn btn-danger-ghost');
    del.innerHTML = '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 7h16M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2m2 0v12a2 2 0 01-2 2H8a2 2 0 01-2-2V7"/></svg> Delete';
    del.addEventListener('click', () => deleteVideo(v.id, row));

    row.append(thumb, info, del);
    return row;
}

async function deleteVideo(id, row) {
    if (!confirm('Delete this video permanently?')) return;
    try {
        const res = await fetch(`${API}/${id}`, { method: 'DELETE' });
        if (res.status === 401 || res.status === 403) { toast('Your session expired — please log in again.', 'err'); return; }
        if (!res.ok) throw new Error();
        row.remove();
        toast('Video deleted', 'ok');
    } catch { toast('Could not delete video', 'err'); }
}

$('#loadMore').addEventListener('click', loadManage);

/* ============================================================
   Upload (multi-file, drag & drop, progress)
   ============================================================ */
let uploadQueue = [];
const dropzone = $('#dropzone');
const fileInput = $('#fileInput');

['dragenter', 'dragover'].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.add('drag'); }));
['dragleave', 'drop'].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.remove('drag'); }));
dropzone.addEventListener('drop', (e) => addFiles(e.dataTransfer.files));
fileInput.addEventListener('change', () => addFiles(fileInput.files));

function addFiles(fileList) {
    const vids = Array.from(fileList).filter((f) => f.type.startsWith('video/') || /\.(mp4|mov|webm|mkv|avi|m4v|ogv)$/i.test(f.name));
    if (!vids.length) { toast('Please choose video files', 'err'); return; }
    for (const f of vids) uploadQueue.push({ file: f, status: 'queued', progress: 0 });
    renderUploadList();
}

function renderUploadList() {
    const list = $('#uploadList');
    list.innerHTML = '';
    uploadQueue.forEach((item, i) => {
        const li = el('li', 'upload-item');
        li.dataset.index = i;
        const name = el('span', 'u-name'); name.textContent = item.file.name;
        const stateEl = el('span', 'u-state'); stateEl.textContent = formatBytes(item.file.size);
        const prog = el('div', 'u-progress');
        const bar = el('div', 'u-progress-bar');
        prog.appendChild(bar);
        li.append(name, stateEl, prog);
        list.appendChild(li);
    });
    $('#startUpload').disabled = uploadQueue.filter((i) => i.status === 'queued').length === 0;
    $('#uploadSummary').textContent = uploadQueue.length ? `${uploadQueue.length} file${uploadQueue.length > 1 ? 's' : ''} selected` : '';
}

function readDuration(file) {
    return new Promise((resolve) => {
        const url = URL.createObjectURL(file);
        const v = document.createElement('video');
        v.preload = 'metadata';
        const done = (val) => { URL.revokeObjectURL(url); resolve(val); };
        const timer = setTimeout(() => done(null), 8000);
        v.onloadedmetadata = () => { clearTimeout(timer); done(isFinite(v.duration) ? v.duration : null); };
        v.onerror = () => { clearTimeout(timer); done(null); };
        v.src = url;
    });
}

function updateItemRow(item) {
    const i = uploadQueue.indexOf(item);
    const li = $(`#uploadList .upload-item[data-index="${i}"]`);
    if (!li) return;
    li.querySelector('.u-progress-bar').style.width = item.progress + '%';
    const stateEl = li.querySelector('.u-state');
    stateEl.className = 'u-state';
    if (item.status === 'done') { stateEl.textContent = 'Done'; stateEl.classList.add('done'); }
    else if (item.status === 'error') { stateEl.textContent = 'Failed'; stateEl.classList.add('err'); }
    else if (item.status === 'uploading') { stateEl.textContent = item.progress + '%'; }
}

function uploadOne(item, single) {
    return new Promise((resolve) => {
        readDuration(item.file).then((duration) => {
            const form = new FormData();
            form.append('file', item.file);
            const title = $('#uploadTitleInput').value.trim();
            if (single && title) form.append('title', title);
            const desc = $('#uploadDescInput').value.trim();
            if (desc) form.append('description', desc);
            if (duration) form.append('durationSeconds', duration);

            const xhr = new XMLHttpRequest();
            xhr.open('POST', API);
            xhr.upload.onprogress = (e) => {
                if (e.lengthComputable) {
                    item.progress = Math.round((e.loaded / e.total) * 100);
                    item.status = 'uploading';
                    updateItemRow(item);
                }
            };
            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) { item.status = 'done'; item.progress = 100; }
                else if (xhr.status === 401 || xhr.status === 403) { item.status = 'error'; item.authError = true; }
                else { item.status = 'error'; }
                updateItemRow(item); resolve();
            };
            xhr.onerror = () => { item.status = 'error'; updateItemRow(item); resolve(); };
            xhr.send(form);
        });
    });
}

$('#startUpload').addEventListener('click', async () => {
    const pending = uploadQueue.filter((i) => i.status === 'queued');
    if (!pending.length) return;
    const single = pending.length === 1 && uploadQueue.length === 1;
    $('#startUpload').disabled = true;

    let ok = 0, authError = false;
    for (const item of pending) {
        await uploadOne(item, single);
        if (item.status === 'done') ok++;
        if (item.authError) authError = true;
    }

    if (authError) { toast('Your session expired — please log in again.', 'err'); return; }
    toast(`Uploaded ${ok} of ${pending.length} video${pending.length > 1 ? 's' : ''}`, ok ? 'ok' : 'err');

    setTimeout(() => {
        uploadQueue = [];
        $('#uploadTitleInput').value = '';
        $('#uploadDescInput').value = '';
        renderUploadList();
        // Reload the manage list from the top so new uploads show.
        manage.page = 0; manage.last = false;
        $('#manageList').innerHTML = '';
        loadManage();
    }, 700);
});

/* ---------- boot ---------- */
loadManage();
