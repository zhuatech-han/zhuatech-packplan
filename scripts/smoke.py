#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只验收明确独立localhost实例；随机测试状态保持0600，不打印凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import concurrent.futures
import http.cookiejar
import json
import os
import secrets
import threading
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


class Client:
    """真实会话和CSRF，不自动重放写请求。知华科技 https://www.zhuatech.cn/。"""

    count = 0
    count_lock = threading.Lock()

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.csrf = None

    def call(self, path, method="GET", body=None, status=200, code=None, raw=False):
        headers = {}
        if method != "GET":
            if self.csrf is None:
                self.csrf = self.call("/auth/csrf")
            headers[self.csrf["header"]] = self.csrf["token"]
        if body is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            self.base + "/api" + path,
            method=method,
            headers=headers,
            data=json.dumps(body).encode() if body is not None else None,
        )
        with Client.count_lock:
            Client.count += 1
        try:
            with self.opener.open(req, timeout=45) as response:
                actual, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            actual, content = error.code, error.read()
        parsed = content.decode() if raw else json.loads(content) if content else None
        if status is not None:
            safe = (
                parsed.get("code", "HTTP_ERROR")
                if isinstance(parsed, dict)
                else "HTTP_ERROR"
            )
            assert (
                actual == status
            ), f"{method} {path}: expected {status}, got {actual}: {safe}"
        if code is not None:
            assert parsed["code"] == code, f"{path}: unexpected safe error code"
        return (actual, parsed) if status is None else parsed

    def login(self, username, password):
        result = self.call(
            "/auth/login", "POST", {"username": username, "password": password}
        )
        self.csrf = None
        return result


def snapshot(admin):
    """保存所有公开业务及目录快照，登录审计单独排除以允许重新登录。知华科技 https://www.zhuatech.cn/。"""
    paths = [
        "/plans",
        "/admin/users",
        "/admin/roles",
        "/admin/permissions",
        "/admin/departments",
        "/admin/menus",
        "/admin/dictionaries",
        "/admin/settings",
        "/reports",
    ]
    out = {path: admin.call(path) for path in paths}
    out["details"] = {
        str(p["id"]): admin.call("/plans/" + str(p["id"])) for p in out["/plans"]
    }
    out["revisions"] = {}
    out["exports"] = {}
    for pid, detail in out["details"].items():
        for revision in detail["revisions"]:
            key = pid + "/" + str(revision["id"])
            path = "/plans/" + pid + "/revisions/" + str(revision["id"])
            out["revisions"][key] = admin.call(path)
            out["exports"][key] = {
                kind: admin.call(path + "/export." + kind, raw=kind != "json")
                for kind in ["json", "csv", "svg"]
            }
    out["audit"] = [e for e in admin.call("/audit") if e["action"] != "LOGIN"]
    return out


def verify(args):
    """重启/恢复重新登录，业务、目录、导出逐项相等并复验角色。知华科技 https://www.zhuatech.cn/。"""
    state = json.loads(Path(args.state_input).read_text())
    assert state["product"] == "PackPlan"
    admin = Client(args.base)
    admin.login(state["adminUsername"], state["adminPassword"])
    assert (
        snapshot(admin) == state["snapshot"]
    ), "Persisted business/identity/revision/event/export snapshot changed"
    for username, meta in state["users"].items():
        client = Client(args.base)
        if meta.get("disabled"):
            client.call(
                "/auth/login",
                "POST",
                {"username": username, "password": meta["password"]},
                401,
                "LOGIN_FAILED",
            )
        else:
            client.login(username, meta["password"])
            assert [p["id"] for p in client.call("/plans")] == meta["visiblePlanIds"]
            client.call("/admin/users", status=403, code="FORBIDDEN")
    print(
        json.dumps(
            {
                "mode": "verify",
                "httpChecks": Client.count,
                "allSnapshotsEqual": True,
                "roleScopesRechecked": True,
            }
        )
    )


