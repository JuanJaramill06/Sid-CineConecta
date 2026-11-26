# Product

## Register

product

## Users

- **Customers** (account holders and their profiles): browse a catalog of movies, series and documentaries, keep a "My list", play content in a simulated player and come back to "Continue watching". They pick a profile first ("Who is watching?"), and each profile has an age-rating limit, so a kids' profile only sees what it is allowed to.
- **Administrators**: back-office staff, independent of customer accounts. They create and edit catalog entries (a different form per type: movie, series with seasons and episodes, documentary), publish or withdraw them, and read two reports (income by plan, consumption by plan and content type) with date filters and CSV export.
- Context: a university project (CineConecta) that will be demonstrated live in a "sustentación". The flows that must read clearly are: contract a subscription and play content, rejections (expired subscription, rating not allowed), both reports with CSV.

## Product Purpose

A subscription streaming platform whose catalog and viewing activity live in MongoDB and whose accounts, plans, subscriptions and payments live in Oracle. The interface has to make the business rules visible and understandable: what a profile can see, why playback is refused, what is published or withdrawn, what the reports say. Success: every rule of the statement can be shown through the UI without explaining the database underneath.

## Brand Personality

Auteur cinema: sober, editorial, confident. The posters and titles are the protagonists; the chrome stays quiet. Voice in Spanish, direct and warm, never cute. Three words: **curated, calm, cinematic**.

## Anti-references

- SaaS templates: identical icon cards, purple gradients, giant hero metrics.
- Generic coursework UI: default Bootstrap look, grey forms, no hierarchy.

## Design Principles

1. **Content first**: covers, titles and synopses carry the screen; controls appear where the decision is made.
2. **Rules are visible, not hidden**: an unavailable or not-allowed item says so and why (withdrawn, rating, expired subscription), instead of silently disappearing or failing.
3. **Two rooms, one house**: the customer side is cinematic, the admin side is a precise working tool; same tokens and type, different density.
4. **Honest states**: loading, empty, error and success are designed states with useful copy, never a bare "Error".
5. **Demo-ready**: every screen reads at a glance on a projector; the key flows need no explanation.

## Accessibility & Inclusion

WCAG 2.1 AA: text contrast ≥ 4.5:1 (≥ 3:1 for large text), visible focus on every interactive element, full keyboard navigation, form errors announced next to their field, and `prefers-reduced-motion` respected for every animation.
