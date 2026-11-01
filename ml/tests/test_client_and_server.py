import pytest

from rims_ml.client import RimsApiError, RimsClient
from rims_ml.server import create_app

PRODUCTS = [
    {"id": 1, "sku": "A-1", "name": "Widget", "categoryName": "Tools", "supplierId": 1, "quantity": 20, "reorderLevel": 10},
    {"id": 2, "sku": "B-1", "name": "Gadget", "categoryName": "Tools", "supplierId": None, "quantity": 0, "reorderLevel": 5},
]


def _sales(days=30):
    import datetime as dt
    out = []
    for n in range(days, 0, -1):
        day = (dt.date.today() - dt.timedelta(days=n)).isoformat() + "T12:00:00"
        out.append({"id": n, "saleDate": day, "items": [{"productId": 1, "quantity": 5}, {"productId": 2, "quantity": 3}]})
    return out


PURCHASES = [
    {"status": "RECEIVED", "supplierId": 1, "orderDate": "2026-09-01T10:00:00", "receivedDate": "2026-09-04T10:00:00", "items": []},
    {"status": "PENDING", "supplierId": 1, "orderDate": "2026-10-20T10:00:00", "receivedDate": None,
     "items": [{"productId": 1, "quantity": 7}]},
]


class FakeClient:
    seen_tokens = []

    def __init__(self, token):
        FakeClient.seen_tokens.append(token)

    def get_all(self, path, params=None):
        return {"/products": PRODUCTS, "/sales": _sales(), "/purchases": PURCHASES}[path]


@pytest.fixture
def client():
    FakeClient.seen_tokens.clear()
    return create_app(FakeClient).test_client()


AUTH = {"Authorization": "Bearer abcdefghijklmnop"}


def test_requires_a_bearer_token(client):
    assert client.get("/api/recommendations").status_code == 401
    assert client.get("/api/recommendations", headers={"Authorization": "Basic xyz"}).status_code == 401


def test_returns_recommendations_and_forwards_the_callers_token(client):
    res = client.get("/api/recommendations", headers=AUTH)
    assert res.status_code == 200
    body = res.get_json()
    assert FakeClient.seen_tokens == ["abcdefghijklmnop"]
    assert body["summary"]["totalProducts"] == 2
    by_id = {i["productId"]: i for i in body["items"]}
    assert by_id[2]["status"] == "STOCKOUT"                 # 3 units/day, none in stock
    assert by_id[1]["pendingQty"] == 7                       # pending purchase order picked up
    assert by_id[1]["leadTimeDays"] == 3                     # received PO took 3 days
    assert body["items"][0]["status"] == "STOCKOUT"          # most urgent first


def test_status_filter_and_parameters(client):
    body = client.get("/api/recommendations?status=STOCKOUT&serviceLevel=0.99&horizon=7", headers=AUTH).get_json()
    assert [i["productId"] for i in body["items"]] == [2]
    assert body["params"]["serviceLevel"] == 0.99 and body["params"]["horizon"] == 7
    assert body["summary"]["totalProducts"] == 2             # summary ignores the display filter


@pytest.mark.parametrize("query", ["serviceLevel=abc", "serviceLevel=1.5", "horizon=0", "leadTime=0"])
def test_bad_parameters_return_400(client, query):
    res = client.get(f"/api/recommendations?{query}", headers=AUTH)
    assert res.status_code == 400 and "message" in res.get_json()


def test_backend_errors_are_passed_through():
    class Rejecting:
        def __init__(self, token): pass
        def get_all(self, path, params=None): raise RimsApiError("Authentication required. Please log in.", 401)
    res = create_app(Rejecting).test_client().get("/api/recommendations", headers=AUTH)
    assert res.status_code == 401 and "log in" in res.get_json()["message"]


def test_cors_only_for_allowed_origins(client):
    ok = client.get("/health", headers={"Origin": "http://localhost:5173"})
    assert ok.headers["Access-Control-Allow-Origin"] == "http://localhost:5173"
    bad = client.get("/health", headers={"Origin": "http://evil.example"})
    assert "Access-Control-Allow-Origin" not in bad.headers
    assert client.options("/api/recommendations", headers={"Origin": "http://localhost:5173"}).status_code == 204


# ---------------------------------------------------------------- RimsClient
class FakeResponse:
    def __init__(self, status, data): self.status_code, self._d, self.text = status, data, str(data)
    def json(self): return self._d


class FakeSession:
    def __init__(self, handler): self.handler, self.calls = handler, []
    def request(self, method, url, headers=None, timeout=None, params=None, json=None):
        self.calls.append((method, url, headers, params))
        return self.handler(method, url, params, json)


def test_get_all_follows_pagination_and_sends_the_token():
    def handler(method, url, params, body):
        p = params["page"]
        return FakeResponse(200, {"content": [{"n": p * 2}, {"n": p * 2 + 1}], "page": p, "size": 1000, "totalElements": 6, "totalPages": 3})
    session = FakeSession(handler)
    rows = RimsClient("http://x/api/", token="tok", session=session).get_all("/sales")
    assert [r["n"] for r in rows] == [0, 1, 2, 3, 4, 5]
    assert session.calls[0][1] == "http://x/api/sales" and session.calls[0][2]["Authorization"] == "Bearer tok"
    assert all(c[3]["size"] == 1000 for c in session.calls)


def test_login_and_error_mapping():
    session = FakeSession(lambda m, u, p, j: FakeResponse(200, {"token": "jwt", "user": {}}) if u.endswith("/auth/login")
                          else FakeResponse(403, {"message": "You do not have permission to perform this action"}))
    c = RimsClient.login("http://x/api", "admin", "admin123", session=session)
    assert c.token == "jwt"
    with pytest.raises(RimsApiError) as e:
        c.get_all("/users")
    assert e.value.status == 403 and "permission" in str(e.value)
