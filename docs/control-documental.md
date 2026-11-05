# Control documental: integración desde KipuApp

**Fecha:** 2026-10-20. **Alcance:** selección documental y navegación; no cambio funcional, ejecución SQL, despliegue ni cierre de auditorías.

## Decisiones del propietario

- `Kipu/docs` es la fuente principal de documentación; `specs/` conserva los artefactos de trabajo por épica.
- Revisar los documentos antes de integrarlos, sin copiar todo por defecto.
- Dejar los originales y el material excluido intactos en `KipuApp`.
- Excluir Bizagi: pertenece a otra entrega del curso.
- Se permite reorganizar la documentación existente. Tras revisar el resultado inicial, el propietario solicita integrar los documentos completos de entidades/MER y comercios, agrupar las evidencias de auditoría y renombrar la guía visual a `DESIGN.md`.

## Selección y equivalencias de nombres antiguos

Las rutas de origen de esta tabla son relativas a `KipuApp`. Las citas históricas en specs, research o informes mantienen su procedencia: localizar el tema con esta tabla, sin requerir otra bóveda ni reescribir evidencia histórica.

| Origen | Decisión | Ubicación y motivo |
| --- | --- | --- |
| `Kipu md/01_Kipu_V4.2_Especificacion_Producto.md` | Integrar con nota de alcance | [Especificación](producto/especificacion.md): base general, no prueba de implementación |
| `Kipu md/02_Kipu_V4.2_Product_Backlog.md` | Integrar con aclaraciones | [Backlog](producto/backlog.md): conserva HU-01–HU-60, reglas y Gherkin; explicita refinamientos del cupo por tipo |
| `Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` | Integrar completo como base global versionada | [Modelo de datos](arquitectura/modelo-de-datos.md): diccionario y diagramas; [índice](arquitectura/README.md) con refinamientos por épica |
| `Kipu md/04_Kipu_V4.2_Trazabilidad_y_Dependencias.md` | Integrar | [Trazabilidad](producto/trazabilidad-y-dependencias.md): dependencias y plan base, no aceptación |
| `Kipu md/05_Cronograma_Scrum_Kipu_V4.1.md` | Integrar como planificación base | [Cronograma](planificacion/cronograma-scrum.md): su cabecera y horizonte acreditan que V4.1 planifica V4.2, no que sea un producto anterior |
| `Entidades/V4.2_Entidades.md` y `V4.2_Modelo_Entidad_Relacion.md` | Integrar completos, con versión visible | [Entidades](arquitectura/entidades.md) y [MER](arquitectura/modelo-entidad-relacion.md): base 2.1 del 20/09; no equivalen a un esquema desplegado verificado |
| `Entidades/Entidades antiguas/` | No migrar | Modelos explícitamente históricos |
| `bizagi/` | Excluir | Entrega académica; sin archivos `.bpm`, `.bpmn` ni imágenes importados |
| `Procesos/` | No copiar el modelado académico | Los 32 documentos incluyen participantes BPMN e instrucciones de dibujo. El [índice de flujos](procesos/README.md) remite a HU/specs, sin convertir esos detalles en requisitos nuevos |
| `Tasas de Bancos del Peru/KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` | Integrar como referencia fechada | [Catálogo de tarjetas](referencias/peru-financiero/catalogo-tarjetas.md): investigación consolidada al 24/09, con advertencias y pendientes de origen |
| `Tasas de Bancos del Peru/Auditorias e Informes Tasas de Bancos/` | No migrar | Fuentes intermedias y revisiones; el maestro conserva su metodología, URLs y discrepancias |
| `Comercios/` (Claude, Gemini, GPT y copia sin extensión) | Integrar una investigación seleccionada; excluir alternativas | [Investigación GPT completa](referencias/comercios/investigacion-peru-2026.md): fuentes, metodología y límites explícitos; [inventario del seed](referencias/comercios/catalogo-aplicacion.md): 63 entradas, separado de los 127 candidatos |
| `Inicialización del Proyecto/Versiones y recomendaciones para la app.md` | No migrar | Recomendaciones de arranque, no configuración real. Por ejemplo propone AGP 9.3.1, mientras el catálogo local registra 9.4.1; esto no juzga compatibilidad, solo evita otra tabla de versiones |
| `Prototipo/P1/` y `Prototipo/Protitpo 2/` | No copiar como guía vigente | Ya hay [UI/UX](ux-ui/README.md). La segunda propuesta declara alcance congelado hasta Sprint 3 |
| `Auditorias/` y `auditoria_prs/` | No copiar en bloque | Ya hay [auditorías](audits/README.md), UI y QA. Igual nombre no implica igualdad o reemplazo |
| `Sin título.md` | Excluir | Su contenido corresponde a un sistema web de gestión de botica, no a Kipu |
| `AGENTS.md`, `CLAUDE.md`, `.git/`, `.obsidian/`, `.mcp.json`, `.om-project`, `.DS_Store` | No importar | Instrucciones y configuración ajenas a la documentación seleccionada. Se actualizan únicamente las instrucciones de `Kipu` |

