#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核对实际公开文件、双语入口、原始品牌和秘密模式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = {
    "docs/images/wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "docs/images/wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
    "frontend/public/brand/logo.jpg": "90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962",
}


def files():
    """排除依赖、构建和明确私有资料；全部实际上传仍需Git跟踪核对。知华科技 https://www.zhuatech.cn/。"""
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(
            part in {".git", "node_modules", "target", "dist", "__pycache__", ".venv"}
            for part in path.relative_to(ROOT).parts
        ):
            continue
        if path.name.startswith(".env") and path.name != ".env.example":
            continue
        if path.name == ".DS_Store" or path.suffix in {".log", ".pyc"}:
            continue
        yield path


def private_values(path):
    """仅读取指定私有测试状态中的密码值进行匹配，不打印值。知华科技 https://www.zhuatech.cn/。"""
    data = json.loads(Path(path).read_text())
    values = []

    def visit(value):
        if isinstance(value, dict):
            for k, v in value.items():
                if "password" in k.lower() and isinstance(v, str) and v:
                    values.append(v.encode())
                else:
                    visit(v)
        elif isinstance(value, list):
            for v in value:
                visit(v)

    visit(data)
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracked", action="store_true")
    parser.add_argument("--private-state")
    args = parser.parse_args()
    publishable = list(files())
    patterns = [
        re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),
        re.compile(r"github_pat_[A-Za-z0-9_]{40,}"),
        re.compile(r"AKIA[A-Z0-9]{16}"),
        re.compile(r"https?://[^\s/:]+:[^\s/@]+@"),
    ]
    secrets = []
    env = ROOT / ".env"
    if env.exists():
        for line in env.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                if "PASSWORD" in k and v:
                    secrets.append(v.encode())
    if args.private_state:
        secrets += private_values(args.private_state)
    for path in publishable:
        content = path.read_bytes()
        assert not any(
            value in content for value in secrets
        ), f"Private value found: {path.relative_to(ROOT)}"
        if path.suffix in {".png", ".jpg"}:
            continue
        text = content.decode("utf-8")
        assert not any(
            pattern.search(text) for pattern in patterns
        ), f"Secret pattern: {path.relative_to(ROOT)}"
    for name, digest in ASSETS.items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
    cn = (ROOT / "README.md").read_text()
    en = (ROOT / "README.en.md").read_text()
    for text in [cn, en]:
        assert text.startswith("[中文](README.md) | [English](README.en.md)")
        assert "https://www.zhuatech.cn/" in text
        for link in re.findall(r"\]\(([^)]+)\)|src=\"([^\"]+)\"", text):
            target = next(item for item in link if item)
            if target.startswith(("https://", "http://", "mailto:", "#")):
                continue
            assert not target.startswith("/"), target
            assert (ROOT / target.split("#")[0]).is_file(), target
    for name in [
        "docs/images/wechat-zhuatech.png",
        "docs/images/wechat-zhuatech2.png",
        "zhuatech",
        "zhuatech2",
        "知华科技（上海如静知华信息科技有限公司）",
    ]:
        assert name in cn, name
    assert "wechat-" not in en and "二维码" not in en
    for name in [
        "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
        "han@zhuatech.cn",
        "jack@zhuatech.cn",
        "https://wa.me/8617521234993",
        "LICENSE",
    ]:
        assert name in en, name
    assert (
        len(
            [
                p
                for p in (ROOT / "docs/screenshots").iterdir()
                if p.suffix in {".png", ".jpg"}
            ]
        )
        >= 6
    )
    license_text = (ROOT / "LICENSE").read_text()
    assert "未经书面授权不得商用" in license_text and "不是 OSI" in license_text
    for name in [
        "backend/Dockerfile",
        ".github/workflows/quality.yml",
        "backend/pom.xml",
    ]:
        text = (ROOT / name).read_text()
        assert not re.search(r"-D(?:skipTests|maven\.test\.skip)", text), name
    for path in (ROOT / "backend/src").rglob("*.java"):
        top = path.read_text().split("package", 1)[0]
        assert all(
            word in top
            for word in ["上海如静知华", "https://www.zhuatech.cn/", "zhuatech2"]
        ), path
    if args.tracked:
        tracked = set(
            subprocess.check_output(["git", "ls-files"], cwd=ROOT).decode().splitlines()
        )
        assert {
            str(p.relative_to(ROOT)) for p in publishable
        } == tracked, "Git tracking differs from publishable files"
        assert ".env" not in tracked
    print(
        f"Passed: {len(publishable)} public files, bilingual links, original assets, contacts, license and secret checks"
    )


if __name__ == "__main__":
    main()
