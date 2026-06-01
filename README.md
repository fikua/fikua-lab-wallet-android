# Fikua Lab — Wallet (Android native)

Native Android EUDI-style holder wallet for the Fikua Lab. The **native sibling**
of the [`fikua-lab-wallet`](../fikua-lab-wallet) PWA — the PWA keeps running at
<https://wallet.lab.fikua.com>; this repo is a from-scratch Kotlin rewrite that
trades portability for what only a native app can do:

- **Hardware key binding** — the holder P-256 key lives in the Android Keystore
  (StrongBox when available), gated by biometrics. The native upgrade over the
  PWA's WebAuthn-PRF approximation (`prf.ts`).
- **ISO 18013-5 proximity** — present credentials over NFC/BLE, which has no web API.
- **Play Store distribution** — signed AAB.

## Foundation

Built on [**Multipaz**](https://github.com/openwallet-foundation/multipaz)
(OpenWallet Foundation; formerly Google's `identity-credential`) — the same
credential stack underpinning the EU EUDI reference wallet. It provides mdoc,
SD-JWT VC, ISO 18013-5 presentment, and a Keystore-backed secure area.

## Module map (PWA → native)

The PWA is the executable spec; each native module references its TS source.

| Native module | PWA reference | Native equivalent |
| ------------- | ------------- | ----------------- |
| `crypto/HolderKeyBinding.kt` | `crypto.ts`, `prf.ts` | Android Keystore / StrongBox |
| `credential/` | `mdoc.ts`, `sdjwt.ts`, `cbor.ts` | Multipaz mdoc + SD-JWT VC |
| `proximity/` | (none — new) | Multipaz ISO 18013-5 NFC/BLE |
| `oid4vci/` | `protocol.ts` | Ktor client, OID4VCI flow |
| `oid4vp/` | `mdocPresentation.ts`, `presentation.ts` | Ktor + Multipaz presentment |
| `storage/` | `storage.ts` | Room (metadata); keys in Keystore |
| `ui/` | `main.ts`, `index.html` | Jetpack Compose |

## Status

🟢 Scaffolding. Implemented so far:

- [x] Gradle + version catalog wired to Multipaz
- [x] `HolderKeyBinding` — Keystore P-256 holder key (StrongBox-preferring,
      biometric-gated) with a Robolectric unit test
- [ ] Credential model + storage
- [ ] OID4VCI issuance flow
- [ ] OID4VP presentation (remote, then proximity)
- [ ] Compose UI
- [ ] Play Store signing + AAB

## Build

```bash
# Add the Gradle wrapper once (no network needed beyond the wrapper jar):
gradle wrapper --gradle-version 8.11
./gradlew :app:testDebugUnitTest   # runs HolderKeyBindingTest under Robolectric
./gradlew :app:assembleDebug
```

## Decisions

- Separate repo from the PWA — see the migration plan in the life-system repo.
- Multipaz over hand-rolled CBOR/COSE — EUDI alignment, less to maintain.

## License

Apache-2.0 (matching the PWA).
