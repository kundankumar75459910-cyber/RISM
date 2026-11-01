"""Thin client for the RIMS Spring Boot API."""
from __future__ import annotations

import requests


class RimsApiError(Exception):
    def __init__(self, message: str, status: int = 502):
        super().__init__(message)
        self.status = status


class RimsClient:
    def __init__(self, base_url: str, token: str | None = None, session=None, timeout: float = 20):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.session = session or requests.Session()
        self.timeout = timeout

    @classmethod
    def login(cls, base_url: str, username: str, password: str, **kw) -> "RimsClient":
        client = cls(base_url, **kw)
        data = client._request("POST", "/auth/login", json={"username": username, "password": password})
        client.token = data["token"]
        return client

    def _request(self, method: str, path: str, **kw):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        try:
            res = self.session.request(method, f"{self.base_url}{path}", headers=headers, timeout=self.timeout, **kw)
        except requests.RequestException as exc:
            raise RimsApiError(f"Cannot reach the RIMS API at {self.base_url}: {exc}", 502) from exc
        if res.status_code >= 400:
            try:
                message = res.json().get("message") or res.text
            except ValueError:
                message = res.text
            raise RimsApiError(message or f"RIMS API returned {res.status_code}", res.status_code)
        return res.json()

    def get_all(self, path: str, params: dict | None = None) -> list[dict]:
        """Follow the API's pagination (page / size, max size 1000) and return every row."""
        rows: list[dict] = []
        page = 0
        while True:
            data = self._request("GET", path, params={**(params or {}), "page": page, "size": 1000})
            rows.extend(data["content"])
            page += 1
            if page >= data["totalPages"]:
                return rows
