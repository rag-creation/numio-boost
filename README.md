<p align="center"><img src="logo.png" width="128" alt="Decibel: Volume Booster logo"></p>

# Decibel: Volume Booster

Decibel by Numio is a simple, open-source volume booster for Android. Turn it on, slide the boost up, and quiet videos, calls and speakers get louder.

No ads. No tracking. No internet permission.

## Features

- One big on/off button, plus a Quick Settings tile
- Volume and Boost sliders on one screen
- Boost up to +12 dB, or up to +24 dB with "Higher boost" (behind a warning)
- Keeps working in the background, with a notification to stop it
- Works for all apps on most phones; falls back to per-app boost in music and video players that support audio effects

## How it works

Decibel uses Android's built-in `LoudnessEnhancer` audio effect. It attaches to the global output mix, and also to any player that announces its audio session, then raises the gain by the amount you choose. Nothing is recorded or sent anywhere.

## Safety

Loud sound can damage speakers and hearing. If you hear crackling, turn the boost down. Keep it low with headphones.

## Download

Grab the APK from [Releases](https://github.com/rag-creation/numio-boost/releases).

## Built with

Kotlin, Jetpack Compose, Material 3. Minimum Android 8.0 (API 26).

## Part of Numio

Decibel is part of the [Numio](https://getnumio.org) family of open-source Android apps, alongside Numio Calculator, Clock by Numio, Notes by Numio and Ezhuthola.

## About

Built by RR with help from Claude.

## License

[GPL-3.0](LICENSE)
