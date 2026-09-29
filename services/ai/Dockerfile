# Declaring [build-system] is the actual fix here: the previous image ran
# `pip install -e .` against a pyproject that had no backend, so pip fell back to
# a legacy setuptools path that cannot reliably build a project defined only in
# pyproject.toml. Non-editable install also keeps the image reproducible --
# a bind-mounted source tree no longer shadows the installed package silently.
FROM python:3.13-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# curl is used only by the healthcheck below. tesseract-ocr used to be installed
# here to back a pytesseract OCR path that no code invokes -- the extractor works
# on uploaded text, not on images -- so it was ~15 MB of native packages and a
# shell-out attack surface for nothing.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Dependencies change far less often than source, so they install in their own
# layer. The tests directory is deliberately not copied: pytest is a dev extra,
# not something the runtime image should carry.
COPY pyproject.toml ./
COPY app/ ./app/
RUN pip install . && rm -rf /root/.cache

# Never run the service as root.
RUN chown -R nobody:nogroup /app
USER nobody

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
