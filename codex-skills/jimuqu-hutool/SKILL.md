---
name: jimuqu-hutool
description: Use when implementing common Java utility behavior in jimuqu-agent with Hutool, including files, IO, text, dates, threads, crypto, HTTP helpers, and runtime convenience utilities. Trigger this skill for cross-cutting helper logic, not for primary JSON serialization.
---

# jimuqu-hutool

Use Hutool as the shared utility layer for `jimuqu-agent`.

## Workflow

1. Prefer Hutool for file, text, time, IO, process-adjacent helpers, and UUID/id utilities.
2. Keep Hutool use inside services and helpers; do not let it define the main architecture.
3. Do not use `hutool-json` as the main JSON layer; use Snack4 instead.
4. Keep wrappers thin; use Hutool directly when the API is already clear.

## Read These References When Needed

- Module map: `references/module-map.md`
- Recipes: `references/jimuqu-recipes.md`
- Pitfalls: `references/pitfalls.md`
- Search paths: `references/search-paths.md`
