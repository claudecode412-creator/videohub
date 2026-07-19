# VideoHub

A video hosting and streaming platform built with **Spring Boot**, in the
"Netflix style": **admins upload** videos through a private, login-protected
area, and **the public just watches** — no account needed. Designed to handle a
growing library of thousands of videos.

## Tech stack

- Java 21, Spring Boot 3.5.16
- Spring Web (REST) + Spring Data JPA + **Spring Security**
- Thymeleaf (for the two admin pages)
- H2 file-based database (no external DB to install)
- Vanilla HTML/CSS/JS frontend (no build step) — dark theme, violet/indigo accent

## Two areas

| Area | URL | Who | What |
|------|-----|-----|------|
| **Public site** | `/` | Everyone, no login | Browse, search, watch, like |
| **Admin area** | `/admin` | You (password) | Upload (bulk), manage, delete |

The public site shows no trace of the admin area — it lives at `/admin` behind a
login. If a logged-out visitor opens `/admin`, they're sent to `/admin/login`.

## Running it

### From Spring Tools for Eclipse (STS)

1. **File → Import… → Maven → Existing Maven Projects**, select this `videohub` folder, finish.
2. Right-click the project → **Run As → Spring Boot App**.
3. Public site: <http://localhost:8080>  ·  Admin: <http://localhost:8080/admin>

### From the command line

```bash
mvn spring-boot:run
```

## Admin login

Set in `src/main/resources/application.properties`:

```properties
app.admin.username=admin
app.admin.password=admin123
```

> ⚠️ **Change this password** before putting the site online. The password is
> read from the properties file and hashed (BCrypt) in memory at startup.

## Built to scale to thousands of videos

- **Pagination** — the API returns videos one page at a time, never all at once.
- **Infinite scroll** — the public grid loads more pages automatically as you scroll.
- **Lazy thumbnails** — a preview only loads when its card scrolls into view.
- **Search** — filter by title, server-side and paginated.
- **Multi-file upload** — drag & drop many videos at once, each with its own progress bar.
- **Sharded storage** — files are spread across 256 sub-folders.
- **Indexed queries** — the `uploaded_at` column is indexed for fast listing.

## Public UI features

- Hero banner with live totals (videos + views).
- Responsive video grid with duration badges, view + like counts, and "time ago".
- Click a video to watch it in a modal player (records a view).
- **Like button** — likes are remembered per browser (no login needed), so a
  viewer can like/unlike once.

## REST API

Public (no login):

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/api/videos?page=&size=&search=` | One page of videos, newest first |
| GET  | `/api/videos/stats` | Totals: video count + total views |
| GET  | `/api/videos/{id}` | Metadata for one video |
| GET  | `/api/videos/{id}/stream` | Stream the video (supports HTTP Range) |
| POST | `/api/videos/{id}/view` | Record a view |
| POST | `/api/videos/{id}/like` | Add a like |
| DELETE | `/api/videos/{id}/like` | Remove a like |

Admin only (login required):

| Method | Path | Description |
|--------|------|-------------|
| POST   | `/api/videos` | Upload (multipart: `file`, `title?`, `description?`, `durationSeconds?`) |
| DELETE | `/api/videos/{id}` | Delete a video and its file |

## Where things are stored

- Uploaded video files: `./uploads/` (sharded into sub-folders)
- Database files: `./data/`

Both are created automatically and git-ignored. Delete them to reset to empty.

## Roadmap

- **Phase 1 — done ✅** — admin login; public site is watch-only; likes.
- **Phase 2** — richer admin dashboard: edit a video's title/description.
- **Phase 3** — categories/tags, better thumbnails.
- **Phase 4 — production** — PostgreSQL instead of H2; cloud (S3) storage;
  re-enable CSRF protection; deploy; a CDN for global streaming.
