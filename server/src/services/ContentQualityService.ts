export type ContentType =
  | 'authentic_core'
  | 'authentic_long'
  | 'creator_long_mix'
  | 'creator_upload'
  | 'ai_generated'
  | 'official_art_track'
  | 'official_music_video'
  | 'rejected';

export class ContentQualityService {
  static readonly aiGeneratorRegex =
    /\b(ai cover|ai generated|ai music|suno|udio|diff-svc|so-vits|rvc|vocaloid ai|ai vocal)\b/i;

  static readonly counterfeitOrSpamRegexes = [
    /\bfrom\s+["']?(king|dhurandhar|ramayana)["']?/i,
    /\b(ramayana|namit malhotra)\b/i,
    /\bking\s*-\s*/i,
    /\bdhurandhar\b/i,
  ];

  static readonly compilationKeywords = [
    'mix', 'remix', 'mashup', '1 hour', 'lo-fi', 'lofi', 'flip', 'slowed', 'reverb',
    'cover', 'acoustic', 'instrumental', 'karaoke', 'bass boosted',
    'compilation', 'jukebox', 'nonstop', 'podcast', 'top 100', 'top hits', 'top 50',
    'billboard', 'best songs', 'best of', 'playlist', 'collection', 'full album',
    'all songs', 'audio jukebox', 'video jukebox'
  ];

  static readonly compilationRegexes = [
    /\bhits\b/i,
    /\bbest\b.*\b(songs|hits|tracks|music|albums)\b/i,
    /\b(19|20)\d{2}\b.*\b(hits|songs|tracks|music|albums)\b/i,
    /\bvarious artists\b/i,
    /\bdance hits\b/i,
    /\bpop hits\b/i,
    /\b(19|20)\d{2}\b.*\bsongs\b/i, // Catches "2000s music songs"
    /\b(best|top)\b.*\bsongs\b/i  // Catches "Best Pop Songs"
  ];

  static isCompilationTitle(title: string, query = ''): boolean {
    const lower = (title || '').toLowerCase();
    const qLower = (query || '').toLowerCase().trim();
    const explicitExemptKeywords = ['cover', 'remix', 'acoustic', 'lofi', 'lo-fi', 'slowed', 'reverb', 'mashup', 'karaoke', 'instrumental'];
    if (explicitExemptKeywords.some((kw) => qLower.includes(kw) && lower.includes(kw))) {
      return false;
    }
    // Check standard keywords unless query explicitly requested them
    if (this.compilationKeywords.some((w) => lower.includes(w) && !qLower.includes(w))) return true;
    // Check regexes
    return this.compilationRegexes.some((regex) => regex.test(lower));
  }

  static isOlderContent(item: any): boolean {
    if (!item) return false;
    const currentYear = new Date().getFullYear();
    const oldestAllowed = currentYear - 1;
    if (item.year) {
      const y = typeof item.year === 'number' ? item.year : parseInt(item.year, 10);
      if (!isNaN(y) && y < oldestAllowed) return true;
    }
    const title = item.title || item.name || '';
    if (/\b(19\d{2}|200\d|201\d)\b/.test(title)) return true;
    if (item.subtitle && /\b(19\d{2}|200\d|201\d)\b/.test(item.subtitle)) return true;
    return false;
  }

  static classify(
    track: any,
    isFromChartOrTrending = false,
    query = ''
  ): { contentType: ContentType; score: number; duration: number } {
    if (!track || !track.title || !track.title.toString().trim()) {
      return { contentType: 'rejected', score: 0, duration: 0 };
    }

    const titleLower = (track.title || '').toString().toLowerCase();
    const artistLower = (track.artist || (Array.isArray(track.artists) ? track.artists.join(' ') : track.artists) || '').toString().toLowerCase();
    const userQueryLower = query.toLowerCase().trim();
    const duration = track.duration?.seconds || (typeof track.duration === 'number' ? track.duration : 0) || (track.durationMs ? Math.round(track.durationMs / 1000) : 180);

    // Duration bounds (< 30s snippet rejection)
    if (duration < 30) {
      return { contentType: 'rejected', score: 0, duration };
    }

    // 1. AI spam rejection (always reject)
    if (this.aiGeneratorRegex.test(titleLower) || this.aiGeneratorRegex.test(artistLower)) {
      return { contentType: 'ai_generated', score: 0, duration };
    }
    const albumLower = (track.album || '').toLowerCase();
    if (this.counterfeitOrSpamRegexes.some((rx) => rx.test(titleLower) || rx.test(albumLower))) {
      return { contentType: 'ai_generated', score: 0, duration };
    }

    // 2. Explicit intent detection
    const explicitExemptKeywords = ['cover', 'remix', 'acoustic', 'lofi', 'lo-fi', 'slowed', 'reverb', 'mashup', 'karaoke', 'instrumental', 'live', 'flip', 'edit'];
    const isExplicitSearch = explicitExemptKeywords.some((kw) => userQueryLower.includes(kw));

    // 3. UGC rejection unless user explicitly searched for cover/remix/live
    if (track.videoType === 'UGC' && !isExplicitSearch && !isFromChartOrTrending) {
      return { contentType: 'creator_upload', score: 10, duration };
    }

    // 4. Compilation Rejection (Even if official), unless originating from verified chart / explore / trending or explicit search
    const isCompilation = (isFromChartOrTrending || isExplicitSearch) ? false : this.isCompilationTitle(titleLower, query);
    if ((isCompilation || duration > 900) && !isExplicitSearch) {
      return { contentType: 'creator_long_mix', score: 0, duration };
    }

    // 5. Official release boost
    if (track.videoType === 'ATV') {
      return { contentType: 'official_art_track', score: 100, duration };
    }
    if (track.videoType === 'OMV') {
      return { contentType: 'official_music_video', score: 80, duration };
    }

    // 6. Routing by duration
    if (duration >= 600 && duration <= 900) {
      return { contentType: 'authentic_long', score: 100, duration }; // 10-15 mins
    }

    return { contentType: 'authentic_core', score: 100, duration }; // < 10 mins
  }
}
