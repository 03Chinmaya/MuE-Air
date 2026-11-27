import os
import hmac
import json
import logging
import re
import asyncio
import difflib
from typing import Any, Dict, List, Optional
from fastapi import Depends, FastAPI, HTTPException, Request
from pydantic import BaseModel, Field
import httpx
import requests
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from ytmusicapi import YTMusic, OAuthCredentials

# Configure basic logging without echoing sensitive request bodies
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ytmusic-sidecar")

limiter = Limiter(key_func=get_remote_address)
app = FastAPI(title="MuE YouTube Music Sidecar", version="1.0")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

_ytm_singleton: Optional[YTMusic] = None


def get_ytm() -> YTMusic:
    global _ytm_singleton
    if not isinstance(YTMusic, type):
        return YTMusic()
    if _ytm_singleton is None:
        _ytm_singleton = YTMusic()
    return _ytm_singleton


def require_secret(request: Request) -> None:
    expected = os.environ.get("SIDECAR_SECRET", "")
    env = os.environ.get("ENV", "dev").lower()
    if env == "prod" and not expected:
        raise HTTPException(status_code=503, detail="sidecar_misconfigured")
    if expected:
        provided = request.headers.get("X-Sidecar-Secret", "")
        if not hmac.compare_digest(provided, expected):
            raise HTTPException(status_code=401, detail="unauthorized")

class PlaylistsRequest(BaseModel):
    refresh_token: str = Field(..., min_length=1, description="Encrypted-at-rest OAuth refresh token")
    client_id: str = Field(..., min_length=1, description="Google OAuth Client ID")
    client_secret: str = Field(..., min_length=1, description="Google OAuth Client Secret")

class PlaylistTracksRequest(BaseModel):
    refresh_token: str = Field(..., min_length=1, description="Encrypted-at-rest OAuth refresh token")
    client_id: str = Field(..., min_length=1, description="Google OAuth Client ID")
    client_secret: str = Field(..., min_length=1, description="Google OAuth Client Secret")
    playlist_id: str = Field(..., min_length=1, description="YouTube Music playlist browseId or ID")

class PublicPlaylistRequest(BaseModel):
    playlist_url_or_id: str = Field(..., min_length=1, description="YouTube Music playlist URL or ID")

class SpotifyPrivatePlaylistRequest(BaseModel):
    playlist_url: str = Field(..., min_length=1, description="Spotify playlist URL, short link, or URI")
    sp_dc_cookie: str = Field(..., min_length=1, description="Spotify sp_dc session cookie")

def init_ytmusic(refresh_token: str, client_id: str, client_secret: str) -> YTMusic:
    # ytmusicapi 1.12.3: YTMusic(auth=...) with expires_at<=now
    # triggers automatic OAuth refresh on first API call via RefreshingToken.
    # Verified by test_invalid_token_returns_502_and_does_not_leak_token
    # (invalid token -> Google returns 401/400 -> credentials.refresh_token() raises -> 502 response).
    creds = OAuthCredentials(client_id=client_id, client_secret=client_secret)
    oauth_dict = {
        "client_id": client_id,
        "client_secret": client_secret,
        "access_token": "",
        "refresh_token": refresh_token,
        "token_type": "Bearer",
        "scope": "https://www.googleapis.com/auth/youtube",
        "expires_at": 0,
        "expires_in": 0,
    }
    return YTMusic(auth=json.dumps(oauth_dict), oauth_credentials=creds)

def normalize_playlist(item: Dict[str, Any]) -> Dict[str, Any]:
    raw_count = item.get("count", 0)
    try:
        count = int(raw_count)
    except (ValueError, TypeError):
        count = 0
    thumbnails = item.get("thumbnails")
    thumbnail_url = None
    if isinstance(thumbnails, list) and thumbnails:
        best = max(thumbnails, key=lambda t: (t.get("width", 0) * t.get("height", 0)))
        thumbnail_url = best.get("url")
    return {
        "id": str(item.get("playlistId") or item.get("id") or ""),
        "title": str(item.get("title") or "Untitled Playlist"),
        "trackCount": count,
        "thumbnail": thumbnail_url,
    }

