# Unified driver onboarding

## Scope

Approved request: Driver Onboarding, Invitations and Fleet Management, 2026-11-04. The supplied brief stops during B4; do not infer additional product requirements beyond the preceding complete sections.

One Drivers directory and one Add Driver action. Manual creation and email invitation converge on account linking, profile completion, explicit submission and business approval. Subscription workflows are excluded.

## Repository findings

- DriverProfileForm currently requires an ACTIVE DRIVER member without a driver profile, preventing profile-first onboarding.
- RegisterDriverRequest already permits a null userId; Driver.userId is nullable and unique.
- DriverService.registerDriver currently sets ACTIVE and isAvailable=true and publishes availability immediately. This violates the requested onboarding gate.
- License plate is required by the request and entity, preventing optional vehicle completion.
- Vehicle is already a separate entity. Reuse fleet assignment rather than introduce another vehicle registry.
- Auth already owns TenantInvitation and TenantEmployeeService. Extend that workflow rather than create a second invitation system.
- Existing driver phone and plate uniqueness is global. Preserve the primary-business rule until cross-business identity semantics are verified; do not silently weaken uniqueness.

## State contract

Account linking, invitation lifecycle, profile review and live availability are separate facts. UI labels derive from those facts.

| Situation | Directory label | Can receive orders |
|---|---|---|
| Unlinked profile without pending invitation | Not invited | No |
| Valid pending invitation, with or without profile | Invitation sent | No |
| Linked account, required details missing | Complete profile | No |
| Complete profile, not submitted | Ready to submit | No |
| Submitted profile | Awaiting approval | No |
| Approved, linked, valid membership and suitable vehicle | Active | Subject to operational availability |
| Suspended membership or driver | Suspended | No |

Expired/revoked invitations remain visible and actionable. Invitation acceptance must match the authenticated verified email, require explicit confirmation, enforce expiry/revocation, and be safe to retry. Never link a profile using a client-supplied user ID alone.

## Implementation sequence

1. Inspect invitation acceptance, primary-business restrictions, fleet relationships, schema migrations and dispatch authorization. Define minimal backward-compatible DTO/schema changes and backup procedure before executing any migration.
2. Implement draft creation and readiness gates in Driver Service. Add explicit submit/review actions, scoped authorization and tenant-safe duplicates. Preserve existing data through migrations; do not change historical approvals blindly.
3. Extend Auth invitations with an optional existing-driver reference, email delivery, resend/revoke and secure acceptance. Ensure retries link once and never create duplicate profiles; define recovery for partial cross-service failure using existing integration conventions.
4. Build the unified Drivers workspace: Add Driver chooser, focused manual form, invite form, success actions, pending invitations, profile details and approval queue. Reuse current visual system and fleet screens. Supply mobile-friendly invite registration/sign-in/verification continuation.
5. Connect Flutter profile completion and submission, prefill existing details, explain missing requirements, and verify both journeys through approval and dispatch readiness. Report actual checks and remaining runtime limitations.

## Acceptance journeys

- Manual draft -> postpone -> reopen -> invite -> verified account accepts -> same profile linked -> complete -> submit -> approve -> eligible for dispatch.
- Email-only invite -> visible pending row -> registration -> verification -> explicit acceptance -> complete profile -> submit -> approve.
- Existing verified account accepts matching-email invitation without another account or duplicate driver.
- Wrong email, expired/revoked/reused token, duplicate phone/plate, cross-tenant access and suspended membership cannot bypass gates.
- Pending/unlinked/unapproved drivers cannot become available through direct API calls or appear as dispatch-ready.

## Verification boundary

Step 1 findings above are source inspection, not runtime proof. No application changes, migrations, mail delivery or end-to-end verification have been performed for this request yet.
