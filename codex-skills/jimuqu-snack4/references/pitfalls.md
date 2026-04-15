# Snack4 Pitfalls

- Do not mix multiple JSON libraries in the same code path.
- Be explicit about generic types; otherwise collections deserialize loosely.
- Keep dynamic `ONode` usage near protocol boundaries, not deep in domain logic.
- Test date, map, and list round-trips explicitly.
