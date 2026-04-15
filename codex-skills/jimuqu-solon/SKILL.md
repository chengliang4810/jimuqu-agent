---
name: jimuqu-solon
description: Use when working on jimuqu-agent Java application structure, Solon bootstrapping, configuration, bean wiring, routing, health endpoints, and package boundaries under JDK 8. Prefer this skill whenever a task touches Solon app startup, @Configuration/@Bean wiring, HTTP controllers, lifecycle hooks, or runtime directory conventions.
---

# jimuqu-solon

Use Solon as the application shell for `jimuqu-agent`.

## Workflow

1. Keep code under `src/main/java/com/jimuqu/agent/`.
2. Use plain constructors plus Solon `@Configuration` and `@Bean` wiring for shared services.
3. Keep `bootstrap`, `config`, and `core` stable; other packages depend on them, not the reverse.
4. Prefer minimal HTTP endpoints: health and optional debug/injection endpoints only.
5. Respect JDK 8: no `var`, records, `java.net.http`, switch expressions, or text blocks.

## Runtime Conventions

- Runtime base directory: `runtime/`
- Context files: `runtime/context/`
- Local runtime skills: `runtime/skills/`
- Cache: `runtime/cache/`
- SQLite database: `runtime/state.db`

## Read These References When Needed

- Module map: `references/module-map.md`
- Recipes: `references/jimuqu-recipes.md`
- Pitfalls: `references/pitfalls.md`
- Search paths: `references/search-paths.md`
