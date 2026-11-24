-- ==============================================================================
-- CINECONECTA - DEMO DE CONCURRENCIA (Oracle, dos sesiones)
--
-- NO se ejecuta con F5 de corrido: es un guion. Cada paso dice en qué hoja va.
-- Seleccione el bloque del paso y ejecútelo con F5 (Run Script) en esa hoja.
--
-- PREPARAR LAS DOS SESIONES
--   * Hoja A: la hoja normal de la conexión.
--   * Hoja B: Ctrl+Shift+N (hoja de trabajo NO compartida) sobre la misma conexión.
--     Dos hojas normales comparten la sesión: B nunca se bloquearía.
--   * Autocommit APAGADO (Herramientas > Preferencias > Base de datos >
--     Avanzado > "Confirmación automática"): si está encendido, A confirma
--     solo y no hay nada que esperar.
--   * Si B se queda esperando más de la cuenta, revise que A haya hecho COMMIT.
--
-- Usa dos cuentas propias, una por caso, y las borra al final. La cuenta 4 del
-- seed (flujo de contratación desde la app) no se toca.
--   demo.contratacion@example.com  -> caso 1 (dos contrataciones a la vez)
--   demo.perfiles@example.com      -> caso 2 (dos perfiles nuevos a la vez)
-- ==============================================================================


-- ##############################################################################
-- PASO 0 (Hoja A) - Preparación. Borra restos de una ejecución interrumpida.
-- ##############################################################################
SET SERVEROUTPUT ON

