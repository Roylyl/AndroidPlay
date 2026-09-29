# Restored 0.1.0 release — 2026-09-25

- Built from the current public source with explicitly selected external authentication assets and the existing local Android signing key.
- 172 JVM/Robolectric tests passed; zero failures/errors. Release lint and signed release build passed.
- Public-tree credential scan passed. Source tests generate identities at runtime; no credential containers or private-key blocks are tracked.
- Verified that the APK contains the intended runtime accessory identity and no Android signing keystore.
- Signing certificate SHA-256: `87b38b12788dcb202a961215f2572e30ec2dc9d8ef4bc070d05f77e49291a363` (unchanged).
- Package `com.shihab.diplay`, version `0.1.0`, version code `10`; restoration changes packaging and public documentation, not app behavior.
- Existing USB-only TLS trust-manager warnings and unused-resource warning remain; this is not a completed security audit.
- No fresh physical-car validation was performed for the restored artifact. Previous emulator and private-build testing do not establish universal compatibility.
