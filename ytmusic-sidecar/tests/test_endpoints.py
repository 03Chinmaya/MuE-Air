import os
import sys
from unittest.mock import MagicMock, patch

# Ensure ytmusic-sidecar root is on Python module path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["status"] == "running"

def test_missing_body_fields():
    # Missing client_id and client_secret
    res1 = client.post("/library/playlists", json={"refresh_token": "some-token"})
    assert res1.status_code == 422

    # Missing playlist_id
    res2 = client.post("/playlist/tracks", json={
        "refresh_token": "tok",
        "client_id": "cid",
        "client_secret": "csec"
    })
    assert res2.status_code == 422

def test_malformed_playlist_id_returns_400_or_422():
    # Empty string or whitespace playlist_id
    res1 = client.post("/playlist/tracks", json={
        "refresh_token": "tok",
        "client_id": "cid",
        "client_secret": "csec",
        "playlist_id": "   "
    })
    assert res1.status_code == 400
    assert res1.json()["detail"] == "invalid_playlist_id"

def test_invalid_token_returns_502_and_does_not_leak_token():
    dummy_secret_token = "secret-super-private-refresh-token-12345"
    response = client.post("/library/playlists", json={
        "refresh_token": dummy_secret_token,
        "client_id": "test-client-id",
        "client_secret": "test-client-secret"
    })
    assert response.status_code == 502
    assert response.json()["detail"] == "playlist_fetch_failed"
    # Strict safety invariant: token must never be present in the response
    assert dummy_secret_token not in response.text

@patch("main.init_ytmusic")
def test_successful_playlists_fetch_with_real_1_12_3_shape(mock_init):
    mock_ytm = MagicMock()
    # Real ytmusicapi 1.12.3 library playlist item format
    mock_ytm.get_library_playlists.return_value = [
        {
            "playlistId": "PLQwVIlKxHM6rz0fDJVv_0UlXGEWf-bFys",
            "title": "Gym Hype 2026",
            "thumbnails": [
                {"url": "https://lh3.googleusercontent.com/small.jpg", "width": 100, "height": 100},
                {"url": "https://lh3.googleusercontent.com/large.jpg", "width": 500, "height": 500}
            ],
            "count": "42",
            "owned": True
        }
    ]
    mock_init.return_value = mock_ytm

    response = client.post("/library/playlists", json={
        "refresh_token": "valid-token",
        "client_id": "valid-id",
        "client_secret": "valid-secret"
    })
    assert response.status_code == 200
    data = response.json()
    assert "playlists" in data
    assert len(data["playlists"]) == 1
    # Verify sidecar normalization to clean contract for Node
    pl = data["playlists"][0]
    assert pl["id"] == "PLQwVIlKxHM6rz0fDJVv_0UlXGEWf-bFys"
    assert pl["title"] == "Gym Hype 2026"
    assert pl["trackCount"] == 42
    assert pl["thumbnail"] == "https://lh3.googleusercontent.com/large.jpg"

@patch("main.init_ytmusic")
def test_successful_playlist_tracks_fetch_with_real_1_12_3_shape(mock_init):
    mock_ytm = MagicMock()
    # Real ytmusicapi 1.12.3 parse_playlist_item track format
    mock_ytm.get_playlist.return_value = {
        "id": "PLQwVIlKxHM6rz0fDJVv_0UlXGEWf-bFys",
        "title": "Gym Hype 2026",
        "tracks": [
            {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "artists": [{"name": "Rick Astley", "id": "UCxxxx"}],
                "album": {"name": "Whenever You Need Somebody", "id": "MPREb_xxxx"},
                "duration": "3:32",
                "duration_seconds": 212,
                "thumbnails": [{"url": "https://lh3.googleusercontent.com/track.jpg", "width": 500, "height": 500}],
                "isAvailable": True,
                "isExplicit": False
            }
        ]
    }
    mock_init.return_value = mock_ytm

    response = client.post("/playlist/tracks", json={
        "refresh_token": "valid-token",
        "client_id": "valid-id",
        "client_secret": "valid-secret",
        "playlist_id": "PLQwVIlKxHM6rz0fDJVv_0UlXGEWf-bFys"
    })
    assert response.status_code == 200
    data = response.json()
    assert "tracks" in data
    assert len(data["tracks"]) == 1
    # Verify sidecar normalization to clean contract for Node
    tr = data["tracks"][0]
    assert tr["videoId"] == "dQw4w9WgXcQ"
    assert tr["title"] == "Never Gonna Give You Up"
    assert tr["artists"] == ["Rick Astley"]
    assert tr["album"] == "Whenever You Need Somebody"
    assert tr["durationMs"] == 212000

@patch("main.init_ytmusic")
def test_empty_library_and_empty_playlist(mock_init):
    mock_ytm = MagicMock()
    mock_ytm.get_library_playlists.return_value = []
    mock_ytm.get_playlist.return_value = {"tracks": []}
    mock_init.return_value = mock_ytm

    # Empty library
    res1 = client.post("/library/playlists", json={
        "refresh_token": "valid-token",
        "client_id": "valid-id",
        "client_secret": "valid-secret"
    })
    assert res1.status_code == 200
    assert res1.json() == {"playlists": []}

    # Empty playlist
    res2 = client.post("/playlist/tracks", json={
        "refresh_token": "valid-token",
        "client_id": "valid-id",
        "client_secret": "valid-secret",
        "playlist_id": "PLempty"
    })
    assert res2.status_code == 200
    assert res2.json() == {"tracks": [], "thumbnail": None}

