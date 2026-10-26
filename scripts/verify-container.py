"""Build/run only disposable synthetic containers; never connect to a host database."""

import argparse
import http.cookies
import json
import os
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POSTGRES = "postgres:18-bookworm@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc"


def docker(*args, env=None):
    result = subprocess.run(["docker", *args], cwd=ROOT, env=env, capture_output=True, text=True)
    if result.returncode:
        # Docker output can contain runtime configuration; do not echo it.
        raise RuntimeError(f"Docker {args[0]} failed (exit {result.returncode})")
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", default="container-evidence.json")
    args = parser.parse_args()
    suffix = uuid.uuid4().hex[:12]
    image, network, database, app = (f"planner-{kind}-{suffix}" for kind in ("image", "net", "db", "app"))
    env = os.environ.copy()
    env.update(POSTGRES_PASSWORD=secrets.token_urlsafe(32), POSTGRES_USER="synthetic",
               POSTGRES_DB="synthetic", DATABASE_USERNAME="synthetic",
               FINANCIALS_API_USERNAME="synthetic-operator", FINANCIALS_API_PASSWORD=secrets.token_urlsafe(32))
    env["DATABASE_PASSWORD"] = env["POSTGRES_PASSWORD"]
    checks = []
    try:
        docker("build", "--tag", image, ".")
        image_id = docker("image", "inspect", "--format", "{{.Id}}", image)
        assert docker("image", "inspect", "--format", "{{.Config.User}}", image) == "10001:10001"
        checks.append("non-root-image")
        docker("network", "create", "--internal", network)
        docker("run", "-d", "--name", database, "--network", network, "--network-alias", "database",
               "--env", "POSTGRES_PASSWORD", "--env", "POSTGRES_USER", "--env", "POSTGRES_DB", POSTGRES, env=env)
        for attempt in range(60):
            try:
                docker("exec", database, "pg_isready", "-U", "synthetic", "-d", "synthetic")
                break
            except RuntimeError:
                if attempt == 59:
                    raise
                time.sleep(1)
        docker("run", "-d", "--name", app, "--network", network,
               "--publish", "127.0.0.1::8080", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--memory", "768m", "--cpus", "2",
               "--env", "DATABASE_URL=jdbc:postgresql://database:5432/synthetic",
               "--env", "DATABASE_USERNAME", "--env", "DATABASE_PASSWORD",
               "--env", "FINANCIALS_API_USERNAME", "--env", "FINANCIALS_API_PASSWORD", image, env=env)
        port = json.loads(docker("inspect", app))[0]["NetworkSettings"]["Ports"]["8080/tcp"][0]["HostPort"]
        origin = f"http://127.0.0.1:{port}"
        cookies = {}

        def request(path, method="GET", data=None, headers=None):
            merged = {"Cookie": "; ".join(f"{key}={value}" for key, value in cookies.items())}
            merged.update(headers or {})
            if data is not None:
                merged["Content-Type"] = "application/json"
                data = data if isinstance(data, bytes) else json.dumps(data).encode()
            req = urllib.request.Request(origin + path, method=method, data=data, headers=merged)
            try:
                response = urllib.request.urlopen(req, timeout=5)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                for value in response.headers.get_all("Set-Cookie", []):
                    parsed = http.cookies.SimpleCookie(value)
                    cookies.update({key: item.value for key, item in parsed.items()})
                return response.status, response.read(), response.headers

        for attempt in range(90):
            try:
                status, body, _ = request("/actuator/health")
                if status == 200 and json.loads(body)["status"] == "UP":
                    break
            except (OSError, ValueError):
                pass
            if attempt == 89:
                raise RuntimeError("Container health did not become UP")
            time.sleep(1)
        checks.append("health-with-isolated-postgres")
        assert docker("exec", app, "id", "-u") == "10001"
        assert docker("inspect", "--format", "{{.HostConfig.ReadonlyRootfs}}", app) == "true"
        checks.append("non-root-read-only-runtime")
        status, index, _ = request("/")
        assert status == 200 and b'<div id="root"' in index
        for path in ("/workspace", "/workspace/planning"):
            status, body, _ = request(path)
            assert status == 200 and body == index
        assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', index.decode())
        assert assets
        for asset in assets:
            status, body, _ = request(asset)
            assert status == 200 and body and body != index
        for path in ("/assets/missing.js", "/api/v1/missing", "/actuator/missing"):
            status, body, _ = request(path)
            assert status in (401, 403, 404) and body != index
        checks.append("single-origin-assets-and-bounded-fallback")
        assert request("/api/v1/financials")[0] == 401
        assert request("/api/v1/auth/signup", "POST", {})[0] == 403
        status, body, headers = request("/api/v1/auth/csrf", headers={
            "Forwarded": "for=203.0.113.1;proto=https;host=attacker.invalid",
            "X-Forwarded-Host": "attacker.invalid", "X-Forwarded-Proto": "https"})
        assert status == 200 and "attacker.invalid" not in str(headers)
        csrf = json.loads(body)
        cookie_headers = headers.get_all("Set-Cookie", [])
        assert any("Secure" in value and "HttpOnly" in value for value in cookie_headers)
        proof = {csrf["headerName"]: csrf["token"]}
        status, body, headers = request("/api/v1/auth/signup", "POST", {
            "email": "container@example.invalid", "password": secrets.token_urlsafe(20), "displayName": "Synthetic Container"}, proof)
        assert status == 201
        account = json.loads(body)
        assert any("financials_session=" in value and "Secure" in value and "HttpOnly" in value
                   and "SameSite=Strict" in value for value in headers.get_all("Set-Cookie", []))
        assert request("/api/v1/auth/session")[0] == 200
        workspace = {"X-Workspace-ID": str(account["workspaces"][0]["id"])}
        assert request("/api/v1/financials", headers=workspace)[0] == 200
        assert request("/api/v1/financials", "PUT", {}, workspace)[0] == 403
        assert request("/api/v1/auth/signin", "POST", b"x" * 1048577)[0] == 413
        assert request("/api/v1/auth/csrf", headers={"Origin": "https://attacker.invalid"})[0] == 403
        checks.extend(["authentication-and-workspace-access", "csrf-and-cors", "secure-cookie-flags",
                       "untrusted-forwarding-headers", "request-body-limit"])
        Path(args.evidence).write_text(json.dumps({"imageId": image_id, "postgresImage": POSTGRES,
            "sourceSha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "checks": checks, "state": "passed", "limits": [
                "Loopback HTTP fixture sends Secure cookies explicitly to test server contracts; not a TLS deployment.",
                "Registration policy is unchanged; no public exposure or Azure readiness is claimed."]}, indent=2) + "\n")
        print("PASS: " + ", ".join(checks))
    finally:
        for name in (app, database):
            subprocess.run(["docker", "rm", "--force", "--volumes", name], capture_output=True)
        subprocess.run(["docker", "network", "rm", network], capture_output=True)
        subprocess.run(["docker", "image", "rm", image], capture_output=True)


if __name__ == "__main__":
    main()
