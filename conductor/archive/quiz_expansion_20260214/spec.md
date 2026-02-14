# Specification: Comprehensive Quiz Database Expansion with Visuals

## Overview
This track scales the practice quiz system to 200+ questions and introduces **Visual Situational Questions**. This format mimics the real ICBC knowledge test, where a student is shown a diagram or photo of a road scenario and must make a decision based on the rules of the road.

## User Stories
*   **As a Student:** I want to see situational photos (e.g., an intersection layout) so that the practice test feels exactly like the real ICBC exam.
*   **As an Admin:** I want to easily associate an image from the "Learn to Drive Smart" manual with a specific question.

## Functional Requirements
*   **Model Update:** Add `category` and `image_path` (optional) to the `QuizQuestion` model.
*   **Visual Integration:** Support for displaying PNG/JPG images above the question text in both web and mobile.
*   **Comprehensive Data:** Bulk load 200+ questions covering all chapters of the BC driving guide.
*   **Situational Sourcing:** Map existing images in `app/static/handbook_images/` to relevant questions.

## Non-Functional Requirements
*   **Clarity:** Images must be optimized for mobile screens so details (like turn signals or small signs) are visible.
*   **Data Integrity:** Questions referencing an image MUST have a valid `image_path`.