No se copian todas las revisiones antiguas ni se crean diagramas vacíos para completar una estructura ideal. Los documentos globales V4.2 seleccionados sí se integran completos con versión, procedencia y advertencias; los artefactos incrementales de Spec Kit no se duplican.

## Diferencias detectadas y tratamiento

### DOC-01 — Categorías Free: contexto por tipo

La especificación general dice 5 de gastos + 5 de ingresos; HU-14/HU-57 del backlog de origen y algunos escenarios dicen solo cinco. [EP-CCO](../specs/003-ep-cco-categorias-comercios/spec.md) contiene una aclaración de autoridad por tipo, y existe una [migración versionada 5 + 5](../supabase/migrations/20261007144500_category_free_quota_5_plus_5.sql).

**Tratamiento:** notas explícitas en el backlog dirigen a ese refinamiento con respaldo técnico en la migración `20261007144500_category_free_quota_5_plus_5.sql` (el cupo 5+5 aplica exclusivamente con el respaldo de dicha migración). En la ronda 2026-11-05 se consolida este criterio en el backlog y artefactos relacionados, preservando la trazabilidad con los escenarios de selección.

### DOC-02 — Modelo físico de septiembre frente a refinamientos

El antiguo documento 03 y los dos de entidades declaran 34 tablas remotas y 10 Room-only. Los modelos de [EP-PLA](../specs/012-ep-pla-planes-monetizacion/data-model.md), [EP-MET](../specs/006-ep-met-metas-reservas/data-model.md), [EP-REC](../specs/007-ep-rec-recurrencias-obligaciones/data-model.md) y otras épicas incluyen contratos e incrementos posteriores. No se hizo una reconciliación exhaustiva columna por columna ni una inspección de bases remotas.

**Tratamiento:** se integran completos el modelo global, las entidades y el MER de V4.2, conservando sus fechas; el índice de arquitectura dirige además al detalle por dominio. Las cabeceras no los presentan como esquema remoto actual. Se advierte específicamente sobre las menciones originales a SMS/Plin y sobre la representación de reservas de metas mediante `GOALS_VIRTUAL`: no sustituyen la constitución, el backlog ni EP-MET. **Pendiente:** reconciliación exhaustiva del modelo global con el esquema objetivo; no reutilizar los conteos antiguos como estado actual.

### DOC-03 — Dos declaraciones de autoridad visual

[Tokens Stitch](DESIGN.md) declara Andean Modernist y el [índice de mockups](ux-ui/mockups/README.md) declara Calm Emerald, con proyectos Stitch distintos.

**Tratamiento:** advertencia visible en ambos documentos y en el índice UI/UX, sin cambiar tokens ni imágenes. **Pendiente:** confirmar la autoridad por pantalla o aprobar una unificación visual; no elegir por fecha solamente.

El índice de mockups también referencia nueve imágenes de estados de tarjetas que no están en este checkout. Se señalan como pendientes sin enlaces navegables y se corrigen tres enlaces de recuperación con caracteres mal codificados. No se generan imágenes ni se afirma que esos estados estén diseñados.

