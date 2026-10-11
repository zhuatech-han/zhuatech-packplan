# PackPlan 安全与限制 / Security

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

Run the learning instance on localhost with random independent passwords generated into a private0600 `.env`. Never commit passwords, cookies, tokens, backup SQL, real customer cargo or container location data. Use `.env.example` for configuration names only. Customer deployment requires written commercial authorization, trusted HTTPS, `COOKIE_SECURE=true`, access isolation, independent security review and verified backup recovery.

Only the same-origin frontend exposes a host port. Nginx limits requests to128KB and avoids access logs. CSV receives its own64KB and quantity limits; SVG exports escape XML and contain no scripts/external assets. JSON export uses authorized source snapshots; CSV neutralizes spreadsheet formula prefixes. Verify application scope and history with the provided private localhost-only smoke checks.

BCrypt respects its72-byte input limit. Session authentication stores a password fingerprint and rechecks account/team status and current role permissions on each request. Do not expose local development ports as a production service. Login throttling and sessions are per-process; upstream abuse controls and secure cookie settings remain deployment responsibilities.

Report vulnerabilities privately to han@zhuatech.cn or jack@zhuatech.cn with redacted reproduction details. Do not publish exploit credentials or customer data in Issues. No production, penetration, hardware-loading or operational safety certification is claimed. Geometry validation checks a simplified declared box model only.
