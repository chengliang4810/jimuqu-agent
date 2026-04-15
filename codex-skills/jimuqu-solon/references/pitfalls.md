# Solon Pitfalls

- Do not let channel-specific code leak into `bootstrap` or `config`.
- Do not hide critical wiring in many scattered configuration classes.
- Do not depend on JDK 9+ APIs.
- Keep runtime directory handling centralized.
- Avoid using Solon features that add complexity without product value in V1.