La misma disponibilidad se explicita en el mapa de migración de mockups; se corrige además la profundidad relativa de 18 enlaces a código/specs en la auditoría UI/UX. Dos citas históricas de specs a un informe S1/S2 y a `Stich Prompts.md` no están incorporadas en este checkout: se conservan como texto con nota de indisponibilidad, no como enlaces rotos ni evidencia actual. No se cambian criterios de aceptación.

### DOC-04 — Planificación y evidencia de entrega

El cronograma tiene horizonte hasta el 17/11/2026 y es coherente nominalmente con 60 HU y 12 épicas V4.2. Esto no acredita cumplimiento. Actualmente las 12 carpetas de épica existen en este repositorio (`specs/001-ep-aps...` a `specs/012-ep-pla...`); mediante el commit `0059e329`, las carpetas de EP-DEU (`005-ep-deu-deudas-prestamos`), EP-PRE (`008-ep-pre-presupuestos-consumos`) y EP-INF (`009-ep-inf-informacion-financiera`) fueron alineadas con el orden del cronograma y dotadas de artefactos propios, dejando EP-DEU de utilizar el prefijo `013`.

**Tratamiento:** conservar fechas y asignaciones como plan base. Todas las épicas cuentan con carpeta y artefactos Spec Kit en este checkout. Consultar las tareas, gates e informes de cada alcance.

### DOC-05 — Tasas e investigaciones no revalidadas

El catálogo bancario tiene corte 24/09/2026 y conserva once discrepancias pendientes según su propio cierre. Sus dictámenes originales no son una auditoría nueva. Las investigaciones de comercios proponen extensiones y no equivalen al seed implementado.

**Tratamiento:** referencia bancaria fechada y enlace a EP-CTA. Para comercios se incorpora la investigación GPT completa con sus límites de evidencia y se documentan aparte las 63 filas del seed; Claude, Gemini y la copia sin extensión permanecen fuera. **Pendiente:** revalidación externa antes de publicar nuevas tasas o ampliar catálogos. No se realizaron solicitudes a bancos ni importaciones de datos.

### DOC-06 — Calidad residual de la especificación

RNF-24 en la especificación de origen dice solamente «Dominio, plataforma y seguridad», sin condición verificable. Las cifras de rendimiento y usabilidad son objetivos, no resultados.

**Tratamiento:** preservar el identificador y registrar la limitación. **Pendiente:** aclarar RNF-24 antes de utilizarlo como criterio de aceptación; no inventar su contenido.

## Duplicidad observada

- `auditoria_prs/PR-03-50-quick-movement/issue-46-visual-evidence.md` coincide byte a byte con [la evidencia ya presente](ux-ui/mockups/states/quick-movement/issue-46-visual-evidence.md): no se copia.
- Los informes `KIPU_FULL_FUNCTIONAL_UX_AUDIT.md`, `issue-39-dark-theme-audit.md` e `issue-39-dark-theme-plan.md` tienen nombres coincidentes pero bytes distintos entre ambos proyectos. No se sobrescriben ni se asume que uno cierre los hallazgos del otro.

## Límites de la revisión

Revisión local de estructura, cabeceras, reglas de negocio del backlog, planificación, modelos por épica y referencias cruzadas. No es una auditoría exhaustiva de cada escenario contra código o esquema desplegado. El MCP `om` no estaba disponible; no se afirma haber contrastado notas externas. La decisión explícita del propietario establece la nueva fuente principal.

Se preservan los originales de `KipuApp`, el contenido funcional de los artefactos Spec Kit y los bytes de la evidencia existente. Las referencias documentales se actualizan al cambiar rutas; las diferencias pendientes no se convierten en decisiones de producto nuevas.

## Corrección de la organización inicial

La primera integración dejó arquitectura y comercios como índices y mantuvo las evidencias antiguas mezcladas. Se corrige para que el contenido seleccionado pueda leerse directamente en `docs/`:

