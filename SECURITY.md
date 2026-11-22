# Security Policy

## Offline-First Privacy Architecture
SakuGuard is designed to keep all financial data strictly on the user's device:
- `android.permission.INTERNET` is explicitly removed in `AndroidManifest.xml` (`tools:node="remove"`).
- `android:allowBackup="false"` disables automatic OS cloud backups.
- Release builds (`!BuildConfig.DEBUG`) enable `WindowManager.LayoutParams.FLAG_SECURE` to protect balances from screenshots and recent-apps thumbnails.
- Encrypted `.saku` backups use `PBKDF2WithHmacSHA256` (`210,000` iterations, 16-byte `SecureRandom` salt) and `AES/GCM/NoPadding` (12-byte IV, 128-bit tag, canonical manifest AAD), paired with strict ZIP validation and `SHA-256` checksums.

## Reporting a Vulnerability
If you discover a security or financial-integrity vulnerability, please do **not** open a public issue with exploit details.

Instead, report it privately via GitHub Security Advisories (`Security` → `Advisories` → `Report a vulnerability` on `elrazaq/SakuGuard`) with:
- Steps to reproduce
- Affected version (`versionName` / commit hash)
- Expected vs. actual behavior
