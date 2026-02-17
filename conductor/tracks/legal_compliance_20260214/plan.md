# Implementation Plan: Legal & Compliance

## Phase 1: Foundational Documents & Footers
- [x] Task 1: Verify current `/terms` and `/privacy` template content against BC boilerplate standards. [1ebcf1b]
- [ ] Task 2: Add a standard legal footer to the base web template (`app/templates/base.html`).
- [ ] Task 3: Add a "Legal" section to the mobile app settings/profile screen.

## Phase 2: Regulatory & Corporate Setup
- [ ] Task 4: Document the incorporation checklist for a B.C. Limited Company.
- [ ] Task 5: Document the registration process for GST/PST and WorkSafeBC.
- [ ] Task 6: Add fields to the User/Instructor models for tax and registration identifiers (internal use).

## Phase 3: Integrated Safety & Verification Logic
- [ ] Task 7: Implement a mandatory "Session Start Disclaimer" in the mobile app.
- [ ] Task 8: Enhance the Instructor setup/profile to include document uploads for ICBC certification and insurance.
- [ ] Task 9: Implement a "Verified" badge system for instructors who have completed document checks.

## Phase 4: Financial Compliance Verification
- [ ] Task 10: Audit the current Stripe payout logic to ensure it adheres to non-transmission rules.
