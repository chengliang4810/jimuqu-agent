# Solon AI Pitfalls

- Do not duplicate built-in dialect logic.
- Do not let session persistence logic leak into the dialect layer.
- Do not hardcode API keys in code or tracked files.
- Tool names and descriptions need to be explicit, or live models will skip them.
- Keep a deterministic non-live fallback test path.
