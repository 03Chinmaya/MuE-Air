package com.mue.core.providers

import com.mue.core.model.ProviderId
import com.mue.core.model.Track

object ContentQualityFilter {
    val AI_KEYWORDS_REGEX = Regex(
        "\\b(ai cover|ai generated|ai music|suno|udio|rvc|so-vits|diff-svc|vocaloid ai|ai vocal)\\b",
        RegexOption.IGNORE_CASE
    )
    val COUNTERFEIT_REGEX = Regex(
        "\\b(king -|dhurandhar|ramayana official|fake)\\b|\\bfrom\\s+[\"']?(king|dhurandhar|ramayana)[\"']?|\\b(ramayana|namit malhotra)\\b",
        RegexOption.IGNORE_CASE
    )
    val BLACKLIST_KEYWORDS = listOf(
        "full album", "status video", "karaoke", "interview", "reaction",
        "lyrics video", "audio jukebox", "jukebox", "mashup", "remix",
        "1 hour", "2 hour", "10 hour", "slowed", "reverb", "8d audio",
        "nightcore", "cover", "acoustic", "lofi", "lo-fi", "flip",
        "instrumental", "bass boosted", "tribute", "bootleg", "fan made"
    )

    // Indian regional language codes and names
    val INDIAN_LANGUAGES = setOf(
        "hindi", "punjabi", "tamil", "telugu", "malayalam", "bhojpuri",
        "bengali", "kannada", "marathi", "gujarati", "odia", "oriya",
        "assamese", "rajasthani", "haryanvi", "urdu", "sanskrit", "bodo",
        "santali", "maithili", "konkani", "dogri", "kashmiri"
    )

    // Regex for Indian Indic scripts: Devanagari, Bengali, Gurmukhi, Gujarati, Oriya, Tamil, Telugu, Kannada, Malayalam
    val INDIC_SCRIPT_REGEX = Regex("[\\u0900-\\u0D7F]")

    // Major Indian Music Labels & Channels
    val INDIAN_LABELS_REGEX = Regex(
        "\\b(t-series|tseries|zee music|sony music india|saregama|yrf|yash raj|speed records|white hill|aditya music|lahari music|tips official|tips music|eros now|geet mp3|desi music factory|dmf|anand audio|divo|think music|mango music|saga music|wave music|worldwide records|jjust music|svf|times music|venus|venus worldwide|hungama)\\b",
        RegexOption.IGNORE_CASE
    )

    // Major Indian Artists / Singers / Composers across Bollywood, Punjabi, South Indian, Bhojpuri, Bengali, etc.
    val INDIAN_ARTISTS_REGEX = Regex(
        "\\b(arijit|shreya ghoshal|neha kakkar|badshah|diljit|sidhu moose|karan aujla|ap dhillon|yo yo honey|honey singh|anirudh|rahman|a\\.?\\s*r\\.?\\s*rahman|pritam|vishal mishra|jubin|b praak|jasleen royal|sachin-jigar|sachin jigar|shankar mahadevan|sonu nigam|alka yagnik|kumar sanu|udit narayan|sunidhi|atif aslam|rahat fateh|kk|mohit chauhan|armaan malik|darshan raval|guru randhawa|harrdy sandhu|king|mc stan|divine|raftaar|emiway|seedhe maut|krsna|sid sriram|spb|balasubrahmanyam|chithra|ilaiyaraaja|ilayaraja|devi sri prasad|dsp|thaman|gv prakash|g\\.?\\s*v\\.?\\s*prakash|harris jayaraj|santhosh narayanan|sushin shyam|hesheham|pawan singh|khesari|shilpi raj|manoj tiwari|sapna choudhary|renuka panwar|diler kharkiya|gulzaar chhaniwala|ajay-atul|ajay atul|swapnil bandodkar|kinjal dave|geeta rabari|jignesh kaviraj|asees kaur|tulsi kumar|dhvani bhanushali|stebin ben|amit trivedi|shilpa rao|monali thakur|neeti mohan|mika singh|daler mehndi|sukwinder singh|sukhwinder|hariharan|anuradha paudwal|kavita krishnamurthy|hema sardesai|lucky ali|pankaj udhas|jagjit singh|ghulam ali|bhupen hazarika|shaan|himesh reshammiya|vishal dadlani|shekhar ravjiani|jonita gandhi|shashwat singh|chani nattan|amrit maan|jordan sandhu|parmish verma|ammy virk|nimrat khaira|sunanda sharma|sharry mann|babbu maan|gurdas maan|maninder buttar|jass manak|kulwinder billa|sidhu|arjan dhillon|prem dhillon|shubh|ikky|wazir patar|prabh deep|karan sehmbi|harmanjeet|gurnam bhullar|khesari lal|arvind akela|kallu|pramod premi|ankush raja|gunjan singh|samar singh|shilpi|akshara singh|antra singh|amrapali|ananya bhat|vijay prakash|sanjith hegde|haricharan|chinmayi|shweta mohan|sujatha|vijay yesudas|k\\.?\\s*j\\.?\\s*yesudas|mg sreekumar|madhu balakrishnan|karthik|naresh iyer|benny dayal|pradeep kumar|dhee|sidsriram|sid sriram|anirudh ravichander|keerthi sagathia|osman mir)\\b",
        RegexOption.IGNORE_CASE
    )

