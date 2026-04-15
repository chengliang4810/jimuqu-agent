# Solon Recipes For jimuqu-agent

- Start app with `Solon.start(App.class, args)`.
- Wire shared services in one or two focused configuration classes.
- Keep controllers thin; route to `gateway` or `bootstrap` services.
- Use constructor injection in plain classes and expose them via `@Bean`.
- Avoid framework-heavy meta-programming for V1.
