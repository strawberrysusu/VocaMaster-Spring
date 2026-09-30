#!/usr/bin/env python3
"""Deploy one app image on the verified AWS host; never recreate data services."""
from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path("/home/ubuntu/vocamaster-aws")
EXPECTED_HOST = "ip-172-26-11-25"
APP_ALIAS = "vocamaster:aws-restored"
DOMAIN = "vocamaster-app.duckdns.org"
DC = ["sudo", "-n", "docker", "compose", "--project-directory", str(ROOT),
      "-f", str(ROOT / "docker-compose.aws.yml")]


class Deployment:
    def __init__(self, release: str, archive: Path, checksum: str):
        self.release, self.archive, self.checksum = release, archive, checksum
        self.log = None
        self.previous_image = None
        self.data_containers = {}
        self.changed = False
        self.result = {"release": release, "deployed": False, "rollback_attempted": False,
                       "rollback_healthy": False, "database_rollback": False,
                       "cleanup_pending": True, "state_recorded": False}

    def run(self, command: list[str], *, timeout: int = 120, log_output: bool = True) -> str:
        # Commands contain only service/image IDs and fixed paths, never secret values.
        process = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 timeout=timeout, check=False, text=True)
        self.log.write("$ " + " ".join(command) + "\n")
        if log_output:
            self.log.write(process.stdout)
        self.log.write(process.stderr)
        self.log.flush()
        if process.returncode:
            raise RuntimeError("command_failed")
        return process.stdout.strip()

    def docker(self, *args: str, **kwargs) -> str:
        return self.run(["sudo", "-n", "docker", *args], **kwargs)

    def app_container(self) -> str:
        container = self.run([*DC, "ps", "--all", "--quiet", "app"])
        if not re.fullmatch(r"[a-f0-9]{12,64}", container):
            raise RuntimeError("one_app_container_required")
        return container

    def wait_and_smoke(self, timeout: int = 240) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                container = self.app_container()
                state = self.docker("inspect", "--format",
                                    "{{.State.Status}}|{{.State.OOMKilled}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}",
                                    container)
                running, oom, health = state.split("|")
                if oom == "true" or running in {"dead", "exited"}:
                    raise RuntimeError("app_exited_or_oom")
                if running == "running" and health == "healthy":
                    try:
                        self.http_smoke()
                        return
                    except (RuntimeError, subprocess.TimeoutExpired, urllib.error.URLError, ValueError):
                        pass
            except (subprocess.TimeoutExpired, urllib.error.URLError, ValueError):
                pass
            time.sleep(5)
        raise RuntimeError("app_not_ready")

    @staticmethod
    def validate_public_page(body: bytes) -> None:
        payload = json.loads(body)
        if not isinstance(payload, dict) or not isinstance(payload.get("content"), list):
            raise ValueError("public_page_contract")
        if not isinstance(payload.get("totalElements"), int) or payload["totalElements"] < 0:
            raise ValueError("public_page_contract")

    def http_smoke(self) -> None:
        # Database-backed API checks and React entry point; public bodies are not printed.
        with urllib.request.urlopen("http://127.0.0.1:8080/public/decks?size=1", timeout=12) as response:
            if response.status != 200:
                raise ValueError("api_http_status")
            self.validate_public_page(response.read(1_048_577))
        with urllib.request.urlopen("http://127.0.0.1:8080/app/", timeout=12) as response:
            body = response.read(1_048_577)
            if response.status != 200 or b'id="root"' not in body:
                raise ValueError("frontend_http_status")
        # Resolve the domain to this server explicitly. No dependency on stale public DNS;
        # the certificate and TLS host name are still verified normally by curl.
        body = self.run(["curl", "--fail", "--silent", "--show-error", "--max-time", "15",
                         "--resolve", f"{DOMAIN}:443:127.0.0.1",
                         f"https://{DOMAIN}/public/decks?size=1"], log_output=False)
        self.validate_public_page(body.encode())

    def persist(self) -> None:
        self.result["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        temporary = ROOT / ".last-deploy.json.tmp"
        temporary.write_text(json.dumps(self.result, indent=2) + "\n", encoding="utf-8")
        os.chmod(temporary, 0o600)
        temporary.replace(ROOT / ".last-deploy.json")

    def record_result(self) -> bool:
        self.result["state_recorded"] = True
        try:
            self.persist()
            recorded = True
        except Exception as error:
            self.result["state_recorded"] = False
            self.result["state_error"] = type(error).__name__
            self.result["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
            recorded = False
            try:
                self.log.write("Deployment state record failed: " + type(error).__name__ + "\n")
                self.log.flush()
            except OSError:
                pass
        print(json.dumps(self.result, sort_keys=True))
        return recorded

    def finish_success(self) -> int:
        # Health and unchanged data-container checks have already committed this release.
        # Housekeeping and state-file errors must never change a healthy app back.
        try:
            self.archive.unlink()
            self.result["cleanup_pending"] = False
        except OSError as error:
            self.result["cleanup_pending"] = True
            try:
                self.log.write("Archive cleanup pending: " + type(error).__name__ + "\n")
                self.log.flush()
            except OSError:
                pass
        # State-record failures are visible as a failed CI step, with deployed=true and
        # state_recorded=false; the healthy current release remains running.
        return 0 if self.record_result() else 1

    def execute(self) -> int:
        if socket.gethostname() != EXPECTED_HOST or os.getuid() == 0:
            raise RuntimeError("wrong_host_or_user")
        if not ROOT.is_dir() or not (ROOT / ".env").is_file():
            raise RuntimeError("runtime_not_prepared")
        if (ROOT / ".env").stat().st_mode & 0o077:
            raise RuntimeError("env_permissions_not_private")
        incoming = (ROOT / "incoming").resolve()
        if not self.archive.resolve().is_relative_to(incoming) or self.archive.is_symlink():
            raise RuntimeError("archive_outside_incoming")
        if not re.fullmatch(r"[a-f0-9]{40}", self.release) or not re.fullmatch(r"[a-f0-9]{64}", self.checksum):
            raise RuntimeError("invalid_release_or_checksum")
        os.umask(0o077)
        logs = ROOT / "deploy-logs"
        logs.mkdir(mode=0o700, exist_ok=True)
        with (ROOT / ".deploy.lock").open("a") as lock, (logs / f"{self.release}.log").open("a", encoding="utf-8") as log:
            fcntl.flock(lock, fcntl.LOCK_EX)
            self.log = log
            try:
                if shutil.disk_usage(ROOT).free < 2 * 1024 ** 3:
                    raise RuntimeError("less_than_2gb_free_disk")
                checksum = hashlib.sha256()
                with self.archive.open("rb") as file:
                    for block in iter(lambda: file.read(1_048_576), b""):
                        checksum.update(block)
                if checksum.hexdigest() != self.checksum:
                    raise RuntimeError("archive_checksum_mismatch")
                self.run([*DC, "config", "--quiet"])
                old_container = self.app_container()
                self.data_containers = {service: self.run([*DC, "ps", "--quiet", service])
                                        for service in ("mysql", "redis")}
                if any(not re.fullmatch(r"[a-f0-9]{12,64}", item) for item in self.data_containers.values()):
                    raise RuntimeError("data_services_must_be_running")
                if self.docker("inspect", "--format", "{{.Config.Image}}", old_container) != APP_ALIAS:
                    raise RuntimeError("unexpected_runtime_image_alias")
                self.previous_image = self.docker("inspect", "--format", "{{.Image}}", old_container)
                if not re.fullmatch(r"sha256:[a-f0-9]{64}", self.previous_image):
                    raise RuntimeError("invalid_previous_image")
                self.docker("tag", self.previous_image, f"vocamaster:rollback-{self.previous_image[-12:]}")
                # Streaming prevents the compressed image from occupying the 2GB host RAM.
                producer = subprocess.Popen(["gzip", "-dc", str(self.archive)], stdout=subprocess.PIPE, stderr=log)
                process = subprocess.Popen(["sudo", "-n", "docker", "load"], stdin=producer.stdout,
                                           stdout=log, stderr=log)
                producer.stdout.close()
                try:
                    if process.wait(timeout=600) or producer.wait(timeout=30):
                        raise RuntimeError("image_load_failed")
                except BaseException:
                    for child in (process, producer):
                        if child.poll() is None:
                            child.kill()
                        child.wait()
                    raise
                image = f"vocamaster:{self.release}"
                details = self.docker("image", "inspect", "--format",
                                      '{{.Architecture}}|{{index .Config.Labels "org.opencontainers.image.revision"}}', image)
                if details != f"amd64|{self.release}":
                    raise RuntimeError("image_revision_or_architecture_mismatch")
                # Set before changing the alias or up: a failed command may have effects.
                self.changed = True
                self.docker("tag", image, APP_ALIAS)
                self.run([*DC, "up", "--detach", "--no-deps", "--no-build", "--force-recreate", "app"], timeout=180)
                self.wait_and_smoke()
                if any(self.run([*DC, "ps", "--quiet", service]) != before
                       for service, before in self.data_containers.items()):
                    raise RuntimeError("data_service_container_changed")
                self.result["deployed"] = True
                self.result["data_service_containers_unchanged"] = True
                self.result["previous_image"] = self.previous_image
            except Exception as error:
                # Detailed command output stays in the private server log, not CI output.
                # Recovery is only for image/startup/health failures before the release
                # commits. Housekeeping and result-record errors are outside this block.
                self.result["deployed"] = False
                self.result["error"] = type(error).__name__
                if self.changed and self.previous_image:
                    self.result["rollback_attempted"] = True
                    try:
                        self.docker("tag", self.previous_image, APP_ALIAS)
                        self.run([*DC, "up", "--detach", "--no-deps", "--no-build", "--force-recreate", "app"], timeout=180)
                        self.wait_and_smoke()
                        self.result["rollback_healthy"] = True
                    except Exception as rollback_error:
                        self.result["rollback_error"] = type(rollback_error).__name__
                self.record_result()
                return 1
            return self.finish_success()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", required=True)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--sha256", required=True)
    args = parser.parse_args()
    try:
        return Deployment(args.release, args.archive, args.sha256).execute()
    except Exception as error:
        # Guard failures occur before a private log exists. Do not include error strings.
        print(json.dumps({"deployed": False, "error": type(error).__name__}))
        return 1


if __name__ == "__main__":
    sys.exit(main())
