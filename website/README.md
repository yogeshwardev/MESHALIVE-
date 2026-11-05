# MeshAlive website

Static site, no build step. Everything in this folder is served as-is.

## Deploy on Cloudflare Pages

1. Cloudflare dashboard → Workers & Pages → Create → Pages → Connect to Git → pick `yogeshwardev/MESHALIVE-`.
2. Build settings: Framework preset **None**, Build command **(empty)**, Build output directory **`website`**.
3. Deploy. Then Custom domains → Set up a custom domain → `meshalive.benzo.co.in` (Cloudflare adds the DNS record automatically when `benzo.co.in` is on Cloudflare).

Or upload directly without Git:

```bash
npx wrangler pages deploy website --project-name meshalive
```

## Contents

- `index.html` – landing page
- `dashboard/` – district control-room dashboard (`/dashboard/?demo=1` loads sample data locally)
- `docs/` – concept document, app screens, portal answers, pitch deck
- `download/MeshAlive.apk` – Android app
- `assets/mock/` – phone and browser mockups
