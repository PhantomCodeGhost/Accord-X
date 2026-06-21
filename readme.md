# Accord

> **This fork is no longer maintained.**
>
> Originally I assumed [AccordLegacy](https://github.com/FoedusProgramme/AccordLegacy) was a dead project, which is why I started this fork. That turned out to be wrong — FoedusProgramme is actively working on the next major version, **Accord 2.0**, with builds distributed through the Telegram chat **[@FoedusDiscussion](https://t.me/FoedusDiscussion)** rather than GitHub Releases.
>
> Meanwhile, [Gramophone](https://github.com/AkaneTan/Gramophone) has moved on substantially during the period when Accord appeared dormant — its internals (player, lyrics, theming, build setup) have diverged enough that backporting changes between the two no longer makes practical sense. The right path forward is to wait for the next Accord release rather than patch this fork.
>
> For the latest official Accord, join that chat. This repository is left up only for historical reference and will not receive further updates.

---

A local music player for Android with an Apple-inspired design. Supports synced lyrics (LRC/SRT), gapless playback, and third-party equalizers.

Fork of [Gramophone](https://github.com/AkaneTan/Gramophone) / [AccordLegacy](https://github.com/FoedusProgramme/AccordLegacy) with bug fixes, updated dependencies, and new features.

## Screenshots

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Home.jpg" width="220" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Browse.jpg" width="220" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Library.jpg" width="220" />
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Search.jpg" width="220" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Player.jpg" width="220" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/Lyrics.png" width="220" />
</p>

## Installation

Download the latest APK from [GitHub Releases](https://github.com/emylfy/Accord/releases/latest).

## Building

```bash
git clone https://github.com/emylfy/Accord.git
cd Accord
./gradlew assembleRelease
```

APK will be in `app/build/outputs/apk/release/`.

## Credits

Based on [Gramophone](https://github.com/AkaneTan/Gramophone) by AkaneTan and [AccordLegacy](https://github.com/FoedusProgramme/AccordLegacy) by FoedusProgramme.

Original developers: [@AkaneTan](https://github.com/AkaneTan), [@lightsummer233](https://github.com/lightsummer233), [@123Duo3](https://github.com/123Duo3)

Fork maintained by [@emylfy](https://github.com/emylfy)

## License

GPL-3.0 — see [LICENSE](LICENSE) for details.
