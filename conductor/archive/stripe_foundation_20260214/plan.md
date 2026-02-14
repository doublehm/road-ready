# Implementation Plan: Stripe Foundation

This plan established the core Stripe Connect integration using Test Mode.

## Phase 1: Data Model & Onboarding

- [x] Task: Update InstructorProfile for Stripe Support (7772406)
    - [x] Write Tests: Verify new fields on InstructorProfile
    - [x] Implement Feature: Add `stripe_account_id` and `stripe_onboarding_completed` to `app/models.py`
- [x] Task: Implement Stripe Account Creation API (3154599)
    - [x] Write Tests: Mock Stripe API calls for account creation
    - [x] Implement Feature: Create endpoint to initiate Connect onboarding in `app/api/instructors.py`
- [x] Task: Handle Stripe Onboarding Webhook/Callback (2cffb1d)
    - [x] Write Tests: Verify callback correctly updates instructor status
    - [x] Implement Feature: Create redirect handler in `app/main.py`

## Phase 2: Payment Integration

- [x] Task: Update Booking Flow for Real-Time Payment Intents (2c3b66c)
    - [x] Write Tests: Verify PaymentIntent creation with fee split
    - [x] Implement Feature: Integrate Stripe Payment Intents into `submit_booking` in `app/main.py`
- [x] Task: Automated Payout Triggers (2ab54d2)
    - [x] Write Tests: Verify transfer to connected account upon lesson completion
    - [x] Implement Feature: Trigger Stripe transfers when booking status changes to 'completed'
