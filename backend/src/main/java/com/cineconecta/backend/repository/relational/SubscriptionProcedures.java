package com.cineconecta.backend.repository.relational;

import java.sql.Types;

import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Calls sp_contract_subscription (database/oracle/01_cineconecta_oracle.sql).
 *
 * The procedure locks the account row (FOR UPDATE), rejects overlapping periods (-20001)
 * and too many active profiles for the new plan (-20002), then inserts subscription +
 * payment. It does NOT commit: call it inside @Transactional. JdbcTemplate joins the JPA
 * transaction (same connection), so the caller's commit or rollback covers both inserts.
 * Errors arrive as DataAccessException; GlobalExceptionHandler maps the -2000x codes.
 */
@Repository
public class SubscriptionProcedures {

    private static final String CALL = "{call sp_contract_subscription(?, ?, ?, ?)}";

    private final JdbcTemplate jdbc;

    public SubscriptionProcedures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the new subscriptionId. */
    public Integer contract(Integer accountId, Integer planId, String paymentReference) {
        return jdbc.execute(CALL, (CallableStatementCallback<Integer>) cs -> {
            cs.setInt(1, accountId);
            cs.setInt(2, planId);
            cs.setString(3, paymentReference);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.execute();
            return cs.getInt(4);
        });
    }
}
