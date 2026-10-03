package com.bravoappointments.tenant;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialises booking changes per tenant with a Postgres transaction-scoped advisory lock, so
 * "check the slot is free, pick a barber, insert" is atomic for that business. The lock is released
 * automatically on commit/rollback, which makes it safe behind PgBouncer's transaction pooling.
 * The exclusion constraint on appointment remains the final backstop.
 */
@Component
public class TenantLock {

    private final JdbcTemplate jdbc;

    public TenantLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID tenantId) {
        long key = tenantId.getMostSignificantBits() ^ tenantId.getLeastSignificantBits();
        // The key is a long computed here, never user text, so inlining it is safe.
        jdbc.execute("select pg_advisory_xact_lock(" + key + ")");
    }
}
