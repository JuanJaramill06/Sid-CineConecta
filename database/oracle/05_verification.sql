-- ==============================================================================
-- CINECONECTA - VERIFICACIÓN RELACIONAL (Oracle)
--
-- Ejecutar con "Run Script" (F5) en SQL Developer sobre el seed de
-- 01_cineconecta_oracle.sql recién cargado (las cuentas 3, 7 y 9 deben seguir
-- vencidas). Funciona igual con o sin la carga de volumen.
--
-- Cada caso intenta UNA operación inválida y comprueba que Oracle la rechace con
-- el error esperado (código + nombre de la restricción). Imprime PASS/FAIL.
-- Todo corre en una sola transacción que termina en ROLLBACK: no deja datos.
-- (Las columnas IDENTITY sí avanzan: quedan huecos en los ids, es normal.)
--
-- Secciones:
--   1. Restricciones del esquema (DML directo)
--   2. Procedimiento de contratación: rechazos -20001..-20004 y RF23 completo
--   3. Fallo a mitad de la transacción (el pago falla y la suscripción no queda)
--   4. Historial y vigencia
-- ==============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_failures  PLS_INTEGER := 0;
    v_sub       subscriptions.subscriptionId%TYPE;
    v_count     NUMBER;
    v_before    NUMBER;
    v_hash      CONSTANT VARCHAR2(60) := '$2a$10$cNwfJIyOYO.7lG3XIlj9b.Bn0efVRAPjm6VyVSYj07FwiTsR6nXVq';
    -- Período lejano para los registros de prueba: no se cruza con ningún otro
    v_far       CONSTANT TIMESTAMP := CAST(TRUNC(CURRENT_DATE) + 200 AS TIMESTAMP);

    PROCEDURE report_pass(p_case VARCHAR2) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE('PASS  ' || p_case);
    END;

    PROCEDURE report_fail(p_case VARCHAR2, p_why VARCHAR2) IS
    BEGIN
        v_failures := v_failures + 1;
        DBMS_OUTPUT.PUT_LINE('FAIL  ' || p_case || ': ' || p_why);
    END;

    -- SQLCODE/SQLERRM se pasan como parámetros: fuera del handler valen 0
    PROCEDURE expect_error(p_case VARCHAR2, p_code PLS_INTEGER, p_object VARCHAR2,
                           p_sqlcode PLS_INTEGER, p_sqlerrm VARCHAR2) IS
        v_first_line VARCHAR2(4000) := REGEXP_SUBSTR(p_sqlerrm, '[^' || CHR(10) || ']+');
    BEGIN
        IF p_sqlcode = p_code AND (p_object IS NULL OR INSTR(UPPER(p_sqlerrm), p_object) > 0) THEN
            report_pass(p_case || '  -> ' || v_first_line);
        ELSE
            report_fail(p_case, 'se esperaba ' || p_code ||
                        CASE WHEN p_object IS NOT NULL THEN ' (' || p_object || ')' END ||
                        ' y llegó ' || v_first_line);
        END IF;
    END;

    PROCEDURE accepted(p_case VARCHAR2) IS
    BEGIN
        report_fail(p_case, 'se aceptó');
    END;

    PROCEDURE assert_true(p_case VARCHAR2, p_cond BOOLEAN) IS
    BEGIN
        IF p_cond THEN report_pass(p_case); ELSE report_fail(p_case, 'condición falsa'); END IF;
    END;

