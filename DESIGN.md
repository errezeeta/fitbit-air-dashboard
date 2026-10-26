---
version: alpha
name: Salud diaria
description: Dark-first personal health dashboard. Petrol surfaces, one mint accent, tabular numerals.
colors:
  primary: "#4FE3C1"
  onPrimary: "#04231C"
  primaryContainer: "#0E3B33"
  onPrimaryContainer: "#B9F7E7"
  secondary: "#86C7DC"
  tertiary: "#D3B173"
  background: "#07121A"
  surface: "#07121A"
  surfaceContainerLow: "#0D1F29"
  surfaceContainer: "#122833"
  surfaceContainerHighest: "#1E3A47"
  onSurface: "#E6F0EE"
  onSurfaceVariant: "#A8BDC4"
  outline: "#3C5560"
  error: "#FFB4AB"
typography:
  display-sm:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: 600
    lineHeight: 42px
    letterSpacing: "-0.8px"
    fontFeature: tnum
  headline-md:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: 600
    lineHeight: 34px
    letterSpacing: "-0.5px"
    fontFeature: tnum
  headline-sm:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: 600
    lineHeight: 28px
    letterSpacing: "-0.3px"
    fontFeature: tnum
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 600
    lineHeight: 22px
    letterSpacing: "-0.1px"
    fontFeature: tnum
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 400
    lineHeight: 18px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 500
    lineHeight: 14px
    letterSpacing: "0.7px"
rounded:
  sm: 8px
  md: 14px
  lg: 20px
  xl: 24px
  "2xl": 28px
spacing:
  xs: 4px
  sm: 8px
  md: 14px
  lg: 18px
components:
  app-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.onSurface}"
    rounded: "{rounded.sm}"
  hero-card:
    backgroundColor: "{colors.primaryContainer}"
    textColor: "{colors.onPrimaryContainer}"
    rounded: "{rounded.2xl}"
    padding: 16px
  metric-tile:
    backgroundColor: "{colors.surfaceContainerHighest}"
    textColor: "{colors.onSurface}"
    rounded: "{rounded.lg}"
    padding: 11px
  vital-card:
    backgroundColor: "{colors.surfaceContainer}"
    textColor: "{colors.onSurface}"
    rounded: "{rounded.lg}"
    padding: 14px
  section-label:
    backgroundColor: "{colors.background}"
    textColor: "{colors.onSurfaceVariant}"
    typography: label-sm
  nav-bar:
    backgroundColor: "{colors.surfaceContainerLow}"
    textColor: "{colors.onSurfaceVariant}"
  brand-mark:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    rounded: "{rounded.md}"
    size: 38px
---

## Overview

A dark-first personal health dashboard fed by Google Health (Fitbit Air). It reads as a
device panel, not a marketing page: dense, quiet, and honest about what the sensor
actually measured.

The identity is the launcher icon — a mint ring with one heartbeat. Everything else is
built from it: petrol surfaces, a single mint accent, and generous rounding that keeps
dense numbers from feeling clinical.

**Dark is the identity.** The light scheme exists (#F7FAF9 background, #0E7C6B primary)
but it is a translation, not the source. When in doubt, design the dark one first.

## Colors

- **Primary (#4FE3C1):** the only accent that carries meaning — the day's progress, the
  selected bar, the active tab, the brand mark. Nowhere else.
- **Primary container (#0E3B33) + onPrimaryContainer (#B9F7E7):** the hero card. Exactly
  one per screen; a second one makes the screen shout.
- **Tertiary (#D3B173), warm sand:** reserved for sleep. Activity and rest must never
  share a colour, or the user cannot tell the two families apart at a glance.
- **Secondary (#86C7DC), cool blue:** supporting data — distance, vitals deltas.
- **Surface ladder:** background #07121A, then #0D1F29, #122833, #1E3A47. Cards sit one
  or two steps above the background; anything closer than that disappears on a phone in
  daylight.
- **onSurfaceVariant (#A8BDC4)** carries every secondary label. It measures 6.1:1 to
  9.7:1 against the surfaces above — never replace it with a lighter grey "for elegance".

## Typography

Inter, bundled (`res/font/inter_*.ttf`), one family for the whole app. System Roboto is
what makes an app look like it shipped by default.

- Numeric styles carry `fontFeatureSettings = "tnum"`. Counters that change must not shift
  the row they sit in.
- 11px is the floor. Chart ticks used to be drawn at 10px and were unreadable.
- Kickers are uppercase label-sm with 0.7px tracking, in onSurfaceVariant — except inside
  the hero, where they use onPrimaryContainer at 70% (5.97:1).
- One idea per text size: a screen has one headline, and everything else is body or label.

## Layout

- Screen padding is 18px horizontally, 14px between blocks.
- The overview leads with one focal metric (steps against goal), then three compact tiles,
  then one trend panel, then the vitals grid. Never four equal hero cards.
- Cards contain one entity. Do not nest a card inside a card to group things; use spacing.

## Elevation & Depth

No shadows and no tonal elevation. Depth is expressed with the surface ladder and one
border colour when a card sits on a surface of the same step. Translucent surfaces are
banned: without a blur behind them they turn the scrolling content into mud.

## Shapes

Five radii, no more: 8 (chips, small labels), 14 (rows, icon box), 20 (tiles, vitals),
24 (panels), 28 (hero). Charts use fully rounded bars (`radius = barWidth / 2`).

## Components

- **Hero card** — one per screen. Shows the focal number, its target, and *one* progress
  indicator. A linear bar next to a circular ring for the same value is a defect.
- **Metric tile** — three across: label, value with unit, one line of context. Values are
  one line with ellipsis; never wrap a number.
- **Vital card** — kicker, value with unit, name, delta against the period average, and a
  sparkline. A vital the device does not record simply is not drawn.
- **Section label** — uppercase label-sm in onSurfaceVariant. It names a group; it is not
  a button and does not need an icon.
- **Chart** — one Canvas, bars with a single vertical gradient of the same colour and a
  selection band at 10% that *glides* between columns. The band and the gradient are the
  only decoration allowed on a plot: no glows, no multi-colour gradients, no drop shadows.
- **App bar** — brand mark drawn in a Canvas (the launcher geometry, never a stock heart),
  product name, and the current section underneath.

## Do's and Don'ts

- Do leave an empty state that says what is missing and when it appears. Don't render a
  blank chart or statistics of zeros.
- Do describe the measurement. Don't interpret it: a sleep duration does not prove the
  sleep was continuous.
- Do keep secondary labels at onSurfaceVariant. Don't reach for a dimmer grey.
- Don't print a footnote apologising for your own metric. Label it honestly in place.
- Don't use a colour as the only signal — the selected bar and the active tab also differ
  in weight and shape.
- Don't add a sixth radius, a second accent for the same metric family, or a shadow.
