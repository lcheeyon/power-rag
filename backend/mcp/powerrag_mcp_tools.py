#!/usr/bin/env python3
"""
Power RAG MCP stdio server: time (free), weather via Open-Meteo (no API key),
Jira Cloud REST, GitHub REST (code search / file content), Google Cloud Logging.

Env:
  JIRA_CLOUD_BASE_URL  default https://powerrag.atlassian.net
  JIRA_CLOUD_EMAIL     Atlassian account email
  JIRA_CLOUD_API_TOKEN API token from https://id.atlassian.com/manage-profile/security/api-tokens

  GITHUB_TOKEN         optional PAT — higher rate limits; private repos
  GITHUB_API_URL       default https://api.github.com
  GITHUB_DEFAULT_OWNER default repo owner for tools (e.g. lcheeyon for github.com/lcheeyon/power-rag)
  GITHUB_DEFAULT_REPO  default repo name (e.g. power-rag)

  GCP_PROJECT_ID       default project for Cloud Logging (optional if ADC has a project)
  GOOGLE_APPLICATION_CREDENTIALS  path to service account JSON with logging.logEntries.list (or use gcloud ADC)
"""
from __future__ import annotations

import base64
import json
import os
import re
from datetime import datetime
from html.parser import HTMLParser
from typing import Any
from urllib.parse import quote, urlparse, urlunparse
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import httpx
from mcp.server.fastmcp import FastMCP

DEFAULT_JIRA_BASE = "https://powerrag.atlassian.net"
DEFAULT_JQL = "project = KAN ORDER BY created DESC"
GITHUB_API_DEFAULT = "https://api.github.com"


def _github_default_owner() -> str:
    return os.environ.get("GITHUB_DEFAULT_OWNER", "").strip()


def _github_default_repo() -> str:
    return os.environ.get("GITHUB_DEFAULT_REPO", "").strip()
MAX_FILE_CHARS = 80_000
MAX_FETCH_CHARS = 120_000
LOGGING_SCOPE = "https://www.googleapis.com/auth/logging.read"

mcp = FastMCP(
    "powerrag-tools",
    instructions=(
        "fetch_url — use for reading web pages; response is always JSON (ok, status_code, text, error). "
        "get_current_time, get_weather (no key); "
        "jira_search_issues / jira_get_issue (Jira Cloud env); "
        "github_search_code / github_get_repository_content (optional GITHUB_TOKEN); "
        "gcp_logging_query (GCP ADC + logging.read, GCP_PROJECT_ID)."
    ),
)


