---
status: superseded by ADR-0003
---

# Payment owns the COD ledger

SwiftRoute will treat the future Payment/COD Ledger module as the sole source of truth for COD obligations, actual collections, driver cash custody, handover batches, and discrepancies. Order keeps only the expected COD amount and a payment-status projection, while Driver identifies the responsible courier and reports collection actions; this avoids equating delivery completion with payment and keeps COD separate from driver earnings.

## Considered options

- Keep `cod_collections` in Order: simpler initially, but makes Order own mutable financial facts and leaves collection, custody, and reconciliation coupled to delivery state.
- Reuse Driver earnings settlement: rejected because COD belongs to the tenant and is not driver compensation.
- Create a dedicated Payment/COD Ledger module: selected because one authoritative ledger can enforce monetary invariants and expose a small reconciliation interface to every caller.

## Consequences

Existing `order_db.cod_collections` becomes migration input rather than the long-term financial source of truth. Payment collection state and physical cash-custody state remain separate, and verified financial history is corrected with adjustment entries instead of destructive edits.