def create(args):
    """用真实API验收创建、导入、计算、拒绝非法布局、独立复核和冻结。知华科技 https://www.zhuatech.cn/。"""
    target = Path(args.state_output).resolve()
    if target.exists():
        raise SystemExit("Refuse existing private state")
    env = dict(
        line.split("=", 1)
        for line in Path(args.env_file).read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    admin = Client(args.base)
    admin_profile = admin.login(
        env.get("ADMIN_USERNAME", "admin"), env["ADMIN_PASSWORD"]
    )
    assert {m["code"] for m in admin_profile["menus"]} == {
        "plans",
        "reports",
        "users",
        "roles",
        "settings",
        "audit",
    }
    suffix = secrets.token_hex(4)
    password = "Aa9" + secrets.token_hex(20)
    clients, users = {}, {}
    outside_dept = admin.call(
        "/admin/departments",
        "POST",
        {"name": "TEST outside team", "zone": "UTC", "enabled": True},
    )["id"]

    def user(label, role, dept=1):
        username = "test-" + label + "-" + suffix
        row = admin.call(
            "/admin/users",
            "POST",
            {
                "username": username,
                "displayName": "TEST " + label,
                "roleId": role,
                "departmentId": dept,
                "enabled": True,
                "password": password,
            },
        )
        users[username] = {"id": row["id"], "roleId": role, "password": password}
        client = Client(args.base)
        client.login(username, password)
        clients[label] = client
        return row["id"]

    planner_id = user("planner", 2)
    reviewer_id = user("reviewer", 3)
    assigned_id = user("assigned", 4)
    user("reader", 5)
    user("outside", 2, outside_dept)
    disabled_id = user("disabled", 2)
    planner, reviewer = clients["planner"], clients["reviewer"]

    def plan(label, review_id=reviewer_id):
        return planner.call(
            "/plans",
            "POST",
            {
                "code": "TEST_" + suffix.upper() + "_" + label,
                "title": "TEST " + label + " cargo",
                "departmentId": 1,
                "reviewerId": review_id,
                "category": "CARTON",
                "length": 3000,
                "width": 1800,
                "height": 2000,
                "maxWeightKg": "2000",
                "note": "TEST synthetic dimensions / no real cargo",
            },
        )

    def detail(p):
        return planner.call("/plans/" + str(p["id"]))

    def act(p, action, who=planner, body=None):
        values = {"version": detail(p)["plan"]["version"], **(body or {})}
        return who.call("/plans/" + str(p["id"]) + "/" + action, "POST", values)

    def header():
        return "code,name,length_mm,width_mm,height_mm,quantity,weight_kg,rotate,top_load_kg\n"

    main = plan("EXPORT")
    csv = (
        header()
        + 'AA,"TEST large, carton",1000,900,500,6,100,true,250\nBB,=TEST compact box,600,600,400,8,25,true,50\nCC,TEST oversized box,3500,100,100,1,1,false,0\n'
    )
    before = detail(main)
    preview = act(main, "preview-csv", body={"csv": csv})
    assert (
        len(preview) == 3
        and detail(main)["plan"]["version"] == before["plan"]["version"]
    )
    act(main, "import-csv", body={"csv": csv})
    imported = detail(main)
    assert len(imported["cargo"]) == 3
    planner.call(
        "/plans/" + str(main["id"]) + "/import-csv",
        "POST",
        {
            "version": imported["plan"]["version"],
            "csv": header() + "ZZ,TEST,1.1,1,1,1,1,false,0\n",
        },
        400,
        "INVALID_INPUT",
    )
    assert detail(main)["cargo"] == imported["cargo"]
    original_version = detail(main)["plan"]["version"]
    revision = act(main, "calculate")
    assert revision["layout"]["placements"] and any(
        b["code"] == "CC" for b in revision["layout"]["unplaced"]
    )
    assert (
        len(revision["layout"]["placements"]) + len(revision["layout"]["unplaced"])
        == 15
    )
    planner.call(
        "/plans/" + str(main["id"]) + "/calculate",
        "POST",
        {"version": original_version},
        409,
        "VERSION_CONFLICT",
    )
    pos = [
        {k: box[k] for k in ["uid", "x", "y", "z", "rotated"]}
        for box in revision["layout"]["placements"]
    ]
    bad = [dict(p) for p in pos]
    bad[0]["x"] = 20000
    planner.call(
        "/plans/" + str(main["id"]) + "/manual",
        "POST",
        {"version": detail(main)["plan"]["version"], "placements": bad},
        400,
        "OUT_OF_BOUNDS",
    )
    assert len(detail(main)["revisions"]) == 1
    revision = act(main, "manual", body={"placements": pos})
    assert len(detail(main)["revisions"]) == 2
    clients["outside"].call(
        "/plans/" + str(main["id"]), status=403, code="OUT_OF_SCOPE"
    )
    clients["reader"].call(
        "/plans/" + str(main["id"]) + "/calculate",
        "POST",
        {"version": detail(main)["plan"]["version"]},
        403,
        "FORBIDDEN",
    )
    planner.call(
        "/plans/" + str(main["id"]) + "/submit",
        "POST",
        {"version": detail(main)["plan"]["version"], "note": "TEST not acknowledged"},
        409,
        "UNPLACED_ACK_REQUIRED",
    )
    act(
        main,
        "submit",
        body={
            "note": "TEST explicit oversized cargo excluded",
            "acknowledgeUnplaced": True,
        },
    )
    act(main, "return", reviewer, {"note": "TEST independent revision requested"})
    act(main, "calculate")
    act(
        main,
        "submit",
        body={
            "note": "TEST reviewed current excluded list",
            "acknowledgeUnplaced": True,
        },
    )
    act(main, "approve", reviewer, {"note": "TEST independent dimensional review"})
    planner.call(
        "/plans/" + str(main["id"]) + "/calculate",
        "POST",
        {"version": detail(main)["plan"]["version"]},
        409,
        "STATE_INVALID",
    )
    approved_rev = detail(main)["active"]["id"]
    csv_export = planner.call(
        f'/plans/{main["id"]}/revisions/{approved_rev}/export.csv', raw=True
    )
    assert "'=TEST compact box" in csv_export and "UNPLACED" in csv_export
    svg_export = planner.call(
        f'/plans/{main["id"]}/revisions/{approved_rev}/export.svg', raw=True
    )
    assert "<svg" in svg_export and "<script" not in svg_export
    clients["outside"].call(
        f'/plans/{main["id"]}/revisions/{approved_rev}/export.json',
        status=403,
        code="OUT_OF_SCOPE",
    )
    historical = planner.call(f'/plans/{main["id"]}/revisions/{approved_rev}')
    act(main, "reopen", body={"note": "TEST retain old approval and revise"})
    assert (
        detail(main)["active"] is None
        and planner.call(f'/plans/{main["id"]}/revisions/{approved_rev}') == historical
    )
    act(main, "calculate")
    # Leave a separate pending snapshot for actual reviewer page inspection.
    pending = plan("REVIEW", assigned_id)
    act(
        pending,
        "import-csv",
        body={"csv": header() + "RR,TEST review carton,1000,900,600,4,20,true,50\n"},
    )
    act(pending, "calculate")
    act(pending, "submit", body={"note": "TEST assigned review ready"})
    assert [p["id"] for p in clients["assigned"].call("/plans")] == [pending["id"]]
    reviewer.call(
        "/plans/" + str(pending["id"]) + "/approve",
        "POST",
        {
            "version": detail(pending)["plan"]["version"],
            "note": "TEST wrong assigned reviewer",
        },
        409,
        "REVIEWER_ONLY",
    )
    # Two independent authenticated sessions submit the same revision version.
    shared_version = detail(main)["plan"]["version"]
    planner_username = next(u for u, meta in users.items() if meta["id"] == planner_id)

    def race(_):
        c = Client(args.base)
        c.login(planner_username, password)
        return c.call(
            "/plans/" + str(main["id"]) + "/calculate",
            "POST",
            {"version": shared_version},
            status=None,
        )

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(race, range(2)))
    assert sorted(r[0] for r in results) == [200, 409]
    assert next(r[1] for r in results if r[0] == 409)["code"] == "VERSION_CONFLICT"
    cancelled = plan("CANCELLED")
    act(cancelled, "cancel", body={"note": "TEST explicit cancellation"})
    planner.call(
        "/plans/" + str(cancelled["id"]) + "/calculate",
        "POST",
        {"version": detail(cancelled)["plan"]["version"]},
        409,
        "STATE_INVALID",
    )
    account = next(a for a in admin.call("/admin/users") if a["id"] == disabled_id)
    account["enabled"] = False
    account["password"] = ""
    admin.call("/admin/users/" + str(disabled_id), "PUT", account)
    clients["disabled"].call("/plans", status=401, code="UNAUTHENTICATED")
    users[account["username"]]["disabled"] = True
    assert "passwordHash" not in json.dumps(admin.call("/admin/users"))
    for name, meta in users.items():
        if not meta.get("disabled"):
            label = next(
                label
                for label, c in clients.items()
                if "test-" + label + "-" + suffix == name
            )
            meta["visiblePlanIds"] = [p["id"] for p in clients[label].call("/plans")]
    state = {
        "product": "PackPlan",
        "adminUsername": env.get("ADMIN_USERNAME", "admin"),
        "adminPassword": env["ADMIN_PASSWORD"],
        "users": users,
        "guiPlanId": main["id"],
        "pendingPlanId": pending["id"],
        "snapshot": snapshot(admin),
        "concurrency": [r[0] for r in results],
    }
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(state, stream, ensure_ascii=False, indent=2)
    print(
        json.dumps(
            {
                "mode": "create",
                "httpChecks": Client.count,
                "businessPaths": 6,
                "roles": 6,
                "concurrentVersionRace": [200, 409],
                "snapshotWrittenPrivate": True,
            }
        )
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--env-file")
    parser.add_argument("--state-output")
    parser.add_argument("--state-input")
    parser.add_argument("--create-test-data", action="store_true")
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.base)
    if (
        url.hostname not in ["localhost", "127.0.0.1", "::1"]
        or url.username
        or url.password
        or url.query
        or url.fragment
        or url.path not in ["", "/"]
    ):
        parser.error(
            "Only an explicitly designated isolated localhost instance is supported"
        )
    if args.state_input:
        verify(args)
    elif args.env_file and args.state_output and args.create_test_data:
        create(args)
    else:
        parser.error(
            "Use --state-input or --env-file/--state-output/--create-test-data"
        )


if __name__ == "__main__":
    main()
