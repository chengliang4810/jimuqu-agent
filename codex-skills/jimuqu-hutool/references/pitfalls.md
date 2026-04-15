# Hutool Pitfalls

- Do not let Hutool become the config or JSON source of truth.
- Avoid using `hutool-all` APIs indiscriminately inside domain code; isolate helper-heavy code.
- Be careful with encodings and line separators on Windows.
- Prefer explicit exceptions around file and process operations.
