---
name: FinLab
description: Portfolio simulation lab drawn on an engineer's computation pad.
colors:
  paper: "#e5eed8"
  paper-raised: "#eef4e5"
  paper-sunk: "#d8e4c8"
  binding: "#1c2a21"
  binding-ink: "#e5eed8"
  binding-muted: "#a9bb9d"
  ink: "#1b221e"
  ink-2: "#435047"
  ink-3: "#56645a"
  rule: "#9fb391"
  red-pencil: "#a8231b"
  blue-pencil: "#1d4a94"
  highlight: "#f1df4f"
  blueprint-paper: "#0f3257"
  blueprint-paper-raised: "#154072"
  blueprint-binding: "#081d35"
  blueprint-ink: "#f1f6fc"
  blueprint-ink-2: "#c3d4e8"
  blueprint-red-pencil: "#ffb0a3"
  blueprint-chalk: "#ffe38a"
typography:
  display:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "clamp(3.5rem, 2rem + 6vw, 6rem)"
    fontWeight: 700
    lineHeight: 0.85
    letterSpacing: "-0.01em"
  title:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "clamp(2rem, 1.4rem + 2vw, 2.75rem)"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "0.01em"
  heading:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "0.04em"
  label:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0.08em"
  figure:
    fontFamily: "'Azeret Mono', ui-monospace, 'SF Mono', Menlo, monospace"
    fontSize: "1.125rem"
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  body:
    fontFamily: "system-ui, -apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "normal"
rounded:
  none: "0px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "48px"
  grid: "20px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0 20px"
    height: "44px"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0 20px"
    height: "44px"
  button-danger:
    backgroundColor: "transparent"
    textColor: "{colors.red-pencil}"
    rounded: "{rounded.none}"
    height: "44px"
  field:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "8px 12px"
    height: "44px"
  binding-strip:
    backgroundColor: "{colors.binding}"
    textColor: "{colors.binding-ink}"
    height: "60px"
  sheet-tab-active:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
  answer:
    backgroundColor: "{colors.highlight}"
    textColor: "{colors.ink}"
    typography: "{typography.figure}"
    rounded: "{rounded.none}"
---

# Design System: FinLab

## Overview

**Creative North Star: "The Computation Pad"**

Every screen is a sheet torn from an engineer's green-tint computation pad. The pad's printed 5 mm grid is the page ground, a dark glued binding strip carries navigation, and each sheet opens with a ruled title block that states its subject and sheet number. The working is shown in graphite; the answer is boxed and highlighted. This refuses both the dark neon trading terminal and the blue SaaS card grid the category usually ships.

The dark scheme is the same pad after it has been to the print room: a blueprint, Prussian-blue ground with white linework and chalk-yellow for the second series and the answer. Dark mode follows `prefers-color-scheme`; nothing else changes between the two.

**Key Characteristics:**
- Pale green paper with a faint 20 px / 100 px grid on `body`, never on individual panels.
- Square corners everywhere; 1 to 2 px inked rules separate content instead of cards and shadows.
- Condensed DIN-style caps for labels and headings, tabular mono for every figure, system sans for prose (covers Korean).
- Highlighter yellow appears only on the answer: the key total, the backtest verdict, the active "Live" mode chip, the primary button's hover underline.

## Colors

Restrained: one paper ground, graphite ink, and three pencil colours with fixed jobs.

### Primary
- **Graphite** (`#1b221e`, blueprint `#f1f6fc`): body text, primary buttons, inked rules, the first chart series.

### Secondary
- **Red pencil** (`#a8231b`, blueprint `#ffb0a3`): losses, SELL, destructive actions, form errors.
- **Blue pencil** (`#1d4a94`, blueprint chalk `#ffe38a`): links, DEPOSIT/WITHDRAW, the second chart series, focus rings.

### Tertiary
- **Highlighter** (`#f1df4f`, blueprint `#ffe38a`): the boxed answer only.

### Neutral
- **Pad paper** (`#e5eed8`) ground; **raised** (`#eef4e5`) for writing fields, code and slips; **sunk** (`#d8e4c8`) for hover rows and pressed states.
- **Printed rule** (`#9fb391`) for ledger lines; **binding** (`#1c2a21`) for the top strip.
- Secondary text `#435047`, tertiary `#56645a` (both ≥4.5:1 on paper).

**The Pencil Jobs Rule.** Each pencil colour has one job. Gains stay graphite with a `+` sign; losses are red pencil with a `-` sign, so colour is never the only signal.

**The One Highlight Rule.** If more than one thing on a sheet is highlighted, none of them is the answer.

## Typography

