# Implementación de la auditoría 2026-11-11

Rama `Lume03/audit-fixes` (worktree `C:\Users\Alume\orca\workspaces\Kipu\audit-fixes`), creada desde `main` en `2edc52bc`. Son 22 commits, uno por lote. El plan y las decisiones están en [INFORME.md](INFORME.md), §7 y §8, y cada lote tiene su entrega en `L*-delivery.md`.

## Lotes y commits

| Lote | Commit | Qué resolvió | Worker |
|---|---|---|---|
| L0 | `8e6d3969` | Higiene del repo: `.run/` fuera de git, `.gitignore`, `AGENTS.md` y `CLAUDE.md` versionados, sin `hilt-android-testing` | Codex Luna high |
| L1 | `7d2fa389` | Corrección de apertura: revierte el ajuste vigente, recibo idempotente, publicación en el feed (SQL1-01/02/03, ACC5-01) | Codex Sol high |
| L2 | `3054b4be` | Eliminar compras con tarjeta sin pagos y cualquier reembolso; reembolso solo con tarjeta; MOV2-01 | Codex Sol high |
| L3 | `bf163645` | Política común de outbox en cuentas y movimientos | Codex Sol high |
| L3b | `98af521d` | La misma política en presupuestos, categorías, recurrencias, deudas, perfil y captura | Codex Terra → Sol medium |
| L4 | `895ab2c3` | Revisiones encadenadas, orden de comandos y RPC de categorías con gana-el-último | Codex Sol medium |
| L4 (fix) | `5f7887b6` | Versiones parciales de conflictos y tests SQL alineados (lo encontró la suite local) | Fable |
| L5 | `f932755e` | Compensación de rechazos locales y aviso «No sincronizado» | Codex Sol medium |
| L6 | `8fd773a3` | Deudas, más el dueño por comando en deudas, metas y categorías | Codex Sol medium |
| L7 | `fc3a8020` | Billing y edge functions | Codex Sol medium |
| Docs | `3517687c` | Las 16 decisiones registradas en las specs | Gemini Flash high |
| L8a | `a30b9f0f` | Seguridad: FLAG_SECURE, widgets, biometría, contraseña Unicode, signOut | Codex Terra medium |
| L8b | `8106ea0c` | Enmascarado de saldos en todas las pantallas | Gemini Flash high |
| L9a | `d710b2fe` | Reglas de dinero y fechas en SQL | Codex Sol medium |
| L9b | `af2d2d88` | Fechas y cálculos en la app | Codex Terra medium |
| L9c | `e0db5586` | La deuda de la tarjeta incluye el interés (servidor + app, Room 41) | Codex Sol high |
| L10a | `32805a46` | UI de cuentas, tarjetas y movimientos | Gemini Flash high |
| L10b | `e3e025f8` | UI del resto de módulos | Gemini Flash high |
| L11 | `c409e474` | Tests rotos, vacíos y desactualizados | Gemini Flash high |
| L11b | `a7347900` | Bugs destapados por los tests (deduplicación de Yape, P10, metas) | Codex Terra medium |
| L12 | `8415d7cb` | Limpieza: unas 3.500 líneas netas borradas | Gemini Flash high |
| Pull | `630b7792` | El feed compartido se descarga una vez por sincronización | Codex Sol high |

En total, 661 archivos, +27.988 y −6.237 líneas, contando la documentación de la auditoría.

## Cambios aplicados en remoto (Supabase `tnvapmertsncbslafoug`)

Antes de subir nada, cada migración pasó por `supabase db reset` y por la suite pgTAP completa en local, que es lo que autorizó el dueño. Se aplicaron con su versión exacta en `supabase_migrations.schema_migrations`:

`20261111120000`, `20261111130000`, `20261111140000`, `20261112100000`, `20261112110000`, `20261112120000`, `20261112130000` y `20261112140000`. Remoto pasó de 116 a 124 migraciones, igual que el repo.

Edge functions desplegadas:
- `verify-purchase` (v8)
- `plans` (v11)
- `play-rtdn` (v3)
- `auth-access` (v15)

Comprobaciones de datos en remoto, todas de solo lectura:
- 0 saldos de apertura corruptos.
- 0 conflictos de categorías abiertos.
- Todas las cuentas con saldo inicial tienen su apertura en el ledger.
- Hay 1 compra con interés (20 cuotas), afectada a propósito por L9c.

## Verificación final

| Suite | Resultado |
|---|---|
| pgTAP (`supabase test db` tras `db reset`) | 82 archivos, 1.602 tests, PASS |
| Deno (`deno test supabase/functions`) | 158 PASS |
| Unitarios (`:app:testDebugUnitTest`) | 1.743 tests, 0 fallos, 1 omitido (muestras privadas de PDF) |
| androidTest (`:app:compileDebugAndroidTestKotlin`) | Compila; **no se ejecutó**, porque no había dispositivo |

## Correcciones del orquestador sobre lo que entregaron los workers

- **L3:** `APPEND_OR_REPLACE` habría dejado los movimientos nuevos esperando detrás del backoff de un fallo anterior. Se dejó `REPLACE`, que con los leases es seguro.
- **L4:** la resolución de conflictos de categorías fallaba con versiones parciales. Lo encontró la suite pgTAP local y se corrigió antes de subir.
- **L8a:** mover el límite por IP a después de Auth permitía probar contraseñas sin freno. Se revirtió; SEC1-07 queda como `shortcut:` documentado.
- **L9b:** sumar el interés solo en la app habría mostrado una deuda imposible de pagar. Se revirtió y se rehízo en L9c, en servidor y app a la vez.
- **L11:** había marcado como `TODO` tests que fallaban por un bug real (P10). El bug se corrigió en L11b.

## Lo que queda para el dueño

1. **Pruebas en el teléfono.** No se hicieron: no había dispositivo y es la parte que la autonomía no cubre. Cada `L*-delivery.md` lista sus casos. Los prioritarios, en A16 y S24 con `installDebug` + `am instrument`:
   - corregir una apertura dos veces;
   - eliminar una compra en cuotas;
   - reembolso con tarjeta y sincronización en el otro teléfono;
   - 20 comandos en modo avión y reconexión;
   - Recientes y widgets con la app bloqueada;
   - biometría en Android 9 o 10 (emulador);
   - avisos de recurrencia consecutivos.
2. **Ejecutar los androidTest** en dispositivo, incluida la migración Room 40→41 (`CardInterestMigrationTest`).
3. **Revisar y fusionar** `Lume03/audit-fixes` a `main`.
   - **Atención:** remoto ya tiene las migraciones nuevas. La app de `main` sigue funcionando con ellas, pero las funciones nuevas (eliminar compras con tarjeta, aviso «No sincronizado», interés en la deuda) solo llegan con esta rama.
4. **Billing:** las compras reales hay que probarlas en la pista de pruebas internas de Play. Una compra antigua sin `obfuscatedAccountId` quedaría rechazada (decisión 11; está marcado como `shortcut:` en `verify-purchase`).
5. **Pendientes conocidos, documentados en las entregas:**
   - SEC1-07: el cupo por IP se gasta también con logins correctos.
   - La revisión de privacidad de las 211 capturas de `docs/referencias/`.
   - Los hallazgos medios y bajos que cada worker listó como no hechos.
