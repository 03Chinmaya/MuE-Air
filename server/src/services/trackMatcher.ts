/**
 * TrackMatcher
 * Resolves cross-provider track references (e.g. YouTube / Local to JioSaavn).
 * Conforms to docs/00_STACK_LOCK.md and Phase 11C.5 specification.
 * Shared implementation for both Server jobs and Client callers.
 */

import { decryptJioSaavnMediaUrl } from './jiosaavnAudio.js';

export interface TrackMatchInput {
  title: string;
  artist?: string;
  artists?: string[];
  provider?: string;
  providerId?: string;
  id?: string;
  [key: string]: any;
}

export interface TrackMatchResult {
  matchedProviderId: string;
  track?: any;
}

export type TrackSearchProviderFn = (query: string, limit?: number) => Promise<any[]>;

let customSearchProvider: TrackSearchProviderFn | null = null;

export function cleanTrackTitle(title: string): string {
  if (!title) return '';
  return title
    .replace(/\s*[\(\[](?:official\s*(?:video|audio|music\s*video|lyric\s*video)?|lyrics?|audio|visualizer|hd|4k|remastered|full\s*song|video|lyric|prod\.?|feat\.?|ft\.?)[^\)\]]*[\)\]]/gi, '')
    .replace(/\s*-\s*remaster(?:ed)?(?:\s*\d+)?/gi, '')
    .replace(/[\(\)\[\]\{\}]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export class TrackMatcher {
  /**
   * Sets an optional search provider (used by mobile client to route through JioSaavnApiClient and preserve test spies).
   */
  static setSearchSongsProvider(provider: TrackSearchProviderFn | null): void {
    customSearchProvider = provider;
  }

  /**
   * Matches a track across providers to JioSaavn for playback.
   */
  static async matchTrack(
    track: TrackMatchInput,
    targetProvider: 'jiosaavn' | 'youtube' = 'jiosaavn'
  ): Promise<TrackMatchResult | null> {
    const anyTrack = track as any;

    if (track.provider === targetProvider) {
      const pid = anyTrack.providerId || anyTrack.id || '';
      return { matchedProviderId: pid, track: anyTrack };
    }

    const artistName =
      anyTrack.artist ||
      (Array.isArray(anyTrack.artists) && anyTrack.artists.length > 0 ? anyTrack.artists[0] : '');

    const executeSearch = async (q: string): Promise<any | null> => {
      if (!q || q.trim().length < 2) return null;
      const trimmed = q.trim();

      // 1. Client-side provider if available
      if (customSearchProvider) {
        try {
          const matches = await customSearchProvider(trimmed, 5);
          if (matches && matches.length > 0) {
            return matches[0];
          }
        } catch {
          // Fallback
        }
        return null;
      }

      // 2. Server-side fetch fallback
      try {
        const targetUrl = `https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=5&p=1&q=${encodeURIComponent(
          trimmed
        )}`;

        const ctrl = new AbortController();
        const timeout = setTimeout(() => ctrl.abort(), 6000);

        const res = await fetch(targetUrl, {
          signal: ctrl.signal,
          headers: {
            'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            Accept: 'application/json, text/plain, */*',
            Referer: 'https://www.jiosaavn.com/',
          },
        });
        clearTimeout(timeout);

        const text = await res.text();
        const cleanJson = text.trim().replace(/^[^{[]*/, '').replace(/[^}\]]*$/, '');
        if (res.ok && cleanJson.length > 2) {
          const data = JSON.parse(cleanJson);
          const results = data.results || [];
          if (results.length > 0) {
            return results[0];
          }
        }
      } catch {
        // Fallback
      }

      return null;
    };

    const cleanTitle = cleanTrackTitle(track.title || '');
    const cleanArtist = (artistName || '').trim();

    // Pass 1: Clean Title + Clean Artist
    let top = await executeSearch(`${cleanTitle} ${cleanArtist}`.trim());

    // Pass 2: Clean Title only (if 0 results and title is distinctive)
    if (!top && cleanTitle && cleanTitle.length > 2 && cleanArtist) {
      top = await executeSearch(cleanTitle);
    }

    // Pass 3: Raw Title with brackets stripped
    if (!top) {
      const strippedRaw = (track.title || '').replace(/[\(\)\[\]\{\}]/g, ' ').replace(/\s+/g, ' ').trim();
      if (strippedRaw && strippedRaw !== cleanTitle) {
        top = await executeSearch(strippedRaw);
      }
    }

    if (top) {
      const encUrl = top.more_info?.encrypted_media_url || top.encrypted_media_url;
      const cdnUri = decryptJioSaavnMediaUrl(encUrl);
      const rawYear = top.year || top.more_info?.year;
      const mappedTrack = {
        id: top.id,
        title: (top.title || top.song || '').replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&amp;/g, '&'),
        artist: (top.primary_artists || top.singers || top.artist || '').replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&amp;/g, '&'),
        artists: (top.primary_artists || top.singers || top.artist || '')
          .split(',')
          .map((a: string) => a.trim().replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&amp;/g, '&'))
          .filter(Boolean),
        album: (top.album || '').replace(/&quot;/g, '"').replace(/&#039;/g, "'").replace(/&amp;/g, '&'),
        year: rawYear ? parseInt(rawYear, 10) : undefined,
        durationMs: parseInt(top.more_info?.duration || top.duration || '0', 10) * 1000,
        artworkUrl: top.image ? top.image.replace('150x150', '500x500') : undefined,
        provider: 'jiosaavn' as const,
        providerId: top.id,
        encrypted_media_url: encUrl,
        uri: cdnUri || undefined,
      };
      return {
        matchedProviderId: top.id,
        track: mappedTrack,
      };
    }

    return null;
  }

  /**
   * Resolves a track to JioSaavn for playback.
   * If already on jiosaavn, returns the track directly.
   * If cross-provider (e.g. YouTube), matches against JioSaavn CDN catalog.
   */
  static async resolveToJioSaavn(track: any): Promise<any | null> {
    if (track.provider === 'jiosaavn') {
      return track;
    }
    const result = await this.matchTrack(track, 'jiosaavn');
    return result?.track ?? null;
  }
}