BEGIN
    -- Precondición: el seed funcional está cargado
    SELECT COUNT(*) INTO v_count FROM accounts WHERE accountId BETWEEN 1 AND 10;
    IF v_count <> 10 THEN
        RAISE_APPLICATION_ERROR(-20900, 'Cargue primero el seed de 01_cineconecta_oracle.sql');
    END IF;

    -- ==========================================================================
    DBMS_OUTPUT.PUT_LINE('--- 1. Restricciones del esquema ---');
    -- ==========================================================================

    -- RF2: correo repetido aunque cambien las mayúsculas
    BEGIN
        INSERT INTO accounts (fullName, email, passwordHash)
        VALUES ('Duplicado', 'JUAN.Perez@Example.COM', v_hash);
        accepted('RF2 correo repetido con otras mayúsculas');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF2 correo repetido con otras mayúsculas', -1, 'UX_ACCOUNTS_EMAIL_CI', SQLCODE, SQLERRM);
    END;

    -- RF8: nombre de perfil repetido dentro de la cuenta
    BEGIN
        INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (1, 'JUAN', 18);
        accepted('RF8 nombre de perfil repetido en la cuenta 1');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF8 nombre de perfil repetido en la cuenta 1', -1, 'UX_PROFILES_NAME_CI', SQLCODE, SQLERRM);
    END;

    BEGIN
        INSERT INTO profiles (accountId, name, maxAgeRating, isActive) VALUES (1, 'Prueba', 12, 'X');
        accepted('isActive = ''X''');
    EXCEPTION WHEN OTHERS THEN
        expect_error('isActive = ''X''', -2290, 'CK_PROFILES_ACTIVE', SQLCODE, SQLERRM);
    END;

    BEGIN
        INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (1, 'Prueba', -1);
        accepted('maxAgeRating negativo');
    EXCEPTION WHEN OTHERS THEN
        expect_error('maxAgeRating negativo', -2290, 'CK_PROFILES_RATING', SQLCODE, SQLERRM);
    END;

    BEGIN
        INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (999999, 'Huérfano', 12);
        accepted('perfil de una cuenta inexistente');
    EXCEPTION WHEN OTHERS THEN
        expect_error('perfil de una cuenta inexistente', -2291, 'FK_PROFILES_ACCOUNT', SQLCODE, SQLERRM);
    END;

    BEGIN
        INSERT INTO plans (name, price, maxProfiles) VALUES ('Gratis', 0, 1);
        accepted('plan con precio 0');
    EXCEPTION WHEN OTHERS THEN
        expect_error('plan con precio 0', -2290, 'CK_PLANS_PRICE', SQLCODE, SQLERRM);
    END;

    BEGIN
        INSERT INTO plans (name, price, maxProfiles) VALUES ('Sin perfiles', 9900, 0);
        accepted('plan con 0 perfiles');
    EXCEPTION WHEN OTHERS THEN
        expect_error('plan con 0 perfiles', -2290, 'CK_PLANS_MAXPROFILES', SQLCODE, SQLERRM);
    END;

    -- RF21: el fin debe ser posterior al inicio
    BEGIN
        INSERT INTO subscriptions (accountId, planId, startsAt, endsAt) VALUES (9, 1, v_far, v_far);
        accepted('suscripción con fin = inicio');
    EXCEPTION WHEN OTHERS THEN
        expect_error('suscripción con fin = inicio', -2290, 'CK_SUBS_PERIOD', SQLCODE, SQLERRM);
    END;

    -- Suscripción de prueba SIN pago (solo para probar las reglas de PAYMENTS)
    INSERT INTO subscriptions (accountId, planId, startsAt, endsAt)
    VALUES (9, 1, v_far, v_far + INTERVAL '30' DAY)
    RETURNING subscriptionId INTO v_sub;

    BEGIN
        INSERT INTO payments (subscriptionId, paidAt, amount, reference) VALUES (v_sub, v_far, 0, 'VERIF-MONTO-0');
        accepted('pago con monto 0');
    EXCEPTION WHEN OTHERS THEN
        expect_error('pago con monto 0', -2290, 'CK_PAYMENTS_AMOUNT', SQLCODE, SQLERRM);
    END;

    INSERT INTO payments (subscriptionId, paidAt, amount, reference) VALUES (v_sub, v_far, 14900, 'VERIF-PAGO-1');
    report_pass('control: el primer pago de la suscripción se acepta');

    -- RF20: exactamente un pago por suscripción
    BEGIN
        INSERT INTO payments (subscriptionId, paidAt, amount, reference) VALUES (v_sub, v_far, 14900, 'VERIF-PAGO-2');
        accepted('RF20 segundo pago para la misma suscripción');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF20 segundo pago para la misma suscripción', -1, 'UQ_PAYMENTS_SUBSCRIPTION', SQLCODE, SQLERRM);
    END;

    -- ==========================================================================
    DBMS_OUTPUT.PUT_LINE('--- 2. Procedimiento de contratación ---');
    -- ==========================================================================

    -- Control positivo: la cuenta 3 (vencida, 1 perfil activo) contrata Estándar
    v_sub := NULL;
    sp_contract_subscription(3, 2, 'VERIF-OK-1', v_sub);
    SELECT COUNT(*) INTO v_count FROM payments WHERE subscriptionId = v_sub AND amount = 24900;
    assert_true('control: la cuenta 3 contrata Estándar, con un pago de 24900', v_sub IS NOT NULL AND v_count = 1);
    -- RF21: la vigencia incluye el instante de inicio (startsAt = ahora)
    SELECT COUNT(*) INTO v_count FROM v_active_subscriptions WHERE subscriptionId = v_sub;
    assert_true('RF21 la suscripción recién creada ya está vigente', v_count = 1);

    -- RF16/RF22: no se puede contratar con una suscripción vigente
    BEGIN
        sp_contract_subscription(1, 3, 'VERIF-RF16', v_sub);
        accepted('RF16 la cuenta 1 (vigente del seed) contrata otra vez');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF16 la cuenta 1 (vigente del seed) contrata otra vez', -20001, NULL, SQLCODE, SQLERRM);
    END;

    BEGIN
        sp_contract_subscription(3, 4, 'VERIF-OK-2', v_sub);
        accepted('RF22 la cuenta 3 contrata dos veces seguidas');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF22 la cuenta 3 contrata dos veces seguidas', -20001, NULL, SQLCODE, SQLERRM);
    END;

    -- RF23: la cuenta 9 tiene 3 perfiles activos y Básico admite 1
    BEGIN
        sp_contract_subscription(9, 1, 'VERIF-RF23', v_sub);
        accepted('RF23 cuenta 9 contrata Básico con 3 perfiles activos');
    EXCEPTION WHEN OTHERS THEN
        expect_error('RF23 cuenta 9 contrata Básico con 3 perfiles activos', -20002, NULL, SQLCODE, SQLERRM);
    END;

    -- RF23 + RF11: desactiva los excedentes (las filas se conservan) y vuelve a intentar
    UPDATE profiles SET isActive = 'N' WHERE profileId IN (20, 21);
    v_sub := NULL;
    sp_contract_subscription(9, 1, 'VERIF-RF23-OK', v_sub);
    assert_true('RF23 tras desactivar 2 perfiles, la cuenta 9 sí contrata Básico', v_sub IS NOT NULL);

    BEGIN
        sp_contract_subscription(999999, 1, 'VERIF-NOACC', v_sub);
        accepted('cuenta inexistente');
    EXCEPTION WHEN OTHERS THEN
        expect_error('cuenta inexistente', -20003, NULL, SQLCODE, SQLERRM);
    END;

    BEGIN
        sp_contract_subscription(7, 99, 'VERIF-NOPLAN', v_sub);
        accepted('plan inexistente');
    EXCEPTION WHEN OTHERS THEN
        expect_error('plan inexistente', -20004, NULL, SQLCODE, SQLERRM);
    END;

    -- ==========================================================================
    DBMS_OUTPUT.PUT_LINE('--- 3. Fallo a mitad de la transacción (RF19 / RNF11) ---');
    -- ==========================================================================
    -- La cuenta 7 pasa todas las validaciones y el INSERT de la suscripción se hace.
    -- Luego el INSERT del pago falla porque la referencia ya existe (seed).
    -- El procedimiento hace ROLLBACK TO sp_contract: la suscripción no debe quedar.
    SELECT COUNT(*) INTO v_before FROM subscriptions WHERE accountId = 7;
    BEGIN
        sp_contract_subscription(7, 2, 'SIM-00000001', v_sub);
        accepted('pago con referencia repetida');
    EXCEPTION WHEN OTHERS THEN
        expect_error('el pago falla por referencia repetida', -1, 'UQ_PAYMENTS_REFERENCE', SQLCODE, SQLERRM);
    END;
    SELECT COUNT(*) INTO v_count FROM subscriptions WHERE accountId = 7;
    assert_true('RF19 la suscripción de la cuenta 7 NO quedó registrada (' || v_before || ' antes, ' ||
                v_count || ' después)', v_count = v_before);
    SELECT COUNT(*) INTO v_count FROM v_active_subscriptions WHERE accountId = 7;
    assert_true('RF19 la cuenta 7 sigue sin suscripción vigente', v_count = 0);

    -- ==========================================================================
    DBMS_OUTPUT.PUT_LINE('--- 4. Historial y vigencia ---');
    -- ==========================================================================

    -- RF17: la cuenta 6 conserva sus 3 períodos, cada uno con su pago, bajo 2 planes
    SELECT COUNT(*), COUNT(DISTINCT s.planId) INTO v_count, v_before
    FROM   subscriptions s JOIN payments p ON p.subscriptionId = s.subscriptionId
    WHERE  s.accountId = 6;
    assert_true('RF17 la cuenta 6 conserva 3 suscripciones con pago, de 2 planes distintos', v_count = 3 AND v_before = 2);

    -- RF11: los perfiles desactivados conservan su fila
    SELECT COUNT(*) INTO v_count FROM profiles WHERE profileId IN (16, 18, 20, 21) AND isActive = 'N';
    assert_true('RF11 los perfiles 16, 18, 20 y 21 siguen existiendo, desactivados', v_count = 4);

    -- El historial no se puede borrar por partes
    BEGIN
        DELETE FROM subscriptions WHERE subscriptionId = 5;
        accepted('borrar una suscripción que tiene pago');
    EXCEPTION WHEN OTHERS THEN
        expect_error('borrar una suscripción que tiene pago', -2292, 'FK_PAYMENTS_SUBSCRIPTION', SQLCODE, SQLERRM);
    END;

    BEGIN
        DELETE FROM accounts WHERE accountId = 6;
        accepted('borrar una cuenta con perfiles y suscripciones');
    EXCEPTION WHEN OTHERS THEN
        expect_error('borrar una cuenta con perfiles y suscripciones', -2292, NULL, SQLCODE, SQLERRM);
    END;

    -- RF21: el instante final se excluye (la suscripción 11 terminó hoy a las 00:00)
    SELECT COUNT(*) INTO v_count FROM v_active_subscriptions WHERE subscriptionId = 11;
    assert_true('RF21 la suscripción 11 (terminó hoy 00:00) ya no está vigente', v_count = 0);
    SELECT COUNT(*) INTO v_count FROM v_active_subscriptions WHERE subscriptionId = 1;
    assert_true('RF21 la suscripción 1 sigue vigente', v_count = 1);

    -- ==========================================================================
    ROLLBACK;  -- no deja nada de lo anterior
    DBMS_OUTPUT.PUT_LINE(CHR(10) || CASE WHEN v_failures = 0
        THEN 'Todas las pruebas pasaron (ROLLBACK hecho)'
        ELSE v_failures || ' prueba(s) fallaron (ROLLBACK hecho)' END);
    IF v_failures > 0 THEN
        RAISE_APPLICATION_ERROR(-20999, v_failures || ' prueba(s) de verificación fallaron');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
