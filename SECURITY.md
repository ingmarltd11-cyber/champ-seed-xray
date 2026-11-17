# Security

## No secrets or network access

Champ Auto Mine is intentionally self-contained:

- It does not read, store, transmit or request API keys, tokens or passwords.
- It contains no HTTP, WebSocket, socket or URL connection code.
- It has no analytics, telemetry, update checker or remote configuration.
- The only file it writes is the local configuration file `config/champ-auto-mine.json`.

The mod does not need an account and works fully offline.

## Read-only repository

This is a public repository. No collaborators are added, so visitors only have
read access to the upstream repository. GitHub account security controls who may
push to the default branch. Forks are independent copies and cannot write back
to this repository.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting on the repository's Security
tab instead of opening a public issue for a security problem. Include the
affected version, steps to reproduce and the impact.
