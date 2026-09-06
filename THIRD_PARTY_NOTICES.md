# Third-party notices

MOROK is an unofficial Telegram API client derived from [DrKLO/Telegram](https://github.com/DrKLO/Telegram), commit `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` (12.10.1). Preserve the upstream `LICENSE` and copyright headers. Telegram describes the Android source as GPL v2 or later on its [source-code page](https://telegram.org/apps#source-code). Provide corresponding source and build materials with any distributed APK.

All original native/library dependency notices remain in the tree. The ten upstream git submodules and exact commits are recorded in `upstream.lock.json`; their individual licenses continue to apply. No AyuGram/exteraGram code has been copied; their documentation was used only as a product reference.

New cryptographic dependency: [Google Tink Java / Android](https://github.com/tink-crypto/tink-java), Maven `com.google.crypto.tink:tink-android:1.15.0`, Apache-2.0. It provides the Android Keystore AEAD implementation; encryption is not implemented from scratch.

The MOROK «Личина» vector reconstruction derives from the project owner's supplied `morok.png`; its SHA256 and production metadata are in `assets/morok/brand.json`. Brand artwork authorship/rights were supplied by the owner, not independently verified. Required notices for any future copied implementation must include repository, exact commit, source path, author and license.