- `arquitectura/modelo-de-datos.md`, `entidades.md` y `modelo-entidad-relacion.md` contienen los documentos completos, no páginas vacías. El mapa por dominio pasa a `arquitectura/README.md`.
- `referencias/comercios/investigacion-peru-2026.md` conserva la investigación seleccionada; `catalogo-aplicacion.md` contiene el inventario del seed, con nombre y subcategoría de sus 63 entradas.
- La auditoría funcional se agrupa en `audits/funcional/2026-09-28/`; sus evidencias se separan en diez recorridos y por tipo de archivo. Los once archivos locales sin recorrido conservan su exclusión de Git en `_local-only/`.
- La auditoría de tema oscuro se agrupa en `audits/tema-oscuro/issue-39/`, con informe, plan y evidencias propios.
- `docs/stitch-design-system.md` pasa a [docs/DESIGN.md](DESIGN.md). No se recupera el contenido del antiguo DESIGN previo a Stitch. Se actualizan enlaces, referencias textuales y dos campos de procedencia visual en `CardStylePresets.kt`, sin cambiar colores ni comportamiento.
- Se preserva la evidencia seleccionada; los archivos locales excluidos se describen más abajo. El traslado no certifica la privacidad del contenido histórico ni borra datos de commits anteriores.

## Verificación de la integración

- Se conservan las 60 cabeceras de HU y los diez sprints en los documentos integrados.
- Los enlaces locales agregados o modificados se verifican contra el filesystem; las URLs externas y la equivalencia semántica de sus contenidos no se revalidan.
- No se importan diagramas BPMN/Bizagi ni se modifican migraciones o requisitos funcionales. El MER integrado usa diagramas Mermaid ya existentes. Los cambios en specs y en los dos campos de procedencia Kotlin son únicamente de rutas documentales.
- `AGENTS.md` y `CLAUDE.md` se actualizan localmente para apuntar a `docs/`, pero están ignorados por las reglas actuales de `.gitignore`. Esos cambios no se distribuirán mediante un commit ordinario. La política compartida queda documentada en `docs/README.md`; se conserva su exclusión y no se fuerza su inclusión.

### Verificación posterior a la corrección

- 846 enlaces locales de Markdown comprobados en `docs/`, `specs/` y el README raíz: sin destinos de archivo/carpeta rotos. No incluye verificación de URLs externas o anclas de sección.
- Evidencia funcional depurada por solicitud del propietario: de 173 XML se conservaron los 4 enlazados por el informe y se eliminaron 169 no referenciados. PNG, diagnósticos y demás payloads conservados no se editaron salvo los resúmenes Room citados abajo; la evidencia de issue-39 mantiene sus hashes.
- Limpieza posterior solicitada: se eliminaron 180 PNG funcionales no referenciados por ningún Markdown del repositorio (aprox. 39.3 MiB). Las 77 capturas referenciadas y toda la evidencia de issue-39 se conservan.
- Los 13 snapshots `kipu.db` y sus seis sidecars WAL/SHM se movieron a `_local-only/snapshots-room/`, ignorado por Git. Los enlaces a los binarios se quitaron del informe y se mantienen referencias a los resúmenes de texto disponibles. El historial de Git previo puede seguir conteniendo los archivos versionados antes de la reorganización.
- Los once archivos locales sin recorrido siguen ignorados por Git. La modificación de `.gitignore` reemplaza la regla de la antigua carpeta de evidencia por `_local-only/`; no cambia la exclusión de `AGENTS.md` o `CLAUDE.md`.
- Las 63 filas de comercio/subcategoría coinciden con el seed leído localmente; no se ejecuta SQL.
- Se conservan los cinco bloques Mermaid del MER original y los documentos detallados de modelado/entidades.
- El diff de Kotlin se verifica como sustitución exclusiva de dos rutas de procedencia documental. No se ejecutan Gradle ni pruebas de aplicación para este cambio de referencias.

## Decisiones de producto 2026-11-01 (auditoría técnica)

Fuente: "Decisión PO 2026-11-01, auditoría técnica". Se registran en los artefactos indicados sin cambiar código, migraciones ni otros requisitos. Con D4, **SPEC-CTA-01 queda resuelta**.

