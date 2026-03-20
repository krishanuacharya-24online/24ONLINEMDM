# 24onlineMDMAgent

`24onlineMDMAgent` is the packaged cross-platform customer-facing device agent for enrollment, posture collection, compliance sync, remediation visibility, and support diagnostics.

## Product Characteristics

- installable and uninstallable desktop app
- tray-resident background app after the main window is closed
- explicit tray `Quit` closes the app fully
- startup launches hidden to the tray when the app is opened through the startup path
- reopening from the tray is driven from the tray context menu so startup stays quiet until a user explicitly chooses `View Window`
- fixed embedded service endpoint: `http://192.168.254.20:8080`
- OS-backed secure credential storage for tenant secrets
- responsive enterprise UI with local support diagnostics
- startup and desktop-shortcut preferences are managed from the installed app in the `Run Application` section

## Runtime Inputs

The packaged app UI expects:

- `tenant_id`
- `tenant_key`
- `setup_key`

`base_url` is embedded into the build and not edited at runtime.

## Secure Storage

- `tenant_key` and `setup_key` are not persisted in `agent-config.json`
- sensitive values are stored in `agent-secrets.json` using Electron `safeStorage`
- on Linux, persistent secret storage requires a supported desktop key store such as Secret Service or KWallet

`agent-config.json` stores only non-secret settings such as:

- `base_url`
- `tenant_id`
- `launch_at_startup`
- `desktop_shortcut`

## Platform Build Matrix

Current package targets in this repo:

- Windows installer: `x64`, `ia32`, `arm64`
- Linux packages: `x64`, `arm64`
- macOS packages: `x64`, `arm64`

Startup and desktop-shortcut preferences are applied after installation from the app itself.

## Development

Install dependencies:

```powershell
cd .\tools\24onlineMDMAgent
npm install
```

Start the app in development mode:

```powershell
npm start
```

Run a smoke boot of the Electron shell:

```powershell
npm run smoke:ui
```

## Packaging

The packaging entrypoint generates icons, writes the fixed build profile for `http://192.168.254.20:8080`, rebuilds `dist/`, and stages top-level installable artifacts into `release/`.

### Windows

Generic installer build:

```powershell
npm run dist:win
```

### Linux

Run on Linux or Linux CI:

```bash
npm run dist:linux
```

From a Windows workstation with Docker available, you can also build Linux artifacts inside the official Electron Builder container:

```powershell
docker run --rm -t `
  -v "C:/path/to/24onlinemdm/tools/24onlineMDMAgent:/project" `
  -v 24onlinemdm-agent-node-modules:/project/node_modules `
  -w /project `
  electronuserland/builder:20 `
  /bin/bash -lc "npm ci && node ./scripts/build-release.cjs --linux AppImage deb rpm --x64 --arm64"
```

### macOS

Run on macOS or macOS CI:

```bash
npm run dist:mac
```

macOS artifacts must be built on macOS. A Windows or Linux workstation can prepare the source tree and scripts, but not produce the final macOS package directly.

### Cross-Platform Release Command

For CI pipelines that execute on the appropriate build hosts:

```bash
npm run dist:all
```

Do not expect one workstation to produce every platform artifact reliably. Use the target OS, or CI runners per platform, for the final release matrix.

## Generated Build Artifacts

Build outputs are written under:

- `tools/24onlineMDMAgent/dist/`
- `tools/24onlineMDMAgent/release/`

Typical artifact types:

- Windows: NSIS installer `.exe`
- Linux: `.AppImage`, `.deb`, `.rpm`
- macOS: `.dmg`, `.zip`

## Local Files

The packaged app stores files in the Electron user-data directory for the current OS:

- `agent-config.json`
- `agent-secrets.json`
- `agent-state.json`
- `out/last-claim.json`
- `out/last-payload.json`
- `out/last-ingest-response.json`
- `out/last-result.json`
- `out/last-latest-decision.json`
- `out/last-ack.json`

## Backend Note

The backend still requires `X-Tenant-Id` and `X-Tenant-Key` for result polling and decision acknowledgment. The desktop agent therefore keeps tenant credentials available for the full workflow.
