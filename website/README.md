# MeshAlive website

Static site, no build step. Everything in this folder is served as-is at https://meshalive.benzo.co.in.

## Deploy on Cloudflare

The repo root has `wrangler.jsonc`, which tells Cloudflare to serve **this `website/` folder** (landing page, `/dashboard/`, `/download/MeshAlive.apk`).

### Worker connected to GitHub (Workers & Pages → your Worker)

1. Settings → Build: the Worker's name must be `meshalive` (the `name` in `wrangler.jsonc`). If your Worker has a different name, change `name` in `wrangler.jsonc` to match.
2. Deploy command: `npx wrangler deploy`. Leave the build command empty and the root directory as `/`.
3. Every push to `main` redeploys.

### Pages project (if you used Pages instead)

Settings → Builds & deployments → Build output directory: **`website`**, build command empty, framework preset None. Then retry the latest deployment.

### Manual deploy from this computer

```bash
npx wrangler login
npx wrangler deploy
```

Custom domain: Settings → Domains & Routes → Add → Custom domain → `meshalive.benzo.co.in`.

## Contents

- `index.html`: landing page with the Android download, QR code and install steps
- `dashboard/`: district control-room dashboard (`/dashboard/?demo=1` loads sample data)
- `download/MeshAlive.apk`: Android app (19 MB, under Cloudflare's 25 MiB file limit)
- `assets/`: phone and browser mockups, download QR code
- `404.html`: not-found page