DELETE FROM payments
WHERE  subscriptionId IN (SELECT s.subscriptionId FROM subscriptions s JOIN accounts a ON a.accountId = s.accountId
                          WHERE  LOWER(a.email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM subscriptions
WHERE  accountId IN (SELECT accountId FROM accounts
                     WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM profiles
WHERE  accountId IN (SELECT accountId FROM accounts
                     WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM accounts
WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com');

INSERT INTO accounts (fullName, email, passwordHash)
VALUES ('Demo Contratación', 'demo.contratacion@example.com', '$2a$10$cNwfJIyOYO.7lG3XIlj9b.Bn0efVRAPjm6VyVSYj07FwiTsR6nXVq');
INSERT INTO accounts (fullName, email, passwordHash)
VALUES ('Demo Perfiles', 'demo.perfiles@example.com', '$2a$10$cNwfJIyOYO.7lG3XIlj9b.Bn0efVRAPjm6VyVSYj07FwiTsR6nXVq');

-- demo.perfiles: 1 perfil activo y Estándar vigente (máx. 2). RF9 exige suscripción
-- vigente para crear perfiles, así que la carrera real es entre dos creaciones.
INSERT INTO profiles (accountId, name, maxAgeRating)
SELECT accountId, 'Titular', 18 FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com';

DECLARE
    v_acc INTEGER;
    v_sub INTEGER;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com';
    sp_contract_subscription(v_acc, 2, 'DEMO-PREP-PERFILES', v_sub);
END;
/
COMMIT;

SELECT a.accountId, a.email,
       (SELECT COUNT(*) FROM profiles p WHERE p.accountId = a.accountId AND p.isActive = 'Y') AS perfiles_activos,
       (SELECT v.planName FROM v_active_subscriptions v WHERE v.accountId = a.accountId)     AS plan_vigente
FROM   accounts a
WHERE  LOWER(a.email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com');


-- ##############################################################################
-- PASO 1 (Hoja A y Hoja B) - Probar que son dos sesiones: los SID deben diferir
-- ##############################################################################
SELECT SYS_CONTEXT('USERENV', 'SID') AS sid FROM dual;


-- ##############################################################################
-- CASO 1: DOS CONTRATACIONES SIMULTÁNEAS DE LA MISMA CUENTA (RF16, RF22, RNF10)
-- El procedimiento toma FOR UPDATE sobre la fila de la cuenta: la segunda
-- contratación espera a la primera y, al ver su suscripción, la rechaza.
-- ##############################################################################

-- PASO 1.1 (Hoja A) - Contrata Estándar y NO confirma
SET SERVEROUTPUT ON
DECLARE
    v_acc INTEGER;
    v_sub INTEGER;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.contratacion@example.com';
    sp_contract_subscription(v_acc, 2, 'DEMO-CASO1-A', v_sub);
    DBMS_OUTPUT.PUT_LINE('A: suscripción ' || v_sub || ' creada, SIN COMMIT. Ahora ejecute el paso 1.2 en la hoja B.');
END;
/

-- PASO 1.2 (Hoja B) - Contrata Premium para la misma cuenta: SE QUEDA ESPERANDO
SET SERVEROUTPUT ON
DECLARE
    v_acc INTEGER;
    v_sub INTEGER;
    v_t0  DATE := SYSDATE;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.contratacion@example.com';
    sp_contract_subscription(v_acc, 3, 'DEMO-CASO1-B', v_sub);
    DBMS_OUTPUT.PUT_LINE('B: suscripción ' || v_sub || ' creada (NO debería pasar)');
EXCEPTION WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('B esperó ' || ROUND((SYSDATE - v_t0) * 86400) || ' s y fue rechazada: ' ||
                         REGEXP_SUBSTR(SQLERRM, '[^' || CHR(10) || ']+'));
END;
/

-- PASO 1.3 (Hoja A) - Confirma. En ese momento B se desbloquea y recibe ORA-20001.
COMMIT;

-- PASO 1.4 (Hoja B) - Cierra su transacción y verifica: 1 suscripción, 0 superpuestas
ROLLBACK;
SELECT COUNT(*) AS suscripciones,
       (SELECT COUNT(*) FROM subscriptions s1 JOIN subscriptions s2
               ON s2.accountId = s1.accountId AND s2.subscriptionId > s1.subscriptionId
              AND s2.startsAt < s1.endsAt AND s1.startsAt < s2.endsAt
        WHERE  s1.accountId = a.accountId) AS superpuestas
FROM   accounts a JOIN subscriptions s ON s.accountId = a.accountId
WHERE  LOWER(a.email) = 'demo.contratacion@example.com'
GROUP  BY a.accountId;


-- ##############################################################################
-- CASO 2: DOS PERFILES NUEVOS A LA VEZ EN LA MISMA CUENTA (RF9, RF10)
-- demo.perfiles tiene Estándar (máx. 2) y 1 perfil activo: cabe UNO más.
-- Cada sesión cuenta los activos y, si hay cupo, inserta. Son filas distintas,
-- así que nada las obliga a esperarse: es una anomalía de "write skew".
--
--   2a. SIN bloqueo: las dos ven 1 activo, las dos insertan -> 3 activos (> 2).
--   2b. CON SELECT ... FOR UPDATE sobre ACCOUNTS (el mismo bloqueo que toma
--       sp_contract_subscription): la segunda espera, cuenta 2 y se rechaza.
--
-- Conclusión para la app: ProfileService DEBE tomar ese mismo FOR UPDATE sobre
-- la cuenta antes de crear o reactivar un perfil (y hacerlo en la misma
-- transacción que el INSERT/UPDATE).
-- ##############################################################################

-- ---------- 2a. SIN BLOQUEO (muestra el problema) ----------

-- PASO 2a.1 (Hoja A) - Crea "Perfil A1" y NO confirma
DECLARE
    v_acc INTEGER; v_max INTEGER; v_active INTEGER;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com';
    SELECT maxProfiles INTO v_max FROM v_active_subscriptions WHERE accountId = v_acc;  -- RF9
    SELECT COUNT(*) INTO v_active FROM profiles WHERE accountId = v_acc AND isActive = 'Y';
    IF v_active >= v_max THEN
        RAISE_APPLICATION_ERROR(-20002, v_active || ' perfiles activos, el plan admite ' || v_max);
    END IF;
    INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (v_acc, 'Perfil A1', 12);
    DBMS_OUTPUT.PUT_LINE('A: vio ' || v_active || ' activo(s) de ' || v_max || ' e insertó "Perfil A1", SIN COMMIT');
END;
/

-- PASO 2a.2 (Hoja B) - Crea "Perfil B1": NO espera (no hay bloqueo) y también inserta
DECLARE
    v_acc INTEGER; v_max INTEGER; v_active INTEGER;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com';
    SELECT maxProfiles INTO v_max FROM v_active_subscriptions WHERE accountId = v_acc;
    SELECT COUNT(*) INTO v_active FROM profiles WHERE accountId = v_acc AND isActive = 'Y';
    IF v_active >= v_max THEN
        RAISE_APPLICATION_ERROR(-20002, v_active || ' perfiles activos, el plan admite ' || v_max);
    END IF;
    INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (v_acc, 'Perfil B1', 12);
    DBMS_OUTPUT.PUT_LINE('B: vio ' || v_active || ' activo(s) de ' || v_max || ' e insertó "Perfil B1"');
END;
/

-- PASO 2a.3 (Hoja A) y luego (Hoja B) - Las dos confirman
COMMIT;

-- PASO 2a.4 (cualquier hoja) - Resultado: 3 activos con un plan de 2 (RF10 violado)
SELECT COUNT(*) AS perfiles_activos,
       (SELECT maxProfiles FROM v_active_subscriptions v WHERE v.accountId = a.accountId) AS maximo_del_plan
FROM   accounts a JOIN profiles p ON p.accountId = a.accountId AND p.isActive = 'Y'
WHERE  LOWER(a.email) = 'demo.perfiles@example.com'
GROUP  BY a.accountId;

-- PASO 2a.5 (Hoja A) - Deshace la anomalía para la variante 2b
DELETE FROM profiles
WHERE  accountId = (SELECT accountId FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com')
AND    name IN ('Perfil A1', 'Perfil B1');
COMMIT;

-- ---------- 2b. CON BLOQUEO DE LA CUENTA (la solución) ----------

-- PASO 2b.1 (Hoja A) - Bloquea la cuenta, crea "Perfil A2" y NO confirma
DECLARE
    v_acc INTEGER; v_max INTEGER; v_active INTEGER;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com'
    FOR UPDATE;
    SELECT maxProfiles INTO v_max FROM v_active_subscriptions WHERE accountId = v_acc;
    SELECT COUNT(*) INTO v_active FROM profiles WHERE accountId = v_acc AND isActive = 'Y';
    IF v_active >= v_max THEN
        RAISE_APPLICATION_ERROR(-20002, v_active || ' perfiles activos, el plan admite ' || v_max);
    END IF;
    INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (v_acc, 'Perfil A2', 12);
    DBMS_OUTPUT.PUT_LINE('A: vio ' || v_active || ' activo(s) de ' || v_max || ' e insertó "Perfil A2", SIN COMMIT');
END;
/

-- PASO 2b.2 (Hoja B) - Intenta crear "Perfil B2": SE QUEDA ESPERANDO en el FOR UPDATE
DECLARE
    v_acc INTEGER; v_max INTEGER; v_active INTEGER;
    v_t0  DATE := SYSDATE;
BEGIN
    SELECT accountId INTO v_acc FROM accounts WHERE LOWER(email) = 'demo.perfiles@example.com'
    FOR UPDATE;
    SELECT maxProfiles INTO v_max FROM v_active_subscriptions WHERE accountId = v_acc;
    SELECT COUNT(*) INTO v_active FROM profiles WHERE accountId = v_acc AND isActive = 'Y';
    IF v_active >= v_max THEN
        RAISE_APPLICATION_ERROR(-20002, v_active || ' perfiles activos, el plan admite ' || v_max);
    END IF;
    INSERT INTO profiles (accountId, name, maxAgeRating) VALUES (v_acc, 'Perfil B2', 12);
    DBMS_OUTPUT.PUT_LINE('B: insertó "Perfil B2" (NO debería pasar)');
EXCEPTION WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('B esperó ' || ROUND((SYSDATE - v_t0) * 86400) || ' s y fue rechazada: ' ||
                         REGEXP_SUBSTR(SQLERRM, '[^' || CHR(10) || ']+'));
END;
/

-- PASO 2b.3 (Hoja A) - Confirma. B se desbloquea, cuenta 2 activos y se rechaza.
COMMIT;

-- PASO 2b.4 (Hoja B) - Suelta el bloqueo y verifica: 2 activos, máximo 2
ROLLBACK;
SELECT COUNT(*) AS perfiles_activos,
       (SELECT maxProfiles FROM v_active_subscriptions v WHERE v.accountId = a.accountId) AS maximo_del_plan
FROM   accounts a JOIN profiles p ON p.accountId = a.accountId AND p.isActive = 'Y'
WHERE  LOWER(a.email) = 'demo.perfiles@example.com'
GROUP  BY a.accountId;


-- ##############################################################################
-- PASO 3 (Hoja A) - Limpieza: borra las dos cuentas de demo y todo lo suyo
-- ##############################################################################
DELETE FROM payments
WHERE  subscriptionId IN (SELECT s.subscriptionId FROM subscriptions s JOIN accounts a ON a.accountId = s.accountId
                          WHERE  LOWER(a.email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM subscriptions
WHERE  accountId IN (SELECT accountId FROM accounts
                     WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM profiles
WHERE  accountId IN (SELECT accountId FROM accounts
                     WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com'));
DELETE FROM accounts
WHERE  LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com');
COMMIT;

-- Debe devolver 0 cuentas de demo y 0 suscripciones de la cuenta 4
SELECT (SELECT COUNT(*) FROM accounts
        WHERE LOWER(email) IN ('demo.contratacion@example.com', 'demo.perfiles@example.com')) AS cuentas_demo,
       (SELECT COUNT(*) FROM subscriptions WHERE accountId = 4)                             AS subs_cuenta_4
FROM   dual;