class _HtmlTextExtractor(HTMLParser):
    """Strip tags; drop script/style bodies."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._skip_depth = 0
        self._chunks: list[str] = []

    def handle_starttag(self, tag: str, attrs: Any) -> None:
        if tag in ("script", "style", "noscript", "template"):
            self._skip_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style", "noscript", "template") and self._skip_depth > 0:
            self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._skip_depth == 0:
            t = data.strip()
            if t:
                self._chunks.append(t)


def _encode_url_path_special_chars(url: str) -> str:
    """Encode reserved/special characters in the path (e.g. parentheses) for picky servers."""
    p = urlparse(url)
    if not p.scheme or not p.netloc:
        return url
    new_path = quote(p.path, safe="/-._~")
    return urlunparse((p.scheme, p.netloc.lower(), new_path, p.params, p.query, p.fragment))


def _body_to_text(content: bytes, content_type: str, max_chars: int) -> str:
    ct = (content_type or "").split(";")[0].strip().lower()
    text = content.decode("utf-8", errors="replace")
    if "json" in ct:
        try:
            obj = json.loads(text)
            return json.dumps(obj, ensure_ascii=False, indent=2)[:max_chars]
        except json.JSONDecodeError:
            return text[:max_chars]
    low = text.lstrip().lower()
    if "html" in ct or low.startswith("<!doctype html") or low.startswith("<html"):
        try:
            p = _HtmlTextExtractor()
            p.feed(text)
            p.close()
            out = "\n".join(p._chunks)
        except Exception:
            out = text
        return out[:max_chars]
    return text[:max_chars]


def _fetch_url_json_payload(url: str, max_chars: int) -> dict[str, Any]:
    raw = (url or "").strip()
    if not raw:
        return {"ok": False, "url": "", "error": "url is empty"}
    parsed = urlparse(raw)
    if parsed.scheme not in ("http", "https"):
        return {
            "ok": False,
            "url": raw,
            "error": f"unsupported scheme: {parsed.scheme!r} (only http/https)",
        }
    n = max(1024, min(int(max_chars), 500_000))
    headers_bot = {
        "User-Agent": "powerrag-mcp-fetch/1.0 (compatible; research assistant)",
        "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,text/plain;q=0.8,*/*;q=0.5",
    }
    headers_browser = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        ),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-SG,en;q=0.9",
    }
    try:
        with httpx.Client(follow_redirects=True, timeout=60.0) as client:
            r = client.get(raw, headers=headers_bot)
            final_url = str(r.url)
            ct = r.headers.get("content-type", "")
            if r.status_code == 404 and raw != _encode_url_path_special_chars(raw):
                alt = _encode_url_path_special_chars(raw)
                r2 = client.get(alt, headers=headers_bot)
                if r2.status_code != 404:
                    r, raw = r2, alt
                    final_url = str(r.url)
                    ct = r.headers.get("content-type", "")
            # Some sites return 4xx to non-browser clients; retry once with a browser UA on the final URL.
            if r.status_code >= 400:
                rb = client.get(str(r.url), headers=headers_browser)
                if rb.status_code < 400:
                    r = rb
                    final_url = str(r.url)
                    ct = r.headers.get("content-type", "")
            if r.status_code >= 400:
                return {
                    "ok": False,
                    "url": final_url,
                    "status_code": r.status_code,
                    "content_type": ct,
                    "error": f"HTTP {r.status_code}",
                    "hint": "Page may have moved, require auth, or block bots. Try an updated URL from the site, "
                    "or encode special characters in the path (e.g. ( ) as %28 %29).",
                    "body_preview": (r.text or "")[:800],
                }
            body = r.content
            text = _body_to_text(body, ct, n)
            return {
                "ok": True,
                "url": final_url,
                "status_code": r.status_code,
                "content_type": ct,
                "text": text,
                "truncated": len(text) >= n,
            }
    except httpx.TimeoutException:
        return {"ok": False, "url": raw, "error": "request timed out"}
    except Exception as e:
        return {"ok": False, "url": raw, "error": str(e)}


@mcp.tool()
def fetch_url(url: str, max_chars: int = MAX_FETCH_CHARS) -> str:
    """Fetch an https (or http) URL for reading. Returns a single JSON object string with ok, url, status_code, content_type, and text (extracted/plain) on success, or error + hint on failure — always valid JSON for the LLM tool pipeline (avoids parse errors on 404/HTML error pages)."""
    payload = _fetch_url_json_payload(url, max_chars)
    return json.dumps(payload, ensure_ascii=False)


def _jira_config() -> tuple[str, str, str] | None:
    base = os.environ.get("JIRA_CLOUD_BASE_URL", DEFAULT_JIRA_BASE).rstrip("/")
    email = os.environ.get("JIRA_CLOUD_EMAIL", "").strip()
    token = os.environ.get("JIRA_CLOUD_API_TOKEN", "").strip()
    if not email or not token:
        return None
    return base, email, token


def _jira_headers(email: str, token: str) -> dict[str, str]:
    raw = f"{email}:{token}".encode()
    b64 = base64.b64encode(raw).decode("ascii")
    return {
        "Authorization": f"Basic {b64}",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }


def _adf_plain_text(node: Any) -> str:
    """Best-effort extract text from Jira Document Format (ADF)."""
    if node is None:
        return ""
    if isinstance(node, str):
        return node
    if isinstance(node, dict):
        if node.get("type") == "text" and "text" in node:
            return str(node["text"])
        parts: list[str] = []
        for c in node.get("content") or []:
            t = _adf_plain_text(c)
            if t:
                parts.append(t)
        return " ".join(parts)
    if isinstance(node, list):
        return " ".join(_adf_plain_text(x) for x in node if x)
    return ""


@mcp.tool()
def get_current_time(timezone_name: str = "UTC") -> str:
    """Return the current date and time in an IANA timezone (e.g. Asia/Singapore, Europe/London).
    When the user asks for local time without naming a city/zone, use the browser timezone from the
    chat prompt if present; otherwise UTC or a zone the user explicitly requested."""
    tz_name = (timezone_name or "UTC").strip() or "UTC"
    try:
        tz = ZoneInfo(tz_name)
    except ZoneInfoNotFoundError:
        return json.dumps(
            {
                "error": f"Unknown timezone: {tz_name!r}",
                "hint": "Use an IANA name like Asia/Tokyo or America/New_York.",
            }
        )
    now = datetime.now(tz)
    return json.dumps(
        {
            "timezone": tz_name,
            "iso": now.isoformat(),
            "display": now.strftime("%Y-%m-%d %H:%M:%S %Z (UTC%z)"),
        }
    )


def _geocode(name: str) -> tuple[float, float, str] | None:
    q = name.strip()
    if not q:
        return None
    m = re.match(
        r"^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$", q
    )
    if m:
        lat, lon = float(m.group(1)), float(m.group(2))
        return lat, lon, f"{lat},{lon}"
    r = httpx.get(
        "https://geocoding-api.open-meteo.com/v1/search",
        params={"name": q, "count": 1, "language": "en", "format": "json"},
        timeout=30.0,
    )
    r.raise_for_status()
    data = r.json()
    results = data.get("results") or []
    if not results:
        return None
    g = results[0]
    lat, lon = g["latitude"], g["longitude"]
    label = g.get("name", q)
    if g.get("country"):
        label = f"{label}, {g['country']}"
    return lat, lon, label


@mcp.tool()
def get_weather(location: str, units: str = "metric") -> str:
    """Current weather for a city/region or coordinates as 'latitude,longitude'. Data from Open-Meteo (free, no API key). units: 'metric' or 'imperial'."""
    loc = (location or "").strip()
    if not loc:
        return json.dumps({"error": "location is required"})
    unit = (units or "metric").lower().strip()
    temp_u = "fahrenheit" if unit == "imperial" else "celsius"
    wind_u = "mph" if unit == "imperial" else "kmh"
    try:
        geo = _geocode(loc)
        if not geo:
            return json.dumps({"error": f"Could not geocode: {loc!r}"})
        lat, lon, label = geo
        wr = httpx.get(
            "https://api.open-meteo.com/v1/forecast",
            params={
                "latitude": lat,
                "longitude": lon,
                "current": "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
                "temperature_unit": temp_u,
                "wind_speed_unit": wind_u,
            },
            timeout=30.0,
        )
        wr.raise_for_status()
        w = wr.json().get("current") or {}
        return json.dumps(
            {
                "location": label,
                "latitude": lat,
                "longitude": lon,
                "temperature": w.get("temperature_2m"),
                "temperature_unit": temp_u,
                "relative_humidity_percent": w.get("relative_humidity_2m"),
                "weather_code": w.get("weather_code"),
                "wind_speed": w.get("wind_speed_10m"),
                "wind_speed_unit": wind_u,
                "source": "Open-Meteo",
            }
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


@mcp.tool()
def jira_search_issues(jql: str = "", max_results: int = 15) -> str:
    """Search Jira Cloud with JQL. Pass empty string to use the default query (project KAN, newest first). Requires JIRA_CLOUD_EMAIL and JIRA_CLOUD_API_TOKEN. The JSON includes presentation_hint: use it when replying so each issue shows key, summary, and status on separate lines."""
    cfg = _jira_config()
    if not cfg:
        return json.dumps(
            {
                "error": "Jira is not configured",
                "hint": "Set JIRA_CLOUD_EMAIL and JIRA_CLOUD_API_TOKEN (optional JIRA_CLOUD_BASE_URL).",
            }
        )
    base, email, token = cfg
    jql_use = (jql or "").strip() or DEFAULT_JQL
    n = max(1, min(int(max_results), 50))
    fields_csv = ",".join(
        [
            "summary",
            "status",
            "assignee",
            "created",
            "updated",
            "issuetype",
            "priority",
            "description",
        ]
    )
    try:
        # POST /rest/api/3/search was removed (410); use enhanced JQL search (GET).
        r = httpx.get(
            f"{base}/rest/api/3/search/jql",
            headers=_jira_headers(email, token),
            params={"jql": jql_use, "maxResults": n, "fields": fields_csv},
            timeout=60.0,
        )
        if r.status_code >= 400:
            return json.dumps(
                {"error": f"HTTP {r.status_code}", "body": r.text[:2000]}
            )
        data = r.json()
        issues = data.get("issues") or []
        out: list[dict[str, Any]] = []
        for issue in issues:
            f = issue.get("fields") or {}
            assignee = f.get("assignee") or {}
            out.append(
                {
                    "key": issue.get("key"),
                    "summary": f.get("summary"),
                    "status": (f.get("status") or {}).get("name"),
                    "type": (f.get("issuetype") or {}).get("name"),
                    "priority": (f.get("priority") or {}).get("name"),
                    "assignee": assignee.get("displayName"),
                    "created": f.get("created"),
                    "updated": f.get("updated"),
                    "description_preview": (_adf_plain_text(f.get("description")) or "")[
                        :500
                    ],
                }
            )
        # One line per issue so the model does not concatenate keys; include summary + status.
        presentation_lines = [
            f"- **{it['key']}** — {it.get('summary') or '(no summary)'} "
            f"(_{it.get('status') or '?'}_)"
            for it in out
        ]
        presentation_hint = (
            "When listing these to the user, copy one bullet per issue (KEY, summary, status), "
            "each on its own line with a blank line between issues if helpful. "
            "Do not merge keys into one run (e.g. avoid KAN-5KAN-4).\n\n"
            + "\n\n".join(presentation_lines)
            if presentation_lines
            else ""
        )
        return json.dumps(
            {
                "jql": jql_use,
                "returned": len(out),
                "is_last": data.get("isLast"),
                "issues": out,
                "presentation_hint": presentation_hint,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


@mcp.tool()
def jira_get_issue(issue_key: str) -> str:
    """Load a single Jira issue by key (e.g. KAN-123). Requires JIRA_CLOUD_* credentials."""
    cfg = _jira_config()
    if not cfg:
        return json.dumps(
            {
                "error": "Jira is not configured",
                "hint": "Set JIRA_CLOUD_EMAIL and JIRA_CLOUD_API_TOKEN.",
            }
        )
    base, email, token = cfg
    key = (issue_key or "").strip().upper()
    if not re.match(r"^[A-Z][A-Z0-9]+-\d+$", key):
        return json.dumps({"error": f"Invalid issue key format: {issue_key!r}"})
    try:
        r = httpx.get(
            f"{base}/rest/api/3/issue/{key}",
            headers=_jira_headers(email, token),
            params={"fields": "summary,status,assignee,created,updated,description,comment,issuetype,priority"},
            timeout=60.0,
        )
        if r.status_code >= 400:
            return json.dumps(
                {"error": f"HTTP {r.status_code}", "body": r.text[:2000]}
            )
        issue = r.json()
        f = issue.get("fields") or {}
        comments = f.get("comment") or {}
        comment_list = comments.get("comments") or []
        recent = []
        for c in comment_list[-5:]:
            recent.append(
                {
                    "author": ((c.get("author") or {}).get("displayName")),
                    "created": c.get("created"),
                    "body_preview": _adf_plain_text(c.get("body"))[:400],
                }
            )
        return json.dumps(
            {
                "key": issue.get("key"),
                "summary": f.get("summary"),
                "status": (f.get("status") or {}).get("name"),
                "type": (f.get("issuetype") or {}).get("name"),
                "priority": (f.get("priority") or {}).get("name"),
                "assignee": ((f.get("assignee") or {}).get("displayName")),
                "created": f.get("created"),
                "updated": f.get("updated"),
                "description": _adf_plain_text(f.get("description")),
                "recent_comments": recent,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


# ── GitHub (code search + repository content) ───────────────────────────────


def _github_api_url() -> str:
    return os.environ.get("GITHUB_API_URL", GITHUB_API_DEFAULT).rstrip("/")


def _github_headers() -> dict[str, str]:
    h = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "powerrag-mcp/1.0",
    }
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h


@mcp.tool()
def github_search_code(
    query: str,
    per_page: int = 15,
) -> str:
    """Search code across GitHub (public repos, or private repos your token can access). Use GitHub code search syntax in `query`. If the query has no `repo:` qualifier, env GITHUB_DEFAULT_OWNER and GITHUB_DEFAULT_REPO (default upstream: lcheeyon/power-rag) are appended as `repo:owner/repo`. Optional: GITHUB_TOKEN."""
    q = (query or "").strip()
    if not q:
        return json.dumps({"error": "query is required", "hint": "Example: RagService language:Java or filename:pom.xml"})
    if "repo:" not in q.lower():
        o, r = _github_default_owner(), _github_default_repo()
        if o and r:
            q = f"{q} repo:{o}/{r}"
    n = max(1, min(int(per_page), 30))
    try:
        r = httpx.get(
            f"{_github_api_url()}/search/code",
            headers=_github_headers(),
            params={"q": q, "per_page": n},
            timeout=60.0,
        )
        if r.status_code == 401:
            return json.dumps(
                {
                    "error": "HTTP 401",
                    "hint": "Set GITHUB_TOKEN for authenticated search and higher rate limits.",
                }
            )
        if r.status_code == 403:
            return json.dumps(
                {
                    "error": "HTTP 403",
                    "body": r.text[:1500],
                    "hint": "Private repos need GITHUB_TOKEN; code search rate limits apply.",
                }
            )
        if r.status_code >= 400:
            return json.dumps({"error": f"HTTP {r.status_code}", "body": r.text[:2000]})
        data = r.json()
        items = data.get("items") or []
        out: list[dict[str, Any]] = []
        for it in items:
            repo = it.get("repository") or {}
            out.append(
                {
                    "path": it.get("path"),
                    "repository": repo.get("full_name"),
                    "html_url": it.get("html_url"),
                    "text_matches_preview": [
                        (m.get("fragment") or "")[:400]
                        for m in (it.get("text_matches") or [])[:2]
                    ],
                }
            )
        return json.dumps(
            {
                "query": q,
                "total_count": data.get("total_count"),
                "returned": len(out),
                "items": out,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


@mcp.tool()
def github_get_repository_content(
    owner: str,
    repo: str,
    path: str,
    ref: str = "",
) -> str:
    """Read a file or list a directory from a GitHub repository via the Contents API. owner/repo default from GITHUB_DEFAULT_OWNER / GITHUB_DEFAULT_REPO (upstream power-rag: lcheeyon, power-rag) when left empty. path like `README.md` or `backend/pom.xml`. Optional ref = branch, tag, or SHA."""
    o = (owner or "").strip() or _github_default_owner()
    rp = (repo or "").strip() or _github_default_repo()
    p = (path or "").strip().lstrip("/")
    if not o or not rp:
        return json.dumps(
            {
                "error": "owner and repo are required (or set GITHUB_DEFAULT_OWNER and GITHUB_DEFAULT_REPO)",
                "hint": "Upstream: https://github.com/lcheeyon/power-rag",
            }
        )
    url = f"{_github_api_url()}/repos/{o}/{rp}/contents/{p}"
    params: dict[str, str] = {}
    ref_use = (ref or "").strip()
    if ref_use:
        params["ref"] = ref_use
    try:
        r = httpx.get(url, headers=_github_headers(), params=params or None, timeout=60.0)
        if r.status_code == 404:
            return json.dumps({"error": "HTTP 404", "hint": "Check owner, repo, path, and ref."})
        if r.status_code >= 400:
            return json.dumps({"error": f"HTTP {r.status_code}", "body": r.text[:2000]})
        data = r.json()
        if isinstance(data, list):
            entries = []
            for e in data[:200]:
                entries.append(
                    {
                        "name": e.get("name"),
                        "path": e.get("path"),
                        "type": e.get("type"),
                        "size": e.get("size"),
                        "sha": e.get("sha"),
                    }
                )
            return json.dumps(
                {
                    "owner": o,
                    "repo": rp,
                    "path": p,
                    "type": "directory",
                    "entry_count": len(entries),
                    "entries": entries,
                },
                ensure_ascii=False,
            )
        if data.get("type") != "file":
            return json.dumps({"error": "unexpected_content_type", "raw_keys": list(data.keys())})
        raw_b64 = data.get("content") or ""
        # GitHub uses base64 with newlines
        cleaned = "".join(raw_b64.split())
        try:
            text = base64.b64decode(cleaned).decode("utf-8", errors="replace")
        except Exception:
            text = ""
        truncated = len(text) > MAX_FILE_CHARS
        if truncated:
            text = text[:MAX_FILE_CHARS] + "\n\n… [truncated]"
        return json.dumps(
            {
                "owner": o,
                "repo": rp,
                "path": data.get("path", p),
                "sha": data.get("sha"),
                "size": data.get("size"),
                "encoding": data.get("encoding"),
                "truncated": truncated,
                "content": text,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


# ── Google Cloud Logging ─────────────────────────────────────────────────────


def _gcp_logging_project_id(explicit: str) -> str | None:
    pid = (explicit or "").strip()
    if pid:
        return pid
    env = os.environ.get("GCP_PROJECT_ID", "").strip()
    return env or None


def _gcp_logging_token_and_project(project_hint: str) -> tuple[str, str | None] | None:
    try:
        from google.auth import default as ga_default
        from google.auth.transport.requests import Request as GARequest

        credentials, project = ga_default(scopes=[LOGGING_SCOPE])
        credentials.refresh(GARequest())
        token = credentials.token
        if not token:
            return None
        resolved = _gcp_logging_project_id(project_hint) or (project or None)
        return token, resolved
    except Exception:
        return None


@mcp.tool()
def gcp_logging_query(
    log_filter: str,
    max_entries: int = 25,
    project_id: str = "",
) -> str:
    """Query Google Cloud Logging with a filter expression (Logs Explorer syntax). Parameter log_filter examples: `resource.type="cloud_run_revision" AND severity>=ERROR`, `textPayload:"timeout"`. Requires ADC with logging.read and GCP_PROJECT_ID (or pass project_id)."""
    flt = (log_filter or "").strip()
    if not flt:
        return json.dumps({"error": "filter is required", "hint": 'Example: severity>=ERROR AND resource.type="k8s_container"'})
    n = max(1, min(int(max_entries), 100))
    auth = _gcp_logging_token_and_project(project_id)
    if not auth:
        return json.dumps(
            {
                "error": "Cloud Logging auth not available",
                "hint": "Set GOOGLE_APPLICATION_CREDENTIALS to a service account JSON with logging.logEntries.list, "
                "or run `gcloud auth application-default login`. Set GCP_PROJECT_ID.",
            }
        )
    token, proj = auth
    if not proj:
        return json.dumps(
            {
                "error": "No GCP project id",
                "hint": "Set GCP_PROJECT_ID or choose a project in gcloud application-default credentials.",
            }
        )
    body = {
        "resourceNames": [f"projects/{proj}"],
        "filter": flt,
        "orderBy": "timestamp desc",
        "pageSize": n,
    }
    try:
        r = httpx.post(
            "https://logging.googleapis.com/v2/entries:list",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            json=body,
            timeout=90.0,
        )
        if r.status_code >= 400:
            return json.dumps(
                {
                    "error": f"HTTP {r.status_code}",
                    "body": r.text[:2500],
                    "project": proj,
                }
            )
        data = r.json()
        entries_in = data.get("entries") or []
        entries_out: list[dict[str, Any]] = []
        for e in entries_in:
            entries_out.append(
                {
                    "timestamp": e.get("timestamp"),
                    "severity": e.get("severity"),
                    "log_name": e.get("logName"),
                    "resource": (e.get("resource") or {}).get("type"),
                    "text_preview": (e.get("textPayload") or "")[:800]
                    if e.get("textPayload")
                    else None,
                    "json_payload_preview": json.dumps(e.get("jsonPayload"), default=str)[:800]
                    if e.get("jsonPayload") is not None
                    else None,
                }
            )
        return json.dumps(
            {
                "project": proj,
                "filter": flt,
                "returned": len(entries_out),
                "entries": entries_out,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps({"error": str(e)})


if __name__ == "__main__":
    mcp.run(transport="stdio")