| ID | Decisión | Artefactos afectados |
| --- | --- | --- |
| D1 | Alertas de utilización 50/80/100 % (HU-10): se evalúan y emiten solo en el servidor y llegan a la app por centro de notificaciones/sync. La app no evalúa umbrales localmente. Sin conexión no hay aviso hasta sincronizar (aceptado). | [EP-CTA spec](../specs/002-ep-cta-cuentas-tarjetas/spec.md) (nota HU-10 y clarificación 2026-11-01) |
| D2 | Recurrencias: la sincronización app↔servidor del outbox se implementa ahora (cliente API + worker con reintentos, idempotencia y conflictos), conforme a FR-010. No cambia requisitos. | [EP-REC spec](../specs/007-ep-rec-recurrencias-obligaciones/spec.md) y [plan](../specs/007-ep-rec-recurrencias-obligaciones/plan.md) (clarificación 2026-11-01) |
| D3 | CAPTCHA eliminado: login, registro, OTP y reenvío quedan protegidos por los rate limits existentes, sin token CAPTCHA en cliente ni servidor. | [EP-APS contratos](../specs/001-ep-aps-acceso-perfil-seguridad/contracts/), [plan](../specs/001-ep-aps-acceso-perfil-seguridad/plan.md), [research](../specs/001-ep-aps-acceso-perfil-seguridad/research.md), [quickstart](../specs/001-ep-aps-acceso-perfil-seguridad/quickstart.md), [tasks](../specs/001-ep-aps-acceso-perfil-seguridad/tasks.md); [auditoría de onboarding](ux-ui/audits/AUTH_ONBOARDING_IMPLEMENTATION.md) (notas Superseded) |
| D4 | Tarjeta archivada: no recibe pagos ni compras; no puede archivarse con deuda pendiente (debe liquidarse antes). Nunca existe tarjeta archivada con deuda. Resuelve SPEC-CTA-01 ([hallazgo en auditoría funcional](audits/funcional/2026-09-28/informe.md)). | EP-CTA: [spec](../specs/002-ep-cta-cuentas-tarjetas/spec.md) (clarificación), [plan](../specs/002-ep-cta-cuentas-tarjetas/plan.md), [data-model](../specs/002-ep-cta-cuentas-tarjetas/data-model.md), [ui-contract](../specs/002-ep-cta-cuentas-tarjetas/contracts/ui-contract.md) |
| D5 | Zona horaria de negocio única America/Lima para todos los cálculos de fechas de negocio (recurrencias, pagos esperados, recordatorios, reanudación, periodos). | EP-REC: [spec](../specs/007-ep-rec-recurrencias-obligaciones/spec.md) y [plan](../specs/007-ep-rec-recurrencias-obligaciones/plan.md) |
| D6 | RF-C12 (residuo de céntimos a la cuota 1) aplica solo a compras nuevas. No se migran cronogramas existentes: los datos actuales son de prueba y se eliminarán antes de producción. | EP-CTA: [spec](../specs/002-ep-cta-cuentas-tarjetas/spec.md) (clarificación 2026-11-01) |


### D7 — Modo desarrollo de planes (PO 2026-11-02)

Con el gate interno encendido, cualquier usuario autenticado puede activarse Premium sin pago: Free, Mensual (30 días), Anual (365 días) o Lifetime (sin vencimiento), desde las pantallas de planes y también en builds release. La autoridad es el servidor; se reutilizan overrides y grants firmados con lease offline de hasta 72 horas. Preferencias HU-52 y compras reales permanecen separadas, sin datos simulados en billing. Se conserva la precedencia permanente DEVELOPER existente.

**Obligatorio antes del lanzamiento:** APAGARLO con `internal.set_dev_plan_mode(false, 'preparación del lanzamiento')` y cargar `billing_products` reales. La migración no enciende el gate. Apagarlo impide nuevas activaciones y grants de prueba; los grants ya emitidos vencen dentro de su lease offline existente. La compatibilidad pendiente de Lifetime con `verify-purchase` debe validarse con un producto de pago único de Google Play antes de ofrecerlo comercialmente.

Artefactos: [EP-PLA spec](../specs/012-ep-pla-planes-monetizacion/spec.md), [plan](../specs/012-ep-pla-planes-monetizacion/plan.md), [tasks](../specs/012-ep-pla-planes-monetizacion/tasks.md), [contrato](../specs/012-ep-pla-planes-monetizacion/contracts/dev-plan-mode.md) y [ADR-012](adr/ADR-012-freemium-intent.md). Esta decisión aprueba únicamente el modo temporal de desarrollo; no acredita despliegue remoto.

