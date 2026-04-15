# Solon AI Recipes For jimuqu-agent

- Build models with `ChatModel.of(apiUrl).provider(...).apiKey(...).model(...)`.
- Use `provider("openai-responses")` with a `/v1/responses` URL.
- Use `InMemoryChatSession` or `InMemoryAgentSession` for session replay after loading stored NDJSON.
- Register tool objects with `toolsAdd(new MyTools())`.
- For live gateway tests, send text through the gateway, not directly to the model.
