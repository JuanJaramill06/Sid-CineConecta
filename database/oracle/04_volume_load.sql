-- ==============================================================================
-- CINECONECTA - CARGA DE VOLUMEN (Oracle) para las mediciones de índices
--
-- Ejecutar con "Run Script" (F5) en SQL Developer DESPUÉS de cineconecta_oracle.sql.
-- Reejecutable: borra SOLO su rango exacto y vuelve a insertar. Las cuentas que
-- cree la app o la demo después de la carga (ids >= 11001) nunca se tocan.
--
-- Volumen: 10.000 cuentas, 25.000 perfiles, 120.000 suscripciones, 120.000 pagos.
--
-- Fechas relativas a HOY: cargar el MISMO día que database/mongodb/04_volume.js
-- (las sesiones de Mongo deben caer dentro de estos períodos).
--
-- ------------------------------------------------------------------------------
-- CONTRATO DE IDS CON MongoDB (04_volume.js usa exactamente estas fórmulas)
--   i = 0 .. 9999                       (índice de cuenta)
--   accountId      = 1001 + i                                   -> 1001 .. 11000
--   perfiles       = 2 + MOD(i, 2)   (2 si i es par, 3 si es impar; 2,5 en promedio)
--   profileId      = 1001 + 2*i + FLOOR(i/2) + j,  j = 0 .. perfiles-1 -> 1001 .. 26000
--   maxAgeRating   = j=0: 18 | j=1: 16 si MOD(i,3)=0, si no 12 | j=2: 7
--   k = 0 .. 11                         (12 suscripciones mensuales consecutivas)
--   subscriptionId = 1001 + 12*i + k                            -> 1001 .. 121000
--   startsAt       = TRUNC(hoy) - 350 + 30*k   (k=11: de hoy-20 a hoy+10, vigente)
--   endsAt         = startsAt + 30
--   planId         = 2 perfiles: 2 + MOD(i+k, 3)  (Estándar/Premium/Familiar)
--                    3 perfiles: 3 + MOD(i+k, 2)  (Premium/Familiar)
--                    -> los perfiles activos nunca superan maxProfiles (RF23)
--   paymentId      = subscriptionId, amount = precio del plan, paidAt = startsAt
-- Contraseña de todas las cuentas de volumen: Cliente123 (mismo hash del seed)
-- ==============================================================================

SET SERVEROUTPUT ON
SET TIMING ON

-- ------------------------------------------------------------------------------
-- 1. Borrar el rango del volumen (orden: pagos, suscripciones, perfiles, cuentas)
-- ------------------------------------------------------------------------------
DELETE FROM payments
WHERE  subscriptionId IN (SELECT subscriptionId FROM subscriptions
                          WHERE  accountId BETWEEN 1001 AND 11000);
DELETE FROM subscriptions WHERE accountId BETWEEN 1001 AND 11000;
DELETE FROM profiles      WHERE profileId BETWEEN 1001 AND 26000;
DELETE FROM accounts      WHERE accountId BETWEEN 1001 AND 11000;
COMMIT;

-- ------------------------------------------------------------------------------
-- 2. Cuentas (INSERT ... SELECT con CONNECT BY: mucho más rápido que un bucle)
-- ------------------------------------------------------------------------------
INSERT INTO accounts (accountId, fullName, email, passwordHash, createdAt)
SELECT 1000 + LEVEL,
       'Cliente Volumen ' || (1000 + LEVEL),
       'vol' || (1000 + LEVEL) || '@example.com',
       '$2a$10$cNwfJIyOYO.7lG3XIlj9b.Bn0efVRAPjm6VyVSYj07FwiTsR6nXVq',
       CAST(TRUNC(CURRENT_DATE) - 360 AS TIMESTAMP)
FROM   dual
CONNECT BY LEVEL <= 10000;

-- ------------------------------------------------------------------------------
-- 3. Perfiles: (cuenta i) x (j = 0..2), solo j < 2 + MOD(i,2)
-- ------------------------------------------------------------------------------
INSERT INTO profiles (profileId, accountId, name, maxAgeRating, isActive)
SELECT 1001 + 2 * a.i + FLOOR(a.i / 2) + p.j,
       1001 + a.i,
       CASE p.j WHEN 0 THEN 'Titular' WHEN 1 THEN 'Segundo' ELSE 'Niños' END,
       CASE p.j WHEN 0 THEN 18 WHEN 1 THEN CASE WHEN MOD(a.i, 3) = 0 THEN 16 ELSE 12 END ELSE 7 END,
       'Y'
