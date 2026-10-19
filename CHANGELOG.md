# Changelog

## Unreleased

### Added

- Folia support: the plugin declares `folia-supported` and schedules work on the global region, entity and async schedulers when running on Folia. Paper and Purpur behaviour is unchanged.
- On Folia, `/aach generate` removes old advancements and regenerates them on the next restart, as Folia cannot reload server data at runtime.

### Fixed

- Cached statistic increments are now atomic, so concurrent updates are no longer lost.
- Ranking, inspect, book, toggle and cooldown caches are now safe to access from several threads.
- Reloading publishes achievements and GUI items atomically instead of clearing and refilling them in place.
- A null teleport destination no longer breaks distance tracking.

## 12.0

### Added

- `/aach doctor [page]` read-only diagnostics for configuration files, runtime compatibility, integrations, and database health.
- Explicit `achievement.inspect` and `achievement.doctor` permissions.
- Plugin website metadata and a Java 21/25 CI matrix.

### Changed

- The supported baseline is now Paper/Purpur 1.21+ with Java 21+.
- New installations use SQLite by default. Existing H2 1.4 databases remain supported without an automatic migration.
- PostgreSQL JDBC was updated to 42.7.13.
- Database reads and writes now share one ordered worker, preventing concurrent access to the shared JDBC connection.
- Shading preserves JDBC service providers and no longer minimizes the plugin jar.

### Fixed

- Cached statistics are marked persisted only after a successful write of the same revision.
- Failed SQL operations invalidate the cached connection before retrying.
- `/aach inspect` no longer resolves player profiles or sends messages from an asynchronous thread.
- Player-only commands now explain why they cannot be run from the console.
- Re-awarding an existing command achievement no longer repeats the all-achievements reward.
- Stale `TESTconfig.yml` references were replaced with `config.yml`.
