-- ==============================================================================
-- CINECONECTA - EXPERIMENTO DE ÍNDICES (Oracle)
--
-- Ejecutar con "Run Script" (F5) después de 04_volume_load.sql (120.000 pagos).
--
-- Consulta medida: informe de ingresos por plan en un rango de fechas
--   payments ⋈ subscriptions ⋈ plans, filtro por payments.paidAt.
-- Índice evaluado: ix_payments_paidat (paidAt, amount, subscriptionId). Cubre
-- todo lo que la consulta lee de PAYMENTS, así que no necesita ir a la tabla.
--
-- Mediciones: CON índice (1 mes ≈ 8 % y 1 año ≈ 100 %), se borra el índice,
-- SIN índice (los mismos rangos) y se recrea. Si algo falla, el bloque recrea
-- el índice antes de propagar el error, y el paso 4 lo vuelve a comprobar.
--
-- Dos caminos según los privilegios (el paso 3 lo detecta solo):
--   Camino A (siempre): EXPLAIN PLAN + DBMS_XPLAN.DISPLAY (plan, costo y
--             cardinalidad estimados) + mediana de 5 ejecuciones en ms.
--   Camino B (si hay acceso a V$SQL, V$SQL_PLAN, V$SQL_PLAN_STATISTICS_ALL y
--             V$SESSION): además gather_plan_statistics + DISPLAY_CURSOR
--             ('ALLSTATS LAST') con Buffers (lecturas lógicas) = métrica principal.
-- ==============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

-- ------------------------------------------------------------------------------
-- 1. Estadísticas al día y red de seguridad (el índice existe antes de empezar)
-- ------------------------------------------------------------------------------
DECLARE
    n NUMBER;
BEGIN
    SELECT COUNT(*) INTO n FROM user_indexes WHERE index_name = 'IX_PAYMENTS_PAIDAT';
    IF n = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_payments_paidat ON payments (paidAt, amount, subscriptionId)';
        DBMS_OUTPUT.PUT_LINE('ix_payments_paidat no existía (corrida interrumpida): recreado');
    END IF;
    FOR t IN (SELECT table_name FROM user_tables
              WHERE table_name IN ('PAYMENTS', 'SUBSCRIPTIONS', 'PLANS')) LOOP
        DBMS_STATS.GATHER_TABLE_STATS(USER, t.table_name, cascade => TRUE);
    END LOOP;
END;
/

-- ------------------------------------------------------------------------------
-- 2. Volumen y selectividad de cada rango
-- ------------------------------------------------------------------------------
SELECT COUNT(*) AS pagos_total,
       SUM(CASE WHEN paidAt >= CAST(TRUNC(CURRENT_DATE) - 30 AS TIMESTAMP)
                 AND paidAt <  CAST(TRUNC(CURRENT_DATE) + 1 AS TIMESTAMP) THEN 1 ELSE 0 END) AS pagos_1_mes,
       ROUND(100 * SUM(CASE WHEN paidAt >= CAST(TRUNC(CURRENT_DATE) - 30 AS TIMESTAMP)
                             AND paidAt <  CAST(TRUNC(CURRENT_DATE) + 1 AS TIMESTAMP) THEN 1 ELSE 0 END)
             / COUNT(*), 1) AS pct_1_mes,
       ROUND(100 * SUM(CASE WHEN paidAt >= CAST(TRUNC(CURRENT_DATE) - 366 AS TIMESTAMP)
                             AND paidAt <  CAST(TRUNC(CURRENT_DATE) + 1 AS TIMESTAMP) THEN 1 ELSE 0 END)
             / COUNT(*), 1) AS pct_1_anio
FROM   payments;

SELECT segment_name, segment_type, ROUND(bytes / 1048576, 1) AS mb
FROM   user_segments
WHERE  segment_name IN ('PAYMENTS', 'SUBSCRIPTIONS', 'IX_PAYMENTS_PAIDAT')
ORDER  BY segment_name;

