# Design System: SwiftNote

## 1. Visual Theme & Atmosphere

A refined, gallery-airy mobile note-taking interface with confident spatial hierarchy and fluid spring-physics motion. The atmosphere is warm yet clinical — like a well-lit Scandinavian workspace. Density is balanced (5/10), variance is offset-asymmetric (6/10), and motion is fluid (7/10). The design feels effortlessly premium: generous whitespace, disciplined color restraint, and purposeful micro-interactions that reward touch.

**Platform:** Android Mobile — touch-first, single-hand optimized
**Architecture:** Dark surface canvas with warm neutral tones; an ink-on-paper feeling in light mode

## 2. Color Palette & Roles

### Light Theme
- **Snow Canvas** (#FAFBFC) — Primary background surface, page-level canvas
- **Pure Surface** (#FFFFFF) — Card fills, elevated containers, input fields
- **Warm Mist** (#F8F9FA) — Secondary surface, subtle section differentiation
- **Charcoal Ink** (#1A1D23) — Primary text, headings, maximum weight
- **Slate Body** (#4A5568) — Body text, descriptions, medium weight
- **Muted Steel** (#94A3B8) — Placeholder text, metadata, timestamps
- **Whisper Border** (#E2E8F0) — Card borders, dividers, structural 1px lines
- **Teal Accent** (#0D9488) — Single accent for CTAs, FABs, active states, focus rings (saturation: 72%)

### Dark Theme
- **Deep Slate** (#0F1419) — Primary background, near-black canvas
- **Elevated Surface** (#1C2128) — Card fills, containers
- **Soft Surface** (#282E36) — Input fields, secondary containers
- **Snow Text** (#F0F4F8) — Primary text on dark
- **Muted Light** (#94A3B8) — Secondary text on dark
- **Dim Steel** (#64748B) — Metadata, timestamps on dark
- **Subtle Edge** (#334155) — Borders on dark surfaces
- **Teal Glow** (#2DD4BF) — Accent on dark, slightly lighter for visibility

### Status Colors
- **Success Verdant** (#059669) — Sync complete, success states
- **Caution Amber** (#D97706) — Warnings, pending states
- **Danger Crimson** (#DC2626) — Destructive actions, errors

## 3. Typography Rules

- **Display/Headlines:** Satoshi (weight 700) — Track-tight (-0.02em), controlled scale. Size hierarchy: 28sp → 22sp → 18sp → 16sp
- **Body:** Satoshi (weight 400/500) — Relaxed leading (1.5), max 65ch, Slate Body color
- **Mono:** JetBrains Mono — For timestamps, sync metadata, character counts
- **Banned:** Inter, Roboto defaults for premium contexts. No generic serifs.
- **Android Fallback:** System default sans-serif with above weight rules applied

### Scale (Mobile)
| Role | Size | Weight | Tracking |
|------|------|--------|----------|
| Screen Title | 28sp | 700 | -0.02em |
| Section Header | 22sp | 600 | -0.01em |
| Card Title | 18sp | 600 | 0 |
| Body | 16sp | 400 | 0 |
| Caption/Meta | 13sp | 500 | 0.02em |
| Mono Data | 12sp | 400 | 0.04em |

## 4. Component Stylings

### Buttons
- **Primary (FAB):** Teal Accent fill, rounded-full (pill), no outer glow. Tactile scale-down (0.95) on press with spring physics. Subtle shadow (0dp 4dp 12dp rgba(13,148,136,0.25))
- **Secondary:** Ghost/outline style. 1px Teal border, transparent fill. Teal text. On press: Teal fill at 8% opacity
- **Destructive:** Danger Crimson fill, white text. Same tactile behavior
- **Icon Buttons:** 44dp minimum touch target. Circular container with surface tint on hover/press

### Cards (Note Cards)
- Generously rounded corners (16dp radius)
- Pure Surface fill with 1px Whisper Border
- Whisper-soft shadow: 0dp 2dp 8dp rgba(0,0,0,0.04)
- Left accent stripe (3dp width) using note-specific color from rotation palette
- Internal padding: 16dp horizontal, 14dp vertical
- No nested cards — flat hierarchy

### Note Card Accent Rotation
Cycle through these for visual variety (one per note, hash-based):
- Teal (#0D9488), Amber (#D97706), Rose (#E11D48), Indigo (#4F46E5), Emerald (#059669), Sky (#0284C7)

### Inputs
- Label above (Caption weight), Charcoal Ink color
- Field: Pure Surface fill, 12dp radius, 1px Whisper Border
- Focus: 2px Teal Accent ring, border transitions to Teal
- Error: Danger Crimson border, error text below in 13sp

### Loaders
- Skeletal shimmer matching exact note card dimensions
- Gradient sweep from Warm Mist → Pure Surface → Warm Mist (1.5s loop)
- No circular spinners

### Empty States
- Composed illustration: subtle line-art icon (64dp) + descriptive headline + helper body text
- Centered vertically in available space
- Teal accent on the illustration stroke

### Bottom Sheets
- Surface fill with 24dp top radius
- Drag handle: 40dp × 4dp, Muted Steel color, centered, 12dp top margin
- Overlay scrim: #1A1D23 at 40% opacity

## 5. Layout Principles

- **Spacing scale:** 4dp base unit. Common: 8, 12, 16, 20, 24, 32, 48dp
- **Screen padding:** 20dp horizontal, 16dp vertical (top safe area respected)
- **Card gaps:** 12dp between note cards in list, 12dp grid gutter
- **Section spacing:** 24dp between major sections
- **Max content width:** 560dp (centered on tablets)
- **Grid:** 2-column for GRID view mode, single column for LIST, full-width for CARD
- **No overlapping elements** — every component occupies clear spatial zone
- **Bottom nav / FAB clearance:** 88dp minimum from bottom edge

## 6. Motion & Interaction

- **Spring Physics:** stiffness 300, damping 24 — snappy yet weighty feel
- **FAB:** Perpetual subtle float animation (translateY ±2dp, 3s infinite)
- **Card Press:** Scale to 0.97 + subtle shadow reduction on touch-down
- **List Entry:** Staggered fade+slideUp cascade (50ms delay per item, 300ms duration)
- **Screen Transitions:** Shared axis motion (Material 3 forward/backward)
- **Search Bar:** Expand from icon with spring physics, fade-in content
- **Bottom Sheet:** Spring slide-up from below viewport
- **Performance:** Animate exclusively via `graphicsLayer` transforms and alpha. Never animate size/padding directly

## 7. Anti-Patterns (Banned)

- No pure black (#000000) — use Deep Slate (#0F1419) or Charcoal Ink (#1A1D23)
- No purple/indigo as primary accent — replaced with Teal
- No neon outer-glow shadows
- No oversaturated gradients on backgrounds
- No 3-column equal card grids on mobile
- No circular loading spinners (use skeletal shimmers)
- No generic "Roboto" feel — enforce weight hierarchy for premium distinction
- No cards-inside-cards nesting
- No excessive material elevation stacking
- No AI copywriting cliches in UI copy
- No bouncing scroll indicators or "scroll down" hints
- No custom cursor effects (mobile-irrelevant)
- No floating labels on inputs — always label above

## 8. Screen-Specific Guidelines

### Main Notes Screen
- Top area: Screen title (left-aligned, 28sp, 700 weight) + icon actions (right)
- Search bar: Below title, full-width, pill-shaped, Warm Mist fill
- View mode toggle: Compact segmented control (right-aligned below search)
- Notes grid/list: LazyColumn/LazyVerticalGrid with staggered entry animation
- FAB: Bottom-right, 56dp, Teal fill, plus icon, perpetual float micro-animation

### Add/Edit Note Screen
- Clean top bar: Back arrow (left), action icons (right), no excessive decoration
- Title field: Large (22sp, weight 600), no visible border, placeholder in Muted Steel
- Body field: Full available height, 16sp, relaxed leading
- Bottom toolbar: Formatting options, reminder chip, character count (Mono, right-aligned)

### Onboarding Screen
- Single centered content block with generous vertical spacing
- App icon/wordmark at top (not oversized)
- Three option cards with icon + title + description
- Cards use left accent stripe, not full-color fill

### Sync Settings Screen
- Section groups with subtle Warm Mist background blocks
- QR code: Contained in a card with 24dp padding, centered
- Status indicators: Small colored dots (8dp) + descriptive text

