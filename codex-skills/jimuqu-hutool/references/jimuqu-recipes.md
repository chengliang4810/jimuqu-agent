# Hutool Recipes For jimuqu-agent

- Use `FileUtil`, `IoUtil`, `StrUtil`, `IdUtil`, `DateUtil`, and `CollUtil` first.
- Use `HttpUtil` only in non-Solon-AI protocol helpers or channel adapter utilities.
- Use `DigestUtil` and related crypto helpers for signature checks when needed.
- Prefer small wrappers over custom utility reimplementation.
