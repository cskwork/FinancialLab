# Asset register

Raster assets shipped from `frontend/public/images/`. All were generated on 2026-09-24 with GPT Image 2 (ChatGPT Images 2.0, via the local Codex CLI on the owner's ChatGPT plan) for the "computation pad" redesign, then cropped/resized with ImageMagick and encoded with cwebp. The exact generation prompt is embedded in each file (JPEG comment) or in its `.json` sidecar (WebP). No stock or third-party imagery; no people, brands or logos.

| File | Size | Used on | Alt text | Source / processing |
|---|---|---|---|---|
| `hero-pad-1600.webp`, `hero-pad-960.webp` | 1600×1067, 960×640 | Landing hero (`srcset`) | "A green engineering computation pad with two hand-plotted pencil curves, columns of figures, one negative value in red pencil and a final figure boxed in yellow highlighter, beside a mechanical pencil and a red pencil." | GPT Image 2, 1536×1024 PNG → resized, WebP q80/q78. Illustration; the figures on it are not app data. |
| `empty-axes.webp` | 560×560 | `EmptySheet.vue` (empty states) | Decorative (`alt=""`); the message beside it carries the meaning. | GPT Image 2, 1254² PNG → cropped 930² at (300,230), resized, WebP q80. |
| `og-finlab.jpg` | 1200×630 | Open Graph / Twitter card (`index.html`) | "FinLab lettered in pencil on green engineering graph paper beside a hand-drawn line chart" | GPT Image 2, 1732×908 PNG → cover-cropped to 1200×630, JPEG q84. The boxed number is illustrative, not a claim. |
| `favicon.svg` | vector | Favicon | n/a | Hand-authored SVG. |