**Label/Display Font:** Barlow Condensed (self-hosted via @fontsource, 500/600/700)
**Figure Font:** Azeret Mono (self-hosted, 400/600)
**Body Font:** system-ui stack with Apple SD Gothic Neo / Noto Sans KR for Hangul

**Character:** engineering lettering. Condensed caps name things; mono states quantities; the system sans explains.

### Hierarchy
- **Display** (700, clamp 3.5–6rem, line-height 0.85, uppercase): the landing wordmark and login title only.
- **Title** (600, clamp 2–2.75rem, uppercase): the H1 inside each title block.
- **Heading** (600, 1.375rem, uppercase, 0.04em): block heads (`h2`) sitting on a 2 px inked rule.
- **Label** (600, 0.875rem, uppercase, 0.08em): field labels, table heads, tabs, buttons.
- **Figure** (mono, tabular-nums): every amount, date, count, percentage and chart tick.
- **Body** (400, 1rem, line-height 1.55, max 68ch): prose and notes.

**The Figures Are Mono Rule.** Any number a user might compare down a column is set in the figure face with tabular numerals and right-aligned in tables (`td.num`).

## Layout

- Page max width 1320 px, 24 px side padding (16 px under 720 px), 80 px bottom space.
- Sheets use a 4/8 or 5/7 two-column split on desktop (summary vs ledger, given vs results) and collapse to one `minmax(0, 1fr)` column under 1000–1080 px. Always use `minmax(0, …)` tracks so ledgers scroll inside their own box.
- Blocks are separated by 48–64 px; a block head sits on a 2 px graphite rule, rows on 1 px printed rules, and a closing total or new-entry line on a 3 px double rule.
- Ledgers scroll horizontally inside `.ledger-scroll` (position: relative) on narrow screens; the page itself never scrolls sideways.
- The binding strip collapses to brand + Menu button under 960 px; the drawer lists the numbered sheets, the appendix links and settings.

## Elevation & Depth

Flat by default. Depth exists only for things that float over the sheet: the settings panel and toast slips use `0 8px 24px -8px rgb(27 34 30 / 0.25)` (blueprint: `rgb(0 0 0 / 0.5)`). The landing key-art plate is tilted 0.6° to read as a loose sheet. No other shadows.

## Shapes

Square corners throughout (`border-radius: 0`). Borders are 1 px (printed rules, minor controls), 1.5 px (title blocks, buttons, segmented controls) or 2 px (block heads, the answer box).

## Components

- **Binding strip (NavBar).** Dark strip, numbered sheet tabs `01–04`; the active tab takes the paper colour and joins the page below. Settings (language, mock/live backend) live in a `<details>` panel with a MOCK/LIVE chip.
- **Title block (`TitleBlock.vue`).** Ruled grid: subject cell holding the H1 and a one-line subtitle, a SHEET cell (`02 / 04`, `App. A`), and an optional actions cell. No eyebrow label above the H1.
- **Buttons (`.btn`).** 44 px min height, label face caps. Primary is solid graphite and gains a highlighter underline on hover; secondary is outlined; quiet has no border; danger is red pencil. Icons come from lucide-vue-next at 18 px.
- **Fields (`.field`).** Writing lines: raised paper fill, 1.5 px graphite underline, label above in label face.
- **Segmented control (`.seg`).** Outlined group; pressed segment is solid graphite. Used for backend mode and transaction type filters (with counts).
- **Answer (`.answer`, `.verdict`).** 2 px graphite box with a highlighter band; the backtest verdict's box draws left to right once per run.
- **Ledger tables.** Label-face heads on a 2 px rule, 1 px printed row rules, mono right-aligned figures, sunk-paper row hover.
- **Equity chart (`EquityChart.vue`).** SVG on the grid: graphite DCA line, blue-pencil lump-sum line, dashed invested step line. One scrub marker, defaulting to the last session; pointer or ← → Home End move it and the readout above updates.
- **Empty sheet (`EmptySheet.vue`).** Pencil-sketched empty axes (`/images/empty-axes.webp`) beside the message and one action; compact variant drops the image.
- **Toast slips.** Raised paper slip with an inked border and a kind chip (Done uses the highlighter).

## Do's and Don'ts

- Do open every sheet with a title block and a literal sheet number.
- Do box and highlight exactly one answer per sheet.
- Do keep figures in the mono face with tabular numerals.
- Do use red pencil only for losses, SELL, errors and destructive actions, always with a sign or label.
- Don't add cards, rounded corners or drop shadows to separate content; use ruled lines.
- Don't put a small tracked label above a heading.
- Don't use emoji or unicode glyphs as icons; use lucide-vue-next.
- Don't draw the grid inside panels; the page ground carries it.
