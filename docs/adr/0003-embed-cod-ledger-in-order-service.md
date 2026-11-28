---
status: accepted
---

# Embed the COD ledger in Order Service

SwiftRoute keeps Payment/COD Ledger as a distinct domain module but deploys it inside `order-service` and stores its tables in `order_db`. This preserves one authoritative financial interface and the separation between delivery, collection, custody and earnings while avoiding another service and database on the project's constrained infrastructure. COD commands are synchronous and use narrow `operation_id` constraints; the module deliberately has no outbox/inbox until an independent payment deployment or external accounting integration creates a concrete need.
