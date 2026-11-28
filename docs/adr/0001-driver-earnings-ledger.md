# Driver earnings are an event-driven ledger

SwiftRoute records driver earnings in Driver Service from an idempotent delivery-completed event instead of deriving totals from the current order state or Flutter UI. Each earning keeps the applied policy snapshot, and settlement only changes ledger status; this separates customer delivery fees and COD from driver compensation while allowing pricing policy to evolve without rewriting historical earnings.
