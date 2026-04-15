---
name: jimuqu-solon-ai
description: Use when implementing LLM access, ChatModel building, tool calling, chat sessions, ReAct or simple agents, and openai-responses usage in jimuqu-agent. This skill should trigger for work on model protocol integration, Solon AI dialects, session replay, tool wiring, and live AI integration tests.
---

# jimuqu-solon-ai

Use Solon AI as the LLM and agent substrate for `jimuqu-agent`.

## Workflow

1. Prefer `ChatModel` first; only use higher-level agent abstractions when they reduce code.
2. Reuse built-in dialects for `openai`, `openai-responses`, `ollama`, `gemini`, and `anthropic`.
3. Use `ChatSession` or `AgentSession` snapshots for session replay; store snapshots outside the model layer.
4. Add tools through `ChatOptions.toolsAdd(...)` or model defaults.
5. Keep protocol-specific details inside `llm`, not `engine`.

## jimuqu Rules

- `openai-responses` already exists in Solon AI and should be used directly.
- Live model integration tests must use a gateway-style path plus an in-memory adapter.
- V1 does not need multimodal or image/audio features.

## Read These References When Needed

- Module map: `references/module-map.md`
- Recipes: `references/jimuqu-recipes.md`
- Pitfalls: `references/pitfalls.md`
- Search paths: `references/search-paths.md`
