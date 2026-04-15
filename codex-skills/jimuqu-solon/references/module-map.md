# Solon Module Map

- Core framework source: `D:\projects\solon-main\solon\src\main\java\org\noear\solon`
- Annotations: `annotation/`
- Bean lifecycle and container: `core/`
- Routing and handlers: `core/route/`, `core/handle/`
- Startup entrypoints: `org/noear/solon/Solon.java`

For jimuqu-agent, Solon should own:

- application startup
- bean assembly
- controllers
- health endpoints
- runtime config binding
