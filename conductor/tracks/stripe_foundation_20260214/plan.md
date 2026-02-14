# Implementation Plan: Stripe Foundation

This plan established the core Stripe Connect integration using Test Mode.

## Phase 1: Data Model & Onboarding

- [x] Task: Update InstructorProfile for Stripe Support (7772406)
    - [x] Write Tests: Verify new fields on InstructorProfile
    - [x] Implement Feature: Add `stripe_account_id` and `stripe_onboarding_completed` to `app/models.py`
- [x] Task: Implement Stripe Account Creation API (3154599)
    - [x] Write Tests: Mock Stripe API calls for account creation
    - [x] Implement Feature: Create endpoint to initiate Connect onboarding in `app/api/instructors.py`
- [ ] Task: Handle Stripe Onboarding Webhook/Callback
    - [ ] Write Tests: Verify callback correctly updates instructor status
    - [ ] Implement Feature: Create redirect handler in `app/main.py`

## Phase 2: Payment Integration

- [ ] Task: Update Booking Flow for Real-Time Payment Intents
    - [ ] Write Tests: Verify PaymentIntent creation with fee split
    - [ ] Implement Feature: Integrate Stripe Payment Intents into `submit_booking` in `app/main.py`
- [ ] Task: Automated Payout Triggers
    - [ ] Write Tests: Verify transfer to connected account upon lesson completion
    - [ ] Implement Feature: Trigger Stripe transfers when booking status changes to 'completed'