def normalize_track(item: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    video_id = str(item.get("videoId") or item.get("id") or "")
    artists_raw = item.get("artists") or []
    artists = [a.get("name") for a in artists_raw if isinstance(a, dict) and a.get("name")]
    if not artists:
        author = item.get("author") or item.get("channel")
        if isinstance(author, str) and author.strip():
            artists = [author.strip()]
        elif isinstance(author, dict) and author.get("name"):
            artists = [author.get("name")]

    album_raw = item.get("album")
    album_name = album_raw.get("name") if isinstance(album_raw, dict) else (album_raw if isinstance(album_raw, str) else None)
    duration_s = item.get("duration_seconds")
    try:
        duration_ms = int(duration_s) * 1000 if duration_s is not None else 0
    except (ValueError, TypeError):
        duration_ms = 0

    thumbnails = item.get("thumbnails") or []
    thumb_url = None
    if isinstance(thumbnails, list) and thumbnails:
        thumb_url = max(thumbnails, key=lambda t: (t.get("width", 0) * t.get("height", 0))).get("url")

    title = str(item.get("title") or "Unknown Title")
    if not video_id and title == "Unknown Title":
        return None

    video_type_raw = str(item.get("videoType") or item.get("musicVideoType") or "")
    if "ATV" in video_type_raw or "OFFICIAL_SOURCE_MUSIC" in video_type_raw:
        video_type = "ATV"
    elif "OMV" in video_type_raw or "MUSIC_VIDEO_TYPE_OMV" in video_type_raw:
        video_type = "OMV"
    elif "UGC" in video_type_raw or "USER_GENERATED" in video_type_raw:
        video_type = "UGC"
    else:
        video_type = video_type_raw if video_type_raw else None

    return {
        "videoId": video_id,
        "title": title,
        "artists": artists,
        "album": album_name,
        "durationMs": duration_ms,
        "thumbnail": thumb_url,
        "videoType": video_type,
    }

def fetch_playlists_via_data_api(access_token: str) -> List[Dict[str, Any]]:
    r = requests.get(
        "https://www.googleapis.com/youtube/v3/playlists?mine=true&part=snippet,contentDetails&maxResults=50",
        headers={"Authorization": f"Bearer {access_token}"},
        timeout=10,
    )
    if not r.ok:
        return []
    items = r.json().get("items", [])
    result = []
    for it in items:
        snip = it.get("snippet", {})
        content = it.get("contentDetails", {})
        thumbnails = snip.get("thumbnails", {})
        best_thumb = None
        if isinstance(thumbnails, dict) and thumbnails:
            best_thumb = max(thumbnails.values(), key=lambda t: t.get("width", 0) * t.get("height", 0)).get("url")
        result.append({
            "id": str(it.get("id") or ""),
            "title": str(snip.get("title") or "Untitled Playlist"),
            "trackCount": int(content.get("itemCount", 0)),
            "thumbnail": best_thumb,
        })
    return result

@app.get("/")
def read_root():
    return {"service": "ytmusic-sidecar", "status": "running"}

@app.api_route("/health", methods=["GET", "HEAD"])
def health_check():
    return {"status": "ok"}

@app.api_route("/readyz", methods=["GET", "HEAD"])
def readiness_check():
    return {"status": "ready"}

@app.post("/library/playlists")
@limiter.limit("20/minute")
def get_library_playlists(
    request: Request,
    req: PlaylistsRequest,
    _: None = Depends(require_secret),
) -> Dict[str, Any]:
    try:
        ytm = init_ytmusic(req.refresh_token, req.client_id, req.client_secret)
        try:
            raw_playlists = ytm.get_library_playlists(limit=50)
            if raw_playlists and isinstance(raw_playlists, list):
                normalized = [normalize_playlist(p) for p in raw_playlists if isinstance(p, dict)]
                return {"playlists": normalized}
        except Exception as inner_exc:
            logger.info("InnerTube browse failed (%s), attempting YouTube Data API fallback", type(inner_exc).__name__)
            access_token = getattr(getattr(ytm, "_token", None), "access_token", None)
            if access_token:
                fallback_playlists = fetch_playlists_via_data_api(access_token)
                if fallback_playlists:
                    return {"playlists": fallback_playlists}
            raise inner_exc
        return {"playlists": []}
    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Failed to fetch library playlists (type: %s)", type(exc).__name__)
        # Hard safety block: never echo tokens or secrets in error responses
        raise HTTPException(status_code=502, detail="playlist_fetch_failed") from None

@app.post("/playlist/tracks")
@limiter.limit("20/minute")
def get_playlist_tracks(
    request: Request,
    req: PlaylistTracksRequest,
    _: None = Depends(require_secret),
) -> Dict[str, Any]:
    if not req.playlist_id or not req.playlist_id.strip():
        raise HTTPException(status_code=400, detail="invalid_playlist_id")

    pid = req.playlist_id.strip()
    try:
        ytm = init_ytmusic(req.refresh_token, req.client_id, req.client_secret)
        raw_tracks = []
        try:
            playlist = ytm.get_playlist(pid, limit=300)
            if isinstance(playlist, dict):
                raw_tracks = playlist.get("tracks", [])
        except Exception:
            # Fallback to unauthenticated YTMusic get_playlist for public/unlisted playlists
            try:
                unauth_ytm = get_ytm()
                playlist = unauth_ytm.get_playlist(pid, limit=300)
                if isinstance(playlist, dict):
                    raw_tracks = playlist.get("tracks", [])
            except Exception:
                pass

        if not raw_tracks or not isinstance(raw_tracks, list):
            return {"tracks": [], "thumbnail": None}
        normalized = [normalize_track(t) for t in raw_tracks if isinstance(t, dict)]
        valid_tracks = [t for t in normalized if t is not None]

        playlist_thumb = None
        if isinstance(playlist, dict):
            p_thumbs = playlist.get("thumbnails") or []
            if isinstance(p_thumbs, list) and p_thumbs:
                playlist_thumb = max(p_thumbs, key=lambda t: (t.get("width", 0) * t.get("height", 0))).get("url")

        return {"tracks": valid_tracks, "thumbnail": playlist_thumb}
    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Failed to fetch playlist tracks for %s (type: %s)", pid, type(exc).__name__)
        # Hard safety block: never echo tokens
        raise HTTPException(status_code=502, detail="playlist_tracks_fetch_failed") from None

@app.post("/playlist/public")
@limiter.limit("20/minute")
def get_public_playlist(
    request: Request,
    req: PublicPlaylistRequest,
    _: None = Depends(require_secret),
) -> Dict[str, Any]:
    raw = req.playlist_url_or_id.strip()
    if not raw:
        raise HTTPException(status_code=400, detail="invalid_playlist_url_or_id")

    if "list=" in raw:
        playlist_id = raw.split("list=")[1].split("&")[0]
    else:
        playlist_id = raw

    try:
        ytm = get_ytm()
        playlist = ytm.get_playlist(playlist_id, limit=300)
        raw_tracks = playlist.get("tracks", []) if isinstance(playlist, dict) else []
        normalized = [normalize_track(t) for t in raw_tracks if isinstance(t, dict)]

        playlist_thumb = None
        if isinstance(playlist, dict):
            p_thumbs = playlist.get("thumbnails") or []
            if isinstance(p_thumbs, list) and p_thumbs:
                playlist_thumb = max(p_thumbs, key=lambda t: (t.get("width", 0) * t.get("height", 0))).get("url")

        return {
            "title": playlist.get("title", "Imported Playlist") if isinstance(playlist, dict) else "Imported Playlist",
            "tracks": [t for t in normalized if t is not None],
            "thumbnail": playlist_thumb,
        }
    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Public playlist fetch failed (type: %s)", type(exc).__name__)
        raise HTTPException(status_code=502, detail="public_playlist_fetch_failed") from None

def resolve_spotify_track_to_ytm(
    title: str,
    artists: List[str],
    duration_ms: int,
    ytm_client: Optional[YTMusic] = None,
) -> Optional[str]:
    primary_artist = artists[0] if artists else ""
    query = f"{title} {primary_artist}".strip()
    if not query:
        return None

    client = ytm_client or YTMusic()
    results = client.search(query, filter="songs", limit=5) or []
    best_vid: Optional[str] = None
    best_ratio = 0.0

    for cand in results:
        if not isinstance(cand, dict):
            continue
        vid = cand.get("videoId")
        cand_title = str(cand.get("title") or "")
        if not vid or not cand_title:
            continue

        ratio = difflib.SequenceMatcher(None, title.lower(), cand_title.lower()).ratio()
        if ratio < 0.75:
            continue

        cand_dur_s = cand.get("duration_seconds")
        if duration_ms > 0 and cand_dur_s is not None:
            try:
                cand_dur_ms = int(cand_dur_s) * 1000
                if abs(cand_dur_ms - duration_ms) > 4000:
                    continue
            except (ValueError, TypeError):
                pass

        if ratio > best_ratio:
            best_ratio = ratio
            best_vid = str(vid)

    return best_vid


@app.post("/spotify/private-playlist")
@limiter.limit("20/minute")
async def get_spotify_private_playlist(
    request: Request,
    req: SpotifyPrivatePlaylistRequest,
    _: None = Depends(require_secret),
) -> Dict[str, Any]:
    url = req.playlist_url.strip()
    match = re.search(r"(?:open\.spotify\.com/playlist/|spotify\.link/|spotify:playlist:)([a-zA-Z0-9]+)", url)
    if not match:
        raise HTTPException(status_code=400, detail="invalid_playlist_url")
    playlist_id = match.group(1)

    sp_dc = req.sp_dc_cookie.strip()
    if not sp_dc:
        raise HTTPException(status_code=400, detail="invalid_spotify_cookie")

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            token_res = await client.get(
                "https://open.spotify.com/get_access_token?reason=transport&productType=web_player",
                headers={"Cookie": f"sp_dc={sp_dc}"}
            )
            if token_res.status_code != 200:
                raise HTTPException(status_code=400, detail="invalid_spotify_cookie")
            token_data = token_res.json()
            token = token_data.get("accessToken")
            if not token or token_data.get("isAnonymous") is True:
                raise HTTPException(status_code=400, detail="invalid_spotify_cookie")

            res = await client.get(
                f"https://api.spotify.com/v1/playlists/{playlist_id}",
                headers={"Authorization": f"Bearer {token}"}
            )

            if res.status_code == 404:
                raise HTTPException(
                    status_code=404,
                    detail="playlist_not_found_or_private",
                    headers={"X-Suggestion": "Make playlist public temporarily"}
                )
            if res.status_code in (401, 403):
                raise HTTPException(status_code=400, detail="invalid_spotify_cookie")
            if res.status_code != 200:
                logger.warning("Spotify playlist fetch failed with status %s", res.status_code)
                raise HTTPException(status_code=502, detail="spotify_fetch_failed")

            data = res.json()
            title = data.get("name") or "Imported Spotify Playlist"
            description = data.get("description")
            p_images = data.get("images") or []
            playlist_thumb = p_images[0].get("url") if isinstance(p_images, list) and p_images else None

            raw_items = data.get("tracks", {}).get("items", []) if isinstance(data.get("tracks"), dict) else []
            raw_items = raw_items[:300]
            ytm_client = get_ytm()
            tracks = []
            unresolved_count = 0
            consecutive_failures = 0

            for idx, item in enumerate(raw_items):
                if not isinstance(item, dict):
                    continue
                track_obj = item.get("track")
                if not isinstance(track_obj, dict):
                    continue
                tid = track_obj.get("id") or ""
                t_name = track_obj.get("name") or "Untitled Track"
                artists = [
                    a.get("name")
                    for a in (track_obj.get("artists") or [])
                    if isinstance(a, dict) and a.get("name")
                ] or ["Unknown Artist"]
                album_name = track_obj.get("album", {}).get("name") if isinstance(track_obj.get("album"), dict) else None
                duration_ms = int(track_obj.get("duration_ms") or 0)
                album_images = track_obj.get("album", {}).get("images") or []
                t_thumb = album_images[0].get("url") if isinstance(album_images, list) and album_images else None

                if consecutive_failures >= 3:
                    unresolved_count += 1
                    continue

                try:
                    resolved_vid = await asyncio.wait_for(
                        asyncio.to_thread(resolve_spotify_track_to_ytm, t_name, artists, duration_ms, ytm_client),
                        timeout=5.0,
                    )
                except Exception:
                    resolved_vid = None

                if not resolved_vid:
                    unresolved_count += 1
                    consecutive_failures += 1
                    continue

                consecutive_failures = 0
                tracks.append({
                    "externalId": tid,
                    "videoId": resolved_vid,
                    "spotifyTrackId": tid,
                    "title": t_name,
                    "artists": artists,
                    "album": album_name,
                    "durationMs": duration_ms,
                    "thumbnail": t_thumb,
                    "thumbnailUrl": t_thumb,
                })

            return {
                "title": title,
                "description": description,
                "thumbnail": playlist_thumb,
                "thumbnailUrl": playlist_thumb,
                "tracks": tracks,
                "unresolvedCount": unresolved_count,
            }
    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Spotify private playlist fetch error: %s", type(exc).__name__)
        raise HTTPException(status_code=502, detail="spotify_fetch_failed") from None

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
