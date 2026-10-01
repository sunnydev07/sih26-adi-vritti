"""JAGO route-map contract test.

P1-5 was a silent wrong-endpoint bug: `get_my_applications` pointed at the
disbursement endpoint, so a question about applications got payment data with
a 200 and nobody noticed. This test loads Core's OpenAPI contract and asserts
every entry in the proxy's route table names a (method, path) the contract
actually defines, so the next drift fails loudly here instead of answering
wrongly in production.
"""

from __future__ import annotations

import re
from pathlib import Path

import yaml

from app.services.jago_service import ROUTE_FOR_TOOL

CORE_YAML = Path(__file__).resolve().parents[3] / "docs" / "openapi" / "core.yaml"


def _contract_routes() -> set[tuple[str, re.Pattern[str]]]:
    spec = yaml.safe_load(CORE_YAML.read_text(encoding="utf-8"))
    routes: set[tuple[str, re.Pattern[str]]] = set()
    for path, operations in spec["paths"].items():
        pattern = re.compile(re.sub(r"\{[^/]+\}", r"[^/]+", path) + r"$")
        for method in operations:
            if method in ("get", "post", "put", "patch", "delete"):
                routes.add((method, pattern))
    return routes


def _contract_jago_tools() -> set[str]:
    spec = yaml.safe_load(CORE_YAML.read_text(encoding="utf-8"))
    params = spec["paths"]["/v1/jago/tool/{name}"]["post"]["parameters"]
    names = [p for p in params if p.get("name") == "name"]
    assert names, "contract must document the {name} path parameter"
    return set(names[0]["schema"]["enum"])


def test_core_contract_exists_and_lists_routes():
    assert CORE_YAML.is_file(), f"missing contract: {CORE_YAML}"
    assert len(_contract_routes()) > 10


def test_every_proxy_route_exists_in_the_core_contract():
    routes = _contract_routes()
    for tool, (method, template) in ROUTE_FOR_TOOL.items():
        assert any(
            method.lower() == m and pattern.match(template) for m, pattern in routes
        ), f"{tool} -> {method} {template} is not in docs/openapi/core.yaml"


def test_every_proxy_tool_is_a_contract_tool_name():
    names = _contract_jago_tools()
    for tool in ROUTE_FOR_TOOL:
        assert tool in names, f"{tool} is not in the contract's jago tool enum"


def test_proxy_and_contract_tool_sets_match():
    # A tool Core knows but the proxy cannot reach is dead; a tool the proxy
    # routes but Core does not know 404s. Both directions fail here.
    from app.services.jago_service import TOOLS

    assert set(ROUTE_FOR_TOOL) == TOOLS
    assert TOOLS == _contract_jago_tools()
