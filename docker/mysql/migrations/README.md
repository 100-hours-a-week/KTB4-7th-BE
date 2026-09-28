# Existing database migration

`20260929-add-analysis-schema.sql` is for QA and production databases that
were created before the analysis-run schema was added.

## Apply order

1. Take or confirm a database backup.
2. Run `20260929-add-analysis-schema.sql` once.
3. Save the two result sets printed by the script.
4. Run the BE smoke flow: application startup, sales upload, analysis, insight,
   forecast, and solution generation.
5. Review legacy `sales_analyses` rows returned by the orphan query with BE
   before adding the deferred `sales_analyses.analysis_run_id` foreign key.

The migration does not delete or modify existing business data. Fresh databases
use `docker/mysql/init` and receive the complete schema, including the foreign
key, during initialization.
