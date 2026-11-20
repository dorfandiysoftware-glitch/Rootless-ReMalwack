# Rootless Re-Malwack

No-root ad / malware / tracker blocker for Android, inspired by [Re-Malwack](https://github.com/ZG089/Re-Malwack).
It runs a local DNS-only VPN and answers blocked domains with 0.0.0.0, using hosts lists (profiles, categories, custom sources, whitelist/blacklist).

License: GPL-3.0.

## 0.3.0

- **No-VPN mode**: system Private DNS (AdGuard, AdGuard Family, Mullvad, Quad9 or a custom host). Does not use the on-device lists.
- **Uninterrupted VPN**: foreground service, auto-restart with backoff, DNS failover, 15-minute watchdog, optional Always-on VPN.
- **uBlock Origin compatibility**: the domains uBO needs for its filter lists are never blocked; per-app VPN exclusion. IronFox: set DNS over HTTPS to "Default Protection".
- Material 3 Expressive with a red/dark theme.
