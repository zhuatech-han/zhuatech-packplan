# PackPlan 架构、算法与数据 / Architecture

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

Browser → Nginx same-origin `/api` → Spring Security session/CSRF → current account/role/scope → transactional service → JPA → MySQL 8.4. Flyway V1 identity, V2 packing; Hibernate validates schema only. The bootstrap creates directories and one externally configured administrator only.

## Tables

`pack_plan`: stable code, team, designated reviewer, mm dimensions, gram limit, state, version and active snapshot. `cargo_line`: stable type code, size, quantity, gram mass, rotation and upper-load limit. `plan_revision`: immutable input/metadata/layout JSON and SHA256. `plan_editor`: retained historical editor relation. `plan_event`: append-only application events. Identity/admin directory tables are in V1.

## Geometry and deterministic heuristic

Algorithm `SUPPORTED-EXTREME-POINT-1` compares four demand orders: volume, mass, footprint, upper-load capacity then volume. A finite candidate set is formed from origin and placed positive faces plus floor projections, ordered by Z, X, Y; test the permitted orientations. Compare placed unit count, then placed volume, retain first tie. Up to20 types/120 units; no global optimum claim, no continuous search or complete feasibility proof.

All dimensions are integer mm and mass integer grams. Strict AABB overlap allows touching faces. Every elevated box must be fully supported by one box exactly below, not a partial union of multiple boxes. The complete upper subtree mass contributes to every ancestor's configured top-load limit. Total mass must remain within the configured cargo limit. Coordinates, IDs and orientation are reconstructed from source demand, including manual updates. Unplaced IDs complete the demand count exactly.

The algorithm treats rigid upright cuboids only. Does not model compressibility, friction, tie-down forces, center of gravity, axle/floor point loads, container structural certification, door/access paths, hazardous-goods rules, loading equipment or multi-container economic optimization. Input top-load values are operator declarations and do not prove cargo strength. Axis projection and painter ordering are illustrative; selected-only view assists inspection of occluded boxes.

## Transactions and scopes

Every business/admin write serializes via department row1 for this single low-volume instance. Clear the request persistence identity cache after lock, then recheck current permissions. No write replay cache. Positive plan versions reject stale/repeated writes. ALL has full scope; DEPARTMENT sees its department; ASSIGNED sees same-department plans created/edited by or explicitly assigned to that account. Scope does not bypass designated-reviewer or historical-editor rules. Identity department changes are prohibited after business references.

Logs contain action/object identifiers only. Passwords BCrypt12, HttpOnly/SameSite Strict session, CSRF, failed-login window, account/team disable and password reset revoke stale sessions. In-memory sessions require login after restart and are not a distributed SaaS authentication service. Database administrators can alter records and digests; application append history is not an immutable external ledger.
