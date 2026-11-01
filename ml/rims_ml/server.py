"""HTTP service the React app calls. The caller's RIMS JWT is forwarded to the Spring Boot API,
so the ML service has no credentials of its own and users only see what their token allows."""
from __future__ import annotations

import os
from datetime import datetime, timezone

from flask import Flask, jsonify, request

from . import snapshot as snapshot_mod
from .client import RimsApiError, RimsClient
from .recommend import Params, recommend, summarize

DEFAULT_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173"


def create_app(client_factory=None) -> Flask:
    app = Flask(__name__)
    api_url = os.environ.get("RIMS_API_URL", "http://localhost:9090/api")
    origins = {o.strip() for o in os.environ.get("ML_CORS_ORIGINS", DEFAULT_ORIGINS).split(",") if o.strip()}
    make_client = client_factory or (lambda token: RimsClient(api_url, token=token))

    @app.after_request
    def cors(resp):
        origin = request.headers.get("Origin")
        if origin in origins:
            resp.headers["Access-Control-Allow-Origin"] = origin
            resp.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type"
            resp.headers["Access-Control-Allow-Methods"] = "GET, OPTIONS"
            resp.headers["Vary"] = "Origin"
        return resp

    @app.errorhandler(RimsApiError)
    def api_error(e):
        return jsonify(message=str(e)), e.status

    @app.errorhandler(ValueError)
    def bad_request(e):
        return jsonify(message=str(e)), 400

    @app.get("/health")
    def health():
        return jsonify(status="ok")

    @app.route("/api/recommendations", methods=["GET", "OPTIONS"])
    def recommendations():
        if request.method == "OPTIONS":
            return "", 204
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or len(auth) < 15:
            return jsonify(message="Authentication required. Please log in."), 401

        def num(name, cast, default):
            raw = request.args.get(name)
            if raw in (None, ""):
                return default
            try:
                return cast(raw)
            except ValueError:
                raise ValueError(f"'{name}' must be a number") from None

        d = Params()
        params = Params(
            horizon=num("horizon", int, d.horizon),
            service_level=num("serviceLevel", float, d.service_level),
            default_lead_time=num("leadTime", float, d.default_lead_time),
            review_days=num("reviewDays", int, d.review_days),
            overstock_days=num("overstockDays", int, d.overstock_days),
        ).validate()

        snap = snapshot_mod.from_api(make_client(auth[len("Bearer "):]))
        items = recommend(snap, params)
        status = request.args.get("status")
        shown = [i for i in items if i["status"] == status] if status else items
        return jsonify(generatedAt=datetime.now(timezone.utc).isoformat(), params={
            "horizon": params.horizon, "serviceLevel": params.service_level,
            "defaultLeadTime": params.default_lead_time, "reviewDays": params.review_days,
            "overstockDays": params.overstock_days, "windowDays": params.window_days,
        }, summary=summarize(items), items=shown)

    return app


def main() -> None:  # pragma: no cover
    port = int(os.environ.get("ML_PORT", "8000"))
    create_app().run(host="0.0.0.0", port=port)


if __name__ == "__main__":  # pragma: no cover
    main()
