---
version: 1
slug: "frontend-src-pages-landingpage-vue"
primary_target: "frontend/src/pages/LandingPage.vue"
related_targets: ["frontend/src/App.vue"]
---

# Surface brief: FinLab app (landing + operate screens)

Scope: whole SPA redesign. Landing is Persuade (reviewer decides to open the demo); dashboard, transactions, goals & plans, backtest, API playground, docs are Operate.
Audience/job: engineers and recruiters click through the demo in minutes; they must reach populated data in one click and see the calculations working.
Constraints: preserve routes, copy meaning, mock/live toggle, ko/en toggle, "Educational simulation. No real money." disclaimer. No fabricated claims.
Decided unattended (owner authorized the run; no answer channel): assigned direction taken as rolled, code-led build.

## Direction contract

THESIS: FinLab is a computation pad, not a trading terminal. Every screen is a sheet from an engineer's green-tint computation pad where the working is shown and the answer is boxed. Refuses the dark neon trading dashboard and the generic blue SaaS card grid.

OWN-WORLD: pale green-tint pad paper with a faint 5 mm grid, graphite ink, red pencil for losses and corrections, blue pencil for the second series, highlighter yellow only on the boxed answer. Sheet header strip (PROJECT / SUBJECT / SHEET nn OF 05) on every page. Condensed DIN-style lettering in caps for labels, tabular mono figures, system sans for prose. Square corners, 1px ruled lines, no shadows. Dark scheme is the same pad as a blueprint: Prussian-blue ground, white linework.

STORY: The visitor reads the problem the engineering way: GIVEN (sample AAPL/SPY data, a demo portfolio), FIND (cost basis, risk, DCA vs lump sum), SOLUTION (FIFO, workers, OpenAPI contract), ANSWER boxed with the primary action. They open the demo and land on a populated sheet.

FIRST VIEWPORT: landing at 1440x900: header strip across the top; left 5 columns: "FinLab" in condensed caps at display scale, tagline, boxed primary "Try Dashboard" plus two ruled secondary links; right 7 columns: key-art plate of a pencil-worked pad with two plotted curves. GIVEN/FIND row starts at the fold. Mobile: header strip, title, CTAs, then plate.

FORM: engineering computation pad / lab notebook, position 7 of 7 on the grounded list, seed c4b5ee08. Raises: from drum-machine step row, one unbroken timeline with a scrub marker that always says where "now" is on the backtest plot; from HyperCard stack, the entry form is the ledger's next blank line in the same columns; from industrial quote grammar, wayfinding is literal: every sheet is labelled by number and subject.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
