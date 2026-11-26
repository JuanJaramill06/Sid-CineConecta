# Índices y optimización (Oracle y MongoDB)

Cada índice existe porque una consulta concreta lo necesita. Este documento lo justifica y muestra el antes y el después medido en los dos motores.

- Scripts de medición: [`database/mongodb/05_index_experiment.js`](../database/mongodb/05_index_experiment.js) y [`database/oracle/06_index_experiment.sql`](../database/oracle/06_index_experiment.sql).
- Volumen: [`04_volume.js`](../database/mongodb/04_volume.js) y [`04_volume_load.sql`](../database/oracle/04_volume_load.sql).

> **Estado:** las columnas "Medido" se llenan pegando la salida de los scripts después de correrlos contra Atlas y ESTUD. Las columnas "Esperado" salen del análisis del plan y de los datos deterministas del volumen. Sirven para contrastar, no son resultados.

## 1. Método

| | Oracle | MongoDB |
|---|---|---|
| Volumen | 10.000 cuentas, 25.000 perfiles, 120.000 suscripciones, **120.000 pagos** | 5.000 contenidos (≈30.800 episodios), **200.000 sesiones** |
| Estadísticas | `DBMS_STATS.GATHER_TABLE_STATS` antes de medir | `db.collection.stats()`: tamaño de datos y de cada índice |
| Métrica principal | **Buffers** (lecturas lógicas, camino B) o la operación de acceso y el costo estimado (camino A) | **keys examinadas, documentos examinados** y etapas del plan |
| Métrica secundaria | Mediana de 5 ejecuciones (ms) | Mediana de 5 ejecuciones (ms) |
| Antes / después | `DROP INDEX` → medir → `CREATE INDEX`; el bloque lo recrea aunque falle | `dropIndex` → medir → `createIndex` en un `finally`, con la misma definición |
| Control | Con y sin índice deben dar los mismos pagos e ingresos | Mismo pipeline en las dos variantes |

**Por qué el tiempo no es la métrica principal:** Atlas M0 y ESTUD son servidores compartidos y el tiempo varía con la carga de otros usuarios. Las lecturas lógicas, las keys y los documentos examinados no dependen de eso y explican el porqué del tiempo.

**Selectividad:**
- Oracle: el rango de 1 mes incluye solo el último período de cada cuenta, ≈ 10.000 de 120.000 pagos (≈ 8 %). El de 1 año los incluye todos.
- Mongo: el rango de 1 mes es ≈ 11 % de las sesiones y el de 1 año, el 100 %.
- Los dos scripts imprimen la selectividad real.

## 2. Índices de Oracle

| Índice | Columnas | Consulta / regla | Por qué |
|---|---|---|---|
| `ux_accounts_email_ci` | `LOWER(email)` único | Registro (RF2) y login (RF3) | Unicidad sin distinguir mayúsculas y búsqueda por correo en el login. Índice basado en función: la consulta debe usar `LOWER(email) = LOWER(:email)` |
| `ux_profiles_name_ci` | `(accountId, LOWER(name))` único | RF8 | Nombre único dentro de la cuenta |
| `ix_profiles_account_active` | `(accountId, isActive)` | RF10/RF12/RF23: contar perfiles activos de una cuenta | El conteo se resuelve solo con el índice. Además indexa la FK `profiles → accounts`: sin índice en la FK, Oracle bloquea toda la tabla hija al modificar la PK del padre |
| `ix_subs_account_period` | `(accountId, startsAt, endsAt)` | Vigencia (RF40, antes de buscar o reproducir) y superposición (RF22, en `sp_contract_subscription`) | Igualdad en `accountId` y rango en las fechas, sin ir a la tabla. Indexa la FK `subscriptions → accounts` |
| `ix_subs_plan` | `(planId)` | Informes por plan; FK `subscriptions → plans` | Evita el bloqueo de tabla por FK sin índice |
| `uq_payments_subscription` | `(subscriptionId)` único | RF20: un pago por suscripción | La restricción crea el índice, que además sirve al join pagos ⋈ suscripciones |
| `uq_payments_reference` | `(reference)` único | Referencia de pago irrepetible | En `05_verification.sql` provoca el fallo a mitad de la transacción |
| `ix_payments_paidat` | `(paidAt, amount, subscriptionId)` | Informe de ingresos por plan en un rango de fechas (RF66, RF70) | `paidAt` primero porque es el filtro. `amount` y `subscriptionId` lo hacen **cubriente**: la consulta no lee la tabla `payments`. **Es el índice del experimento** |

Las llaves primarias (`pk_*`) crean sus propios índices únicos.

## 3. Índices de MongoDB

