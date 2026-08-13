# Security Policy

## Supported versions

Security fixes currently target the latest public release only.

## Reporting a vulnerability

Please do not disclose vulnerabilities, API keys, provider responses, account data, or other sensitive material in a public issue.

Until GitHub private vulnerability reporting is configured for this repository, contact the repository owner privately through the GitHub profile associated with `c1216149718-dev`. Include only the minimum information needed to reproduce the issue, and redact all real credentials and personal data.

## Credential handling

- Never commit `local.properties`, environment files, keystores, API keys, Authorization headers, or captured provider responses.
- Tests and screenshots must use synthetic or redacted fixtures.
- Vela v1.20.0 stores configured provider credentials in private app Preferences DataStore. Migration to Android Keystore-backed encryption is required before production-signed distribution.
