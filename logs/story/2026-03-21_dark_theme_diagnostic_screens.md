# 2026-03-21: Dark Theme — Diagnostic Ride Screens

## Problem
Five diagnostic ride screens used a light theme (#f8f9fa backgrounds, #fff cards, dark text) while the rest of the app used a dark navy theme. This created a jarring visual inconsistency when navigating between screens.

## Screens Converted
1. **DiagnosticRideHistoryScreen** — ride list, stats summary, empty state
2. **DiagnosticRideIntroScreen** — intro modal and feature cards
3. **DiagnosticRideSetupScreen** — supervisor selection and parent name input
4. **DiagnosticRideResultsScreen** — score circles, category cards, violation/tips cards, speed summary
5. **GradeDiagnosticRideScreen** — manual grading form with score input and notes

## Already Dark (no changes needed)
- DiagnosticRideDetailScreen
- DiagnosticRideActiveScreen

## Dark Theme Palette
| Role | Color |
|------|-------|
| Background | `#0B1326` |
| Cards / surfaces | `#131B2E` |
| Borders / secondary surfaces | `#1E293B` |
| Primary text | `#FFFFFF` |
| Body text | `#E2E8F0` |
| Medium text | `#CBD5E1` |
| Secondary / label text | `#94A3B8` |
| Tertiary / muted text | `#64748B` |
| Muted icons | `#475569` |
| Blue accent | `#3B82F6` |
| Green accent | `#15803D` |
| Red accent | `#EF4444` |
| Amber / warning | `#F59E0B` |
| Purple accent | `#8B5CF6` |

## Changes Made
- Replaced all StyleSheet color values (backgrounds, text, borders, shadows)
- Updated all inline icon colors on Ionicons components
- Updated ActivityIndicator colors
- Updated TextInput placeholderTextColor props
- Converted special cards (violation, tips, summary) to translucent dark variants
- Submit button colors updated from `#007bff`/`#28a745` to `#3B82F6`/`#15803D`

## Root Cause
Screens were originally built with a standard light Bootstrap-like palette and never updated when the app adopted a dark theme.
