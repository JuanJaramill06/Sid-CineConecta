-- ==============================================================================
-- CINECONECTA - CATÁLOGO DE OBJETOS (Oracle)
--
-- Ejecutar con "Run Script" (F5). Solo consulta el diccionario de datos.
-- Se limita a los objetos de CineConecta: el usuario del curso puede tener
-- objetos de otras materias.
-- La salida se copia a docs/catalogo-objetos.md.
-- ==============================================================================

SET SQLFORMAT ansiconsole

-- 1. Objetos (tablas, vista, procedimiento, índices y secuencias de IDENTITY)
SELECT object_type, object_name, status, created, last_ddl_time
FROM   user_objects
WHERE  object_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS',
                       'V_ACTIVE_SUBSCRIPTIONS', 'SP_CONTRACT_SUBSCRIPTION')
OR     object_name IN (SELECT index_name FROM user_indexes
                       WHERE  table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS'))
OR     object_name IN (SELECT sequence_name FROM user_tab_identity_cols
                       WHERE  table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS'))
ORDER  BY object_type, object_name;

-- 2. Tablas: filas y fecha de las estadísticas
SELECT table_name, num_rows, blocks, last_analyzed
FROM   user_tables
WHERE  table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS')
ORDER  BY table_name;

-- 3. Columnas
SELECT table_name, column_id, column_name,
       data_type ||
       CASE WHEN data_type IN ('VARCHAR2', 'CHAR') THEN '(' || char_length || ')'
            WHEN data_type = 'NUMBER' AND data_precision IS NOT NULL
                 THEN '(' || data_precision || ',' || data_scale || ')' END AS tipo,
       nullable, identity_column, data_default
FROM   user_tab_columns
WHERE  table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS')
ORDER  BY table_name, column_id;

-- 4. Columnas IDENTITY
SELECT table_name, column_name, generation_type, sequence_name
FROM   user_tab_identity_cols
WHERE  table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS')
ORDER  BY table_name;

-- 5. Restricciones (P = llave primaria, U = única, R = foránea, C = check / not null)
SELECT c.table_name, c.constraint_name, c.constraint_type,
       (SELECT LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position)
        FROM   user_cons_columns cc
        WHERE  cc.constraint_name = c.constraint_name) AS columnas,
       c.search_condition_vc AS condicion,
       r.table_name AS tabla_referenciada,
       c.delete_rule, c.status
FROM   user_constraints c
LEFT   JOIN user_constraints r ON r.constraint_name = c.r_constraint_name
WHERE  c.table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS')
AND    c.constraint_name NOT LIKE 'SYS\_C%' ESCAPE '\'   -- los NOT NULL sin nombre ya salen en la sección 3
ORDER  BY c.table_name, c.constraint_type, c.constraint_name;

-- 6. Índices y sus columnas (los índices por función muestran la expresión en la sección 7)
SELECT i.table_name, i.index_name, i.uniqueness, i.index_type,
       (SELECT LISTAGG(ic.column_name, ', ') WITHIN GROUP (ORDER BY ic.column_position)
        FROM   user_ind_columns ic
        WHERE  ic.index_name = i.index_name) AS columnas,
       i.num_rows, i.distinct_keys, i.status
FROM   user_indexes i
WHERE  i.table_name IN ('PLANS', 'ACCOUNTS', 'PROFILES', 'SUBSCRIPTIONS', 'PAYMENTS')
ORDER  BY i.table_name, i.index_name;

-- 7. Expresiones de los índices por función (LOWER(email), LOWER(name))
SELECT index_name, column_position, column_expression
FROM   user_ind_expressions
WHERE  table_name IN ('ACCOUNTS', 'PROFILES')
ORDER  BY index_name, column_position;

-- 8. Vista
SELECT view_name, text
FROM   user_views
WHERE  view_name = 'V_ACTIVE_SUBSCRIPTIONS';

-- 9. Procedimiento y sus parámetros
SELECT object_name, position, argument_name, in_out, data_type
FROM   user_arguments
WHERE  object_name = 'SP_CONTRACT_SUBSCRIPTION'
ORDER  BY position;
