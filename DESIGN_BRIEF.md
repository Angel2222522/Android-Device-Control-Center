# Device Control Center — Premium Design Brief

**Status:** Direction recorded; implementation pending.  
**Recorded:** 2026-08-09

## Product ambition

Device Control Center should be an app users want to open, not a utility they tolerate. The experience must feel distinctive, premium and emotionally compelling from the first launch, aiming above the typical visual quality of Google Play utility apps.

“Professional” is the floor. The target is a product with a memorable visual identity, confident composition and a satisfying interaction rhythm while remaining honest about what Android can and cannot expose.

## What the current screenshots tell us

The current screen is a functional technical prototype, not an acceptable final design:

- oversized uniform cards give every signal the same visual weight;
- long technical paragraphs make scanning difficult;
- the primary device state is not presented strongly enough;
- evidence, limitations and the main user-facing conclusion compete for attention;
- the layout relies too heavily on default-looking surfaces and spacing;
- unavailable values are truthful, but the presentation does not yet make them feel intentional.

This is recorded design debt. The redesign should improve the composition, not merely change colors or round the existing cards.

## Design principles

1. **Immediate desire and clarity** — the first viewport should create a strong first impression and communicate what matters now within seconds.
2. **One clear focal point** — the current device state and most important attention item lead the screen; secondary metrics support it.
3. **Scan before depth** — compact metric surfaces provide quick answers; evidence, provenance and limitations remain available through progressive disclosure.
4. **A designed visual system** — typography, spacing, shape language, color, iconography and motion should feel intentional and coherent rather than assembled from defaults.
5. **Semantic emotion, not fake certainty** — normal, informational, warning, critical and unavailable states should feel visually different without implying unsupported precision.
6. **Truth survives polish** — no fabricated CPU percentage, battery voltage, health score, optimization percentage or causal diagnosis. Real uncertainty must remain legible.
7. **Physical-device quality** — the result must be checked at the target phone's actual size, density, font scale and dynamic data conditions.

## Next bounded milestone — Overview design/UX v1

In scope:

- redesign the existing overview screen and its current snapshot/diagnosis surfaces;
- establish the first version of the visual language and component hierarchy;
- improve status summary, metrics scanning, warning emphasis and technical-detail disclosure;
- preserve all current truthful data, unavailable states and refresh behavior;
- add only the tests needed to protect presentation/state semantics.

Out of scope:

- new collectors or diagnosis rules;
- background monitoring, history or trend charts;
- new permissions or privileged operations;
- automatic optimization actions;
- final logo/name decision;
- speculative screens for unfinished features.

## Acceptance gate

The milestone is complete only when:

- the code is implemented and remains truthful;
- lint, unit tests, Android 16 build and GitHub Actions pass;
- the APK is inspected for layout and resource integrity;
- the app is installed and visually inspected on the OPPO A60 5G;
- the first viewport, scrolling, warning/unavailable states, refresh feedback and technical-detail readability are checked;
- the result is judged as a deliberate premium experience, not merely a functional arrangement of default cards.
