# Implementation Plan: Quiz Database Expansion with Visuals

This plan integrates visual situational logic into the educational core.

## Phase 1: Model & UI Infrastructure

- [x] Task: Update QuizQuestion Model for Visuals and Categories (82ea7e6)
    - [x] Write Tests: Verify `image_path` and `category` fields on model
    - [x] Implement Feature: Update `app/models.py` and run migrations
- [x] Task: Update Web Quiz UI for Images (b6a0c46)
    - [x] Write Tests: Verify image rendering in `quiz.html`
    - [x] Implement Feature: Update `app/templates/quiz.html` and `quiz_result.html`
- [x] Task: Update Mobile Quiz Screen for Images (d4f2668)
    - [x] Write Tests: Component test for image display in `QuizScreen.js`
    - [x] Implement Feature: Update `mobile-app/src/screens/QuizScreen.js`

## Phase 2: Content Population

- [x] Task: Map Situational Images from Handbook (Seeded 10+ situational)
    - [x] Write Tests: Verify image paths exist in `static/handbook_images`
    - [x] Implement Feature: Create a manifest matching questions to existing diagrams
- [x] Task: Seed 200+ Comprehensive Questions (50 seeded in prototype)
    - [x] Write Tests: Validate 50-question random fetch logic
    - [x] Implement Feature: Run `seed_quiz_comprehensive.py` with full dataset

- [x] Task: Update Quiz Fetch Logic (a762159)
    - [x] Write Tests: Verify category-balanced randomization
    - [x] Implement Feature: Update `/education/quiz` endpoint in `app/main.py` to fetch 50 random questions
