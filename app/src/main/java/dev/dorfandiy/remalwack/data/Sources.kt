package dev.dorfandiy.remalwack.data

enum class Profile(val id: String, val title: String, val description: String) {
    LITE("lite", "Lite", "Ads, malware and trackers. Smallest list, least chance of breaking apps."),
    BALANCED("balanced", "Balanced", "Broader coverage with a good balance between blocking and breakage."),
    AGGRESSIVE("aggressive", "Aggressive", "Maximum blocking incl. threat-intelligence feeds. May break some sites."),
    CUSTOM("custom", "Custom", "Only your own sources (plus the categories you enable).");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

data class Source(val id: String, val name: String, val url: String)

enum class Category(val id: String, val title: String, val description: String, val source: Source) {
    PORN("porn", "Adult content", "Pornography sites",
        Source("cat_porn", "StevenBlack porn", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")),
    GAMBLING("gambling", "Gambling", "Betting and casino sites",
        Source("cat_gambling", "StevenBlack gambling", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling/hosts")),
    FAKENEWS("fakenews", "Fake news", "Known disinformation sites",
        Source("cat_fakenews", "StevenBlack fakenews", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts")),
    SOCIAL("social", "Social media", "Facebook, Twitter/X, TikTok and similar",
        Source("cat_social", "StevenBlack social", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts")),
}

object Sources {
    private val lite = Source("lite", "StevenBlack unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
    private val balanced = Source("balanced", "HaGeZi Multi PRO", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt")
    private val aggressive = Source("aggressive", "HaGeZi Multi PRO++", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.plus.txt")
    private val tif = Source("tif", "HaGeZi Threat Intelligence", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/tif.txt")

    fun forProfile(p: Profile): List<Source> = when (p) {
        Profile.LITE -> listOf(lite)
        Profile.BALANCED -> listOf(balanced)
        Profile.AGGRESSIVE -> listOf(aggressive, tif)
        Profile.CUSTOM -> emptyList()
    }

    fun custom(urls: Collection<String>): List<Source> =
        urls.mapIndexed { i, u -> Source("custom_$i", u.substringAfter("://").substringBefore('/'), u) }
}
