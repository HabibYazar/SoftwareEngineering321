# HMS Community frontend technology stack

The frontend is a React single-page application written in TypeScript and TSX, with CSS for shared styling and JavaScript for Node test files. Vite serves and builds the application. React DOM mounts it in the browser. Spring Boot, PostgreSQL, and Docker Compose belong to the backend or deployment environment.

## Dependencies and tooling

These are the direct dependencies declared by the frontend, with exact versions cross-checked against the lockfile and the local installation used for verification.

| Package | Declared range | Locked and verified installed version | Purpose |
| --- | --- | --- | --- |
| `react` | `^19.2.0` | `19.3.1` | Components, context, and hooks |
| `react-dom` | `^19.2.0` | `19.3.1` | Browser rendering |
| `typescript` | `~5.9.0` | `5.9.3` | Static type checking |
| `vite` | `^7.1.0` | `7.3.8` | Development server and production bundle |
| `@vitejs/plugin-react` | `^5.0.0` | `5.2.0` | React transforms and Fast Refresh |
| `@types/react` | `^19.2.0` | `19.3.0` | React type declarations |
| `@types/react-dom` | `^19.2.0` | `19.3.0` | React DOM type declarations |

Use Node.js 22.18 or later for the repository's documented test workflow. The locked Vite tooling declares Node support of `^20.19.0 || >=22.12.0`. The tests also import TypeScript directly through Node's built-in type stripping. The package does not define an npm version requirement. The local versions checked were Node.js `26.13.1` and npm `11.20.0`. There are no direct routing, UI framework, or test framework dependencies. [Package manifest](../package.json) · [Lockfile](../package-lock.json)

## Development and verification

Run the following from the frontend directory in PowerShell. Use `npm.cmd ci` to install the lockfile dependencies if they are missing. For HTTP mode, leave the mock flags unset or set them to `false`.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

```powershell
npm.cmd test
npm.cmd run build
```

The build script runs `tsc -b` followed by `vite build`. Tests use Node's built-in runner and cover auth, staff, and navigation logic. Browser interaction checks are listed in the story documents. The [frontend README](../README.md) lists synthetic sign-in credentials.

## Shared design and layout

- `AppLayout` renders the shared sidebar, page controls, skip link, and main landmark. `Sidebar` contains the role-aware Home link, theme toggle, and sign-out action. It collapses below the shared breakpoint and restores focus to Menu when closed with Escape.
- The app reuses `Button`, `TextField`, `Card`, `Alert`, and `Icon` for consistent semantics and styling. Flex wrapping keeps page controls usable on narrow screens. The shared CSS supplies the typography, focus outline, spacing, and control sizing.
- The light palette uses warm neutral beige. The dark palette uses navy and charcoal. `tokens.css` defines both palettes, shared spacing and motion. The theme applies through the root `data-theme` attribute and follows the system preference on first load. The user can override it with `hms.theme` in local storage. The reduced-motion preference disables token-based transitions. [Shared components](../src/components) · [Theme tokens](../src/theme/tokens.css)

## Routing and history

The application uses hash addresses, including `#/login`, `#/foundation`, `#/home/<Role>`, and `#/doctors`. The route resolver validates each destination against the active role. `ProtectedRoute` also prevents protected components from mounting without the required role.

Normal in-app links add native browser entries. App Back and Forward traverse adjacent entries recorded by the current document and role, so they cannot leave the application. Native browser navigation can traverse the browser's full history and every application entry is checked on arrival. An unfamiliar entry or an auth change starts a new range for the app controls. History metadata contains only an opaque range marker and an index. [History logic](../src/navigation/history.ts)

The Home link opens the active user's role workspace. Refresh repeats the doctor or architecture health read on those pages. It is disabled on the sign-in and role landing pages, which have no refreshable data. Reloading the entire document requires the user to sign in again.

## Authentication, HTTP, and integration

`AuthProvider` and the protected business HTTP clients share the in-memory session. Logout clears it immediately and attempts server-side invalidation. A current protected `401` returns the user to sign-in, while `403` reports the denied operation and keeps the session. [US-02](US-02-frontend.md) The application persists the theme preference and keeps authentication data in memory.

Business HTTP service factories have injectable `fetch` implementations for testing. The versioned base path is `/api/v1`. Vite proxies `/api` to `http://localhost:8080` in development. Container Nginx sends that path to `backend:8080`. Authentication and the directory each select a separate mock only when Vite is in development mode and their flag is explicitly `true`. HTTP failures surface as errors. [OpenAPI contract](../../api/openapi.yaml) · [Vite proxy](../vite.config.ts) · [Nginx proxy](../nginx.conf)

Use only synthetic accounts and records. Mock and controlled HTTP results establish frontend behavior. Live authentication, token revocation, and Spring Boot/PostgreSQL verification still require their actual backend environments. Server-side authorization for the directory must also be aligned with the documented contract. [US-01](US-01-frontend.md) · [US-09](US-09-frontend.md)
