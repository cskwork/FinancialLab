# Product

<!-- impeccable:product-schema 1 -->

> Written 2026-09-24 without a live interview: the owner authorized an unattended revamp, so every fact below is inferred from the repository (README.md, PRD.md, frontend source, seed data) and the deployed site. Items marked **(assumed)** were not confirmed by the owner.

## Platform

web

## Users

- Primary (from PRD.md "Audience"): senior engineers and technical recruiters evaluating the author's fintech engineering skill. They arrive from a portfolio link, click through for a few minutes on desktop or phone, and judge whether the app behaves like production software.
- Secondary (assumed): a curious individual investor using the demo to try dollar-cost-averaging vs lump-sum questions on sample data.

## Product Purpose

FinLab is an educational, offline-first portfolio simulator. It tracks a demo portfolio (FIFO cost basis), records transactions, sets goals and recurring DCA plans, backtests DCA against lump-sum on local OHLCV data, and exposes the same operations through an OpenAPI playground. Success: a reviewer can log in with one click, see a populated portfolio, run a backtest, and understand that the calculations are real and run off the main thread.

## Positioning

Runs entirely in the browser (IndexedDB + Web Workers) behind the same contract a Spring Boot backend implements; one toggle switches Mock to Live. The OpenAPI playground, Javadoc and coverage links are evidence that the frontend and backend share one contract.

## Operating Context

- Mock mode is the default and works offline; Live mode needs a backend URL (default placeholder `https://finlab-api.fly.dev`, not verified to be running).
- Login is mock: any email, no password; `demo@finlab.app` is the demo account.
- UI language defaults to Korean with an English toggle; several page strings are still English-only.
- Deployed on Vercel as a static SPA (`vercel.json`), built from `frontend/`.

## Capabilities and Constraints

- Routes: landing, login, dashboard, transactions (add, delete, filter, CSV import), goals & plans, backtest lab, API playground, docs hub.
- Sample data: AAPL and SPY daily candles, 2024-01-02 to 2024-04-01 (about 62 sessions each), and a seed transaction list.
- Educational simulation only: no real money, no live market data, no trading.
- No backend or database schema migrations in frontend work; IndexedDB schema stays at version 1.

## Brand Commitments

- Name: FinLab. No logo asset exists beyond an emoji placeholder.
- Disclaimer "Educational simulation. No real money." must stay visible.

## Evidence on Hand

- Real: OpenAPI contract at `frontend/public/docs/openapi.json`; sample OHLCV CSVs and seed transactions under `frontend/public/data/`.
- Placeholder only: Javadoc, coverage and test-report pages under `frontend/public/docs/` are stubs.
- Absent, never fabricate: users, testimonials, performance claims, uptime, returns guarantees, a demo video.

## Product Principles

1. Numbers first: every screen leads with the figure the user came for, in tabular, aligned numerals.
2. Show the working: calculations (FIFO, Sharpe, drawdown, DCA) are explained where they appear, never implied to be advice.
3. One-click demo: a reviewer reaches populated data without typing.
4. Honest about simulation: mock vs live state and "no real money" are always visible.

## Accessibility & Inclusion

- WCAG 2.2 AA contrast and keyboard access (assumed target).
- Gains and losses must not rely on color alone (sign and label accompany color).
- Korean and English text must both fit; avoid fixed-width labels.