    // Indian Music & Culture Keywords in Title/Album
    val INDIAN_KEYWORDS_REGEX = Regex(
        "\\b(bollywood|hindi|punjabi|tamil|telugu|malayalam|bhojpuri|kannada|marathi|gujarati|bengali|bangla|haryanvi|odia|oriya|assamese|rajasthani|sufi|qawwali|ghazal|desi|bhangra|garba|dandiya|bhajan|kirtan|aarti|chalisa|mantra|stotram|raga|coke studio bharat|coke studio india|indie india|i-pop|indian pop|tollywood|kollywood|sandalwood|mollywood|dhol|shehnai|tabla|chutney|lofi hindi|hindi lofi|punjabi lofi|slowed re-verb hindi)\\b",
        RegexOption.IGNORE_CASE
    )

    // Explicit Non-Indian / Western Pop / K-Pop Artists that should never appear in Indian-only Home feed
    val NON_INDIAN_ARTISTS_REGEX = Regex(
        "\\b(taylor swift|drake|bts|blackpink|billie eilish|ariana grande|justin bieber|ed sheeran|the weeknd|dua lipa|post malone|eminem|travis scott|sabrina carpenter|bruno mars|lady gaga|kendrick lamar|kanye west|rihanna|coldplay|olivia rodrigo|harry styles|katy perry|selena gomez|charlie puth|shawn mendes|maroon 5|imagine dragons|beyonc[eé]|adele|cardi b|nicki minaj|doja cat|miley cyrus|sza|camila cabello|sam smith|lana del rey|dua lipa|shakira|pitbull|marshmello|alan walker|chainsmokers|david guetta|calvin harris|avicii|dj snake|ti[eë]sto|skrillex|stray kids|twice|newjeans|seventeen|exo|tomorrow x together|enhypen|aespa|le sserafim|ive)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isIndianOrRegional(track: Track): Boolean {
        if (track.provider == ProviderId.JIOSAAVN) return true

        val title = track.title
        val artist = track.artist
        val album = track.album ?: ""
        val lang = track.language?.lowercase()?.trim()

        if (NON_INDIAN_ARTISTS_REGEX.containsMatchIn(artist) || NON_INDIAN_ARTISTS_REGEX.containsMatchIn(title)) {
            return false
        }

        if (!lang.isNullOrBlank() && INDIAN_LANGUAGES.any { lang.contains(it) }) {
            return true
        }

        if (INDIC_SCRIPT_REGEX.containsMatchIn(title) || INDIC_SCRIPT_REGEX.containsMatchIn(artist) || INDIC_SCRIPT_REGEX.containsMatchIn(album)) {
            return true
        }

        if (INDIAN_LABELS_REGEX.containsMatchIn(artist) || INDIAN_LABELS_REGEX.containsMatchIn(album) || INDIAN_LABELS_REGEX.containsMatchIn(title)) {
            return true
        }

        if (INDIAN_ARTISTS_REGEX.containsMatchIn(artist) || INDIAN_ARTISTS_REGEX.containsMatchIn(title)) {
            return true
        }

        if (INDIAN_KEYWORDS_REGEX.containsMatchIn(title) || INDIAN_KEYWORDS_REGEX.containsMatchIn(album)) {
            return true
        }

        return false
    }

    fun isAI(title: String?, artist: String? = null, album: String? = null): Boolean =
        (!title.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(title)) ||
        (!artist.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(artist)) ||
        (!album.isNullOrBlank() && AI_KEYWORDS_REGEX.containsMatchIn(album))

    fun isCounterfeit(title: String?, artist: String? = null, album: String? = null): Boolean =
        (!title.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(title)) ||
        (!artist.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(artist)) ||
        (!album.isNullOrBlank() && COUNTERFEIT_REGEX.containsMatchIn(album))

    fun isAI(track: Track): Boolean = isAI(track.title, track.artist, track.album)

    fun isCounterfeit(track: Track): Boolean = isCounterfeit(track.title, track.artist, track.album)

    fun isBlacklisted(track: Track): Boolean =
        BLACKLIST_KEYWORDS.any { track.title.lowercase().contains(it) }

    fun shouldReject(track: Track, userQuery: String = ""): Boolean {
        if (isAI(track) || isCounterfeit(track)) return true
        if (track.videoType == "UGC") {
            val explicitQuery = Regex("\\b(cover|remix|live|acoustic|flip)\\b", RegexOption.IGNORE_CASE).containsMatchIn(userQuery)
            if (!explicitQuery) return true
        }
        val qLower = userQuery.lowercase().trim()
        val titleLower = track.title.lowercase()
        val hasDisallowedBlacklist = BLACKLIST_KEYWORDS.any { kw ->
            titleLower.contains(kw) && (qLower.isBlank() || !qLower.contains(kw))
        }
        if (hasDisallowedBlacklist) return true
        return false
    }
}
