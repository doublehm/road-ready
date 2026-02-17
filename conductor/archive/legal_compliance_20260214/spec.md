# Specification: Legal & Compliance Track

## 1. Objective
Ensure the Road Ready startup is "bulletproof" from a legal and operational standpoint, specifically adhering to British Columbia's regulations. This involves establishing the correct corporate structure, fulfilling regulatory registrations, and implementing robust safety and liability protections.

## 2. Core Requirements

### 2.1. Corporate & Tax Compliance
- **Incorporation:** Transition to a B.C. Limited Company.
- **Naming:** Reserve and register the "Road Ready" business name.
- **Taxation:** Register for GST/PST as required by revenue thresholds.
- **WorkSafeBC:** Register for coverage to protect against occupational injuries for contractors/employees.

### 2.2. Safety & Liability Ecosystem
- **Terms of Service (ToS):** Implement comprehensive legal text covering liability disclaimers, especially for on-road incidents.
- **Privacy Policy:** Define data collection, storage, and sharing policies, focusing on sensitive driver's license data.
- **Session Disclaimers:** Mandatory legal disclaimer acceptance before any logging or session starts.
- **Instructor Verification:** Workflow for verifying ICBC certification and commercial insurance.

### 2.3. Financial Integrity
- **Money Transmission:** Ensure compliance by utilizing Stripe's managed payout system to avoid being classified as an unlicensed money transmitter.

## 3. Implementation Phases

### Phase 1: Foundational Documents (Current Prototype)
- Draft and deploy `/terms` and `/privacy` pages.
- Add legal footers to all web and mobile views.

### Phase 2: Regulatory Registration (External Actions)
- Document the steps for incorporation and tax IDs.
- Set up WorkSafeBC account.

### Phase 3: Integrated Safety Features (App Logic)
- Implement the "Accept Terms" modal in the mobile app.
- Build the instructor insurance/license upload and verification status UI.