| Índice | Clave | Consulta / regla | Por qué |
|---|---|---|---|
| `ix_content_title_text` | `{title: "text"}`, idioma español | Búsqueda por título (RF32) | Busca palabras, no subcadenas; ignora mayúsculas y tildes; aplica *stemming* en español. `$text` **exige** este índice |
| `ix_content_status_type_year` | `{status:1, type:1, releaseYear:-1}` | Catálogo filtrado y paginado (RF33-34) | ESR: dos igualdades (`status = PUBLISHED`, `type`) y después `releaseYear`, que sirve para el filtro por año **y** para el orden de la paginación sin `SORT` en memoria |
| `ix_content_status_genres` | `{status:1, genres:1}` | Filtro por género (RF33) | Multikey. Un índice compuesto admite **un solo** campo arreglo, por eso va separado del anterior |
| `ux_content_episode_id` | `{"series.seasons.episodes._id":1}` único parcial (`type: SERIES`) | Ids de episodio no reutilizables (RF29) | Impide repetir un id de episodio **entre** series. La unicidad **dentro** de una serie la valida la app (ver `modelo-documental.md` §9) |
| `ux_mylist_profile_content` | `{profileId:1, contentId:1}` único | RF36-37 | Impide duplicados. Su prefijo `profileId` sirve para leer la lista de un perfil |
| `ix_sessions_profile_activity` | `{profileId:1, lastActivityAt:-1}` | Continuar viendo (RF57-58) | ESR exacto: igualdad en `profileId` y orden por `lastActivityAt`. Lee solo las sesiones del perfil, ya ordenadas |
| `ix_sessions_report` | `{startedAt:1, planId:1, contentType:1, profileId:1, watchedSeconds:1}` | Informe de consumo (RF67-70) | `startedAt` primero porque es el único filtro (rango). Los demás campos son todos los que lee la agregación: queda **covered**, sin leer documentos |
| `ux_admin_email` | `{email:1}` único | Login de administrador (RF64) | |

### 3.1 ESR frente a covered (informe de consumo)

La regla ESR (Equality, Sort, Range) ordena los campos de un índice compuesto así: primero las igualdades, luego el orden y al final los rangos. El informe de consumo **no tiene igualdades ni orden**: filtra solo por un rango de `startedAt` y agrupa en memoria por `planId` y `contentType`.

- `{planId, contentType, startedAt}`, que es lo que sugiere ESR si se pensara en filtrar por plan, **no sirve aquí**. Sin igualdad sobre `planId`, MongoDB tendría que recorrer el índice completo, porque no tiene *skip scan*.
- Con `startedAt` primero, el rango es un solo tramo contiguo del índice.
- Agregar `planId`, `contentType`, `profileId` y `watchedSeconds` cuesta espacio y escrituras, pero hace la consulta covered: examina N claves y **0 documentos**.
- El experimento 3 muestra qué pasa cuando la consulta deja de ser covered.

## 4. Experimentos en MongoDB (`05_index_experiment.js`)

### 4.1 Continuar viendo (perfil 1001, ≈ 8 sesiones de 200.000)

Se explica el acceso `find({profileId}).sort({lastActivityAt:-1})` y se mide el tiempo del pipeline completo.

| Variante | Plan esperado | Keys esp. | Docs esp. | Plan medido | Keys | Docs | Mediana ms |
|---|---|---|---|---|---|---|---|
| Sin índice | `COLLSCAN -> SORT` | 0 | ≈ 200.000 (toda la colección) | | | | |
| Con `ix_sessions_profile_activity` | `IXSCAN -> FETCH` | ≈ 8 | ≈ 8 | | | | |

### 4.2 Informe de consumo (covered)

| Rango | Variante | Plan esperado | Keys esp. | Docs esp. | Plan medido | Keys | Docs | Mediana ms |
|---|---|---|---|---|---|---|---|---|
| 1 mes | Sin índice | `COLLSCAN -> GROUP` | 0 | ≈ 200.000 | | | | |
| 1 mes | Con `ix_sessions_report` | `IXSCAN -> PROJECTION_COVERED -> GROUP` | ≈ 22.000 | **0** | | | | |
| 1 año | Sin índice | `COLLSCAN -> GROUP` | 0 | ≈ 200.000 | | | | |
| 1 año | Con `ix_sessions_report` | `IXSCAN -> PROJECTION_COVERED -> GROUP` | ≈ 200.000 | **0** | | | | |

En 1 año el índice examina todas las claves, pero sigue sin leer documentos. El índice ocupa mucho menos que los documentos (comparar `indexSizes` con el tamaño de los datos en el paso 0), así que puede seguir ganando aunque no filtre nada.

### 4.3 Cuándo el índice deja de convenir (no covered)

Es la misma agregación, pero cuenta también las sesiones completadas, y `completed` no está en el índice. Se fuerza cada camino con `hint`.

| Rango | Variante | Plan esperado | Keys esp. | Docs esp. | Keys | Docs | Mediana ms |
|---|---|---|---|---|---|---|---|
| 1 mes | hint índice | `IXSCAN -> FETCH` | ≈ 22.000 | ≈ 22.000 | | | |
| 1 mes | hint `$natural` | `COLLSCAN` | 0 | ≈ 200.000 | | | |
| 1 año | hint índice | `IXSCAN -> FETCH` | ≈ 200.000 | ≈ 200.000 | | | |
| 1 año | hint `$natural` | `COLLSCAN` | 0 | ≈ 200.000 | | | |

