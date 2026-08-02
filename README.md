## Delta Chat Android Client — Yggdrasil Fork

This is a **thin fork** of the [Delta Chat](https://delta.chat/) Android client with embedded **Yggdrasil** peer-to-peer IPv6 mesh network support via [yggstack](https://github.com/DrewCyber/yggstack/tree/mobile-bindings-ai).

Features added:
- Embedded Yggdrasil node (auto-starts on app boot)
- Settings UI for connection status, peer management, and port forwarding
- Remote peer discovery with RTT (latency) checking — fetch public Yggdrasil
  peers and measure round-trip time, sorted by responsiveness
- Telegram public channel viewer — add channels by handle (e.g. @durov)
  and view them as virtual chats in the conversation list
- Instant account login with Yggdrasil access from the overflow menu

[<img src="https://delta.chat/assets/badges/get-it-on-gplay.png" alt="Get it on Google Play" height="48">](https://play.google.com/store/apps/details?id=chat.delta)
[<img src="https://delta.chat/assets/badges/get-it-on-fdroid.png" alt="Get it on F-Droid" height="48">](https://f-droid.org/app/com.b44t.messenger)

Other download options and downloads for other platforms can be
found at [get.delta.chat](https://get.delta.chat).

For the core library and other common info, please refer to the
[Chatmail Core Library](https://github.com/chatmail/core).

For general contribution hints, please refer to [CONTRIBUTING.md](./CONTRIBUTING.md).
For building the app, refer to  [BUILDING.md](./BUILDING.md).

<img alt="Screenshot Chat List" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="298" /> <img alt="Screenshot Chat View" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="298" />


# Translations

Android metadata and changelogs are translated using [Weblate](https://hosted.weblate.org/projects/deltachat/android-metadata/).

<a href="https://hosted.weblate.org/engage/deltachat/">
<img src="https://hosted.weblate.org/widget/deltachat/android-metadata/svg-badge.svg" alt="Translation status" />
</a>

App strings and website are translated using [Transifex](https://app.transifex.com/delta-chat/).

# Credits

Many of the user interface classes were based on the Android Signal messenger when we ported it from the former Telegram-UI base in 2019. 
Meanwhile, development has diverged in many areas. 


# License

Licensed GPLv3+, see the LICENSE file for details.

Copyright © Delta Chat contributors.
