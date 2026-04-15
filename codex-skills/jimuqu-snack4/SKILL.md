---
name: jimuqu-snack4
description: Use when implementing JSON serialization, deserialization, ONode transformations, NDJSON persistence, Snack4 options/features, or Solon Snack4 integration in jimuqu-agent. Trigger this skill for JSON modeling and message/session snapshot handling.
---

# jimuqu-snack4

Use Snack4 as the main JSON layer for `jimuqu-agent`.

## Workflow

1. Use `ONode` for dynamic JSON and schema-light transformations.
2. Use typed deserialization for stable domain models.
3. Use NDJSON support from Solon AI chat messages when storing session snapshots.
4. Keep JSON policy centralized; do not mix Jackson, Gson, Fastjson, or Hutool JSON as peers.

## Read These References When Needed

- Module map: `references/module-map.md`
- Recipes: `references/jimuqu-recipes.md`
- Pitfalls: `references/pitfalls.md`
- Search paths: `references/search-paths.md`
