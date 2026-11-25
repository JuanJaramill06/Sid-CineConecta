package com.cineconecta.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.mongodb.MongoTimeoutException;

/** How database failures and sp_contract_subscription errors reach the client. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static UncategorizedSQLException oracle(int code, String message) {
        return new UncategorizedSQLException("sp_contract_subscription", "{call ...}",
                new SQLException(message, "72000", code));
    }

    @Test
    void overlappingSubscriptionIs409() {
        ResponseEntity<ErrorResponse> r = handler.dataAccess(oracle(20001,
                "ORA-20001: La cuenta ya tiene una suscripción vigente\nORA-06512: at line 1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().code()).isEqualTo("SUBSCRIPTION_OVERLAP");
    }

    @Test
    void tooManyActiveProfilesIs422WithTheProcedureMessage() {
        ResponseEntity<ErrorResponse> r = handler.dataAccess(oracle(20002,
                "ORA-20002: Hay 3 perfiles activos y el plan admite 1. Desactive los excedentes antes de contratar\n"
                + "ORA-06512: en \"CINE.SP_CONTRACT_SUBSCRIPTION\", línea 60"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody().message())
                .isEqualTo("Hay 3 perfiles activos y el plan admite 1. Desactive los excedentes antes de contratar");
    }

    @Test
    void accountOrPlanNotFoundIs404() {
        assertThat(handler.dataAccess(oracle(20003, "ORA-20003: La cuenta no existe: 99")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.dataAccess(oracle(20004, "ORA-20004: El plan no existe: 9")).getBody().message())
                .isEqualTo("El plan no existe: 9");
    }

    @Test
    void uniqueViolationsAre409InBothEngines() {
        var oracleUnique = new DataIntegrityViolationException("insert",
                new SQLException("ORA-00001: unique constraint (UX_PROFILES_NAME_CI) violated", "23000", 1));
        var mongoE11000 = new DuplicateKeyException("E11000 duplicate key error ux_mylist_profile_content");

        assertThat(handler.dataAccess(oracleUnique).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.dataAccess(mongoE11000).getBody().code()).isEqualTo("DUPLICATE");
    }

    @Test
    void unreachableDatabaseIs503NeverAllowed() {
        assertThat(handler.dataAccess(new DataAccessResourceFailureException("Oracle down")).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.transaction(new CannotCreateTransactionException("no connection")).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.other(new RuntimeException(new MongoTimeoutException("Atlas timeout"))).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void loginWithDatabaseDownIs503NotBadCredentials() {
        var ex = new InternalAuthenticationServiceException("lookup failed",
                new DataAccessResourceFailureException("Oracle down"));

        assertThat(handler.authenticationLookupFailed(ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void unexpectedErrorsAre500WithoutInternals() {
        ResponseEntity<ErrorResponse> r = handler.other(new IllegalStateException("secret detail"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().message()).doesNotContain("secret detail");
    }
}