@patch("main.YTMusic")
def test_public_playlist_endpoint_success(mock_ytm_cls):
    mock_inst = MagicMock()
    mock_inst.get_playlist.return_value = {
        "title": "Summer Vibes",
        "tracks": [
            {
                "videoId": "abc123vid",
                "title": "Sunshine Track",
                "artists": [{"name": "Summer Artist"}],
                "album": {"name": "Summer Album"},
                "duration_seconds": 180,
            }
        ]
    }
    mock_ytm_cls.return_value = mock_inst

    # Test with full URL containing list=
    res = client.post("/playlist/public", json={
        "playlist_url_or_id": "https://music.youtube.com/playlist?list=PL_TEST_URL_123&si=xyz"
    })
    assert res.status_code == 200
    data = res.json()
    assert data["title"] == "Summer Vibes"
    assert len(data["tracks"]) == 1
    assert data["tracks"][0]["videoId"] == "abc123vid"
    assert data["tracks"][0]["title"] == "Sunshine Track"
    assert data["tracks"][0]["durationMs"] == 180000
    mock_inst.get_playlist.assert_called_with("PL_TEST_URL_123", limit=300)

@patch("main.YTMusic")
def test_public_playlist_endpoint_error_does_not_leak(mock_ytm_cls):
    mock_inst = MagicMock()
    mock_inst.get_playlist.side_effect = RuntimeError("InnerTube secret internal error")
    mock_ytm_cls.return_value = mock_inst

    res = client.post("/playlist/public", json={
        "playlist_url_or_id": "PL_FAIL_123"
    })
    assert res.status_code == 502
    assert res.json() == {"detail": "public_playlist_fetch_failed"}

def test_spotify_private_playlist_invalid_url():
    res = client.post("/spotify/private-playlist", json={
        "playlist_url": "https://invalid-url.com/something",
        "sp_dc_cookie": "dummy_cookie"
    })
    assert res.status_code == 400
    assert res.json()["detail"] == "invalid_playlist_url"

def test_spotify_private_playlist_invalid_cookie():
    res = client.post("/spotify/private-playlist", json={
        "playlist_url": "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
        "sp_dc_cookie": "invalid_cookie"
    })
    assert res.status_code == 400
    assert res.json()["detail"] == "invalid_spotify_cookie"


@patch("main.YTMusic")
def test_spotify_resolver_match_and_duration_filter(mock_ytm_cls):
    from main import resolve_spotify_track_to_ytm

    mock_inst = MagicMock()
    mock_inst.search.return_value = [
        {"videoId": "wrong_dur", "title": "Kesariya", "duration_seconds": 300},
        {"videoId": "ytm_kesariya_123", "title": "Kesariya", "duration_seconds": 268},
    ]
    vid = resolve_spotify_track_to_ytm("Kesariya", ["Arijit Singh"], 268_000, ytm_client=mock_inst)
    assert vid == "ytm_kesariya_123"


@patch("main.YTMusic")
@patch("main.httpx.AsyncClient")
def test_spotify_private_playlist_resolver_miss_increments_unresolved_count(mock_async_client_cls, mock_ytm_cls):
    token_resp = MagicMock(status_code=200)
    token_resp.json.return_value = {"accessToken": "tok123", "isAnonymous": False}

    pl_resp = MagicMock(status_code=200)
    pl_resp.json.return_value = {
        "name": "My Mix",
        "images": [{"url": "https://img.example/pl.jpg"}],
        "tracks": {
            "items": [
                {
                    "track": {
                        "id": "sp_1",
                        "name": "Matched Song",
                        "artists": [{"name": "Artist A"}],
                        "album": {"name": "Alb", "images": []},
                        "duration_ms": 200_000,
                    }
                },
                {
                    "track": {
                        "id": "sp_2",
                        "name": "Obscure Unmatched Track",
                        "artists": [{"name": "Artist B"}],
                        "album": {"name": "Alb", "images": []},
                        "duration_ms": 200_000,
                    }
                },
            ]
        },
    }

    mock_client = MagicMock()
    async def fake_get(url, **kwargs):
        return token_resp if "get_access_token" in url else pl_resp
    mock_client.get = fake_get

    mock_cm = MagicMock()
    async def aenter(*args): return mock_client
    async def aexit(*args): return False
    mock_cm.__aenter__ = aenter
    mock_cm.__aexit__ = aexit
    mock_async_client_cls.return_value = mock_cm

    mock_ytm = MagicMock()
    mock_ytm.search.side_effect = [
        [{"videoId": "ytm_ok_1", "title": "Matched Song", "duration_seconds": 200}],
        [],
    ]
    mock_ytm_cls.return_value = mock_ytm

    res = client.post("/spotify/private-playlist", json={
        "playlist_url": "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
        "sp_dc_cookie": "valid_sp_dc"
    })
    assert res.status_code == 200
    body = res.json()
    assert body["unresolvedCount"] >= 1
    assert len(body["tracks"]) == 1
    assert body["tracks"][0]["videoId"] == "ytm_ok_1"
    assert body["tracks"][0]["spotifyTrackId"] == "sp_1"
