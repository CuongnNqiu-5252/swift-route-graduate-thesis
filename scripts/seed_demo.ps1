# Helper script to seed demo data with pristine UTF-8 encoding
docker cp scripts/seed_demo_full.sql swiftroute-postgres:/tmp/seed_demo_full.sql
docker exec -i swiftroute-postgres psql -U openpg -d postgres -f /tmp/seed_demo_full.sql
Write-Host "Demo data seeded successfully with pristine UTF-8 encoding!" -ForegroundColor Green

