# Accessibility Verification

This guide defines the automated and manual accessibility evidence expected for
the financial application. Use only synthetic accounts and synthetic financial
values during shared tests, screenshots, reports, or bug reproduction.

## Automated Gate

From the repository root:

```powershell
.\scripts\run-browser-checks.ps1 -TestPath e2e/accessibility.spec.ts
```

Use `-InstallBrowsers` on a new machine. The wrapper starts Spring Boot and Vite
against a unique PostgreSQL schema and removes that schema in a `finally` block.
The focused Playwright suite applies axe WCAG 2.0, 2.1, and 2.2 A/AA rules to:

- sign-in and account-creation forms
- empty-workspace onboarding
- all twelve financial sections
- a draft removal confirmation dialog
- dialog focus entry, Escape dismissal, and focus return

The hosted `Accessibility` job runs the same audit and blocks the final `Scans`
job. Do not disable a failing axe rule or exclude an application region without
a documented owner decision and a linked follow-up.

Automated results cannot determine whether announcements are understandable,
reading order is useful, or a workflow is practical with assistive technology.
Complete the manual protocol for accessibility-critical interaction changes and
before a production release.

## Manual Setup

Use the latest supported desktop browser with one screen reader:

- Preferred Windows pair: Chrome and NVDA
- Acceptable Windows fallback: Edge and Narrator
- Acceptable macOS pair: Safari and VoiceOver

Start the screen reader before opening the application. Create a synthetic
account, initialize an empty workspace, and enter only clearly synthetic values.
Run the keyboard journey without using a mouse or touch input.

## Keyboard Journey

Confirm each item:

- Focus is always visible and follows a logical order.
- Sign In and Create Account form one tab stop; Left/Right and Home/End move and
  select the account tabs.
- Every form control, section button, save/reset/export action, and table row
  action is reachable and operable.
- The financial navigation identifies the current section.
- Opening a removal dialog moves focus to Cancel, Tab and Shift+Tab remain in
  the dialog, Escape closes it, and focus returns to the invoking control.
- No workflow creates a keyboard trap or requires hover-only interaction.

## Screen-Reader Journey

Confirm each item:

- The page title, main landmark, headings, account tabs, and active tab panel are
  announced with useful names and states.
- Signup fields and onboarding pay-period fields expose their labels, required
  state, and native input type.
- Workspace-ready, unsaved-draft, save-success, conflict, and error messages are
  announced once with an appropriate status or alert priority.
- Financial section navigation and the compact Financial section selector expose
  useful names and the selected/current state.
- Tables announce captions, headers, empty rows, data cells, and named row
  actions without relying on visual position alone.
- Form helper text is understandable in context, and draft add/edit/remove
  actions announce distinct accessible names.
- The removal dialog announces its title and description before its actions.
- The conflict recovery action clearly states that reloading discards the local
  draft.

## Evidence Record

Record the following in the pull request, release evidence, or linked issue:

```text
Commit:
Date:
Tester:
Operating system:
Browser and version:
Screen reader and version:
Keyboard journey: pass/fail
Screen-reader journey: pass/fail
Issues or follow-ups:
```

Do not attach screenshots, traces, copied announcements, exports, or database
rows containing personal financial data. A manual run is evidence for the
tested browser and assistive-technology pair only; it is not a universal
accessibility certification.
