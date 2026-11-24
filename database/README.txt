Database migration completed: 2026-11-18
Target: localhost:3306/restaurantServiceDB (MySQL 8.4.13).

The target database did not exist before this operation. It was created with
utf8mb4 / utf8mb4_unicode_ci, then populated with schema generated from all
25 current JPA entities, including inherited audit and soft-delete fields.

Results: 25 tables, 390 columns, 50 foreign keys, 14 unique constraints.
Hibernate schema validation passed. Column names and constraint definitions
were independently compared to information_schema and matched the generated SQL.

Files:
- schema-from-entities.sql: schema-only baseline, already applied locally.
  Select an EMPTY target database before running this file on another server.
  Do not rerun the baseline on this database; it intentionally has no DROP commands.
- schema-inventory.txt: number of columns and rows in each table after migration.
- migration-report.txt: verification results at schema creation, before seeding.
- seed-demo.sql: 101 demo rows adapted from data.sql and applied locally.

Seed status:
The original ../data.sql was left unchanged. All 101 INSERT statements are
commented out. This root file is not automatically loaded by Spring Boot.
Several INSERTs omit required values which Java field initializers do not supply
when using raw SQL:
  branch: geofence_enabled=false, geofence_radius_meters=150,
          geofence_max_accuracy_meters=100, vat_applied_on_service_fee=false.
  promotion: all_branches=false, usage_count=0, version=0.
  invoice: e_invoice_attempt_count=0.
INSERT IGNORE can silently substitute implicit values for missing required
columns, so merely uncommenting the seed is not sufficient.
The corrected seed was applied in a single transaction after checking that the
target tables were empty. It includes 13 dishes, 5 accounts, 2 restaurants,
3 branches, 7 dining tables, orders, payments and other sample records.

Do not confuse this baseline with a versioned migration framework. The existing
application still uses its configured Hibernate ddl-auto=update on normal startup.
SQL files are ignored by the repository's existing *.sql gitignore rule.
