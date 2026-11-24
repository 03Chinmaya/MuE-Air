export function normalizeTrackDedupeKey(title: string, artists: any): string {
  const normTitle = (title || '')
    .toLowerCase()
    .replace(/&quot;/g, '')
    .replace(/&amp;/g, '')
    .replace(/&#039;/g, '')
    .replace(/&apos;/g, '')
    .replace(/[^a-z0-9]/g, '');

  let artistList: string[] = [];
  if (Array.isArray(artists)) {
    artistList = artists;
  } else if (typeof artists === 'string') {
    try {
      const parsed = JSON.parse(artists);
      artistList = Array.isArray(parsed) ? parsed : [String(parsed)];
    } catch {
      artistList = artists.split(',');
    }
  } else if (artists) {
    artistList = [String(artists)];
  }

  const cleanArtists = artistList
    .map((a) => String(a || '').trim().toLowerCase().replace(/[^a-z0-9]/g, ''))
    .filter(Boolean)
    .sort();

  const artistKey = cleanArtists.slice(0, 2).join(':');
  return `${normTitle}:::${artistKey}`;
}

/**
 * YouTube Music Metadata Proxy Routes (Hono)
 * Proxies YouTube Music search and metadata requests.
 * Implements docs/05_API.md Section 11.5.
 * Strictly adheres to ZERO AUDIO STREAMING from YouTube (metadata only).
 */

import { Hono } from 'hono';
import { Innertube, Mixins, Platform } from 'youtubei.js';
import YTMusic from 'ytmusic-api';
import { AppEnv } from '../types.js';
import { enrichTracksWithJioSaavn } from '../services/trackMatcher.js';
import { ContentQualityService } from '../services/ContentQualityService.js';

export const youtubeRoutes = new Hono<AppEnv>();

const CACHE_VERSION = 'v22h';

// Lazy singletons for YTM providers
/**
 * Shared Innertube singleton — configured with gl:'IN' for Home feed India lock.
 *
 * ⚠️ ARCHITECTURAL NOTE (Phase 22.M, 2026-11-18):
 * This session context biases BROWSING feeds (getExplore, charts, trending,
 * new-releases) toward Indian content — intended behavior for Home.
 *
 * However, TEXT SEARCH ((yt.music as any).search(q, { type: 'song' })) queries
 * the GLOBAL entity catalog and does NOT respect session `location: 'IN'`.
 * This is why Search correctly returns global results (Taylor Swift, BTS, etc.)
 * despite sharing this Innertube instance with Home.
 *
 * 🔴 RISK: If a future youtubei.js version changes search to respect session
 * `gl`/`location`, Search will silently become India-biased — breaking the
 * requirement that Search = global + Indian.
 *
 * 🛡️ MITIGATION if that happens: Create a second Innertube instance with
 * `gl: undefined` (or `gl: 'US'`) for search-only routes, OR pass per-call
 * `gl` override to the search wrapper.
 *
 * Verified: Phase 22.M Part 2 — 4/4 global queries (Taylor Swift, BTS,
 * Ed Sheeran, Kesariya) returned correct content from backend curl + emulator.
 */
let innertubeInstance: Innertube | null = null;
async function getInnertube(): Promise<Innertube> {
  if (!innertubeInstance) {
    if (Platform.shim) {
      Platform.shim.eval = (data: any, env: any) => new Function(...Object.keys(env), data.output)(...Object.values(env));
    }
    innertubeInstance = await Innertube.create({ location: 'IN', lang: 'en' });
  }
  return innertubeInstance;
}

let ytMusicApiInstance: InstanceType<typeof YTMusic> | null = null;
async function getYtMusicApi(): Promise<InstanceType<typeof YTMusic>> {
  if (!ytMusicApiInstance) {
    const ytm = new YTMusic();
    await ytm.initialize();
    ytMusicApiInstance = ytm;
  }
  return ytMusicApiInstance;
}

const isBadCompilationTitle = (t: string, q = '') => ContentQualityService.isCompilationTitle(t, q);

export const KNOWN_LABELS = new Set([
  't-series',
  'sony music india',
  'zee music company',
  'speed records',
  'yrf',
  'tips official',
  'saregama music',
  'aditya music',
  'eros now',
  'geet mp3',
  'desi music factory',
  'white hill music',
  'lahari music',
  't-series regional',
  't-series telugu',
  't-series tamil',
  't-series kannada',
  'aditya music india',
  'sony music',
  'tips music',
]);

export function cleanArtistName(rawName: string, titleContext?: string): string {
  if (!rawName) return 'Unknown Artist';
  let name = rawName.trim();
  name = name.replace(/\s*-\s*Topic$/i, '');
  name = name.replace(/\s+Official(\s+Channel)?$/i, '');
  name = name.replace(/\s+VEVO$/i, '');
  name = name.replace(/\s+Records$/i, '');
  name = name.replace(/\s+Music$/i, '');
  name = name.trim();

  if (KNOWN_LABELS.has(rawName.trim().toLowerCase()) || KNOWN_LABELS.has(name.toLowerCase())) {
    if (titleContext) {
      const parts = titleContext.split(/[-|–—]/).map((p) => p.trim());
      if (parts.length > 1) {
        const candidate = parts.find(
          (p) => !/official|video|4k|hd|lyrics|audio|song|full video|teasers?|remix|mashup/i.test(p) && p.length > 2
        );
        if (candidate) return candidate;
      }
    }
    return 'Various Artists';
  }

  return name || 'Unknown Artist';
}

export function normalizeVideoType(raw: string | undefined | null): 'ATV' | 'OMV' | 'UGC' | null {
  if (!raw) return null;
  if (raw.includes('ATV')) return 'ATV';
  if (raw.includes('OMV') || raw.includes('MUSIC_VIDEO_TYPE_OMV')) return 'OMV';
  if (raw.includes('UGC')) return 'UGC';
  return null;
}

export function extractYtmItemMetadata(item: any): {
  videoType: 'ATV' | 'OMV' | 'UGC' | null;
  artistChannelId: string | null;
  albumId: string | null;
} {
  const rawVideoType =
    item?.videoType ||
    item?.overlay?.content?.endpoint?.payload?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType ||
    item?.endpoint?.payload?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType ||
    item?.musicVideoType ||
    null;

  const artistChannelId =
    item?.artists?.[0]?.channel_id ||
    item?.artists?.[0]?.id ||
    item?.author?.channel_id ||
    item?.author?.id ||
    item?.artist?.artistId ||
    item?.artistId ||
    null;

  const albumId =
    item?.album?.id ||
    item?.album?.albumId ||
    item?.album?.endpoint?.payload?.browseEndpoint?.browseId ||
    item?.album?.endpoint?.payload?.browseId ||
    item?.albumId ||
    null;

  return {
    videoType: normalizeVideoType(rawVideoType),
    artistChannelId: artistChannelId || null,
    albumId: albumId || null,
  };
}

export const withTimeout = <T>(p: Promise<T>, ms: number): Promise<T> =>
  Promise.race([p, new Promise<T>((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);

interface SearchCacheEntry {
  data: any;
  expiresAt: number;
}
const searchCache = new Map<string, SearchCacheEntry>();
const SEARCH_CACHE_TTL_MS = 5 * 60 * 1000;
let searchCacheWrites = 0;

function pruneSearchCache(): void {
  const now = Date.now();
  for (const [key, entry] of searchCache.entries()) {
    if (entry.expiresAt <= now) {
      searchCache.delete(key);
    }
  }
}
try {
  const timer = setInterval(pruneSearchCache, 10 * 60 * 1000);
  if (typeof timer.unref === 'function') timer.unref();
} catch {}



/**
 * Node.js YTM Data Fetcher with automatic fallback:
 * Primary: youtubei.js
 * Fallback: ytmusic-api
 */
export function hasNonEmptyData(data: any): boolean {
  if (!data) return false;
  if (Array.isArray(data)) return data.length > 0;
  if (Array.isArray(data.tracks) && data.tracks.length > 0) return true;
  if (Array.isArray(data.releases) && data.releases.length > 0) return true;
  if (Array.isArray(data.trending) && data.trending.length > 0) return true;
  if (Array.isArray(data.playlists) && data.playlists.length > 0) return true;
  if (Array.isArray(data.albums) && data.albums.length > 0) return true;
  if (Array.isArray(data.artists) && data.artists.length > 0) return true;
  return false;
}

export async function fetchJioSaavnTrending(region: string = 'IN'): Promise<any[]> {
  try {
    const query = region === 'IN' ? 'top songs hindi' : 'trending';
    const targetUrl = `https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=20&p=1&q=${encodeURIComponent(query)}`;
    const res = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        Referer: 'https://www.jiosaavn.com/',
      },
    });
    if (!res.ok) return [];
    const json: any = await res.json();
    const results = json?.results || [];
    return results.map((t: any) => ({
      id: t.id,
      title: t.title?.replace(/&quot;/g, '"')?.replace(/&#039;/g, "'") || 'Unknown Title',
      artist: t.subtitle?.replace(/&quot;/g, '"')?.replace(/&#039;/g, "'") || (t.more_info?.artistMap?.primary_artists?.[0]?.name) || 'Unknown Artist',
      artists: t.more_info?.artistMap?.primary_artists?.map((a: any) => a.name) || [t.subtitle || 'Unknown Artist'],
      album: t.more_info?.album || null,
      durationMs: parseInt(t.more_info?.duration || '180', 10) * 1000,
      year: parseInt(t.year || '0', 10) || null,
      artworkUrl: t.image?.replace('150x150', '500x500') || '',
      provider: 'jiosaavn',
      videoType: 'ATV' as const,
      artistChannelId: null,
      albumId: null,
    }));
  } catch (err) {
    console.warn('[fetchJioSaavnTrending] failed:', err);
    return [];
  }
}

export async function fetchJioSaavnSearch(query: string): Promise<any[]> {
  try {
    if (!query || query.trim().length < 2) return [];
    const targetUrl = `https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=25&p=1&q=${encodeURIComponent(query.trim())}`;
    const res = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        Referer: 'https://www.jiosaavn.com/',
      },
    });
    if (!res.ok) return [];
    const json: any = await res.json();
    const results = json?.results || [];
    return results.map((t: any) => ({
      id: t.id,
      videoId: t.id,
      title: t.title?.replace(/&quot;/g, '"')?.replace(/&#039;/g, "'")?.replace(/&amp;/g, '&') || 'Unknown Title',
      artist: t.subtitle?.replace(/&quot;/g, '"')?.replace(/&#039;/g, "'")?.replace(/&amp;/g, '&') || (t.more_info?.artistMap?.primary_artists?.[0]?.name) || 'Unknown Artist',
      artists: t.more_info?.artistMap?.primary_artists?.map((a: any) => a.name) || [t.subtitle || 'Unknown Artist'],
      album: t.more_info?.album || null,
      durationMs: parseInt(t.more_info?.duration || '180', 10) * 1000,
      duration: parseInt(t.more_info?.duration || '180', 10),
      year: parseInt(t.year || '0', 10) || null,
      artworkUrl: t.image?.replace('150x150', '500x500') || '',
      thumbnail: t.image?.replace('150x150', '500x500') || '',
      provider: 'youtube_music',
      videoType: 'ATV' as const,
      artistChannelId: null,
      albumId: null,
      availableForPlayback: true,
    }));
  } catch (err) {
    console.warn('[fetchJioSaavnSearch] failed:', err);
    return [];
  }
}

const pickBestArtwork = (item: any): string => {
  const thumbs = item?.thumbnails || item?.thumbnail || item?.thumbnail?.contents || [];
  if (Array.isArray(thumbs) && thumbs.length > 0) {
    // Prefer highest-res; check aspect hints (sqp= for square, default for video)
    const sorted = [...thumbs].sort((a, b) => (b.width || 0) - (a.width || 0));
    return sorted[0]?.url || '';
  }
  if (typeof thumbs === 'object' && (thumbs as any)?.url) {
    return (thumbs as any).url;
  }
  return '';
};

function extractShelfItems(res: any, shelfTitleIncludes?: string): any[] {
  const items: any[] = [];
  if (!res) return items;
  
  // Direct array
  if (Array.isArray(res)) {
    return res.filter(Boolean);
  }
  
  const contents = res.contents || res.results || res.albums || res.videos || res.playlists || res.songs || res.items || [];
  for (const entry of contents) {
    if (!entry) continue;
    
    // MusicShelf / SectionList with contents
    if (Array.isArray(entry.contents)) {
      const shelfTitle = (entry.title?.text || entry.title?.toString() || '').toLowerCase();
      if (!shelfTitleIncludes || shelfTitle.includes(shelfTitleIncludes.toLowerCase())) {
        for (const item of entry.contents) {
          if (item && !item.contents) items.push(item);
        }
      }
      continue;
    }
    
    // Grid / Carousel with items
    if (Array.isArray(entry.items)) {
      const shelfTitle = (entry.title?.text || entry.title?.toString() || entry.header?.title?.text || '').toLowerCase();
      if (!shelfTitleIncludes || shelfTitle.includes(shelfTitleIncludes.toLowerCase())) {
        for (const item of entry.items) {
          if (item) items.push(item);
        }
      }
      continue;
    }
    
    // MusicCardShelf
    if (entry.constructor?.name === 'MusicCardShelf' || entry.type === 'MusicCardShelf') {
      if (entry.title) items.push(entry);
      continue;
    }
    
    // Standalone item
    const title = (entry.title?.text || entry.title?.toString() || entry.name || '').trim();
    const isShelfHeader = ['videos', 'albums', 'community playlists', 'playlists', 'songs'].includes(title.toLowerCase());
    if (!isShelfHeader) {
      items.push(entry);
    }
  }
  return items;
}

function extractExploreTrending(explore: any): any[] {
  if (!explore) return [];
  const sections = (explore.sections || explore.contents || []) as any[];
  const trendingSec = sections.find((s: any) => {
    const title = (s.header?.title?.text || s.header?.title?.toString() || s.title?.text || s.title?.toString() || '').toLowerCase();
    return title.includes('trending');
  });
  if (!trendingSec) return [];

  const shelfItems = extractShelfItems(trendingSec);
  const items = shelfItems.length > 0 ? shelfItems : (trendingSec.contents || []);
  if (!Array.isArray(items) || items.length === 0) return [];

  return items.map((item: any) => {
    const vid = item.id || item.video_id || item.videoId || item.endpoint?.payload?.videoId;
    const title = item.title?.text || item.title?.toString() || item.name || 'Unknown Title';
    const authors = (item.authors || item.artists || []).map((a: any) => a.name || a.toString()).filter(Boolean);
    const author = authors.length > 0 ? authors.join(', ') : (item.author?.name || 'Unknown Artist');
    const rawThumb = pickBestArtwork(item) || (Array.isArray(item.thumbnail)
      ? (item.thumbnail[item.thumbnail.length - 1]?.url || item.thumbnail[0]?.url)
      : (item.thumbnails?.[0]?.url || item.thumbnail?.contents?.[0]?.url || item.thumbnail?.url || `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`));
    const meta = extractYtmItemMetadata(item);
    return {
      videoId: vid,
      id: vid,
      title,
      artist: author,
      artists: authors.length > 0 ? authors : [author],
      artworkUrl: rawThumb,
      duration: item.duration?.seconds || item.duration || 180,
      durationMs: (item.duration?.seconds || item.duration || 180) * 1000,
      provider: 'youtube_music',
      videoType: meta.videoType || 'ATV',
      artistChannelId: meta.artistChannelId,
      albumId: meta.albumId,
    };
  }).filter((t: any) => Boolean(t.videoId));
}

async function fetchFromInnertube(type: string, queryOrId: string = '', filter?: string, continuation?: string): Promise<any> {
  try {
    const yt = await getInnertube();
    if (type === 'search') {
      if (continuation) {
        try {
          const nextData = await yt.actions.execute('/search', { continuation, parse: true });
          const feed = new Mixins.Feed(yt.actions, nextData, true);
          const continuationItems = feed.memo?.get('ContinuationItem');
          const nextContinuation = (continuationItems?.[0] as any)?.endpoint?.payload?.token || null;

          const rawTracks = (feed.videos || []).map((v: any) => {
            const author = v.author?.name || v.author?.toString() || 'Unknown Artist';
            const meta = extractYtmItemMetadata(v);
            return {
              videoId: v.id,
              title: v.title?.text || v.title?.toString() || 'Unknown Title',
              artist: author,
              artists: [author],
              artworkUrl: v.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${v.id}/hqdefault.jpg`,
              duration: v.duration?.seconds || 180,
              provider: 'youtube_music',
              videoType: meta.videoType,
              artistChannelId: meta.artistChannelId,
              albumId: meta.albumId,
            };
          });

          const seen = new Set<string>();
          const uniqueRawTracks = rawTracks.filter((t: any) => {
            const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          });
          const qualityTracks = uniqueRawTracks
            .map((t: any) => {
              const { contentType, score } = ContentQualityService.classify(t, false, queryOrId);
              return { ...t, contentType, score };
            })
            .filter((t: any) => t.contentType !== 'ai_generated' && t.contentType !== 'creator_long_mix' && t.score > 10);
          const tracks = await enrichTracksWithJioSaavn(qualityTracks);
          return {
            tracks,
            albums: [],
            playlists: [],
            artists: [],
            continuation: nextContinuation,
            query: queryOrId,
          };
        } catch (err) {
          console.warn('[fetchYtmData] Continuation fetch failed:', err);
          return {
            tracks: [],
            albums: [],
            playlists: [],
            artists: [],
            continuation: null,
            query: queryOrId,
          };
        }
      }

      if (filter === 'playlists') {
        let playlists: any[] = [];
        try {
          const plRes: any = await (yt.music as any).search(queryOrId, { type: 'playlist' });
          const items = extractShelfItems(plRes, 'playlist');
          playlists = items.slice(0, 30).map((p: any) => ({
            id: p.id || p.playlist_id || p.content_id,
            title: p.title?.text || p.title?.toString() || p.name || 'Featured Playlist',
            subtitle: p.author?.name || p.author?.toString() || p.authors?.[0]?.name || 'Playlist',
            author: p.author?.name || p.author?.toString() || p.authors?.[0]?.name || 'Playlist',
            artworkUrl: p.thumbnails?.[0]?.url || p.thumbnail?.contents?.[0]?.url || p.thumbnails?.[p.thumbnails.length - 1]?.url || '',
            thumbnail: p.thumbnails?.[0]?.url || p.thumbnail?.contents?.[0]?.url || '',
            type: 'playlist',
          }));
        } catch {
          const plRes = await yt.search(queryOrId, { type: 'playlist' });
          const items = (plRes.playlists || (plRes as any).results || []);
          playlists = items.slice(0, 30).map((p: any) => ({
            id: p.id || p.content_id,
            title: p.title?.text || p.title?.toString() || p.metadata?.title?.text || 'Featured Playlist',
            subtitle: p.author?.name || p.author?.toString() || 'Playlist',
            artworkUrl: p.thumbnails?.[0]?.url || p.content_image?.primary_thumbnail?.image?.[0]?.url || '',
            type: 'playlist',
          }));
        }
        const filteredPlaylists = playlists.filter((p: any) => !isBadCompilationTitle(p.title || p.name, queryOrId));
        return { playlists: filteredPlaylists, query: queryOrId };
      }

      if (filter === 'albums') {
        let albums: any[] = [];
        try {
          const albRes: any = await (yt.music as any).search(queryOrId, { type: 'album' });
          const items = extractShelfItems(albRes, 'album');
          albums = items.slice(0, 30).map((a: any) => ({
            id: a.id || a.album_id || a.content_id,
            title: a.title?.text || a.title?.toString() || a.name || 'Featured Album',
            subtitle: a.author?.name || a.author?.toString() || a.artists?.[0]?.name || a.year?.text || a.year?.toString() || 'Album',
            year: a.year?.text || a.year?.toString() || a.year || undefined,
            artworkUrl: a.thumbnails?.[0]?.url || a.thumbnail?.contents?.[0]?.url || a.thumbnails?.[a.thumbnails.length - 1]?.url || '',
            type: 'album',
          }));
        } catch {
          const albRes: any = await yt.search(queryOrId, { type: 'album' as any }).catch(() => null);
          const items = (albRes as any)?.albums || (albRes as any)?.results || [];
          albums = items.slice(0, 30).map((a: any) => ({
            id: a.id || a.album_id,
            title: a.title?.text || a.title?.toString() || a.name || 'Featured Album',
            subtitle: a.author?.name || a.author?.toString() || 'Album',
            artworkUrl: a.thumbnails?.[0]?.url || '',
            type: 'album',
          }));
        }
        const filteredAlbums = albums.filter((a: any) => !isBadCompilationTitle(a.title || a.name, queryOrId));
        return { albums: filteredAlbums, query: queryOrId };
      }

      if (filter === 'artists') {
        let artists: any[] = [];
        try {
          const res: any = await (yt.music as any).search(queryOrId, { type: 'artist' });
          const shelves = res?.contents || res?.results || [];
          for (const shelf of shelves) {
            const items = shelf?.contents || [shelf];
            for (const item of items) {
              const artistId = item.id || item.endpoint?.payload?.browseId;
              const name = item.name || item.title?.text || item.flex_columns?.[0]?.title?.text;
              const subscribers = item.subscribers || item.subtitle?.text || item.flex_columns?.[1]?.title?.text || '';
              const thumbnails = item.thumbnail?.contents || item.thumbnails || [];
              const artworkUrl = thumbnails?.[0]?.url || thumbnails?.[thumbnails.length - 1]?.url || '';
              if (artistId && name) {
                artists.push({
                  id: artistId,
                  name,
                  subscribers,
                  thumbnails,
                  artworkUrl,
                  type: 'artist',
                });
              }
            }
          }
        } catch (err) {
          console.warn('[fetchYtmData] youtubei.js artist search failed:', err);
        }

        if (artists.length === 0) {
          try {
            const ytm = await getYtMusicApi();
            const ytmArtists = await ytm.searchArtists(queryOrId);
            artists = (ytmArtists || []).slice(0, 30).map((a: any) => ({
              id: a.artistId || a.id,
              name: a.name,
              subscribers: '',
              thumbnails: a.thumbnails || [],
              artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
              type: 'artist',
            }));
          } catch {}
        }

        return { artists: artists.slice(0, 30), query: queryOrId };
      }

      let rawTracks: any[] = [];
      let nextContinuation: string | null = null;
      let albums: any[] = [];
      let artists: any[] = [];
      let playlists: any[] = [];
      let videos: any[] = [];

      const [songRes, albumRes, artistRes, playlistRes, videoRes] = await Promise.allSettled([
        withTimeout((yt.music as any).search(queryOrId, { type: 'song' }), 4000),
        withTimeout((yt.music as any).search(queryOrId, { type: 'album' }), 4000),
        withTimeout((yt.music as any).search(queryOrId, { type: 'artist' }), 4000),
        withTimeout((yt.music as any).search(queryOrId, { type: 'playlist' }), 4000),
        withTimeout((yt.music as any).search(queryOrId, { type: 'video' }), 4000),
      ]);

      const pick = (r: PromiseSettledResult<any>) => (r.status === 'fulfilled' ? r.value : null);
      const musicRes = pick(songRes);
      const albRes = pick(albumRes);
      const artRes = pick(artistRes);
      const plRes = pick(playlistRes);
      const vidRes = pick(videoRes);

      // 1. Process Songs
      if (musicRes) {
        try {
          const songItems = extractShelfItems(musicRes, 'song');
          const rawItems = songItems.length > 0 ? songItems : (musicRes?.songs || musicRes?.contents || musicRes?.results || []);
          if (rawItems.length > 0) {
            rawTracks = rawItems.slice(0, 30).map((s: any) => {
              const title = s.title?.text || s.title?.toString() || s.name || 'Unknown Title';
              let trackArtists: string[] = [];
              if (Array.isArray(s.artists) && s.artists.length > 0) {
                trackArtists = s.artists
                  .map((a: any) => (typeof a === 'string' ? a : a.name || ''))
                  .filter((n: string) => n.length > 0);
              }
              if (trackArtists.length === 0 && (s.author || s.artist)) {
                const authorName = typeof s.author === 'string' ? s.author : (s.author?.name || s.artist?.name || s.artist || '');
                trackArtists = [cleanArtistName(authorName, title)];
              }
              const primaryArtist = trackArtists[0] || 'Unknown Artist';
              const albumName = s.album?.name || (typeof s.album === 'string' ? s.album : '');
              const meta = extractYtmItemMetadata(s);
              return {
                videoId: s.id || s.videoId,
                id: s.id || s.videoId,
                title,
                artist: primaryArtist,
                artists: trackArtists.length > 0 ? trackArtists : [primaryArtist],
                album: albumName,
                artworkUrl: s.thumbnails?.[s.thumbnails.length - 1]?.url || s.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${s.id || s.videoId}/hqdefault.jpg`,
                duration: s.duration?.seconds || s.duration || 180,
                provider: 'youtube_music',
                videoType: meta.videoType,
                artistChannelId: meta.artistChannelId,
                albumId: meta.albumId,
              };
            });
            const continuationItems = musicRes?.memo?.get('ContinuationItem');
            nextContinuation = (continuationItems?.[0] as any)?.endpoint?.payload?.token || musicRes?.continuation || null;
          }
        } catch (err) {
          console.warn('[fetchFromInnertube] processing songs failed:', err);
        }
      }

      if (rawTracks.length === 0) {
        try {
          const res = await withTimeout(yt.search(queryOrId, { type: 'video' }), 2000).catch(() => null);
          if (res) {
            const continuationItems = (res as any).memo?.get('ContinuationItem');
            nextContinuation = (continuationItems?.[0] as any)?.endpoint?.payload?.token || (res as any).continuation || null;

            rawTracks = ((res as any).videos || []).slice(0, 30).map((v: any) => {
              const title = v.title?.text || v.title?.toString() || 'Unknown Title';
              const authorName = v.author?.name || v.author?.toString() || '';
              const cleaned = cleanArtistName(authorName, title);
              const meta = extractYtmItemMetadata(v);
              return {
                videoId: v.id,
                id: v.id,
                title,
                artist: cleaned,
                artists: [cleaned],
                artworkUrl: v.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${v.id}/hqdefault.jpg`,
                duration: v.duration?.seconds || 180,
                provider: 'youtube_music',
                videoType: meta.videoType || 'OMV',
                artistChannelId: meta.artistChannelId,
                albumId: meta.albumId,
              };
            });
          }
        } catch (err) {
          console.warn('[fetchFromInnertube] yt.search video fallback failed:', err);
        }
      }

      // 2. Process Albums
      if (albRes) {
        try {
          const items = extractShelfItems(albRes, 'album');
          albums = items.slice(0, 10).map((a: any) => ({
            id: a.id || a.album_id || a.content_id,
            title: a.title?.text || a.title?.toString() || a.name || 'Featured Album',
            subtitle: a.author?.name || a.author?.toString() || a.artists?.[0]?.name || a.year?.text || a.year?.toString() || 'Album',
            year: a.year?.text || a.year?.toString() || a.year || undefined,
            artworkUrl: a.thumbnails?.[0]?.url || a.thumbnail?.contents?.[0]?.url || a.thumbnails?.[a.thumbnails.length - 1]?.url || '',
            type: 'album',
          }));
        } catch {}
      }

      // 3. Process Artists
      if (artRes) {
        try {
          const items = extractShelfItems(artRes, 'artist');
          for (const item of items) {
            const artistId = item.id || item.endpoint?.payload?.browseId;
            const name = item.name || item.title?.text || item.flex_columns?.[0]?.title?.text;
            const subscribers = item.subscribers || item.subtitle?.text || item.flex_columns?.[1]?.title?.text || '';
            const thumbnails = item.thumbnail?.contents || item.thumbnails || [];
            const artworkUrl = thumbnails?.[0]?.url || thumbnails?.[thumbnails.length - 1]?.url || '';
            if (artistId && name) {
              artists.push({
                id: artistId,
                name,
                subscribers,
                thumbnails,
                artworkUrl,
                type: 'artist',
              });
            }
          }
        } catch {}
      }

      // 4. Process Playlists
      if (plRes) {
        try {
          const items = extractShelfItems(plRes, 'playlist');
          playlists = items.slice(0, 10).map((p: any) => ({
            id: p.id || p.playlist_id || p.content_id,
            title: p.title?.text || p.title?.toString() || p.name || 'Featured Playlist',
            subtitle: p.author?.name || p.author?.toString() || p.authors?.[0]?.name || 'Playlist',
            author: p.author?.name || p.author?.toString() || p.authors?.[0]?.name || 'Playlist',
            artworkUrl: p.thumbnails?.[0]?.url || p.thumbnail?.contents?.[0]?.url || p.thumbnails?.[p.thumbnails.length - 1]?.url || '',
            thumbnail: p.thumbnails?.[0]?.url || p.thumbnail?.contents?.[0]?.url || '',
            type: 'playlist',
          }));
        } catch {}
      }

      // 5. Process Videos
      if (vidRes) {
        try {
          const items = extractShelfItems(vidRes, 'video');
          videos = items.slice(0, 10).map((v: any) => {
            let author = v.artists?.[0]?.name || v.author?.name || v.artist?.name || (typeof v.author === 'string' ? v.author : undefined);
            if (!author || author === 'Unknown Artist') {
              const subtitle = v.subtitle?.text || v.flex_columns?.[1]?.title?.text || '';
              if (subtitle.includes('•')) {
                const parts = subtitle.split('•').map((s: string) => s.trim()).filter(Boolean);
                const isDuration = (s: string) => /^\d+:\d{2}(:\d{2})?$/.test(s);
                const isViews = (s: string) => /\d+(\.\d+)?[KMB]?\s*(views|plays)/i.test(s);
                author = parts.find((p: string) => !isDuration(p) && !isViews(p)) ?? parts[0] ?? 'Unknown Artist';
              } else if (subtitle) {
                author = subtitle;
              }
            }
            author = author || 'YouTube Music';
            const vidId = v.id || v.videoId;
            const thumbUrl = v.thumbnails?.[0]?.url || v.thumbnails?.[v.thumbnails.length - 1]?.url || (vidId ? `https://i.ytimg.com/vi/${vidId}/hqdefault.jpg` : '');
            return {
              videoId: vidId,
              id: vidId,
              title: v.title?.text || v.title?.toString() || v.name || 'Unknown Video',
              artist: author,
              artists: [author],
              artworkUrl: thumbUrl,
              thumbnail: thumbUrl,
              duration: v.duration?.seconds || v.duration || 180,
              durationMs: (v.duration?.seconds || v.duration || 180) * 1000,
              provider: 'youtube_music',
            };
          });
        } catch {}
      }

      const seen = new Set<string>();
      const uniqueRawTracks = rawTracks.filter((t: any) => {
        const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      const qualityTracks = uniqueRawTracks
        .map((t: any) => {
          const { contentType, score } = ContentQualityService.classify(t, false, queryOrId);
          return { ...t, contentType, score };
        })
        .filter((t: any) => t.contentType !== 'ai_generated' && t.contentType !== 'creator_long_mix' && t.score > 10);

      let enrichedTracks: any[] = [];
      if (qualityTracks.length > 0) {
        const toEnrich = qualityTracks.slice(0, 10);
        const remaining = qualityTracks.slice(10);
        const enrichedHead = await enrichTracksWithJioSaavn(toEnrich);
        enrichedTracks = [...enrichedHead, ...remaining];
      }

      return {
        tracks: enrichedTracks,
        videos,
        albums,
        artists: artists.slice(0, 10),
        playlists,
        continuation: nextContinuation,
        query: queryOrId,
      };
    } else if (type === 'trending') {
      let rawTracks: any[] = [];
      // 1. Primary: yt.music.getExplore()
      try {
        const explore = await withTimeout((yt.music as any).getExplore(), 4000);
        rawTracks = extractExploreTrending(explore);
      } catch (err: any) {
        console.warn('[fetchFromInnertube] trending explore failed:', err?.message);
      }

      // 2. Fallback to JioSaavn trending if explore returned empty
      if (rawTracks.length === 0) {
        try {
          const jioTracks = await fetchJioSaavnTrending(queryOrId || 'IN');
          if (jioTracks.length > 0) {
            rawTracks = jioTracks.map((t: any) => ({
              videoId: t.id,
              id: t.id,
              title: t.title,
              artist: t.artists?.[0] || t.artist || 'Unknown Artist',
              artists: t.artists || [t.artist || 'Unknown Artist'],
              artworkUrl: t.image?.replace('150x150', '500x500') || t.artworkUrl || '',
              duration: t.duration || 180,
              durationMs: (t.duration || 180) * 1000,
              provider: 'jiosaavn',
            }));
          }
        } catch (err: any) {
          console.warn('[fetchFromInnertube] trending jiosaavn failed:', err?.message);
        }
      }

      const seen = new Set<string>();
      const uniqueRawTracks = rawTracks.filter((t: any) => {
        const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      const qualityTracks = uniqueRawTracks
        .map((t: any) => {
          const { contentType } = ContentQualityService.classify(t, true);
          return { ...t, contentType };
        })
        .filter((t: any) => t.contentType !== 'creator_long_mix');
      if (qualityTracks.length > 0) {
        return { tracks: await enrichTracksWithJioSaavn(qualityTracks) };
      }
      return { tracks: [] };
    } else if (type === 'album') {
      try {
        const alb = await yt.music.getAlbum(queryOrId);
        if (alb && alb.contents && alb.contents.length > 0) {
          const rawTracks = alb.contents.map((t: any, idx: number) => ({
            id: t.id || `alb-${idx}`,
            videoId: t.id,
            title: t.title?.toString() || 'Unknown Track',
            artists: t.artists?.map((a: any) => a.name) || [(alb.header as any)?.author?.name || 'Unknown Artist'],
            album: alb.header?.title?.toString() || 'Album',
            duration: t.duration?.seconds || 180,
            durationMs: (t.duration?.seconds || 180) * 1000,
            artworkUrl: (alb.header as any)?.thumbnails?.[0]?.url || '',
            provider: 'youtube_music',
          }));
          const seen = new Set<string>();
          const uniqueRawTracks = rawTracks.filter((t: any) => {
            const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          });
          const qualityTracks = uniqueRawTracks
            .map((t: any) => {
              const { contentType, score } = ContentQualityService.classify(t, false, queryOrId);
              return { ...t, contentType, score };
            })
            .filter((t: any) => t.contentType !== 'ai_generated' && t.contentType !== 'creator_long_mix' && t.score > 10);
          const tracks = await enrichTracksWithJioSaavn(qualityTracks);
          return {
            id: queryOrId,
            provider: 'youtube_music',
            providerId: queryOrId,
            title: alb.header?.title?.toString() || 'Album Metadata',
            artists: [(alb.header as any)?.author?.name || 'Unknown Artist'],
            artworkUrl: (alb.header as any)?.thumbnails?.[0]?.url || '',
            tracks,
          };
        }
      } catch {}
    } else if (type === 'playlist') {
      try {
        const pl = await yt.getPlaylist(queryOrId);
        if (pl && pl.videos && pl.videos.length > 0) {
          const rawTracks = pl.videos.map((v: any, idx: number) => ({
            id: v.id || `pl-${idx}`,
            videoId: v.id,
            title: v.title?.text || v.title?.toString() || 'Unknown Track',
            artist: v.author?.name || v.author?.toString() || 'Unknown Artist',
            artists: [v.author?.name || v.author?.toString() || 'Unknown Artist'],
            duration: v.duration?.seconds || 180,
            durationMs: (v.duration?.seconds || 180) * 1000,
            artworkUrl: v.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${v.id}/hqdefault.jpg`,
            provider: 'youtube_music',
          }));
          const seen = new Set<string>();
          const uniqueRawTracks = rawTracks.filter((t: any) => {
            const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          });
          const qualityTracks = uniqueRawTracks
            .map((t: any) => {
              const { contentType, score } = ContentQualityService.classify(t, false, queryOrId);
              return { ...t, contentType, score };
            })
            .filter((t: any) => t.contentType !== 'ai_generated' && t.contentType !== 'creator_long_mix' && t.score > 10);
          const tracks = await enrichTracksWithJioSaavn(qualityTracks);
          return {
            id: queryOrId,
            provider: 'youtube_music',
            providerId: queryOrId,
            name: pl.info?.title || 'Playlist Metadata',
            trackCount: tracks.length,
            artworkUrl: pl.info?.thumbnails?.[0]?.url || tracks[0]?.artworkUrl || '',
            tracks,
          };
        }
      } catch {}
    } else if (type === 'playlists') {
      let playlists: any[] = [];

      // 1. Target regional trending playlist search (Bollywood Hindi Hits)
      try {
        const searchQuery = 'Bollywood Hindi Hits';
        const searchRes: any = await withTimeout(
          (yt.music as any).search(searchQuery, { type: 'playlist' }),
          4000
        ).catch(() => null);
        const searchPl = extractShelfItems(searchRes, 'playlist');
        const list = searchPl.length > 0 ? searchPl : (searchRes?.contents?.[0]?.contents || searchRes?.playlists || []);
        if (Array.isArray(list) && list.length > 0) {
          const mapped = list.map((p: any) => ({
            id: p.id || p.playlist_id || p.browseId || p.content_id,
            title: p.title?.text || p.title?.toString() || p.title || 'Community Playlist',
            subtitle: p.subtitle?.text || p.subtitle?.toString() || p.author?.name || 'Playlist',
            artworkUrl: pickBestArtwork(p) || p.thumbnails?.[0]?.url || p.thumbnail?.[0]?.url || '',
            type: 'playlist',
          })).filter((p: any) => Boolean(p.id));
          playlists = [...mapped, ...playlists];
        }
      } catch {}

      // 2. Fallback: getHomeFeed() filtered for shelves with "Community playlists"
      if (playlists.length === 0) {
        try {
          const hf = await withTimeout(yt.music.getHomeFeed(), 4000).catch(() => null);
          const sections = (hf?.sections as any[]) || [];
          for (const s of sections) {
            const headerText = (s.header?.title?.text || s.title?.text || '').toLowerCase();
            const items = s.contents || [];
            if (headerText.includes('community') || headerText.includes('playlist')) {
              for (const p of items) {
                const pid = p.id || p.content_id || p.endpoint?.payload?.browseId || p.endpoint?.payload?.playlistId;
                const ptitle = p.title?.text || p.title?.toString() || p.text?.text || 'Community Playlist';
                const pThumb = pickBestArtwork(p) || p.thumbnails?.[0]?.url || p.thumbnail?.[0]?.url || '';
                if (pid) {
                  playlists.push({
                    id: pid,
                    title: ptitle,
                    subtitle: p.subtitle?.text || p.subtitle?.toString() || p.author?.name || 'Community Playlist',
                    artworkUrl: pThumb,
                    type: 'playlist',
                  });
                }
              }
            }
          }
        } catch (err: any) {
          console.warn('[fetchYtmData] getHomeFeed for playlists failed:', err?.message);
        }
      }

      // Deduplicate playlists by id or title
      const seenPl = new Set<string>();
      const dedupedPlaylists = playlists.filter((p: any) => {
        if (!p.id || seenPl.has(p.id) || seenPl.has(p.title.toLowerCase())) return false;
        seenPl.add(p.id);
        seenPl.add(p.title.toLowerCase());
        return true;
      });

      return { playlists: dedupedPlaylists.slice(0, 20) };
    } else if (type === 'charts') {
      let rawTracks: any[] = [];
      let charts: any[] = [];
      try {
        const exp = await yt.music.getExplore();
          const chartSec = (exp.sections as any[])?.find((s: any) =>
            (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('video') ||
            (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('chart')
          );
          if (chartSec) {
            const expItems = extractShelfItems(chartSec);
            const items = expItems.length > 0 ? expItems : (chartSec.contents || []);
            if (items.length > 0) {
              rawTracks = items.map((item: any) => {
                const authorList: string[] = (() => {
                  if (Array.isArray(item.artists) && item.artists.length > 0) {
                    const names = item.artists.map((a: any) => a?.name).filter(Boolean);
                    if (names.length > 0) return names;
                  }
                  if (Array.isArray(item.authors) && item.authors.length > 0) {
                    const names = item.authors.map((a: any) => a?.name).filter(Boolean);
                    if (names.length > 0) return names;
                  }
                  if (item.author?.name) return [item.author.name];
                  if (typeof item.author === 'string') return [item.author];
                  if (item.artist?.name) return [item.artist.name];
                  return ['Unknown Artist'];
                })();
                const vid = item.id || item.video_id || item.endpoint?.payload?.videoId;
                const bestArt = pickBestArtwork(item) || item.thumbnail?.contents?.[0]?.url || item.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`;
                const meta = extractYtmItemMetadata(item);
                return {
                  videoId: vid,
                  id: vid,
                  title: item.title?.text || item.title?.toString() || 'Unknown Title',
                  artist: authorList[0],
                  artists: authorList,
                  album: null,
                  durationMs: (item.duration?.seconds || 180) * 1000,
                  thumbnail: bestArt,
                  artworkUrl: bestArt,
                  videoType: meta.videoType || 'ATV',
                  artistChannelId: meta.artistChannelId,
                  albumId: meta.albumId,
                };
              });
            }
          }
        } catch {}

      try {
        const exp = await yt.music.getExplore();
        const genreSec = (exp.sections as any[])?.find((s: any) =>
          (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('mood') ||
          (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('genre')
        );
        if (genreSec?.contents?.length > 0) {
          charts = genreSec.contents.slice(0, 20).map((p: any) => ({
            id: p.id || p.endpoint?.payload?.browseId || p.content_id,
            title: p.text?.text || p.title?.text || p.title?.toString() || 'Chart',
            subtitle: 'YouTube Music Chart',
            thumbnail: p.thumbnails?.[0]?.url || '',
            type: 'chart',
          }));
        }
      } catch {}

      if (rawTracks.length > 0) {
        const qualityTracks = rawTracks
          .map((t: any) => {
            const { contentType } = ContentQualityService.classify(t, true);
            return { ...t, contentType };
          })
          .filter((t: any) => t.contentType !== 'creator_long_mix')
          .filter((t: any) => !ContentQualityService.isOlderContent(t));
        const tracks = await enrichTracksWithJioSaavn(qualityTracks.length > 0 ? qualityTracks : rawTracks);
        const filteredTracks = tracks.filter((t: any) => !ContentQualityService.isOlderContent(t));
        return { tracks: filteredTracks.length > 0 ? filteredTracks : tracks, charts };
      }
      return { tracks: [], charts };
    } else if (type === 'new-releases') {
      let rawTrending: any[] = [];
      let releases: any[] = [];

      try {
        const exp = await yt.music.getExplore();
        if (releases.length === 0) {
          const albSec = (exp.sections as any[])?.find((s: any) =>
            (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('album') ||
            (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('release')
          );
          if (albSec) {
            const shelfItems = extractShelfItems(albSec);
            const albItems = shelfItems.length > 0 ? shelfItems : (albSec.contents || []);
            if (albItems.length > 0) {
              releases = albItems.slice(0, 20).map((a: any) => {
                const rawSubtitle = a.subtitle?.text || a.subtitle?.toString() || a.author?.name || a.author?.toString() || a.artists?.[0]?.name || 'Album';
                let releaseType = 'album';
                if (/^EP\b/i.test(rawSubtitle)) releaseType = 'ep';
                else if (/^Single\b/i.test(rawSubtitle)) releaseType = 'single';
                const rawThumb = Array.isArray(a.thumbnail)
                  ? (a.thumbnail[a.thumbnail.length - 1]?.url || a.thumbnail[0]?.url)
                  : (a.thumbnails?.[0]?.url
                      || a.thumbnail?.contents?.[0]?.url
                      || a.thumbnail?.url
                      || a.content_image?.primary_thumbnail?.image?.[0]?.url
                      || '');
                return {
                  id: a.id || a.content_id || a.album_id,
                  title: a.title?.text || a.title?.toString() || 'New Release',
                  subtitle: rawSubtitle,
                  artworkUrl: rawThumb,
                  type: releaseType,
                };
              });
            }
          }
        }
        const videoSec = (exp.sections as any[])?.find((s: any) =>
          (s.header?.title?.text || s.title?.text || '').toLowerCase().includes('video')
        );
        if (videoSec?.contents?.length > 0) {
          rawTrending = [];
          for (const v of videoSec.contents.slice(0, 30)) {
            const authors = (v.authors || v.artists || []).map((a: any) => (typeof a === 'string' ? a : a.name || a.toString())).filter(Boolean);
            const resolvedArtist = authors.length > 0 ? authors.join(', ') : (v.author?.name || null);
            if (!resolvedArtist || resolvedArtist.toLowerCase().includes('unknown')) continue; // drop items without parseable artist
            const vid = v.id || v.video_id || v.endpoint?.payload?.videoId;
            if (!vid) continue;
            const bestArt = pickBestArtwork(v) || v.thumbnail?.contents?.[0]?.url || v.thumbnails?.[0]?.url || `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`;
            const meta = extractYtmItemMetadata(v);
            rawTrending.push({
              videoId: vid,
              id: vid,
              title: v.title?.text || v.title?.toString() || 'New Release Track',
              artist: resolvedArtist,
              artists: authors.length > 0 ? authors : [resolvedArtist],
              album: null,
              durationMs: (v.duration?.seconds || 180) * 1000,
              thumbnail: bestArt,
              artworkUrl: bestArt,
              videoType: meta.videoType || 'ATV',
              artistChannelId: meta.artistChannelId,
              albumId: meta.albumId,
            });
          }
        }
      } catch {}

      const filteredReleases = releases.filter((a: any) => !ContentQualityService.isOlderContent(a));
      const finalReleases = filteredReleases.length > 0 ? filteredReleases : releases;

      if (rawTrending.length > 0) {
        const qualityTrending = rawTrending
          .map((t: any) => {
            const { contentType } = ContentQualityService.classify(t, true);
            return { ...t, contentType };
          })
          .filter((t: any) => t.contentType !== 'creator_long_mix')
          .filter((t: any) => !ContentQualityService.isOlderContent(t));
        const trending = await enrichTracksWithJioSaavn(qualityTrending.length > 0 ? qualityTrending : rawTrending);
        const filteredTrending = trending.filter((t: any) => !ContentQualityService.isOlderContent(t));
        return { releases: finalReleases, trending: filteredTrending.length > 0 ? filteredTrending : trending };
      }
      return { releases: finalReleases, trending: [] };
    }
    return null;
  } catch (err) {
    console.warn(`[fetchFromInnertube] failed for type=${type}:`, err);
    return null;
  }
}

async function fetchFromYtmusicApi(type: string, queryOrId: string = '', filter?: string): Promise<any> {
  try {
    const ytm = await getYtMusicApi();
    if (type === 'search') {
      if (filter === 'playlists') {
        const plList = await ytm.searchPlaylists(queryOrId).catch(() => []);
        const playlists = (plList || []).slice(0, 30).map((p: any) => ({
          id: p.playlistId || p.id,
          title: p.name || p.title || 'Featured Playlist',
          subtitle: p.artist?.name || p.author || 'Playlist',
          artworkUrl: p.thumbnails?.[p.thumbnails.length - 1]?.url || p.thumbnails?.[0]?.url || '',
          type: 'playlist',
        }));
        const filteredPlaylists = playlists.filter((p: any) => !isBadCompilationTitle(p.title || p.name, queryOrId));
        return { playlists: filteredPlaylists, query: queryOrId };
      }

      if (filter === 'albums') {
        const albumsRaw = await ytm.searchAlbums(queryOrId).catch(() => []);
        const albums = (albumsRaw || []).slice(0, 30).map((a: any) => ({
          id: a.albumId || a.id,
          title: a.name || a.title || 'Featured Album',
          subtitle: a.artist?.name || a.author || 'Album',
          artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
          type: 'album',
        }));
        const filteredAlbums = albums.filter((a: any) => !isBadCompilationTitle(a.title || a.name, queryOrId));
        return { albums: filteredAlbums, query: queryOrId };
      }

      if (filter === 'artists') {
        const artistsRaw = await ytm.searchArtists(queryOrId).catch(() => []);
        const artists = (artistsRaw || []).slice(0, 30).map((a: any) => ({
          id: a.artistId || a.id,
          name: a.name,
          subscribers: '',
          thumbnails: a.thumbnails || [],
          artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
          type: 'artist',
        }));
        return { artists, query: queryOrId };
      }

      if (filter === 'videos') {
        const vids = await (ytm as any).searchVideos(queryOrId).catch(() => []);
        const rawTracks = (vids || []).slice(0, 30).map((s: any) => ({
          videoId: s.videoId,
          title: s.name || s.title || 'Unknown Video',
          artist: s.artist?.name || s.author || 'Unknown Artist',
          artists: [s.artist?.name || s.author || 'Unknown Artist'],
          artworkUrl: s.thumbnails?.[s.thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${s.videoId}/hqdefault.jpg`,
          duration: s.duration?.seconds || s.duration || 180,
          provider: 'youtube_music',
        }));
        const qualityTracks = rawTracks
          .map((t: any) => {
            const { contentType } = ContentQualityService.classify(t);
            return { ...t, contentType };
          })
          .filter((t: any) => t.contentType !== 'creator_long_mix');
        return { tracks: qualityTracks, query: queryOrId };
      }

      const songs = await ytm.searchSongs(queryOrId);
      const rawTracks = songs.map((s: any) => {
        const meta = extractYtmItemMetadata(s);
        return {
          videoId: s.videoId,
          title: s.name,
          artist: s.artist?.name || 'Unknown Artist',
          artists: [s.artist?.name || 'Unknown Artist'],
          artworkUrl: s.thumbnails?.[s.thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${s.videoId}/hqdefault.jpg`,
          duration: s.duration || 180,
          provider: 'youtube_music',
          videoType: meta.videoType || (s.type === 'SONG' ? 'ATV' : null),
          artistChannelId: meta.artistChannelId || s.artist?.artistId || null,
          albumId: meta.albumId || s.album?.albumId || null,
        };
      });
      const qualityTracks = rawTracks
        .map((t: any) => {
          const { contentType, score } = ContentQualityService.classify(t, false, queryOrId);
          return { ...t, contentType, score };
        })
        .filter((t: any) => t.contentType !== 'ai_generated' && t.contentType !== 'creator_long_mix' && t.score > 10);
      const albumsRaw = await ytm.searchAlbums(queryOrId).catch(() => []);
      const albums = (albumsRaw || [])
        .slice(0, 10)
        .map((a: any) => ({
          id: a.albumId || a.id,
          title: a.name || 'Album',
          subtitle: a.artist?.name || 'Album',
          artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || '',
          type: 'album',
        }))
        .filter((a: any) => !isBadCompilationTitle(a.title));
      const artistsRaw = await ytm.searchArtists(queryOrId).catch(() => []);
      const artists = (artistsRaw || []).slice(0, 10).map((a: any) => ({
        id: a.artistId || a.id,
        name: a.name,
        subscribers: '',
        thumbnails: a.thumbnails || [],
        artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
        type: 'artist',
      }));
      let playlists: any[] = [];
      try {
        const plRaw = await (ytm as any).searchPlaylists(queryOrId).catch(() => []);
        playlists = (plRaw || []).slice(0, 10).map((p: any) => ({
          id: p.playlistId || p.id,
          title: p.name || p.title || 'Playlist',
          subtitle: p.artist?.name || p.author || 'Playlist',
          artworkUrl: p.thumbnails?.[p.thumbnails.length - 1]?.url || p.thumbnails?.[0]?.url || '',
          type: 'playlist',
        }));
      } catch {}
      let videos: any[] = [];
      try {
        const vidRaw = await (ytm as any).searchVideos(queryOrId).catch(() => []);
        videos = (vidRaw || []).slice(0, 10).map((v: any) => ({
          videoId: v.videoId,
          id: v.videoId,
          title: v.name || v.title || 'Unknown Video',
          artist: v.artist?.name || v.author || 'Unknown Artist',
          artists: [v.artist?.name || v.author || 'Unknown Artist'],
          artworkUrl: v.thumbnails?.[v.thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
          duration: v.duration?.seconds || v.duration || 180,
          durationMs: (v.duration?.seconds || v.duration || 180) * 1000,
          provider: 'youtube_music',
        }));
      } catch {}

      return {
        tracks: await enrichTracksWithJioSaavn(qualityTracks),
        videos,
        albums,
        artists,
        playlists,
        continuation: null,
        query: queryOrId,
      };
    } else if (type === 'trending') {
      return { tracks: [] };
    } else if (type === 'album') {
      const album = await ytm.getAlbum(queryOrId);
      const rawTracks = (album.songs || []).map((s: any, idx: number) => ({
        id: s.videoId || `alb-${idx}`,
        videoId: s.videoId,
        title: s.name,
        artists: [s.artist?.name || album.artist?.name || 'Unknown Artist'],
        album: album.name,
        duration: s.duration || 180,
        durationMs: (s.duration || 180) * 1000,
        artworkUrl: s.thumbnails?.[s.thumbnails.length - 1]?.url || album.thumbnails?.[0]?.url || '',
        provider: 'youtube_music',
      }));
      return {
        id: album.albumId || queryOrId,
        provider: 'youtube_music',
        providerId: album.albumId || queryOrId,
        title: album.name || 'Album Metadata',
        artists: [album.artist?.name || 'Unknown Artist'],
        artworkUrl: album.thumbnails?.[album.thumbnails.length - 1]?.url || '',
        year: album.year,
        tracks: await enrichTracksWithJioSaavn(rawTracks),
      };
    } else if (type === 'playlist') {
      const pl = await ytm.getPlaylist(queryOrId).catch(() => null);
      const vids = await ytm.getPlaylistVideos(queryOrId);
      const rawTracks = vids.map((v: any, idx: number) => ({
        id: v.videoId || `pl-${idx}`,
        videoId: v.videoId,
        title: v.name,
        artist: v.artist?.name || 'Unknown Artist',
        artists: [v.artist?.name || 'Unknown Artist'],
        duration: v.duration || 180,
        durationMs: (v.duration || 180) * 1000,
        artworkUrl: v.thumbnails?.[v.thumbnails.length - 1]?.url || `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`,
        provider: 'youtube_music',
      }));
      const seen = new Set<string>();
      const uniqueRawTracks = rawTracks.filter((t: any) => {
        const key = normalizeTrackDedupeKey(t.title, t.artists || t.artist);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      return {
        id: queryOrId,
        provider: 'youtube_music',
        providerId: queryOrId,
        name: pl?.name || 'Playlist Metadata',
        trackCount: vids.length,
        artworkUrl: pl?.thumbnails?.[pl.thumbnails.length - 1]?.url || vids[0]?.thumbnails?.[0]?.url || '',
        tracks: await enrichTracksWithJioSaavn(uniqueRawTracks),
      };
    } else if (type === 'playlists') {
      return { playlists: [] };
    } else if (type === 'charts') {
      return { tracks: [], charts: [] };
    } else if (type === 'new-releases') {
      return { releases: [], trending: [] };
    }
    return null;
  } catch (err) {
    console.warn(`[fetchFromYtmusicApi] failed for type=${type}:`, err);
    return null;
  }
}

export async function fetchYtmData(type: string, queryOrId: string = '', filter?: string, continuation?: string): Promise<any> {
  if (continuation) {
    return fetchFromInnertube(type, queryOrId, filter, continuation);
  }

  // 1. Parallel race between Innertube and YtMusicApi using Promise.allSettled
  const [innertubeResult, ytmusicResult] = await Promise.allSettled([
    withTimeout(fetchFromInnertube(type, queryOrId, filter, continuation), 4500),
    withTimeout(fetchFromYtmusicApi(type, queryOrId, filter), 3000),
  ]);

  const innertubeVal = innertubeResult.status === 'fulfilled' ? innertubeResult.value : null;
  const ytmusicVal = ytmusicResult.status === 'fulfilled' ? ytmusicResult.value : null;

  let ytmData = (innertubeVal && hasNonEmptyData(innertubeVal))
    ? innertubeVal
    : ((ytmusicVal && hasNonEmptyData(ytmusicVal)) ? ytmusicVal : null);

  // 2. JioSaavn Fallback (last resort for trending, charts, or new-releases if ytmData is empty)
  if (!ytmData || !hasNonEmptyData(ytmData)) {
    if (type === 'charts' || type === 'trending') {
      const jioTracks = await fetchJioSaavnTrending(queryOrId || 'IN');
      const filtered = jioTracks.filter((t: any) => {
        const c = ContentQualityService.classify(t, true);
        return c.contentType !== 'ai_generated' && c.contentType !== 'creator_long_mix' && c.score > 10;
      });
      const sanitized = filtered.map((t: any) => ({
        videoId: t.id,
        id: t.id,
        title: t.title,
        artists: Array.isArray(t.artists) ? t.artists : [t.artist],
        artist: t.artist,
        album: t.album || null,
        durationMs: t.durationMs,
        duration: Math.round(t.durationMs / 1000),
        year: typeof t.year === 'number' ? t.year : parseInt(t.year || '0', 10) || null,
        thumbnail: t.artworkUrl,
        artworkUrl: t.artworkUrl,
        provider: 'jiosaavn',
        videoType: 'ATV' as const,
        artistChannelId: null,
        albumId: null,
        availableForPlayback: true,
      }));
      if (type === 'charts') {
        return { tracks: sanitized, charts: [] };
      } else {
        return { tracks: sanitized };
      }
    } else if (type === 'search' && (!filter || filter === 'songs')) {
      const jioTracks = await fetchJioSaavnSearch(queryOrId);
      const filtered = jioTracks.filter((t: any) => {
        const c = ContentQualityService.classify(t, false, queryOrId);
        return c.contentType !== 'ai_generated' && c.contentType !== 'creator_long_mix' && c.score > 10;
      });
      const sanitized = filtered.map((t: any) => ({
        videoId: t.id,
        id: t.id,
        title: t.title,
        artists: Array.isArray(t.artists) ? t.artists : [t.artist],
        artist: t.artist,
        album: t.album || null,
        durationMs: t.durationMs,
        duration: Math.round(t.durationMs / 1000),
        year: typeof t.year === 'number' ? t.year : parseInt(t.year || '0', 10) || null,
        thumbnail: t.artworkUrl,
        artworkUrl: t.artworkUrl,
        provider: 'youtube_music',
        videoType: 'ATV' as const,
        artistChannelId: null,
        albumId: null,
        availableForPlayback: true,
      }));
      return {
        tracks: sanitized,
        albums: [],
        playlists: [],
        artists: [],
        continuation: null,
        query: queryOrId,
      };
    }
  }

  // Also fallback to JioSaavn if ytmData exists for search but tracks array is empty
  if (type === 'search' && (!filter || filter === 'songs') && (!ytmData?.tracks || ytmData.tracks.length === 0)) {
    const jioTracks = await fetchJioSaavnSearch(queryOrId);
    if (jioTracks.length > 0) {
      const filtered = jioTracks.filter((t: any) => {
        const c = ContentQualityService.classify(t, false, queryOrId);
        return c.contentType !== 'ai_generated' && c.contentType !== 'creator_long_mix' && c.score > 10;
      });
      const sanitized = filtered.map((t: any) => ({
        videoId: t.id,
        id: t.id,
        title: t.title,
        artists: Array.isArray(t.artists) ? t.artists : [t.artist],
        artist: t.artist,
        album: t.album || null,
        durationMs: t.durationMs,
        duration: Math.round(t.durationMs / 1000),
        year: typeof t.year === 'number' ? t.year : parseInt(t.year || '0', 10) || null,
        thumbnail: t.artworkUrl,
        artworkUrl: t.artworkUrl,
        provider: 'youtube_music',
        videoType: 'ATV' as const,
        artistChannelId: null,
        albumId: null,
        availableForPlayback: true,
      }));
      return {
        tracks: sanitized,
        albums: ytmData?.albums || [],
        playlists: ytmData?.playlists || [],
        artists: ytmData?.artists || [],
        continuation: null,
        query: queryOrId,
      };
    }
  }

  if (ytmData) return ytmData;

  // Fallbacks if both fail
  if (type === 'search') return { tracks: [], albums: [], artists: [], continuation: null, query: queryOrId };
  if (type === 'playlists') return { playlists: [] };
  if (type === 'trending') return { tracks: [] };
  if (type === 'album') return { id: queryOrId, provider: 'youtube_music', providerId: queryOrId, title: 'Album Metadata', tracks: [] };
  if (type === 'playlist') return { id: queryOrId, provider: 'youtube_music', providerId: queryOrId, name: 'Playlist Metadata', tracks: [] };
  if (type === 'charts') return { tracks: [], charts: [] };
  if (type === 'new-releases') return { releases: [], trending: [] };
  return null;
}

interface TopCandidate {
  type: 'artist' | 'album' | 'track' | 'playlist' | 'video';
  data: any;
  score: number;
}

function scoreCandidate(candidate: { type: string; data: any }, query: string): number {
  const q = query.trim().toLowerCase();
  const rawTitle = (candidate.data.title || candidate.data.name || '').trim().toLowerCase();
  if (!rawTitle) return 0;

  // Clean title for delimiter-laden track titles (e.g., "Kesariya - Brahmāstra | Pritam...")
  const cleanTitle = rawTitle.split(/[-–—|(\[]/)[0].trim();
  const isExactOrCleanExact = (rawTitle === q || cleanTitle === q);
  
  let score = 0;
  
  // Exact match bonus
  if (isExactOrCleanExact) score += 100;
  // Substring match
  else if (rawTitle.includes(q)) score += 30;
  else if (q.includes(rawTitle)) score += 20;
  
  // Type-specific scoring
  if (candidate.type === 'artist') {
    // If exact query matches artist name, massive boost (users searching artist expect artist)
    if (rawTitle === q) score += 50;
    // Artist with subscriber count or verified badge ONLY if query matches artist
    if ((rawTitle === q || rawTitle.includes(q) || q.includes(rawTitle)) && (candidate.data.subscribers || candidate.data.verified)) {
      score += 40;
    }
  }
  
  if (candidate.type === 'track') {
    // If exact query matches track title, tracks are the most common intent
    if (isExactOrCleanExact) score += 40;
    score += 10; // Baseline track priority
    // Direct audio stream / JioSaavn matched
    if (candidate.data.playbackEngine === 'jiosaavn' || candidate.data.streamUrl) score += 20;
    // Official Art Track boost / UGC penalty
    if (candidate.data.videoType === 'ATV') score += 50;
    if (candidate.data.videoType === 'UGC') score -= 100;
    // Known artist association in track's artist field
    const artists = (candidate.data.artists || []).map((a: any) => 
      (typeof a === 'string' ? a : a.name || '').toLowerCase()
    );
    if (artists.some((a: string) => a.includes(q) || q.includes(a))) score += 15;
  }
  
  if (candidate.type === 'album') {
    // Album penalty if only substring match, or slight de-prioritization vs track unless user typed "album"
    if (rawTitle !== q) score -= 30;
    else if (!q.includes('album')) score -= 20;
  }
  
  if (candidate.type === 'playlist') {
    // Playlist penalty unless user explicitly typed "playlist"
    if (!q.includes('playlist')) score -= 30;
  }
  
  return score;
}

// GET /api/youtube/search?q=&filter=&continuation=
youtubeRoutes.get('/search', async (c) => {
  const query = c.req.query('q') || '';
  const filter = c.req.query('filter');
  const continuation = c.req.query('continuation');
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';

  if ((!query || query.trim().length < 2) && !continuation) {
    return c.json({
      topResult: null,
      tracks: [],
      videos: [],
      albums: [],
      playlists: [],
      artists: [],
      continuation: null,
      continuations: {
        songs: null,
        albums: null,
        artists: null,
        playlists: null,
        videos: null,
      },
      query
    });
  }
  const cleanQ = query.trim();

  const cacheKey = `${CACHE_VERSION}:${cleanQ.toLowerCase()}:${region}:${hl}:${filter || 'all'}`;
  if (!continuation) {
    const cached = searchCache.get(cacheKey);
    if (cached && Date.now() < cached.expiresAt) {
      return c.json(cached.data);
    }
  }

  const data = (await fetchYtmData('search', cleanQ, filter, continuation)) || {};
  const tracks = data.tracks || [];
  let albums = data.albums || [];
  let artists = data.artists || [];
  let videos = data.videos || (filter === 'videos' ? tracks : []);
  let playlists = data.playlists || (filter === 'playlists' ? (data.playlists || []) : []);

  // Validation guard to strip out dummy shelf headers or malformed items
  const isValidItem = (item: any) => {
    if (!item) return false;
    const title = (item.title?.toString() || item.name?.toString() || '').trim();
    if (!title || ['Videos', 'Albums', 'Community playlists', 'Playlists', 'Songs'].includes(title)) return false;
    if (item.artworkUrl && item.artworkUrl.includes('/undefined/')) return false;
    return true;
  };

  videos = videos.filter(isValidItem);
  albums = albums.filter(isValidItem);
  playlists = playlists.filter(isValidItem);
  artists = artists.filter(isValidItem);

  // If "all" search and videos/playlists/albums/artists are missing, fetch concurrently
  if (!filter && !continuation) {
    const missingTasks: Promise<any>[] = [];
    if (artists.length === 0) {
      missingTasks.push(
        fetchYtmData('search', cleanQ, 'artists')
          .then((res: any) => {
            const raw = res?.artists || [];
            artists = raw.filter(isValidItem).slice(0, 10);
          })
          .catch(() => {})
      );
    }
    if (videos.length === 0) {
      missingTasks.push(
        fetchYtmData('search', cleanQ, 'videos')
          .then((res: any) => {
            const raw = res?.tracks || res?.videos || [];
            videos = raw.filter(isValidItem).slice(0, 10);
          })
          .catch(() => {})
      );
    }
    if (playlists.length === 0) {
      missingTasks.push(
        fetchYtmData('search', cleanQ, 'playlists')
          .then((res: any) => {
            const raw = res?.playlists || [];
            playlists = raw.filter(isValidItem).slice(0, 10);
          })
          .catch(() => {})
      );
    }
    if (albums.length === 0) {
      missingTasks.push(
        fetchYtmData('search', cleanQ, 'albums')
          .then((res: any) => {
            const raw = res?.albums || [];
            albums = raw.filter(isValidItem).slice(0, 10);
          })
          .catch(() => {})
      );
    }
    if (missingTasks.length > 0) {
      await Promise.all(missingTasks);
    }
  }

  videos = videos.filter(isValidItem);
  albums = albums.filter(isValidItem);
  playlists = playlists.filter(isValidItem);
  artists = artists.filter(isValidItem);

  // Top Result classification using weighted scoring
  const candidates: TopCandidate[] = [];
  for (const a of artists.slice(0, 3)) candidates.push({ type: 'artist', data: a, score: scoreCandidate({ type: 'artist', data: a }, cleanQ) });
  for (const t of tracks.slice(0, 3)) candidates.push({ type: 'track', data: t, score: scoreCandidate({ type: 'track', data: t }, cleanQ) });
  for (const al of albums.slice(0, 3)) candidates.push({ type: 'album', data: al, score: scoreCandidate({ type: 'album', data: al }, cleanQ) });
  for (const p of playlists.slice(0, 3)) candidates.push({ type: 'playlist', data: p, score: scoreCandidate({ type: 'playlist', data: p }, cleanQ) });

  candidates.sort((a, b) => b.score - a.score);
  const topCandidate = candidates[0];
  const topResult = (topCandidate && topCandidate.score > 0)
    ? { type: topCandidate.type, data: topCandidate.data }
    : (tracks.length > 0 ? { type: 'track', data: tracks[0] } : null);

  const responseData = {
    topResult,
    tracks,
    videos,
    albums,
    artists,
    playlists,
    continuation: data.continuation || null,
    continuations: {
      songs: data.continuation || null,
      albums: null,
      artists: null,
      playlists: null,
      videos: null,
    },
    query: cleanQ
  };

  const hasAnyItems = tracks.length > 0 || albums.length > 0 || artists.length > 0 || videos.length > 0 || playlists.length > 0;
  if (!continuation && hasAnyItems) {
    searchCache.set(cacheKey, {
      data: responseData,
      expiresAt: Date.now() + SEARCH_CACHE_TTL_MS
    });
    searchCacheWrites++;
    if (searchCacheWrites % 50 === 0) {
      pruneSearchCache();
    }
  }

  return c.json(responseData);
});

// GET /api/youtube/trending
let cachedTrending: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/trending', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedTrending.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const data = await fetchYtmData('trending', region);
    if (hasNonEmptyData(data)) {
      cachedTrending.set(cacheKey, { timestamp: now, data });
    }
    return c.json(data);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'trending_fetch_failed', message: err?.message }, 502);
  }
});

// GET /api/youtube/track/:id
youtubeRoutes.get('/track/:id', async (c) => {
  const trackId = c.req.param('id');
  return c.json({
    id: trackId,
    provider: 'youtube_music',
    providerId: trackId,
    title: 'Track Metadata',
    artists: ['Artist'],
    durationMs: 180000,
  });
});

// GET /api/youtube/stream/:videoId
youtubeRoutes.get('/stream/:videoId', async (c) => {
  const videoId = c.req.param('videoId');
  if (!videoId) {
    return c.json({ error: 'missing_video_id' }, 400);
  }

  try {
    const quality = c.req.query('quality');
    const yt = await getInnertube();
    const info = await yt.music.getInfo(videoId).catch(() => yt.getInfo(videoId));
    const format = info.chooseFormat({
      type: 'audio',
      quality: quality === 'low' ? 'worst' : 'best'
    });
    if (!format) {
      return c.json({ error: 'no_audio_format_found' }, 404);
    }
    const streamUrl = await format.decipher(yt.session.player) || format.url;
    if (!streamUrl) {
      return c.json({ error: 'stream_not_found' }, 404);
    }
    return c.json({ streamUrl });
  } catch (err: any) {
    console.error(`[GET /stream/${videoId}] Error:`, err);
    return c.json({ error: 'stream_fetch_failed', message: err?.message }, 502);
  }
});

// GET /api/youtube/album/:id
youtubeRoutes.get('/album/:id', async (c) => {
  const albumId = c.req.param('id');
  const data = await fetchYtmData('album', albumId);
  return c.json(data);
});

// GET /api/youtube/artist/:id
youtubeRoutes.get('/artist/:id', async (c) => {
  const artistId = c.req.param('id');
  try {
    const ytmPromise = getYtMusicApi()
      .then((ytm) => withTimeout(ytm.getArtist(artistId), 4000))
      .catch(() => null);
    const ytPromise = getInnertube()
      .then((yt) => withTimeout((yt.music as any).getArtist(artistId), 4000))
      .catch(() => null);

    const [metaRes, tracksRes] = await Promise.allSettled([ytmPromise, ytPromise]);

    const artistData: any = metaRes.status === 'fulfilled' ? metaRes.value : null;
    const innertubeArtist: any = tracksRes.status === 'fulfilled' ? tracksRes.value : null;

    if (!artistData && !innertubeArtist) {
      return c.json({
        id: artistId,
        provider: 'youtube_music',
        providerId: artistId,
        name: 'Artist',
        thumbnails: [],
        artworkUrl: '',
        bannerUrl: '',
        subscriberCount: null,
        description: null,
        topTracks: [],
        albums: [],
      });
    }

    const header = innertubeArtist?.header;
    const name = artistData?.name || header?.title?.text || header?.title?.toString() || 'Artist';

    let subCount: string | null = null;
    const subLabel = header?.subscription_button?.subscribe_accessibility_label || '';
    if (subLabel) {
      subCount = subLabel
        .replace(/Subscribe to this channel\.\s*/i, '')
        .replace(/Unsubscribe from this channel\.\s*/i, '')
        .trim();
    }
    const description = header?.description?.text || null;

    const thumbnails = (artistData?.thumbnails && artistData.thumbnails.length > 0)
      ? artistData.thumbnails
      : (header?.thumbnail?.contents || []);
    const artworkUrl = thumbnails.length > 0 ? (thumbnails[thumbnails.length - 1]?.url || thumbnails[0]?.url) : '';
    const bannerUrl = thumbnails.length > 1 ? thumbnails[thumbnails.length - 1]?.url : artworkUrl;

    let vlBrowseId: string | null = null;
    if (innertubeArtist?.sections) {
      for (const sec of innertubeArtist.sections) {
        const title = (sec.title?.text || sec.header?.title?.text || '').toLowerCase();
        if (title.includes('song') || title.includes('track')) {
          vlBrowseId = sec.endpoint?.payload?.browseId || null;
          break;
        }
      }
    }
    if (!vlBrowseId && (artistData as any)?.songs?.browseId) {
      vlBrowseId = (artistData as any).songs.browseId;
    }

    let rawTopTracks: any[] = [];
    const yt = await getInnertube();
    if (vlBrowseId) {
      try {
        const pl: any = await withTimeout((yt.music as any).getPlaylist(vlBrowseId), 4000).catch(() => null);
        if (pl && Array.isArray(pl.items) && pl.items.length > 0) {
          rawTopTracks = pl.items.map((t: any, idx: number) => {
            const meta = extractYtmItemMetadata(t);
            const title = t.title?.text || t.title?.toString() || 'Unknown Track';
            const artistsList = t.artists?.map((a: any) => a.name) || [name];
            const primaryArtist = artistsList[0] || name;
            const durSec = t.duration?.seconds || (typeof t.duration === 'number' ? t.duration : 0);
            let albumTitle = t.album?.name || '';
            let extractedAlbumId = meta.albumId || t.album?.id || null;
            if (!albumTitle && Array.isArray(t.flex_columns)) {
              for (const col of t.flex_columns) {
                const runEndpoint = col.title?.runs?.[0]?.endpoint;
                const pageType = runEndpoint?.payload?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;
                if (pageType === 'MUSIC_PAGE_TYPE_ALBUM' || pageType?.includes('ALBUM')) {
                  albumTitle = col.title?.text || col.title?.toString() || '';
                  if (!extractedAlbumId) extractedAlbumId = runEndpoint?.payload?.browseId || null;
                  break;
                }
              }
            }
            const artwork = t.thumbnails?.[t.thumbnails.length - 1]?.url || t.thumbnails?.[0]?.url || (t.thumbnail?.contents?.[0]?.url) || '';

            return {
              id: t.id || t.videoId || `track-${idx}`,
              videoId: t.id || t.videoId,
              title,
              artist: primaryArtist,
              artists: artistsList,
              album: albumTitle,
              duration: durSec || 180,
              durationMs: (durSec || 180) * 1000,
              artworkUrl: artwork,
              provider: 'youtube_music',
              videoType: meta.videoType || null,
              artistChannelId: meta.artistChannelId || artistId,
              albumId: extractedAlbumId,
            };
          });
        }
      } catch (err) {
        console.warn(`[GET /artist/${artistId}] Playlist fetch failed:`, err);
      }
    }

    // Fallback: yt.music.search(name, { type: 'song' })
    if (rawTopTracks.length === 0) {
      try {
        const searchRes: any = await withTimeout((yt.music as any).search(name, { type: 'song' }), 4000).catch(() => null);
        const shelf = searchRes?.contents?.[0]?.contents ? searchRes.contents[0] : searchRes;
        const items = shelf?.contents || [];
        rawTopTracks = items.map((t: any, idx: number) => {
          const meta = extractYtmItemMetadata(t);
          const title = t.title?.text || t.title?.toString() || 'Unknown Track';
          const artistsList = t.artists?.map((a: any) => a.name) || [name];
          const primaryArtist = artistsList[0] || name;
          const durSec = t.duration?.seconds || (typeof t.duration === 'number' ? t.duration : 0);
          return {
            id: t.id || t.videoId || `track-${idx}`,
            videoId: t.id || t.videoId,
            title,
            artist: primaryArtist,
            artists: artistsList,
            album: t.album?.name || '',
            duration: durSec || 180,
            durationMs: (durSec || 180) * 1000,
            artworkUrl: t.thumbnails?.[0]?.url || '',
            provider: 'youtube_music',
            videoType: meta.videoType || null,
            artistChannelId: meta.artistChannelId || artistId,
            albumId: meta.albumId || null,
          };
        });
      } catch (err) {
        console.warn(`[GET /artist/${artistId}] Search fallback failed:`, err);
      }
    }

    // Quality Filter via ContentQualityService: drop AI generated and UGC
    const qualityTracks = rawTopTracks
      .map((t) => {
        const quality = ContentQualityService.classify(t, false, name);
        return { ...t, contentType: quality.contentType, score: quality.score };
      })
      .filter((t) => t.contentType !== 'ai_generated' && t.videoType !== 'UGC');

    let albums: any[] = [];
    if (artistData && Array.isArray(artistData.topAlbums) && artistData.topAlbums.length > 0) {
      albums = artistData.topAlbums.map((a: any, idx: number) => ({
        id: a.albumId || a.playlistId || `album-${idx}`,
        title: a.name || 'Album',
        year: a.year ? String(a.year) : null,
        artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
        type: 'album',
      }));
    } else if (innertubeArtist?.sections) {
      for (const sec of innertubeArtist.sections) {
        const title = (sec.title?.text || sec.header?.title?.text || '').toLowerCase();
        if (title.includes('album') || title.includes('single')) {
          const mappedAlbums = (sec.contents || []).slice(0, 10).map((a: any, idx: number) => {
            const sub = a.subtitle?.text || a.subtitle?.toString() || '';
            let releaseType = 'album';
            if (/^EP\b/i.test(sub) || title.includes('ep')) releaseType = 'ep';
            else if (/^Single\b/i.test(sub) || title.includes('single')) releaseType = 'single';
            return {
              id: a.id || a.album_id || `album-${idx}`,
              title: a.title?.text || a.title?.toString() || 'Album',
              year: a.year?.text || (a.year ? String(a.year) : null),
              artworkUrl: a.thumbnails?.[0]?.url || a.thumbnail?.contents?.[0]?.url || '',
              type: releaseType,
            };
          });
          albums.push(...mappedAlbums);
        }
      }
    }

    let enrichedTopTracks: any[] = qualityTracks;
    if (qualityTracks.length > 0) {
      const head = qualityTracks.slice(0, 5);
      const tail = qualityTracks.slice(5, 50);
      try {
        const enrichedHead = await withTimeout(enrichTracksWithJioSaavn(head, 1500), 2000).catch(() => head);
        enrichedTopTracks = [...enrichedHead, ...tail];
      } catch {
        enrichedTopTracks = [...head, ...tail];
      }
    }

    return c.json({
      id: artistId,
      provider: 'youtube_music',
      providerId: artistId,
      name,
      thumbnails,
      artworkUrl,
      bannerUrl,
      subscriberCount: subCount,
      description,
      topTracks: enrichedTopTracks,
      albums,
    });
  } catch (err: any) {
    console.error(`[GET /artist/${artistId}] Error:`, err);
    return c.json({
      id: artistId,
      provider: 'youtube_music',
      providerId: artistId,
      name: 'Artist',
      thumbnails: [],
      artworkUrl: '',
      bannerUrl: '',
      subscriberCount: null,
      description: null,
      topTracks: [],
      albums: [],
      error: err?.message,
    });
  }
});

// GET /api/youtube/artist/:id/albums
youtubeRoutes.get('/artist/:id/albums', async (c) => {
  const artistId = c.req.param('id');
  try {
    const yt = await getInnertube();
    const innertubeArtist: any = await withTimeout((yt.music as any).getArtist(artistId), 4000).catch(() => null);

    const albums: any[] = [];
    if (innertubeArtist?.sections) {
      for (const sec of innertubeArtist.sections) {
        const title = (sec.title?.text || sec.header?.title?.text || '').toLowerCase();
        if (title.includes('album') || title.includes('single') || title.includes('ep')) {
          const items = sec.contents || [];
          for (const a of items) {
            const sub = a.subtitle?.text || a.subtitle?.toString() || '';
            let releaseType = 'album';
            if (/^EP\b/i.test(sub) || title.includes('ep')) releaseType = 'ep';
            else if (/^Single\b/i.test(sub) || title.includes('single')) releaseType = 'single';
            albums.push({
              id: a.id || a.album_id || a.endpoint?.payload?.browseId || `album-${albums.length}`,
              title: a.title?.text || a.title?.toString() || 'Album',
              name: a.title?.text || a.title?.toString() || 'Album',
              subtitle: sub || (a.year ? String(a.year) : 'Album'),
              year: parseInt(String(a.year?.text || a.year || '')) || null,
              artworkUrl: a.thumbnails?.[0]?.url || a.thumbnail?.[0]?.url || a.thumbnail?.contents?.[0]?.url || '',
              type: releaseType,
            });
          }
        }
      }
    }

    if (albums.length === 0) {
      const ytm = await getYtMusicApi();
      const artistData: any = await withTimeout(ytm.getArtist(artistId), 3000).catch(() => null);
      if (artistData?.topAlbums) {
        for (const a of artistData.topAlbums) {
          albums.push({
            id: a.albumId || a.playlistId || `album-${albums.length}`,
            title: a.name || 'Album',
            name: a.name || 'Album',
            subtitle: a.year ? String(a.year) : 'Album',
            year: a.year ? parseInt(String(a.year)) : null,
            artworkUrl: a.thumbnails?.[a.thumbnails.length - 1]?.url || a.thumbnails?.[0]?.url || '',
            type: 'album',
          });
        }
      }
    }

    return c.json({ albums });
  } catch (err: any) {
    console.error(`[GET /artist/${artistId}/albums] Error:`, err);
    return c.json({ albums: [] });
  }
});

// GET /api/youtube/artist/:id/videos
youtubeRoutes.get('/artist/:id/videos', async (c) => {
  const artistId = c.req.param('id');
  try {
    const yt = await getInnertube();
    const innertubeArtist: any = await withTimeout((yt.music as any).getArtist(artistId), 4000).catch(() => null);
    const header = innertubeArtist?.header;
    const name = header?.title?.text || header?.title?.toString() || 'Artist';

    let rawVideos: any[] = [];
    if (innertubeArtist?.sections) {
      for (const sec of innertubeArtist.sections) {
        const title = (sec.title?.text || sec.header?.title?.text || '').toLowerCase();
        if (title.includes('video') || title.includes('live')) {
          const items = sec.contents || [];
          for (const v of items) {
            const rawTitle = v.title?.text || v.title?.toString() || 'Video';
            const vidId = v.id || v.videoId || v.endpoint?.payload?.videoId || `vid-${rawVideos.length}`;
            const meta = extractYtmItemMetadata(v);
            const durSec = v.duration?.seconds || (typeof v.duration === 'number' ? v.duration : 0);
            const thumb = v.thumbnails?.[v.thumbnails.length - 1]?.url ||
                          v.thumbnails?.[0]?.url ||
                          v.thumbnail?.[0]?.url ||
                          v.thumbnail?.contents?.[0]?.url ||
                          `https://i.ytimg.com/vi/${vidId}/hqdefault.jpg`;
            const channelName = v.authors?.[0]?.name || v.author?.name || name;

            rawVideos.push({
              id: vidId,
              videoId: vidId,
              title: rawTitle,
              name: rawTitle,
              channelName,
              artist: channelName,
              artists: [channelName],
              artworkUrl: thumb,
              thumbnail: thumb,
              duration: durSec || 180,
              durationMs: (durSec || 180) * 1000,
              videoType: meta.videoType || 'OMV',
              provider: 'youtube_music',
            });
          }
        }
      }
    }

    // Fallback if no videos found in sections
    if (rawVideos.length === 0) {
      const searchRes: any = await withTimeout((yt.music as any).search(`${name} official video`, { type: 'video' }), 4000).catch(() => null);
      const shelf = searchRes?.contents?.[0]?.contents ? searchRes.contents[0] : searchRes;
      const items = shelf?.contents || [];
      for (const v of items) {
        const rawTitle = v.title?.text || v.title?.toString() || 'Video';
        const vidId = v.id || v.videoId || v.endpoint?.payload?.videoId || `vid-${rawVideos.length}`;
        const meta = extractYtmItemMetadata(v);
        const durSec = v.duration?.seconds || (typeof v.duration === 'number' ? v.duration : 0);
        const thumb = v.thumbnails?.[v.thumbnails.length - 1]?.url ||
                      v.thumbnails?.[0]?.url ||
                      `https://i.ytimg.com/vi/${vidId}/hqdefault.jpg`;
        const channelName = v.authors?.[0]?.name || v.author?.name || name;

        rawVideos.push({
          id: vidId,
          videoId: vidId,
          title: rawTitle,
          name: rawTitle,
          channelName,
          artist: channelName,
          artists: [channelName],
          artworkUrl: thumb,
          thumbnail: thumb,
          duration: durSec || 180,
          durationMs: (durSec || 180) * 1000,
          videoType: meta.videoType || 'OMV',
          provider: 'youtube_music',
        });
      }
    }

    // Filter out UGC and AI content using ContentQualityService
    const filteredVideos = rawVideos
      .map((v) => {
        const quality = ContentQualityService.classify(v, false, name);
        return { ...v, contentType: quality.contentType, score: quality.score };
      })
      .filter((v) => v.contentType !== 'ai_generated' && v.videoType !== 'UGC');

    return c.json({ videos: filteredVideos });
  } catch (err: any) {
    console.error(`[GET /artist/${artistId}/videos] Error:`, err);
    return c.json({ videos: [] });
  }
});

// GET /api/youtube/artist/:id/playlists
youtubeRoutes.get('/artist/:id/playlists', async (c) => {
  const artistId = c.req.param('id');
  try {
    const yt = await getInnertube();
    const innertubeArtist: any = await withTimeout((yt.music as any).getArtist(artistId), 4000).catch(() => null);
    const header = innertubeArtist?.header;
    const name = header?.title?.text || header?.title?.toString() || 'Artist';

    const playlists: any[] = [];
    if (innertubeArtist?.sections) {
      for (const sec of innertubeArtist.sections) {
        const title = (sec.title?.text || sec.header?.title?.text || '').toLowerCase();
        if (title.includes('playlist') || title.includes('featured')) {
          const items = sec.contents || [];
          for (const p of items) {
            const plId = p.id || p.playlistId || p.endpoint?.payload?.browseId || `pl-${playlists.length}`;
            playlists.push({
              id: plId,
              title: p.title?.text || p.title?.toString() || 'Playlist',
              name: p.title?.text || p.title?.toString() || 'Playlist',
              subtitle: p.subtitle?.text || p.subtitle?.toString() || `Playlist • ${name}`,
              author: name,
              artworkUrl: p.thumbnails?.[0]?.url || p.thumbnail?.[0]?.url || p.thumbnail?.contents?.[0]?.url || '',
              thumbnail: p.thumbnails?.[0]?.url || p.thumbnail?.[0]?.url || p.thumbnail?.contents?.[0]?.url || '',
              type: 'playlist',
            });
          }
        }
      }
    }

    return c.json({ playlists });
  } catch (err: any) {
    console.error(`[GET /artist/${artistId}/playlists] Error:`, err);
    return c.json({ playlists: [] });
  }
});

// GET /api/youtube/playlist/:id
youtubeRoutes.get('/playlist/:id', async (c) => {
  const playlistId = c.req.param('id');
  const data = await fetchYtmData('playlist', playlistId);
  return c.json(data);
});

// GET /api/youtube/related/:id
youtubeRoutes.get('/related/:id', async (c) => {
  const trackId = c.req.param('id');
  return c.json({
    trackId,
    related: [],
  });
});

// ==========================================
// Integrations: YouTube Music OAuth & Import
// 10. GET /api/youtube/charts/global
let cachedGlobalCharts: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/charts/global', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedGlobalCharts.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const data = await fetchYtmData('charts', region);
    if (hasNonEmptyData(data)) {
      cachedGlobalCharts.set(cacheKey, { timestamp: now, data });
    }
    return c.json(data);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'charts_fetch_failed', message: err?.message }, 502);
  }
});

// 12. GET /api/youtube/playlists/global
let cachedGlobalPlaylists: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/playlists/global', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedGlobalPlaylists.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const data = await fetchYtmData('playlists', region);
    if (hasNonEmptyData(data)) {
      cachedGlobalPlaylists.set(cacheKey, { timestamp: now, data });
    }
    return c.json(data);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'playlists_fetch_failed', message: err?.message }, 502);
  }
});

// 11. GET /api/youtube/new-releases/global
let cachedGlobalNewReleases: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/new-releases/global', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedGlobalNewReleases.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const data = await fetchYtmData('new-releases', region);
    if (hasNonEmptyData(data)) {
      cachedGlobalNewReleases.set(cacheKey, { timestamp: now, data });
    }
    return c.json(data);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'new_releases_fetch_failed', message: err?.message }, 502);
  }
});

