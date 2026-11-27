export type ImportTarget =
  | { kind: 'youtube'; playlistId: string; cleanedUrl: string | null }
  | { kind: 'spotify'; playlistId: string; cleanedUrl: string };

const ALLOWED_HOSTS = new Set([
  'music.youtube.com',
  'www.youtube.com',
  'youtube.com',
  'youtu.be',
  'open.spotify.com',
  'spotify.link',
]);

const YTM_PLAYLIST_ID_RE = /^[A-Za-z0-9_-]{10,}$/;
const SPOTIFY_PLAYLIST_ID_RE = /^[A-Za-z0-9]{22}$/;

export function validateImportTarget(
  raw: string,
): { ok: true; target: ImportTarget } | { ok: false; reason: string } {
  const trimmed = (raw ?? '').trim();
  if (!trimmed || trimmed.length > 2048) {
    return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
  }

  if (YTM_PLAYLIST_ID_RE.test(trimmed)) {
    return {
      ok: true,
      target: { kind: 'youtube', playlistId: trimmed, cleanedUrl: null },
    };
  }

  if (trimmed.toLowerCase().startsWith('spotify:playlist:')) {
    const id = trimmed.slice('spotify:playlist:'.length);
    if (!SPOTIFY_PLAYLIST_ID_RE.test(id)) {
      return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
    }
    return {
      ok: true,
      target: {
        kind: 'spotify',
        playlistId: id,
        cleanedUrl: `https://open.spotify.com/playlist/${id}`,
      },
    };
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
  }

  const host = parsed.hostname.toLowerCase();
  if (!ALLOWED_HOSTS.has(host)) {
    return { ok: false, reason: 'Unsupported host' };
  }

  if (host === 'open.spotify.com' || host === 'spotify.link') {
    parsed.searchParams.delete('si');
    if (host === 'spotify.link') {
      return {
        ok: true,
        target: { kind: 'spotify', playlistId: '', cleanedUrl: parsed.toString() },
      };
    }
    const match = parsed.pathname.match(/\/playlist\/([A-Za-z0-9]{22})(?:\/|$)/);
    if (!match) {
      return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
    }
    return {
      ok: true,
      target: {
        kind: 'spotify',
        playlistId: match[1],
        cleanedUrl: parsed.toString(),
      },
    };
  }

  const listParam = parsed.searchParams.get('list')?.trim() ?? '';
  if (!listParam) {
    return { ok: false, reason: 'Missing playlist list= parameter' };
  }
  if (!YTM_PLAYLIST_ID_RE.test(listParam)) {
    return { ok: false, reason: 'Not a YouTube Music or Spotify playlist URL' };
  }

  parsed.searchParams.delete('si');
  parsed.searchParams.delete('drli');
  parsed.searchParams.delete('feature');

  return {
    ok: true,
    target: {
      kind: 'youtube',
      playlistId: listParam,
      cleanedUrl: parsed.toString(),
    },
  };
}
