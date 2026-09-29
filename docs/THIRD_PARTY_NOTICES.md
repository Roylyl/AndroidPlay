# Credits and license notices

## Receiver

DiPlay is a modified version of [xcertplay by shilapi](https://github.com/shilapi/xcertplay). The upstream receiver is licensed under GNU GPL version 3; the full text is in `LICENSE` and the original README is retained in `docs/UPSTREAM-README.md`.

Upstream credits [LIVI](https://github.com/f-io/LIVI) and [Showcase](https://github.com/amineross/showcase) for protocol research. Existing source comments and attribution are preserved.

## Home and settings UI

`common/src/main/java/com/shilapi/xcertplay/AndroidPlayActivity.kt` adapts the palette, visual arrangement and interface copy of the [DiAuto project](https://github.com/shihabal3amri/DiAuto). DiAuto's source is licensed under AGPL version 3. The UI file is marked AGPL-3.0-only; its license text is included in `docs/licenses/DiAuto-AGPL-3.0.txt`.

## AndroidPlay icon

The current AndroidPlay icon is an original geometric drawing created for this modification. Its editable source is `asset/androidplay-icon.svg`; `scripts/generate-icons.cjs` generates the Android vector, adaptive, monochrome, notification, CarPlay and website variants from that source.

The Apple CarPlay icon previously used by this fork has been removed from active application resources. Historical upstream screenshots and documents retain their original contents. CarPlay remains an Apple Inc. trademark; use of its name does not imply Apple approval or certification.

## Runtime dependencies

- AndroidX — Android Open Source Project; Apache License 2.0. Jetpack Compose was removed from this AndroidPlay build.
- Kotlin standard library — JetBrains; Apache License 2.0.
- Bouncy Castle 1.79 — The Legion of the Bouncy Castle Inc.; Bouncy Castle license (MIT-style).
- JmDNS 3.6.3 — JmDNS contributors; Apache License 2.0.
- SLF4J — QOS.ch; MIT license.

Gradle dependency declarations and version catalog accompany the source. License files available in the resolved artifacts are included under `docs/licenses/dependencies/`.

## Experimental authentication data

AndroidPlay's local experimental builds used an identity from the DiPlay 0.2.6 APK, as recorded in `ANDROIDPLAY_AUTH_SOURCE.md`. The firmware provenance described in historical upstream notices is an upstream claim, not an independently established authorization to redistribute these data.

The source repository does not include the accessory private key or certificate. Locally provisioned credentials and APKs are excluded by `.gitignore`; neither the project code licenses nor public availability grants permission to redistribute third-party authentication data. Redistribution authorization remains unconfirmed. AndroidPlay is not an Apple-certified or Apple-endorsed product. APK-signing keys are separate local secrets and are not distributed.

## Download website

The static site layout, CSS and generator adapt DiAuto (AGPL-3.0). The AGPL license text is included with the source.

## BYD HUD maneuver icons

Historical upstream notice: AndroidPlay removes the HUD implementation and the maneuver-icon assets described below. The attribution is retained for the upstream record.

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

The maneuver PNGs under `shared/src/main/assets/byd-hud-icons` were imported from BYDMate. Its PolyForm Noncommercial 1.0.0 terms and required notice are included alongside the assets. These files are separate from the project code license; upstream describes them as donor assets and their original provenance is not independently established. The validated DiLink5.1 windshield path uses factory turn codes rather than these images.