// 13. GET /api/youtube/albums/global (Dynamic Albums & Singles)
let cachedGlobalAlbums: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/albums/global', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedGlobalAlbums.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const data = await fetchYtmData('new-releases', region);
    const releases = data?.releases || [];
    const payload = { releases };
    if (hasNonEmptyData(payload)) {
      cachedGlobalAlbums.set(cacheKey, { timestamp: now, data: payload });
    }
    return c.json(payload);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'albums_fetch_failed', message: err?.message }, 502);
  }
});

// 14. GET /api/youtube/explore
let cachedExplore: Map<string, { timestamp: number; data: any }> = new Map();
youtubeRoutes.get('/explore', async (c) => {
  const region = c.req.query('region') || 'IN';
  const hl = c.req.query('hl') || 'en';
  const cacheKey = `${CACHE_VERSION}:${region}:${hl}`;
  const now = Date.now();
  const cached = cachedExplore.get(cacheKey);
  if (cached && now - cached.timestamp < 15 * 60 * 1000 && hasNonEmptyData(cached.data)) {
    return c.json(cached.data);
  }

  try {
    const [charts, playlists, newReleases] = await Promise.all([
      fetchYtmData('charts', region),
      fetchYtmData('playlists', region),
      fetchYtmData('new-releases', region),
    ]);
    const payload = {
      charts: charts?.tracks || [],
      playlists: playlists?.playlists || [],
      newReleases: newReleases?.releases || [],
    };
    if (hasNonEmptyData(payload)) {
      cachedExplore.set(cacheKey, { timestamp: now, data: payload });
    }
    return c.json(payload);
  } catch (err: any) {
    if (cached && hasNonEmptyData(cached.data)) return c.json(cached.data);
    return c.json({ error: 'explore_fetch_failed', message: err?.message }, 502);
  }
});

