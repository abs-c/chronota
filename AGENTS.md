# Chronota engineering rules

1. Plan and Record are separate domain entities.
2. A Plan never becomes a Record.
3. Creating a Record from a Plan copies content only; do not write or expose Plan links. Legacy nullable link columns remain solely for schema compatibility.
4. Records may exist without Plans.
5. Timer and Pomodoro sessions ultimately produce Records.
6. Historical Plan behavior is controlled by HistoricalPlanPolicy.
7. Temporal state must be calculated by centralized domain logic.
8. UI code must not independently implement temporal-state rules.
9. Database schema changes require explicit consideration and migration.
10. Never use destructive Room migration for user data.
11. Never change the `applicationId` (`app.chronota`), the Room database file name (`chronota.db`), or an existing signing key: they define the installed app and where its data lives, so changing them orphans user data. Bump the schema version and ship a non-destructive migration instead. The 0.1.0 preview established this identity; it is fixed from here on.
12. Business data belongs in Room.
13. User preferences belong in DataStore.
14. Today is timeline-first. Avoid adding unnecessary permanent UI elements.
15. Equivalent actions from different gestures must reuse the same domain/repository logic.
16. Do not implement persistence directly inside Composables.
17. Do not hard-code user-visible strings. Maintain English and Simplified Chinese resources together.
18. UI styling must use the shared Chronota Design System.

## Working agreement

- Work in the milestone order in `docs/ROADMAP.md`. Build, run relevant tests,
  check regressions, and record evidence before advancing to the next milestone.
- Keep a single app module; avoid speculative interfaces and use-case layers.
- UI uses four primary tabs (Today, Plans, Records, Me), a floating glass dock, and an adjacent action orb. No fixed title bars on primary pages.
- Match controls to their purpose: switches, plain inputs, radio/checkbox choices, chevrons for navigation, plus rows for adding, and centered colored save buttons. Secondary pages have a back/title banner. Keep colors flat without gradients or heavy shadows.
- The page is a grouped gray and the content on it is white. Cards, row groups, the calendar, fields and chips are the white sheet, applied with the shared `cardSurface` modifier, which lifts 1dp off the page. Elevation beyond that is reserved for what floats: the glass dock and the action orb use 2dp. Display-only lists omit the trailing chevron; keep it where the row navigates or edits.
- Spacing follows the shared 4dp grid (4/8/12/16/24/32) and every size, radius, icon and stroke comes from `DesignTokens`. Do not add hard-coded dp values in feature code.
- Controls and tappable rows are at least 48dp high. Dense timeline blocks and heat-map cells are the only documented exceptions and keep their compact visual height.
- Editors and nested selections use solid central panels with consistent dimming. Keep controls 48dp high, dock and orb 56dp high, and use rounded icon strokes. Category defaults must merge without overwriting user data.
- Recurring plans store only their rule; occurrences are expanded by centralized domain logic and never duplicated into the database.
- Use system fonts without an explicit font family. Numbers use tabular figures through the shared typography tokens, never a monospaced family. Use shared spacing, color,
  shape, typography, and icon tokens. Support light and dark together.
- Use `Instant` for actual events; preserve local calendar semantics for Plans.
- Never manufacture sample history in the production database.
- Do not commit SDKs, machine paths, build products, credentials, or local caches.
- Every release is copied to the Desktop as `Chronota-<versionName>.apk` by
  `scripts/release.ps1`; keep one copy per version so previews stay distinguishable.
- A successful compilation is not evidence of a successful device test. Report
  build, JVM tests, emulator/device tests, and manual checks separately.

## Verification

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

See `README.md` for prerequisites. All future schema versions must be exported,
committed, and accompanied by non-destructive migrations and migration tests.