**Qué se espera:** con 1 mes el índice gana, porque lee ~11 % de los documentos. Con 1 año lee **todos** los documentos y además todas las claves, en orden de índice (acceso aleatorio a los documentos). Así que el recorrido completo debería empatar o ganar. Ese es el punto en que el índice deja de convenir: cuando la consulta no es covered y el rango no es selectivo.

### 4.4 Búsqueda por título: `$regex` frente a `$text`

`$text` no se puede medir "sin índice", porque sin índice de texto la consulta da error. Se compara entonces con la alternativa real, una expresión regular sin ancla, que no puede usar un índice sobre `title`.

| Palabra | Variante | Plan esperado | Docs esp. | n esp. | Plan medido | Keys | Docs | n | Mediana ms |
|---|---|---|---|---|---|---|---|---|---|
| noche | `$regex` | `IXSCAN(status) -> FETCH` o `COLLSCAN` | ≈ 4.750 (todos los publicados) | ≈ 1/15 de los títulos | | | | | |
| noche | `$text` | `TEXT_MATCH -> FETCH` sobre el índice de texto | ≈ n | ≈ igual | | | | | |
| ultima | `$regex` | igual | ≈ 4.750 | **0** (los títulos dicen "última") | | | | | |
| ultima | `$text` | índice de texto | ≈ n | > 0 | | | | | |

La segunda palabra muestra la ventaja funcional de `$text`: no distingue tildes ni mayúsculas.

## 5. Experimento en Oracle (`06_index_experiment.sql`)

**Consulta medida:** el informe de ingresos por plan.

```sql
SELECT pl.name, COUNT(*), SUM(pa.amount)
FROM   payments pa
JOIN   subscriptions s ON s.subscriptionId = pa.subscriptionId
JOIN   plans pl        ON pl.planId = s.planId
WHERE  pa.paidAt >= :desde AND pa.paidAt < :hasta
GROUP  BY pl.name ORDER BY pl.name;
```

**Camino usado** (lo imprime el script): _A / B_ — _motivo_

- **Camino A** (sin acceso a las vistas `V$`): `EXPLAIN PLAN` + `DBMS_XPLAN.DISPLAY`. Las métricas son la operación de acceso a `PAYMENTS`, el costo y la cardinalidad estimados, y la mediana de tiempo.
- **Camino B** (con acceso): además `/*+ gather_plan_statistics */` + `DBMS_XPLAN.DISPLAY_CURSOR('ALLSTATS LAST')`. La métrica principal son los **Buffers** de la última ejecución.

| Medición | Acceso a PAYMENTS esperado | Acceso medido | Costo | Buffers | Mediana ms | Pagos | Ingresos |
|---|---|---|---|---|---|---|---|
| CON_INDICE_1_MES | `INDEX RANGE SCAN IX_PAYMENTS_PAIDAT`, sin `TABLE ACCESS` a PAYMENTS | | | | | | |
| CON_INDICE_1_ANIO | `INDEX FAST FULL SCAN IX_PAYMENTS_PAIDAT` (el índice es más pequeño que la tabla) o `TABLE ACCESS FULL` | | | | | | |
| SIN_INDICE_1_MES | `TABLE ACCESS FULL PAYMENTS` | | | | | | |
| SIN_INDICE_1_ANIO | `TABLE ACCESS FULL PAYMENTS` | | | | | | |

Pagos e ingresos deben coincidir entre CON y SIN del mismo rango; el script lo verifica.

**Qué se espera:**
- **1 mes:** el índice cubriente lee solo el tramo del rango (≈ 8 % de las entradas) y nunca la tabla. Los buffers sobre `PAYMENTS` deben bajar en proporción a la selectividad.
- **1 año:** no hay nada que filtrar. Lo mejor que puede hacer el índice es un *fast full scan*, que sigue siendo cubriente y más barato que leer la tabla, porque las filas del índice son más cortas. La diferencia se reduce mucho, y el optimizador puede preferir la tabla completa.
- **El join:** en los dos casos el costo de unir con `subscriptions` (120.000 filas) pesa igual. Por eso la mejora total es menor que la mejora en el acceso a `payments`.

## 6. Costo de los índices

- **Escrituras:** cada sesión nueva actualiza 3 índices (`_id`, `ix_sessions_profile_activity` e `ix_sessions_report`), y cada pausa toca solo los índices cuyos campos cambian (`lastActivityAt`). Cada pago nuevo actualiza 4 índices (PK, `uq_payments_subscription`, `uq_payments_reference` e `ix_payments_paidat`). Es aceptable: las lecturas por perfil y los informes son mucho más frecuentes que el costo extra de cada inserción.
- **Espacio:**
  - Mongo: el paso 0 de `05_index_experiment.js` imprime el tamaño de cada índice; `ix_sessions_report` es el más grande porque tiene 5 campos.
  - Oracle: el paso 2 de `06_index_experiment.sql` lo imprime desde `USER_SEGMENTS`.

## 7. Conclusiones

_Completar con las mediciones:_ cuánto bajan las keys/docs o los buffers en el rango selectivo, a partir de qué rango deja de convenir el índice no cubriente, y si el optimizador de Oracle eligió el índice en el rango de 1 año.
