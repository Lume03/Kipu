# HU-06 parte A: contrato JSON v1

La exportación es una copia personal consultable; la restauración recupera datos de Supabase y no importa este archivo.

El objeto raíz contiene `schemaVersion: 1`, `ownerId`, `exportedAt` (ISO 8601), `moneyEncoding: "integer_minor_units"` y `entities`. Cada clave de `entities` contiene un array, incluso sin datos. Los nombres y tipos de columnas conservan el contrato local; fechas de calendario son cadenas y timestamps financieros conservan sus unidades persistidas. Los importes son enteros de unidades menores con la moneda del registro o su entidad padre referenciada. No se convierten mediante coma flotante.

`PortableJson.columns` es la lista explícita de tablas y campos de esta versión: perfil/preferencias, cuentas, tarjetas, hechos financieros, movimientos, vínculos, revisiones y asignaciones oficiales, ledger, cuotas y asignaciones de pago, deudas/cuotas/eventos, metas/eventos de reserva, presupuestos/períodos, recurrencias, categorías/presentaciones y comercios/reglas/preferencias propios.

Se selecciona `user_id = ownerId` y el serializador vuelve a comprobar el propietario. Los catálogos globales no se exportan. Las instantáneas JSON de revisiones y plantillas de recurrencia se convierten a objetos con campos financieros permitidos; se excluyen campos desconocidos, credenciales, tokens y evidencia, incluidos objetos de otro propietario.

No se exportan plan, billing, entitlements, outboxes, tombstones, recibos de comandos, conflictos operacionales, notificaciones, candidatos/evidencia de captura/OCR ni importaciones pendientes de comprobantes. La lista permitida evita incluir automáticamente futuras tablas o columnas.

CSV conserva el contrato de `MovementExport`: movimientos propios con identificadores, importes menores, moneda y fechas, escapado RFC 4180 y UTF-8. SAF crea directamente el documento elegido; no se solicita acceso general al almacenamiento ni se crean copias temporales de datos personales.

La restauración lee todas las páginas de los feeds desde cero y aplica los mismos identificadores mediante las operaciones idempotentes existentes. Las lecturas complementarias de historia, reglas personales y hechos financieros usan filtros de propietario y paginación. Las aperturas sintéticas se sustituyen por hechos auditables sin sumar dos veces el saldo. Los pendientes locales se conservan y se advierte que no están en el servidor; no se envían como parte de la restauración.

`RestoreResult` distingue módulos completados/fallidos reintentables y resultado global `COMPLETED`, `PARTIAL` o `ERROR`. El reintento conserva los éxitos del mismo propietario y vuelve a ejecutar los fallidos. El plan efectivo no se restaura: las suscripciones se gestionan en Google Play.

### Comprobación de procedencia EP-DEU — 2026-11-03

La igualdad de conteos de `financial_movements` entre instalaciones no demuestra por sí sola integridad o pérdida. `DebtLocalDataSource.openDebt` registra una fila auxiliar DEBT_DISBURSEMENT mediante `insertRaw`, además de transacción/evento/ledger. Los handlers remotos de apertura persisten la operación en `transactions`, `debt_events` e `internal.ledger_entries`, sin esa fila auxiliar. La restauración recupera esas entidades por sus feeds y `financialFacts` solo lee hechos que existen en el servidor; no reconstruye la copia auxiliar del dispositivo de origen ni la vuelve a contabilizar. Las filas auxiliares locales válidas se conservan. Esto describe la implementación contrastada, sin ampliar el contrato ni autorizar borrados o nuevos hechos remotos. Evidencia y límites: [informe de dispositivos](../sprint-9-device-validation.md#procedencia-de-los-desembolsos-locales--2026-11-03). Room actual de la aplicación se encuentra en v37 (verificado user_version 37 en hardware). La restauración fue validada 7/7 módulos dos veces en ambos teléfonos (A16 y S24) preservando saldos e idempotencia. Room actual de la aplicación se encuentra en v37 (verificado user_version 37 en hardware). La restauración fue validada 7/7 módulos dos veces en ambos teléfonos (A16 y S24) preservando saldos e idempotencia.
