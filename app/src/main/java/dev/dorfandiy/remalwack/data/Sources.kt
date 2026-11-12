package dev.dorfandiy.remalwack.data

/** Same profiles as the original Re-Malwack module (module/profiles/*.txt). */
enum class Profile(val id: String, val title: String, val description: String) {
    DEFAULT("default", "Default", "Re-Malwack's OG default hosts sources list"),
    LITE("lite", "Lite", "Light protection for devices with low resources."),
    BALANCED("balanced", "Balanced", "Simple Castle-grade protection for everyday use."),
    AGGRESSIVE("aggressive", "Aggressive", "Hardcore Fortress-level protection, may block some legitimate services."),
    CUSTOM("custom", "Custom", "Only your own sources (plus the presets you enable).");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class Source(val id: String, val name: String, val url: String)

private const val SB = "https://raw.githubusercontent.com/StevenBlack/hosts/master"
private const val HZ = "https://hagezi-mirror.dnsbunker.org/hosts"
private const val RAY = "https://raw.githubusercontent.com/r-a-y/mobile-hosts/refs/heads/master"

/** Preset blocking sources (same as the WebUI's "Preset Blocking Sources"). */
enum class Category(val id: String, val title: String, val sources: List<Source>) {
    PORN("porn", "Block pornography sites", listOf(
        Source("cat_porn1", "StevenBlack porn", "$SB/alternates/porn-only/hosts"),
        Source("cat_porn2", "Anti-Porn HOSTS", "https://raw.githubusercontent.com/4skinSkywalker/Anti-Porn-HOSTS-File/refs/heads/master/HOSTS.txt"))),
    GAMBLING("gambling", "Block gambling sites", listOf(
        Source("cat_gambling1", "StevenBlack gambling", "$SB/alternates/gambling-only/hosts"),
        Source("cat_gambling2", "BlocklistProject gambling", "https://blocklistproject.github.io/Lists/gambling.txt"))),
    FAKENEWS("fakenews", "Block fake news sites", listOf(
        Source("cat_fakenews", "StevenBlack fakenews", "$SB/alternates/fakenews-only/hosts"))),
    SOCIAL("social", "Block social media sites", listOf(
        Source("cat_social", "StevenBlack social", "$SB/alternates/social-only/hosts"))),
    TRACKERS("trackers", "Block trackers sites", listOf(
        Source("cat_trackers1", "AdGuard Tracking", "$RAY/AdguardTracking.txt"),
        Source("cat_trackers2", "BlocklistProject tracking", "https://blocklistproject.github.io/Lists/tracking.txt"))),
    SAFEBROWSING("safebrowsing", "Enable Safebrowsing", listOf(
        Source("cat_safebrowsing", "Re-Malwack safebrowsing", "https://raw.githubusercontent.com/Re-Malwack/hosts/refs/heads/main/blocklists/safebrowsing.txt")));
}

object Sources {
    private val sb = Source("sb", "StevenBlack Unified", "$SB/hosts")
    private val hzLight = Source("hz_light", "HaGeZi Light", "$HZ/light.txt")
    private val hzMulti = Source("hz_multi", "HaGeZi Multi", "$HZ/multi.txt")
    private val hzPro = Source("hz_pro", "HaGeZi Pro", "$HZ/pro.txt")
    private val hzProPlus = Source("hz_proplus", "HaGeZi Pro++", "$HZ/pro.plus.txt")
    private val oneHosts = Source("1hosts", "1Hosts Lite", "https://badmojr.github.io/1Hosts/Lite/hosts.txt")
    private val agDns = Source("ag_dns", "AdGuard DNS", "$RAY/AdguardDNS.txt")
    private val agAds = Source("ag_ads", "AdGuard Mobile Ads", "$RAY/AdguardMobileAds.txt")
    private val agSpy = Source("ag_spy", "AdGuard Mobile Spyware", "$RAY/AdguardMobileSpyware.txt")
    private val rem = Source("rem01", "Rem01Gaming AdBlock", "https://hosts.rem01gaming.dev/adblock")
    private val blpAds = Source("blp_ads", "BlocklistProject Ads", "https://blocklistproject.github.io/Lists/ads.txt")
    private val blpRedirect = Source("blp_redirect", "BlocklistProject Redirects", "https://blocklistproject.github.io/Lists/redirect.txt")

    fun forProfile(p: Profile): List<Source> = when (p) {
        Profile.DEFAULT -> listOf(sb, hzPro, oneHosts, agDns, agAds, agSpy, rem, blpAds)
        Profile.LITE -> listOf(sb, hzLight, agAds, agSpy)
        Profile.BALANCED -> listOf(sb, hzMulti, oneHosts, agDns, agAds, agSpy, rem)
        Profile.AGGRESSIVE -> listOf(sb, hzProPlus, oneHosts, agDns, agAds, agSpy, rem, blpAds, blpRedirect)
        Profile.CUSTOM -> emptyList()
    }

    fun custom(urls: Collection<String>): List<Source> =
        urls.mapIndexed { i, u -> Source("custom_$i", u.substringAfter("://").substringBefore('/'), u) }
}
