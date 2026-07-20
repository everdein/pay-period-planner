# Responsive Verification

This guide defines the automated and manual responsive evidence expected for
the financial application. Use only synthetic accounts and synthetic financial
values in shared tests, screenshots, reports, or bug reproduction.

## Automated Gate

From the repository root:

```powershell
.\scripts\run-browser-checks.ps1 -TestPath e2e/responsive.spec.ts
```

Use `-InstallBrowsers` on a new machine. The wrapper starts Spring Boot and Vite
against a unique PostgreSQL schema and removes that schema in a `finally` block.
The focused Playwright suite covers signup, onboarding, compact navigation, and
all twelve financial sections at these viewport sizes:

| Class        | Viewport   |
| ------------ | ---------- |
| Narrow phone | 320 x 800  |
| Phone        | 390 x 844  |
| Tablet       | 768 x 1024 |
| Desktop      | 1024 x 768 |

At every viewport, the suite requires:

- no page-level horizontal overflow
- all visible controls to remain inside the viewport
- all visible controls, except native checkboxes, to be at least
  24 by 24 CSS pixels
- every table and list to remain inside its component without horizontal
  scrolling
- compact section navigation at 900 pixels and below, with the full sidebar
  restored above that breakpoint
- a full-width desktop workspace with the navigation rail anchored to the left
  browser edge and content spacing kept inside the main canvas
- the contrasting desktop navigation rail to collapse cleanly into the neutral
  mobile section picker without changing the active section

Monthly and annual withdrawals, income summary, income calendar, asset
accounts, debt, and important dates use the same responsive record-list
pattern. Each item keeps its primary value, supporting metadata, status, and
actions together at every width. Rows use compact vertical spacing while
preserving readable type and action targets; status and actions remain inline
when space allows and wrap naturally on the narrowest content. Overview and
projection retain native table semantics and become labeled detail rows at the
compact breakpoint. Neither a record list, table component, nor the document
may scroll sideways. The browser audit switches to the dark theme before
traversing account creation and the financial sections so themed controls are
included in the same geometry checks.

The hosted `Responsive` job runs the same suite and blocks the final `Scans`
job. Treat a new overflow exclusion, smaller supported width, or breakpoint
change as a product decision that requires updated tests and documentation.

## Manual Checks

Automated geometry checks cannot judge readability or interaction quality.
For responsive-critical changes, inspect each supported viewport and confirm:

- headings, values, notices, actions, and long synthetic account names remain
  readable without overlap or clipping
- forms follow a useful reading order and native date controls remain operable
- primary values, supporting metadata, status, and actions remain easy to scan
- record-list actions wrap without separating a command from its context
- both light and dark themes preserve readable contrast without changing
  geometry or exposing a surface without theme styling
- portrait and landscape orientation changes preserve the current section and
  unsaved draft
- browser zoom at 200 percent does not create page-level horizontal scrolling
  at a 1280-pixel desktop viewport

Record the browser, operating system, viewport or device emulation, zoom level,
result, and any follow-up in the pull request or linked issue. Do not attach
screenshots or traces containing personal financial data.
