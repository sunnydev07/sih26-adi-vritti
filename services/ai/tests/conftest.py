"""Test-wide configuration.

AI_SERVICE_TOKEN is set before app.config is imported, because that module builds
a settings singleton at import time. Setting it here (conftest is imported before
test modules) keeps the token deterministic without patching globals per test.

ALLOW_INSECURE_DEV is deliberately NOT set. app.security refuses protected routes
while the token is blank or the published development value, and a token that is
neither (this one) is exactly the case the gate must let through without the
opt-in. Leaving the opt-in off is what keeps the rest of this suite exercising the
strict path; the opt-in and the refusal are covered on their own terms in
test_security.py.
"""

import os

os.environ.setdefault("AI_SERVICE_TOKEN", "test-service-token")
os.environ.setdefault("CORE_SERVICE_TOKEN", "test-core-token")
os.environ.setdefault("GAP_HMAC_SALT", "test-salt")
