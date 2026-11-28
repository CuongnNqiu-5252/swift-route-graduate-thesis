import httpx
import pytest


@pytest.fixture
def goong_stub(monkeypatch):
    """Explicit synthetic road responses; tests never contact the paid API."""
    monkeypatch.setenv("GOONG_API_KEY", "test-key")
    real_client = httpx.Client

    def handler(request):
        origins = request.url.params["origins"].split("|")
        destinations = request.url.params["destinations"].split("|")
        return httpx.Response(200, json={"rows": [{"elements": [
            {"status": "OK", "distance": {"value": 2000}, "duration": {"value": 300}}
            for _ in destinations]} for _ in origins]})

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kw: real_client(transport=transport, **kw))