// Background cache warm-up to eliminate cold-start delays
setTimeout(async () => {
  try {
    const [charts, playlists, newReleases] = await Promise.allSettled([
      fetchYtmData('charts', 'IN'),
      fetchYtmData('playlists', 'IN'),
      fetchYtmData('new-releases', 'IN'),
    ]);
    const now = Date.now();
    if (charts.status === 'fulfilled' && charts.value && hasNonEmptyData(charts.value)) {
      cachedGlobalCharts.set(`${CACHE_VERSION}:IN:en`, { timestamp: now, data: charts.value });
    }
    if (playlists.status === 'fulfilled' && playlists.value && hasNonEmptyData(playlists.value)) {
      cachedGlobalPlaylists.set(`${CACHE_VERSION}:IN:en`, { timestamp: now, data: playlists.value });
    }
    if (newReleases.status === 'fulfilled' && newReleases.value && hasNonEmptyData(newReleases.value)) {
      cachedGlobalNewReleases.set(`${CACHE_VERSION}:IN:en`, { timestamp: now, data: newReleases.value });
      const releases = newReleases.value?.releases || [];
      cachedGlobalAlbums.set(`${CACHE_VERSION}:IN:en`, { timestamp: now, data: { releases } });
    }
  } catch (err) {
    console.warn('[youtubeRoutes] Cache warm-up failed:', err);
  }
}, 1000);