FROM   (SELECT LEVEL - 1 AS i FROM dual CONNECT BY LEVEL <= 10000) a
JOIN   (SELECT LEVEL - 1 AS j FROM dual CONNECT BY LEVEL <= 3) p
       ON p.j < 2 + MOD(a.i, 2);

-- ------------------------------------------------------------------------------
-- 4. Suscripciones: 12 períodos consecutivos de 30 días por cuenta
-- ------------------------------------------------------------------------------
INSERT INTO subscriptions (subscriptionId, accountId, planId, startsAt, endsAt)
SELECT 1001 + 12 * a.i + m.k,
       1001 + a.i,
       CASE MOD(a.i, 2) WHEN 0 THEN 2 + MOD(a.i + m.k, 3) ELSE 3 + MOD(a.i + m.k, 2) END,
       CAST(TRUNC(CURRENT_DATE) - 350 + 30 * m.k AS TIMESTAMP),
       CAST(TRUNC(CURRENT_DATE) - 320 + 30 * m.k AS TIMESTAMP)
FROM   (SELECT LEVEL - 1 AS i FROM dual CONNECT BY LEVEL <= 10000) a
CROSS JOIN (SELECT LEVEL - 1 AS k FROM dual CONNECT BY LEVEL <= 12) m;

-- ------------------------------------------------------------------------------
-- 5. Pagos: exactamente uno por suscripción, monto = precio del plan
-- ------------------------------------------------------------------------------
INSERT INTO payments (paymentId, subscriptionId, paidAt, amount, reference)
SELECT s.subscriptionId, s.subscriptionId, s.startsAt, p.price,
       'VOL-' || LPAD(s.subscriptionId, 8, '0')
FROM   subscriptions s
JOIN   plans p ON p.planId = s.planId
WHERE  s.accountId BETWEEN 1001 AND 11000;

COMMIT;

-- ------------------------------------------------------------------------------
-- 6. Las columnas IDENTITY continúan después del máximo (app y demo usan >= 11001)
-- ------------------------------------------------------------------------------
ALTER TABLE accounts      MODIFY (accountId      GENERATED BY DEFAULT AS IDENTITY (RESTART START WITH LIMIT VALUE));
ALTER TABLE profiles      MODIFY (profileId      GENERATED BY DEFAULT AS IDENTITY (RESTART START WITH LIMIT VALUE));
ALTER TABLE subscriptions MODIFY (subscriptionId GENERATED BY DEFAULT AS IDENTITY (RESTART START WITH LIMIT VALUE));
ALTER TABLE payments      MODIFY (paymentId      GENERATED BY DEFAULT AS IDENTITY (RESTART START WITH LIMIT VALUE));

-- Estadísticas al día para que el optimizador vea el volumen real
BEGIN
    FOR t IN (SELECT table_name FROM user_tables
              WHERE table_name IN ('ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS', 'PLANS')) LOOP
        DBMS_STATS.GATHER_TABLE_STATS(USER, t.table_name, cascade => TRUE);
    END LOOP;
END;
/

-- ------------------------------------------------------------------------------
-- 7. Verificación (las dos últimas consultas deben devolver 0)
-- ------------------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM accounts      WHERE accountId BETWEEN 1001 AND 11000) AS cuentas,      -- 10000
       (SELECT COUNT(*) FROM profiles      WHERE profileId BETWEEN 1001 AND 26000) AS perfiles,     -- 25000
       (SELECT COUNT(*) FROM subscriptions WHERE accountId BETWEEN 1001 AND 11000) AS suscripciones,-- 120000
       (SELECT COUNT(*) FROM payments      WHERE reference LIKE 'VOL-%')            AS pagos         -- 120000
FROM   dual;

-- Períodos superpuestos para una misma cuenta (RF22): debe ser 0
SELECT COUNT(*) AS periodos_superpuestos
FROM   subscriptions s1
JOIN   subscriptions s2
       ON  s2.accountId = s1.accountId
       AND s2.subscriptionId > s1.subscriptionId
       AND s2.startsAt < s1.endsAt
       AND s1.startsAt < s2.endsAt;

-- Cuentas con más perfiles activos que el máximo de su plan vigente (RF10/RF23): debe ser 0
SELECT COUNT(*) AS cuentas_excedidas
FROM   v_active_subscriptions v
WHERE  (SELECT COUNT(*) FROM profiles p
        WHERE p.accountId = v.accountId AND p.isActive = 'Y') > v.maxProfiles;
