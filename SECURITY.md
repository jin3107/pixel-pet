# Security

## Reporting a vulnerability

Please report security problems privately, not in a public issue: open the
repository's **Security** tab and choose **Report a vulnerability**. If that
option is not available, open a public issue that only says you have a security
report and ask for a private way to send it; do not put details in it.

Only the [latest release](https://github.com/jin3107/pixel-pet/releases/latest)
receives fixes.

## What is sensitive in this app

- **Mini brain API key** (optional feature). Saved keys are encrypted with the
  operating system through Electron `safeStorage` and are never sent back to the
  settings window. The key is only sent to the endpoint you configured. The
  `BRAIN_API_KEY` / `XAI_API_KEY` environment variables, if set, take precedence.
- **App awareness** (optional feature). The foreground window's process name and
  title are read locally every 30 seconds and kept in memory only; windows that
  look like banking, password managers or private browsing are reported without a
  title. Nothing is written to disk and this feature sends nothing anywhere by
  itself. If Mini brain is also on, it receives only the activity category
  (for example "coding"), never titles or app names.
- Pixel Pet makes no other network requests. The installer is currently **not
  code-signed**, so verify that you downloaded it from this repository's releases.
