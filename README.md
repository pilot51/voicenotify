![header-img]

Voice Notify is an Android app that uses Text-to-Speech (TTS) to announce status bar notification messages so you don't need to look at the screen to know what a notification says.

[![releases-shield]][releases]
[![fdroid-shield]][fdroid]
[![weblate-shield]][weblate]
[![license-shield]](LICENSE)
[![chat-discord-shield]][chat-discord]
[![chat-matrix-shield]][chat-matrix]

<a href="https://play.google.com/store/apps/details?id=com.pilot51.voicenotify" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="100">
</a>


## Features
- Widget and quick settings tile to suspend Voice Notify
- Customizable TTS message
- Replace text to be spoken
- Ignore or enable for individual apps
- Ignore or require notifications containing specified text
- Choice of TTS audio stream
- Choice of speaking when screen or headset is on or off, or while in silent/vibrate mode
- Quiet Time
- Shake-to-silence
- Limit length of spoken message
- Repeat notifications at custom interval while screen off
- Custom delay of TTS after notification
- Most settings can be overridden per-app
- Notification log
- Post a test notification
- Backup and restore settings as zip file
- Light and dark themes (follows system theme)


## Getting started
Voice Notify operates through Android's Notification Listener service and must be enabled in the Notification Access settings. A shortcut to that screen is provided at the top of the main Voice Notify screen.

Some device brands, such as Xiaomi and Samsung among several others, have an additional permission that by default prevents apps like Voice Notify from auto-starting or running in the background. When Voice Notify is opened on a known affected device and the service is not running, a dialog will appear with instructions and in some cases can open directly into the relevant settings screen.

**NOTICE:** F-Droid builds apps from source using a different signing key than the builds posted on Google Play and GitHub, meaning they are **not upgrade-compatible** and you would need to uninstall before installing from a different download source, clearing your settings. This is a security feature in Android. The builds on GitHub are the exact same ones published on Google Play and do not have this issue. However, for technical reasons, Android similarly prevents downgrading installed apps to an earlier version.


## License and copyright
Voice Notify is distributed under the [Apache License](LICENSE). See [NOTICE](NOTICE) for other copyrights and licenses.

In summary, you can do just about anything you want as long as any redistribution includes the proper attributions. Do not mislead anyone into thinking you're the original creator, as I've found a couple people doing for their own profit (one for their portfolio, another selling it on Amazon Appstore).

If you wish to republish the app or any part of it, please make sure you fully understand and respect all licenses and copyrights.


## Community
Join the official community chat on [Discord][chat-discord] or [Matrix][chat-matrix]!

_[Matrix][matrix-site] is an open source alternative to Discord that supports self-hosted servers which can be federated with other servers, much like how email works. The room is on my server at #voicenotify:p51.me and you can join it even if your account is on the matrix.org server._

The Discord channel and Matrix room are bridged so messages posted in one immediately appear in the other, combining both platforms as one chat room.

My greatest struggle with the project is staying motivated to work on it. The best thing you can do to help with that is to provide constructive feedback and participate in the community chat room. The more I'm reminded that users exist, the better!


## Contributing
Contributions are greatly appreciated and help make Voice Notify better for all users!

Guidance is provided below to help you contribute effectively.

### Report bugs and request features
Please use the GitHub issue tracker for all bugs and feature requests, even if you report it via email or in the chat room. This will ensure the issue won't become totally lost and forgotten. Trivial issues like typos may be an exception if I acknowledge it.

Before [opening an issue][issues-new], please search [existing issues][issues] to see if anyone else already reported it.

If you find an issue that you support, please react to it with a :+1:, which helps with prioritization.

### Code
[Pull requests][pulls] should generally stay within the intended scope of the project (speaking notifications) and be consistent with the existing code style.

If unsure whether your change is within scope or you have questions before you're ready to open the PR, you may create a [discussion][discussions], an [issue][issues] for the change you wish to make, or ask on [Discord][chat-discord] / [Matrix][chat-matrix].

If you would like to take Voice Notify in a different direction, feel free to fork it as a separate project.

### Translations
Voice Notify is written in US English and crowd-sourced translations are managed in [Weblate][weblate].

_The previous translation service, Get Localization, was discontinued on May 31, 2019._

This project should not use computer-generated translations without verification from a knowledgeable human translator. Please only contribute translations if you understand both English and the target language, and be aware of possible contextual differences.

As the project evolves, there are regularly new strings that need translated and modified strings that need retranslated. For the benefit of the users, please keep that in mind and check back every now and then, especially if you're the only contributor for a language. While the latest translations are merged into `main` immediately before every release, releases will not be delayed to wait for translations unless notified of imminent contributions.

#### App and store completion:
[![weblate-app-chart]][weblate-app]
[![weblate-store-chart]][weblate-store]

### Donations and funding
While Voice Notify did accept donations between 2011 and 2019, this is no longer the case, largely because Google removed the app from the Play Store for accepting payments through PayPal and Google Wallet (which I chose because they allowed custom amounts) instead of Google Play. It also didn't feel right to continue doing so while I had a solid full-time job and people were contributing to the project who wouldn't see any of the money.

Voice Notify is a passion project funded by the income from my job. It is totally free for everyone to use and I will not allow you to feel guilty for not buying me food or drinks or helping me pay my bills! :smile:

### Top contributors (thank you!)
[![contrib-img]][contrib]


<!-- Internal Links -->
[releases]: https://github.com/pilot51/voicenotify/releases
[discussions]: https://github.com/pilot51/voicenotify/discussions
[issues]: https://github.com/pilot51/voicenotify/issues
[issues-new]: https://github.com/pilot51/voicenotify/issues/new/choose
[pulls]: https://github.com/pilot51/voicenotify/pulls
[contrib]: https://github.com/pilot51/voicenotify/graphs/contributors

<!-- External links -->
[fdroid]: https://f-droid.org/packages/com.pilot51.voicenotify
[weblate]: https://hosted.weblate.org/projects/voice-notify
[weblate-app]: https://hosted.weblate.org/projects/voice-notify/app
[weblate-store]: https://hosted.weblate.org/projects/voice-notify/store
[chat-discord]: https://discord.gg/W6XxGT8WG3
[chat-matrix]: https://matrix.to/#/#voicenotify:p51.me
[matrix-site]: https://matrix.org

<!-- Images -->
[header-img]: https://user-images.githubusercontent.com/38007519/39700038-ef65edba-5225-11e8-8277-a7ed93856c7d.png "Voice Notify - Hear your notifications"
[releases-shield]: https://img.shields.io/github/downloads/pilot51/voicenotify/total.svg "Download from GitHub"
[fdroid-shield]: https://img.shields.io/f-droid/v/com.pilot51.voicenotify "F-Droid"
[weblate-shield]: https://hosted.weblate.org/widget/voice-notify/app/svg-badge.svg "Translate on Weblate"
[license-shield]: https://img.shields.io/github/license/pilot51/voicenotify "License"
[chat-discord-shield]: https://img.shields.io/discord/931700919115079710?label=Discord "Chat on Discord"
[chat-matrix-shield]: https://img.shields.io/matrix/voicenotify:p51.me?server_fqdn=matrix.p51.me&label=Matrix "Chat on Matrix"
[weblate-app-chart]: https://hosted.weblate.org/widget/voice-notify/app/multi-auto.svg "Translation status - App"
[weblate-store-chart]: https://hosted.weblate.org/widget/voice-notify/store/multi-auto.svg "Translation status - Store"
[contrib-img]: https://contrib.rocks/image?repo=pilot51/voicenotify