-- ------------------------------------------------------------------------------
-- 3. Experimento
-- ------------------------------------------------------------------------------
DECLARE
    TYPE t_row IS RECORD (
        tag      VARCHAR2(30),
        ms       NUMBER,
        cost     NUMBER,
        buffers  NUMBER,
        pagos    NUMBER,
        ingresos NUMBER,
        access   VARCHAR2(400)
    );
    TYPE t_rows IS TABLE OF t_row INDEX BY PLS_INTEGER;
    v_rows  t_rows;
    v_has_v BOOLEAN := TRUE;
    c_ddl   CONSTANT VARCHAR2(100) := 'CREATE INDEX ix_payments_paidat ON payments (paidAt, amount, subscriptionId)';

    PROCEDURE p(s VARCHAR2) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE(s);
    END;

    -- El comentario con la etiqueta hace único el texto de cada medición (y permite
    -- encontrar su cursor en V$SQL)
    FUNCTION report_sql(p_tag VARCHAR2, p_days NUMBER) RETURN VARCHAR2 IS
    BEGIN
        RETURN 'SELECT /*+ gather_plan_statistics */ /* ' || p_tag || ' */ pl.name, COUNT(*), SUM(pa.amount)'
            || ' FROM payments pa'
            || ' JOIN subscriptions s ON s.subscriptionId = pa.subscriptionId'
            || ' JOIN plans pl ON pl.planId = s.planId'
            || ' WHERE pa.paidAt >= CAST(TRUNC(CURRENT_DATE) - ' || p_days || ' AS TIMESTAMP)'
            || ' AND pa.paidAt < CAST(TRUNC(CURRENT_DATE) + 1 AS TIMESTAMP)'
            || ' GROUP BY pl.name ORDER BY pl.name';
    END;

    FUNCTION ms_since(p_t0 TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        d INTERVAL DAY TO SECOND := SYSTIMESTAMP - p_t0;
    BEGIN
        RETURN EXTRACT(DAY FROM d) * 86400000 + EXTRACT(HOUR FROM d) * 3600000
             + EXTRACT(MINUTE FROM d) * 60000 + ROUND(EXTRACT(SECOND FROM d) * 1000);
    END;

    PROCEDURE measure(p_tag VARCHAR2, p_days NUMBER) IS
        v_sql   VARCHAR2(1000) := report_sql(p_tag, p_days);
        v_cur   SYS_REFCURSOR;
        v_name  VARCHAR2(60);
        v_cnt   NUMBER;
        v_sum   NUMBER;
        v_line  VARCHAR2(4000);
        v_id    VARCHAR2(13);
        v_child NUMBER;
        v_t0    TIMESTAMP WITH TIME ZONE;
        v_times SYS.ODCINUMBERLIST := SYS.ODCINUMBERLIST();
        r       t_row;
    BEGIN
        r.tag := p_tag; r.pagos := 0; r.ingresos := 0;
        p(CHR(10) || '==================== ' || p_tag || ' ====================');

        -- 5 ejecuciones completas (se leen todas las filas); la primera imprime el resultado
        FOR i IN 1 .. 5 LOOP
            v_t0 := SYSTIMESTAMP;
            OPEN v_cur FOR v_sql;
            LOOP
                FETCH v_cur INTO v_name, v_cnt, v_sum;
                EXIT WHEN v_cur%NOTFOUND;
                IF i = 1 THEN
                    p('  ' || RPAD(v_name, 10) || LPAD(v_cnt, 8) || ' pagos ' || TO_CHAR(v_sum, '999G999G999G990'));
                    r.pagos := r.pagos + v_cnt;
                    r.ingresos := r.ingresos + v_sum;
                END IF;
            END LOOP;
            CLOSE v_cur;
            v_times.EXTEND;
            v_times(i) := ms_since(v_t0);
        END LOOP;
        SELECT column_value INTO r.ms
        FROM   TABLE(v_times) ORDER BY column_value OFFSET 2 ROWS FETCH NEXT 1 ROWS ONLY;

        -- Camino A: plan estimado (solo usa PLAN_TABLE)
        DELETE FROM plan_table WHERE statement_id = p_tag;
        EXECUTE IMMEDIATE 'EXPLAIN PLAN SET STATEMENT_ID = ''' || p_tag || ''' FOR ' || v_sql;
        SELECT cost INTO r.cost FROM plan_table WHERE statement_id = p_tag AND id = 0;
        SELECT LISTAGG(TRIM(operation || ' ' || options) || ' ' || object_name, ', ') WITHIN GROUP (ORDER BY id)
        INTO   r.access
        FROM   plan_table
        WHERE  statement_id = p_tag AND object_name IN ('PAYMENTS', 'IX_PAYMENTS_PAIDAT');
        p('  Plan estimado (EXPLAIN PLAN + DBMS_XPLAN.DISPLAY):');
        FOR l IN (SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY('PLAN_TABLE', p_tag, 'TYPICAL'))) LOOP
            p('  ' || l.plan_table_output);
        END LOOP;

        -- Camino B: plan real de la última ejecución, con Buffers
        IF v_has_v THEN
          BEGIN
            EXECUTE IMMEDIATE
                'SELECT sql_id, child_number FROM v$sql WHERE sql_text LIKE :pat' ||
                ' ORDER BY last_active_time DESC FETCH FIRST 1 ROWS ONLY'
                INTO v_id, v_child
                USING 'SELECT /*+ gather_plan_statistics */ /* ' || p_tag || ' */%';
            EXECUTE IMMEDIATE
                'SELECT MAX(last_cr_buffer_gets) FROM v$sql_plan_statistics_all' ||
                ' WHERE sql_id = :1 AND child_number = :2'
                INTO r.buffers USING v_id, v_child;
            p('  Plan real (DISPLAY_CURSOR ''ALLSTATS LAST''; Buffers = lecturas lógicas):');
            OPEN v_cur FOR 'SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(:1, :2, ''ALLSTATS LAST''))'
                USING v_id, v_child;
            LOOP
                FETCH v_cur INTO v_line;
                EXIT WHEN v_cur%NOTFOUND;
                p('  ' || v_line);
            END LOOP;
            CLOSE v_cur;
          EXCEPTION
            WHEN OTHERS THEN  -- el plan real es un extra: si falla, queda el camino A
                IF v_cur%ISOPEN THEN CLOSE v_cur; END IF;
                p('  (No se pudo leer el plan real: ' || SQLERRM || ')');
          END;
        END IF;

        v_rows(v_rows.COUNT + 1) := r;
    END;

BEGIN
    -- Prueba de privilegios con SQL dinámico: con SQL estático el bloque ni
    -- compilaría si faltara el permiso sobre las vistas V$
    DECLARE
        n NUMBER;
    BEGIN
        FOR v IN (SELECT column_value AS name
                  FROM   TABLE(SYS.ODCIVARCHAR2LIST('V$SQL', 'V$SQL_PLAN', 'V$SQL_PLAN_STATISTICS_ALL', 'V$SESSION'))) LOOP
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || v.name || ' WHERE ROWNUM = 1' INTO n;
        END LOOP;
        p('Camino B: hay acceso a V$. Métrica principal: Buffers (lecturas lógicas) del plan real.');
    EXCEPTION
        WHEN OTHERS THEN
            v_has_v := FALSE;
            p('Camino A: sin acceso a V$ (' || SQLERRM || ').');
            p('Métricas: operación de acceso, costo y cardinalidad estimados, y mediana de tiempo.');
    END;

    measure('CON_INDICE_1_MES', 30);
    measure('CON_INDICE_1_ANIO', 366);

    EXECUTE IMMEDIATE 'DROP INDEX ix_payments_paidat';
    BEGIN
        measure('SIN_INDICE_1_MES', 30);
        measure('SIN_INDICE_1_ANIO', 366);
    EXCEPTION
        WHEN OTHERS THEN
            EXECUTE IMMEDIATE c_ddl;  -- nunca dejar la tabla sin el índice
            RAISE;
    END;
    EXECUTE IMMEDIATE c_ddl;

    -- Resumen
    p(CHR(10) || '==================== RESUMEN ====================');
    p(RPAD('medición', 20) || LPAD('mediana ms', 11) || LPAD('costo', 8) || LPAD('buffers', 10) ||
      LPAD('pagos', 9) || LPAD('ingresos', 16) || '  acceso a PAYMENTS');
    FOR i IN 1 .. v_rows.COUNT LOOP
        p(RPAD(v_rows(i).tag, 20) || LPAD(v_rows(i).ms, 11) || LPAD(v_rows(i).cost, 8) ||
          LPAD(NVL(TO_CHAR(v_rows(i).buffers), '-'), 10) || LPAD(v_rows(i).pagos, 9) ||
          LPAD(TO_CHAR(v_rows(i).ingresos, 'FM999G999G999G990'), 16) || '  ' || v_rows(i).access);
    END LOOP;

    -- Con y sin índice deben dar exactamente el mismo resultado
    IF v_rows(1).pagos <> v_rows(3).pagos OR v_rows(1).ingresos <> v_rows(3).ingresos
       OR v_rows(2).pagos <> v_rows(4).pagos OR v_rows(2).ingresos <> v_rows(4).ingresos THEN
        RAISE_APPLICATION_ERROR(-20998, 'Los resultados con y sin índice no coinciden');
    END IF;
    p('OK: con y sin índice devuelven los mismos pagos e ingresos en cada rango.');
END;
/

-- ------------------------------------------------------------------------------
-- 4. Comprobación final: el índice quedó creado y válido
-- ------------------------------------------------------------------------------
DECLARE
    n NUMBER;
BEGIN
    SELECT COUNT(*) INTO n FROM user_indexes WHERE index_name = 'IX_PAYMENTS_PAIDAT';
    IF n = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_payments_paidat ON payments (paidAt, amount, subscriptionId)';
        DBMS_OUTPUT.PUT_LINE('ix_payments_paidat recreado en la comprobación final');
    END IF;
END;
/

SELECT index_name, status, num_rows, leaf_blocks, last_analyzed
FROM   user_indexes
WHERE  index_name = 'IX_PAYMENTS_PAIDAT';
