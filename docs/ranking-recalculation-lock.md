# Ranking recalculation lock

Ranking recalculation coordination is selected with `RANKING_RECALCULATION_LOCK_PROVIDER`.

- `mysql` (default): keeps the existing period-row `PESSIMISTIC_WRITE` lock in each persistence transaction.
- `redis`: uses Redisson `RLock` with a key derived from the four ranking-period dates. It acquires the lock before calling the persistence service and releases it after the `calculate()` or `recordFailed()` transaction proxy returns.

Redis mode uses `RANKING_RECALCULATION_LOCK_WAIT_TIMEOUT` (default `30s`) as the maximum acquisition wait. It does not set a lease time; Redisson's Watchdog renews the lock while the owning client remains healthy. `RANKING_RECALCULATION_LOCK_WATCHDOG_TIMEOUT` defaults to `30s`. Set `RANKING_RECALCULATION_LOCK_KEY_PREFIX` to an environment-specific namespace when environments share a Redis deployment.

Acquisition and timeout failures fail closed: the calculation is not started and there is no automatic fallback to MySQL. A lock failure is logged and returned as `LOCK_FAILED`. Acquisition failure creates no Snapshot because the protected work did not start. A release failure can occur after a transaction committed, so `LOCK_FAILED` does not prove that no Snapshot was written. Every application instance that can recalculate rankings must use the same provider and Redis namespace during a rollout. Mixed MySQL/Redis providers do not coordinate with each other.

Redisson reads the standard `spring.data.redis.url` or host, port, username, password, database, TLS, and timeout settings. This configuration currently supports a single Redis server endpoint. Redis Cluster and Sentinel topology must be verified and explicitly supported before selecting `redis` if the deployed Redis uses either topology.

The application code does not change Redis ACLs. Before enabling Redis mode, provision the lock key prefix and Redisson lock/pub-sub command permissions for the application identity without broadening access to session or cache keys. The current test suite includes an opt-in standalone Redis integration test (`TEST_REDIS_LOCK_ENABLED=true`); local unit tests do not establish production failover or ACL behavior.
