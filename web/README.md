# Solon Claw Web UI

Browser-based dashboard for managing Solon Claw configuration, API keys, and active sessions.

## Stack

- **Vite** + **Vue 3** + **TypeScript**
- **Antdv Next** components
- **Pinia** stores and **Vue Router**

## Development

```bash
# Start the backend from the repository root.
cd ..
mvn "-Dskip.web.build=true" "-DskipTests" package
java -jar target/solonclaw-0.0.1.jar

# In another terminal, start the Vite dev server (with HMR + API proxy)
cd web
npm run dev
```

The Vite dev server proxies `/api` requests to the local Solon backend.

## Build

```bash
npm run build
```

This outputs to `dist/`; the Maven build copies those assets into the Java application resources.

## Structure

```
src/
├── api/                    # Typed backend API wrappers
├── assets/                 # Static assets bundled by Vite
├── components/             # Feature and shared Vue components
├── composables/            # Reusable Vue composition functions
├── data/                   # Static catalogs and view data
├── i18n/                   # Locale messages and translation setup
├── router/                 # Dashboard route definitions
├── shared/                 # Cross-feature UI contracts
├── stores/                 # Pinia stores
├── styles/                 # Global and feature styles
├── utils/                  # Frontend-only utility functions
├── views/                  # Routed dashboard views
├── App.vue                 # Main layout and navigation
└── main.ts                 # Vue entry point
```
