# SwiftRoute Driver

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Product Purpose

- SwiftRoute Driver is the operational mobile companion for drivers working for a tenant business in the SwiftRoute multi-tenant logistics platform.
- The mobile app is implemented in Flutter and currently ships a Material 3 experience with Android as the primary field-use target. The design must remain adaptable to iOS and larger screens.
- A driver signs in or activates an invited account, completes an approved driver/vehicle profile, starts a shift, receives an assigned route, navigates between pickup and delivery stops, records custody and delivery outcomes, reports incidents, hands over COD, and reviews earnings/settlement status.
- Dispatch planning belongs to the business workspace. The driver may accept or reject a route assignment, but should not run route optimization from the mobile app.
- Driver location is sent to the server while operationally available so dispatch can observe progress. The mobile UX must communicate GPS, connectivity, sync, and offline state clearly.
- Earnings are a ledger and settlement workflow, not an in-app wallet or payment product. Statuses include pending review, approved, paid, or cancelled.

Success means the driver can identify the next required action quickly, complete the route safely with minimal distraction, and keep dispatch informed even when connectivity is intermittent.

## Positioning

SwiftRoute Driver connects route execution, package custody, realtime operational status, COD handover, and earnings reconciliation in one tenant-aware driver workflow. It is an execution surface for business-assigned routes, not a customer ordering app, a driver-side route-planning tool, or a payment wallet.

## Users

- Primary user: a delivery driver working outdoors, often one-handed, under time pressure, in bright light, and with intermittent connectivity.
- Secondary context: a driver completing account activation/profile setup before becoming dispatch-ready.
- The interface language is Vietnamese. Copy should use operational language and avoid backend terminology.

## Operating Context

1. Know whether I am ready and online for a shift.
2. Understand and acknowledge the route assigned to me.
3. Reach the next stop with minimal distraction.
4. Confirm pickup, delivery, failure, return, evidence, and COD safely.
5. See what I earned and whether it has been approved or paid.
6. Keep my driver and vehicle profile operationally valid.

The product is used on mobile devices in outdoor field conditions. GPS quality, network connectivity, queued synchronization, battery-conscious location reporting, and offline route access are material parts of the experience.

## Product Principles

- Next action first: the most urgent operational action dominates each screen.
- Map when spatial context matters; list when sequence and detail matter.
- Strong outdoor contrast, short Vietnamese labels, and no hidden critical action.
- Network/GPS state is visible but quiet when healthy and explicit when action is required.
- Keep five top-level destinations at most. Prefer: Hôm nay, Lộ trình, Thu nhập, Bàn giao, Tài khoản.

## Capabilities and Constraints

- Foundations and reusable mobile components.
- Authentication and driver activation.
- Shift/home overview and route assignment acknowledgement.
- Route list, map navigation, stop detail, pickup/delivery proof, incident handling, and offline/sync states.
- COD/package handover.
- Earnings and settlement history.
- Driver profile, vehicle, approval/readiness, and account controls.
- Representative empty, loading, error, GPS-denied, and offline states.
- The implementation is a Flutter application using Material 3.
- Android phones are the primary current field devices; the product must adapt to iOS platform guarantees and larger window classes.
- Only approved drivers with complete required information may become dispatch-ready.
- Dispatch planning and route optimization belong to the business workspace, not the driver app.

## Out of scope

- Customer ordering screens.
- Tenant admin/dispatch web screens.
- Subscription, driver payouts, banking, or wallet architecture.
- Backend, database, or Flutter implementation changes in this design task.

## Assumptions to validate later

- Android phone is the primary production device; iOS support follows the same information architecture with native navigation guarantees.
- The current five-tab navigation remains viable after renaming and hierarchy cleanup.
- The business provides the assigned route; driver-side OR-Tools optimization will be removed from the target experience.

## Brand Commitments

- Product name: SwiftRoute Driver.
- The experience represents the driver's current tenant business and must not imply that SwiftRoute itself is the pickup warehouse or fleet operator.
- Voice is concise, professional, reassuring, and operational. Labels explain what the driver should do next rather than exposing membership, provisioning, synchronization internals, UUIDs, or service architecture.

## Evidence on Hand

- Flutter implementation and existing workflows: `mobile-app/lib/`.
- Driver application screens: `mobile-app/lib/features/driver/presentation/`.
- Existing image asset: `mobile-app/assets/images/login_background.png`.
- Domain terminology: `GLOSSARY.md`.
- Architecture and database documentation: `docs/`.
- Approved editable product redesign: [SwiftRoute Driver — Mobile SaaS Redesign](https://www.figma.com/design/2SokartkKpeTfeoCNl4EJp).
- No testimonials, customer logos, public performance benchmarks, or payout claims are confirmed; future work must not fabricate them.

## Accessibility & Inclusion

- Touch targets must be at least 48 dp on Android and respect the corresponding iOS minimum target guidance.
- Content requires strong outdoor contrast and must not rely on color alone to communicate delivery, warning, failure, or synchronization states.
- Text must support platform font scaling without hiding critical actions or statuses.
- Navigation must preserve Android system Back/predictive Back and iOS edge-swipe behavior.
- Motion must honor reduced-motion settings, and critical workflows must remain usable without animation.
