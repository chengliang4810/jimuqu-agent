# Solon AI Module Map

- Aggregated AI dependency: `D:\projects\solon-ai-main\solon-ai`
- Core chat APIs: `solon-ai-core`
- Agent APIs: `solon-ai-agent`
- Dialects: `solon-ai-llm-dialects\solon-ai-dialect-*`
- OpenAI Responses dialect: `solon-ai-dialect-openai\src\main\java\org\noear\solon\ai\llm\dialect\openai\OpenaiResponsesDialect.java`

For jimuqu-agent, Solon AI should own:

- model configuration
- chat requests
- tool calling
- stream vs call behavior
- session replay integration
