# PackPlan API

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

All endpoints use the same-origin HttpOnly session. Mutations require the actual CSRF header returned by GET `/api/auth/csrf`. No API accepts the client's approved state, calculated weight or collision result.

| Endpoint | Permission / behavior |
|---|---|
| POST `/api/auth/login`, GET `/api/auth/me`, POST `/api/auth/logout`, `/api/auth/password` | Login / current identity / logout / own password change |
| GET `/api/plans`, `/api/options` | `plans`; ALL / DEPARTMENT / ASSIGNED scope |
| POST `/api/plans`; PUT `/api/plans/{id}` | `design`; create / draft input update; stable code/team |
| GET `/api/plans/{id}` | Plan, demand, revision summaries, participants and events |
| POST `/api/plans/{id}/cargo`; PUT/DELETE `/api/plans/{id}/cargo/{line}` | Draft cargo add/update/remove; plan version required |
| POST `/api/plans/{id}/preview-csv` | Validate all CSV rows, no data replacement |
| POST `/api/plans/{id}/import-csv` | Explicit atomic replacement, old revisions retained |
| POST `/api/plans/{id}/calculate` | Deterministic calculation, new retained snapshot |
| POST `/api/plans/{id}/manual` | Full position list; unknown/duplicate UID rejected; missing units explicitly unplaced |
| POST `/api/plans/{id}/submit` | Current snapshot, eligible independent reviewer; partial layout requires `acknowledgeUnplaced=true` and exclusion note |
| POST `/api/plans/{id}/approve`, `/return` | `review`; only assigned non-editor; note ≥4 characters |
| POST `/api/plans/{id}/withdraw`, `/reopen`, `/cancel` | `design`; controlled state and note; cancelled records retained |
| GET `/api/plans/{id}/revisions/{rev}` | Read-only historical snapshot, plan ownership checked |
| GET `/api/plans/{id}/revisions/{rev}/export.json`, `.csv`, `.svg` | `export`; scope and digest checked; fixed server filename |
| GET `/api/reports`, `/api/audit` | Current role/scope; assigned scope cannot read full department audit |
| GET/POST `/api/admin/{kind}`, PUT `/api/admin/{kind}/{id}` | Whitelisted identity/directory resources, ALL scope and permission |

Cargo fields: `code,name,length,width,height,quantity,weightKg,rotate,topLoadKg`. Dimensions are integer mm; kg fields are exact decimal strings up to three decimal places. Plan fields include `code,title,departmentId,reviewerId,category,length,width,height,maxWeightKg,note`.

Every write on an existing plan carries the plan's positive `version`, not a cargo-line or revision version. The server increments it once upon each successful change. Old/repeated versions return 409 `VERSION_CONFLICT`; no write is automatically retried. Example manual placement member: `{ "uid": "AA-001", "x": 0, "y": 0, "z": 0, "rotated": false }`. The server rebuilds dimensions and mass from demand.

CSV header is exactly `code,name,length_mm,width_mm,height_mm,quantity,weight_kg,rotate,top_load_kg`; boolean strings are `true`/`false`. UTF-8, optional BOM, quotes/escaped quotes supported, maximum 64KB and 20 types / 120 boxes. Invalid rows, duplicates and quantities reject the complete import. Binary XLS/XLSX files are not parsed.

Error codes: 400 invalid input/geometry/CSV; 401 expired identity; 403 permission/scope; 409 stale version/state/independence; 413 configured resource/CSV limit. The returned JSON exposes a safe `code`, without SQL or credentials. Re-read current data after an unknown network result.
