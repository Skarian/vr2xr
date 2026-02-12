# ExecPlan Index

This file tracks all ExecPlans for this repository. It is required by `.agent/PLANS.md`.

## Conventions

- Active plans live in: `.agent/execplans/active/`
- Archived plans live in: `.agent/execplans/archive/`
- Plan filename format: `EP-YYYY-MM-DD__slug.md`
- Plan header fields live inside each plan file and must match the index entry.

## Index entry format (use this consistently)

For each plan, add a single bullet in the appropriate section:

- `EP-YYYY-MM-DD__slug` — `<Title>` — `Status:<DRAFT|ACTIVE|BLOCKED|DONE|ARCHIVED>` — `Created:YYYY-MM-DD` — `Updated:YYYY-MM-DD` — `Path:<repo-relative path>` — `Owner:<UNCONFIRMED|name>` — `Summary:<one line>` — `Links:<optional>`

For archived plans, also include:

- `Archived:YYYY-MM-DD` — `Outcome:<one line>`

Keep entries short, greppable, and consistent.

## Active ExecPlans

- `EP-2026-02-12__vr2xr-mvp-build` — `Build vr2xr MVP End-to-End for Sideload Use` — `Status:ACTIVE` — `Created:2026-02-12` — `Updated:2026-02-12` — `Path:.agent/execplans/active/EP-2026-02-12__vr2xr-mvp-build.md` — `Owner:nskaria` — `Summary:Exhaustive multi-phase plan to deliver single-user MVP through signed sideload release` — `Links:PRD_v2.md`

## Archived ExecPlans

- (none yet)
