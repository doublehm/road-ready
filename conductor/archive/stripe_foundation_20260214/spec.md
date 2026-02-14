# Specification: Stripe Foundation for Payments and Payouts

## Overview
This track establishes the technical foundation for handling payments from students and automated payouts to driving instructors using Stripe Connect in Test Mode. It enables the platform to act as a marketplace, splitting lesson fees between the platform and the instructor.

## User Stories
*   **As an Instructor:** I want to connect my (test) bank account to the platform so that I can receive payouts for completed lessons automatically.
*   **As a Student:** I want to pay for my lessons securely, knowing that my payment confirms my booking.
*   **As an Admin:** I want the platform to automatically collect its service fee (15%) from every transaction without manual intervention.

## Functional Requirements
*   **Connected Accounts:** Support for Stripe Express/Custom connected accounts for instructors.
*   **Model Updates:** `InstructorProfile` must store a `stripe_account_id` and onboarding status.
*   **Onboarding Flow:** A secure redirect to Stripe for instructors to provide their (mock) payout details.
*   **Payment splitting:** logic to handle destination charges where the platform takes a 15% cut.

## Non-Functional Requirements
*   **Security:** Stripe keys must never be committed to git; use environment variables.
*   **Testability:** All features must work fully in Stripe Test Mode.
