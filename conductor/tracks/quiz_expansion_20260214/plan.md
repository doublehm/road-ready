# Implementation Plan: Quiz Database Expansion with Visuals

This plan integrates visual situational logic into the educational core.

## Phase 1: Model & UI Infrastructure

- [x] Task: Update QuizQuestion Model for Visuals and Categories (82ea7e6)
    - [x] Write Tests: Verify `image_path` and `category` fields on model
    - [x] Implement Feature: Update `app/models.py` and run migrations
- [x] Task: Update Web Quiz UI for Images (b6a0c46)
    - [x] Write Tests: Verify image rendering in `quiz.html`
    - [x] Implement Feature: Update `app/templates/quiz.html` and `quiz_result.html`
- [ ] Task: Update Mobile Quiz Screen for Images
    - [ ] Write Tests: Component test for image display in `QuizScreen.js`
    - [ ] Implement Feature: Update `mobile-app/src/screens/QuizScreen.js`

## Phase 2: Content Population

- [ ] Task: Map Situational Images from Handbook
    - [ ] Write Tests: Verify image paths exist in `static/handbook_images`
    - [ ] Implement Feature: Create a manifest matching questions to existing diagrams
- [ ] Task: Seed 200+ Comprehensive Questions
    - [ ] Write Tests: Validate 50-question random fetch logic
    - [ ] Implement Feature: Run `seed_quiz_comprehensive.py` with full dataset
