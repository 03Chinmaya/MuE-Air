import os
import sys

def get_credentials():
    env_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../server/.env'))
    env_vars = {}
    if os.path.exists(env_path):
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    env_vars[k.strip()] = v.strip().strip('"\'')

    client_id = env_vars.get('GOOGLE_CLIENT_ID')
    client_secret = env_vars.get('GOOGLE_CLIENT_SECRET')
    refresh_token = env_vars.get('REAL_REFRESH_TOKEN')
    return client_id, client_secret, refresh_token

if __name__ == '__main__':
    client_id, client_secret, refresh_token = get_credentials()
    print(f"Config present: client_id={bool(client_id)}, client_secret={bool(client_secret)}, refresh_token={bool(refresh_token)}")

    if not (client_id and client_secret and refresh_token):
        print("Missing credentials in server/.env")
        sys.exit(1)

    sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
    from main import get_library_playlists, get_playlist_tracks, PlaylistsRequest, PlaylistTracksRequest

    try:
        req = PlaylistsRequest(refresh_token=refresh_token, client_id=client_id, client_secret=client_secret)
        res = get_library_playlists(req)
        playlists = res.get("playlists", [])
        print(f"SUCCESS: Fetched {len(playlists)} library playlists:")
        for p in playlists:
            print(f"  - Playlist: \"{p.get('title')}\" | id: {p.get('id')} | trackCount: {p.get('trackCount')}")

        if playlists:
            sample_id = playlists[0].get('id')
            track_req = PlaylistTracksRequest(
                refresh_token=refresh_token,
                client_id=client_id,
                client_secret=client_secret,
                playlist_id=sample_id
            )
            track_res = get_playlist_tracks(track_req)
            tracks = track_res.get("tracks", [])
            print(f"\nSUCCESS: Fetched {len(tracks)} tracks from playlist \"{playlists[0].get('title')}\":")
            for t in tracks[:5]:
                print(f"  - Track: \"{t.get('title')}\" | artists: {t.get('artists')} | videoId: {t.get('videoId')}")
    except Exception as e:
        print(f"ERROR ({type(e).__name__}): {e}")
        sys.exit(1)
