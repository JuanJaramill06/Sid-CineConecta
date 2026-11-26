package com.cineconecta.backend.exception;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * Turns every exception into an ErrorResponse with the right status.
 *
 * Database errors (RNF12): if Oracle or MongoDB does not answer, the request fails with
 * 503 and a clear message; nothing is authorized by default. Errors raised by
 * sp_contract_subscription keep their meaning: -20001 overlap (409), -20002 too many
 * active profiles (422), -20003 / -20004 not found (404). A unique-index violation
 * (ORA-00001 or MongoDB E11000) is a 409.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern ORACLE_USER_MESSAGE = Pattern.compile("ORA-2000\\d: ([^\\n]*)");
    private static final int ORA_UNIQUE_VIOLATION = 1;

    // ---- Application exceptions --------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> businessRule(BusinessRuleException ex) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenOperationException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<ErrorResponse> paymentRejected(PaymentRejectedException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_REJECTED", ex.getMessage());
    }

    // Demo of a failure between the inserts and the commit (RF19): everything was rolled back.
    @ExceptionHandler(SimulatedFailureException.class)
    public ResponseEntity<ErrorResponse> simulatedFailure(SimulatedFailureException ex) {
        log.warn("Simulated failure: {}", ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATED_FAILURE", ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> unavailable(ServiceUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage());
    }

    // ---- Bad requests --------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "Hay datos inválidos en la solicitud", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> invalidParameters(HandlerMethodValidationException ex) {
        List<String> details = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "Hay datos inválidos en la solicitud", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", "Hay datos inválidos en la solicitud", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "El cuerpo de la solicitud no es un JSON válido");
    }

    @ExceptionHandler({ ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponse> badParameter(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    // ---- Authentication and authorization ------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Correo o contraseña incorrectos");
    }

    // Login wraps any error of the user lookup, including a database that is down.
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> authenticationLookupFailed(InternalAuthenticationServiceException ex) {
        if (isUnavailable(ex)) {
            return serviceUnavailable(ex);
        }
        return internalError(ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "No se pudo autenticar");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permiso para esta operación");
    }

    // ---- Databases -----------------------------------------------------------------------------

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> dataAccess(DataAccessException ex) {
        SQLException sql = findCause(ex, SQLException.class);
        if (sql != null) {
            String oracleMessage = oracleUserMessage(sql);
            switch (sql.getErrorCode()) {
                case 20001:
                    return build(HttpStatus.CONFLICT, "SUBSCRIPTION_OVERLAP",
                            "La cuenta ya tiene una suscripción vigente o con período superpuesto");
                case 20002:
                    return build(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_ACTIVE_PROFILES",
                            oracleMessage != null ? oracleMessage
                                    : "Desactive perfiles hasta no superar el máximo del nuevo plan");
                case 20003:
                case 20004:
                    return build(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            oracleMessage != null ? oracleMessage : "No existe");
                case ORA_UNIQUE_VIOLATION:
                    return duplicate();
                default:
                    break;
            }
        }
        if (ex instanceof DuplicateKeyException) {     // MongoDB E11000, e.g. My list (RF37)
            return duplicate();
        }
        if (isUnavailable(ex)) {
            return serviceUnavailable(ex);
        }
        if (ex instanceof DataIntegrityViolationException) {
            return build(HttpStatus.CONFLICT, "CONFLICT", "Los datos no cumplen una restricción de la base de datos");
        }
        return internalError(ex);
    }

    // Opening an Oracle transaction fails this way when the database does not answer.
    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<ErrorResponse> transaction(TransactionException ex) {
        if (ex instanceof CannotCreateTransactionException || isUnavailable(ex)) {
            return serviceUnavailable(ex);
        }
        return internalError(ex);
    }

    // ---- Anything else -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> other(Exception ex) {
        if (isUnavailable(ex)) {
            return serviceUnavailable(ex);
        }
        // Standard Spring MVC errors (404 no route, 405 method, 415 media type...) keep their status.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String detail = springError.getBody().getDetail();
            return build(status, "HTTP_" + status.value(), detail != null ? detail : ex.getMessage());
        }
        return internalError(ex);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /** True if any cause says that a database could not be reached or did not answer. */
    static boolean isUnavailable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DataAccessResourceFailureException
                    || t instanceof TransientDataAccessResourceException
                    || t instanceof QueryTimeoutException
                    || t instanceof CannotCreateTransactionException
                    || t instanceof SQLRecoverableException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException
                    || t instanceof ConnectException
                    || t instanceof com.mongodb.MongoTimeoutException
                    || t instanceof com.mongodb.MongoSocketException) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    // "ORA-20002: Hay 3 perfiles activos y el plan admite 1...\nORA-06512: ..." -> the first line text
    private static String oracleUserMessage(SQLException sql) {
        if (sql.getMessage() == null) {
            return null;
        }
        Matcher m = ORACLE_USER_MESSAGE.matcher(sql.getMessage());
        return m.find() ? m.group(1).trim() : null;
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> duplicate() {
        return build(HttpStatus.CONFLICT, "DUPLICATE", "Ya existe un registro con esos datos");
    }

    private ResponseEntity<ErrorResponse> serviceUnavailable(Exception ex) {
        log.warn("Database unavailable: {}", ex.toString());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Una de las bases de datos no está disponible. Intente de nuevo en unos minutos");
    }

    private ResponseEntity<ErrorResponse> internalError(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado");
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatusCode status, String code, String message) {
        return build(status, code, message, List.of());
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatusCode status, String code, String message,
                                                       List<String> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, Instant.now(), details));
    }
}