/**
 * Enriches a collection of tracks with pre-matched JioSaavn 320kbps CDN stream URLs.
 * Enables <30ms instant playback on the client with zero runtime matching delay.
 */
export async function enrichTracksWithJioSaavn(tracks: any[], timeoutMs: number = 1500): Promise<any[]> {
  if (!Array.isArray(tracks) || tracks.length === 0) return [];

  const ENRICH_TIMEOUT_MS = timeoutMs;

  const enriched = await Promise.all(
    tracks.map(async (t) => {
      try {
        const matchPromise = TrackMatcher.matchTrack(
          {
            title: t.title || t.name,
            artist: t.artist || (Array.isArray(t.artists) ? t.artists[0] : ''),
            artists: Array.isArray(t.artists) ? t.artists : (t.artist ? [t.artist] : []),
            provider: t.provider || 'youtube_music',
            providerId: t.videoId || t.providerId || t.id,
            id: t.id || t.videoId,
            duration: t.duration,
            durationMs: t.durationMs,
            more_info: t.more_info,
            encrypted_media_url: t.encrypted_media_url,
          },
          'jiosaavn'
        );

        const timeoutPromise = new Promise((_, rej) =>
          setTimeout(() => rej(new Error('enrich_timeout')), ENRICH_TIMEOUT_MS)
        );

        const match = (await Promise.race([matchPromise, timeoutPromise])) as any;

        if (match && match.matchedProviderId) {
          const encUrl =
            (match.track as any)?.encrypted_media_url ||
            (match.track as any)?.more_info?.encrypted_media_url ||
            t.more_info?.encrypted_media_url ||
            t.encrypted_media_url;

          const cdnUri = (match.track as any)?.uri || decryptJioSaavnMediaUrl(encUrl);

          const isGenericOrLabel = (name?: string) => {
            if (!name) return true;
            const n = name.toLowerCase().trim();
            return (
              n === 'unknown artist' ||
              n === 'various artists' ||
              n.includes('t-series') ||
              n.includes('sony music') ||
              n.includes('zee music') ||
              n.includes('speed records') ||
              n.includes('yrf') ||
              n.includes('tips official') ||
              n.includes('saregama') ||
              n.includes('aditya music')
            );
          };

          const matchedArtist = match.track?.artist || (match.track?.artists ? match.track.artists[0] : null);
          const resolvedArtist = (!isGenericOrLabel(t.artist) && t.artist) ? t.artist : (matchedArtist || t.artist || 'Unknown Artist');
          const resolvedArtists = (!isGenericOrLabel(t.artist) && Array.isArray(t.artists) && t.artists.length > 0)
            ? t.artists
            : (match.track?.artists || [resolvedArtist]);

          const jioSquareArt = match.track?.artworkUrl || (match.track as any)?.image?.replace('150x150', '500x500');
          const bestArt = jioSquareArt || t.artworkUrl || t.thumbnail;

          return {
            ...t,
            id: t.id || t.videoId || match.matchedProviderId,
            videoId: t.videoId || t.id,
            title: t.title || match.track?.title,
            artist: resolvedArtist,
            artists: resolvedArtists,
            album: t.album || match.track?.album || null,
            year: parseInt(String(t.year ?? (match.track as any)?.year ?? ''), 10) || null,
            duration: t.duration || (match.track?.durationMs ? Math.round(match.track.durationMs / 1000) : 180),
            durationMs: t.durationMs || match.track?.durationMs || (t.duration ? t.duration * 1000 : 180000),
            artworkUrl: bestArt,
            thumbnail: bestArt,
            providerId: match.matchedProviderId,
            jiosaavnTrackId: match.matchedProviderId,
            uri: cdnUri || undefined,
            availableForPlayback: Boolean(cdnUri),
          };
        }

        return {
          ...t,
          availableForPlayback: false,
        };
      } catch {
        return {
          ...t,
          availableForPlayback: false,
        };
      }
    })
  );

  return enriched;
}