## Decisiones del Owner 2026-11-05 (autoridad para esta ronda)

Fuente: "Decisiones del Owner (2026-11-05)". Se registran en los artefactos indicados sin cambiar código, migraciones ni otros requisitos fuera del alcance asignado.

| ID | Decisión | Artefactos afectados |
| --- | --- | --- |
| D-20261105-01 | HU-13 (Cierre exclusivo): una compra hecha exactamente el día de cierre entra al ciclo SIGUIENTE (cierre EXCLUSIVO, comportamiento actual del código). | [Backlog](producto/backlog.md) (HU-13), [EP-CTA spec](../specs/002-ep-cta-cuentas-tarjetas/spec.md) |
| D-20261105-02 | HU-10 (Umbrales de crédito fijos): umbrales de utilización de crédito FIJOS 50/80/100 (no editables por el usuario). Se elimina el escenario de edición de umbrales en el backlog. | [Backlog](producto/backlog.md) (HU-10), [EP-CTA spec](../specs/002-ep-cta-cuentas-tarjetas/spec.md) |
| D-20261105-03 | HU-08 / HU-57 (Débito compartido y cupos): la tarjeta de débito COMPARTE el cupo Free de su banco/cuenta (corrección aprobada 2026-10-21, migración `20261021191708_shared_bank_instrument_quota.sql`). Se actualiza la regla de 4 cupos. | [Backlog](producto/backlog.md) (HU-08, HU-57), [EP-CTA spec/quickstart](../specs/002-ep-cta-cuentas-tarjetas/) |
| D-20261105-04 | HU-49 / HU-58 (OCR Yape en Free): OCR local de comprobantes Yape PERMITIDO en Free con consentimiento y revisión (excepción FR-011 EP-AUT). El resto de la captura (notificaciones, autorregistro) sigue Premium. | [Backlog](producto/backlog.md) (HU-49, HU-58), [EP-AUT spec](../specs/011-ep-aut-captura-inbox/) |
| D-20261105-05 | HU-52 (Copy Trial solo si elegible): el texto informativo del Trial se muestra SOLO si el usuario es elegible (comportamiento actual). | [Backlog](producto/backlog.md) (HU-52), [EP-PLA spec](../specs/012-ep-pla-planes-monetizacion/) |
| D-20261105-06 | HU-22 / HU-58 (Filtro por fuente): se IMPLEMENTA el filtro por fuente (Manual / Notificación / OCR) como filtro avanzado Premium; los movimientos históricos sin procedencia se clasifican como "Manual", salvo los vinculados a candidatos de captura. | [Backlog](producto/backlog.md) (HU-22, HU-58), [EP-MOV spec](../specs/004-ep-mov-movimientos-ledger/) |
| D-20261105-07 | HU-40 / HU-42 (Navegación al detalle y fallback): los eventos de recurrencia del calendario y los avisos de deuda/meta abren el detalle de su objeto; si ya no existe, fallback "no disponible". | [EP-INF spec](../specs/009-ep-inf-informacion-financiera/), [EP-NOT spec](../specs/010-ep-not-notificaciones-avisos/) |
| D-20261105-08 | BUG-003 (HU-28/29 - Anulación de pago de deuda): anular pago de deuda desde el detalle de la deuda Y desde el movimiento vinculado, misma RPC; reabre la deuda si estaba cerrada. | [EP-DEU spec](../specs/005-ep-deu-deudas-prestamos/) |
| D-20261105-09 | Play Billing (HU-53/54/55/56): BLOQUEADO externamente (sin track/testers/productos en Play Console). No marcar como aceptado. | [EP-PLA spec](../specs/012-ep-pla-planes-monetizacion/), [Cronograma](planificacion/cronograma-scrum.md) |
| D-20261105-10 | Yape real (HU-49 escenarios 1-3): pendiente de validación en vivo por el Owner. | [Backlog](producto/backlog.md) (HU-49), [EP-AUT spec](../specs/011-ep-aut-captura-inbox/) |
