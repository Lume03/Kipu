# Verification report — EP-AUT Sprint 8

**Verdict: APPROVED**
**Branch:** `011-ep-aut-captura-inbox`
**Verified HEAD:** `63932975d0c2763f4164c01d18d73ded256ac24a`
**Date:** 2026-10-29

## Scope

Capture domain, local Room persistence and migration 28→29, candidate inbox/review, notification ingress, safe ledger confirmation, and the expected-payment association primitives consumed by capture.

## Results

- `:app:testDebugUnitTest --tests 'com.kipu.app.feature.capture.*'` — **121 passed, 0 failures, 0 errors, 0 skipped**.
- `:app:testDebugUnitTest --tests 'com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepositoryTest'` — **5 passed, 0 failures, 0 errors, 0 skipped**. This covers the local notification repository changed to keep capture review events out of the remote outbox.
- `:app:connectedDebugAndroidTest` filtered to `CaptureAtomicResolutionTest` — **6 passed** on Galaxy A16 (`SM-A165M`, Android 16).
- The same instrumentation task filtered to `CaptureRoomMigrationTest` — **2 passed**.
- The same instrumentation task filtered to `ExpectedPaymentAssociationTest` — **4 passed**.
- `git diff --check origin/main...HEAD` — **PASS**.

No Supabase/pgTAP migration belongs to this Room-only persistence slice. The report verifies local tests and migration behavior; it does not certify remote deployment or real-provider notification compatibility.


## HU-49 / Sprint 9 - 2026-11-03

**Verdict: APPROVED en pruebas unitarias/build y validación acotada en hardware; aceptación PARCIAL.** Casos de notificación viva Yape en ambos órdenes, sharing desde app Yape real y 8 muestras privadas permanecen NO VERIFICADO.

Authority: backlog HU-49 plus Owner D1/D2 recorded in spec.md. Work on main explicitly assigned by coordinator; no push, PR or remote deployment.

- Full `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 1543 tests, 0 failures, 0 errors, 3 skipped.
- After the final symmetric separation fix, `./gradlew :app:testDebugUnitTest --tests '*HybridYapeRoomTest' --tests '*CaptureCorrelationTest' :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 12 tests, 0 failures/errors/skips.
- `git diff --check`: PASS.
- Room **35 -> 36 -> 37**: capture_evidence con índice único owner/referencia/fuente y fields_edited para proteger correcciones (Room 36); y actualización a Room 37, que sustituye ese índice por uno **no único** `(owner_id, reference, source)` con clave primaria `(owner_id, source, event_id)` (`RoomMigrations.kt` MIGRATION_36_37, `CaptureEvidence.kt`, `37.json`) (`HybridYapeMigrationTest.v36To37SeedsHistoricalProvenanceAndValidatesSchema`) con esquema 37.json exportado y validado.
- Validación en hardware (A16 y S24): migración real 33 → 37 exitosa en ambos teléfonos (`user_version 37` confirmado en baselines privados y final-validation.json).
- Hardware A16: OCR sintético local mediante Photo Picker con PNG sintético pasó (importe 46.70, aviso de revisión, candidato final DISCARDED, transacciones 7 y ledger 9 intactos); ACTION_SEND de texto rechazado con mensaje de advertencia. Pruebas instrumentadas de Room/migración (`ReceiptCandidateRoomTest`, `CaptureAtomicResolutionTest`, `YapeReceiptLedgerAcceptanceTest`, `ReceiptRoomMigrationTest`, `HybridYapeMigrationTest`) pasaron en A16.
- Hardware S24: cobertura aislada de concesión de URI Android (`ReceiptShareUriGrantTest` en commit 8da42128) pasó: proveedor no exportado en UID emisor, rechazo antes y después de concesión revocable, lectura exitosa con ContentResolver y ML Kit local.

Coverage: notification/share in either order, equal normalized reference with leading zeros, no second candidate after confirmation, edited fields retained, single/ambiguous weak matches, distinct references at equal amount, inclusive +/-10 minute limits, refund direction/history separation, owner isolation, explicit union with no ledger write, persisted separate decision in both directions, rollback/retry, blurry amount left null, invalid/unknown MIME rejected before bytes, Yape income extraction with confidence/integrity, Free OCR consent, and existing Premium expiry/manual conversion regressions.

The first runs exposed introduced Hilt/selection regressions and an incomplete migration fixture; these were fixed before the green full run. No unrelated preexisting failing tests remain. No Supabase changes or pgTAP migration applies to this local-only increment. Analytics was not touched. Límites que permanecen NO VERIFICADO:
1. Flujo completo de compartir desde la aplicación Yape real (la prueba de concesión de URI Android en hardware no prueba la interfaz de Yape ni el selector de Android).
2. Notificación Yape viva en ambos órdenes con OCR (el listener permaneció desactivado en la cuenta real para no generar movimientos falsos).
3. Ocho muestras privadas del propietario (fixture privado ausente, prueba omitida por assumeTrue).
Esta verificación constituye aceptación parcial de los componentes probados, sin certificar integración integral con proveedores externos.

## Revisión cruzada independiente Sprint 9 — 2026-11-03

Revisión directa (Opus) del rango `35858567..e7702012`, sin ejecutar suites nuevas. Revisado: correlación por referencia validada por importe, moneda y dirección en ambos órdenes; sugerencia sin referencia solo con candidato único y confirmación explícita; exclusión de separados, descartados y reembolsos; consultas acotadas por propietario. Sin defecto bloqueante de código. Hallazgo documental: FR-012 y Key Entities de la [spec](spec.md) siguen describiendo un índice único, pero Room v37 lo convirtió en no único para que una misma referencia con hechos distintos no se fusione. Hay que reconciliar el texto del requisito con autorización del Product Owner; aquí no se modifica. Los NO VERIFICADO anteriores no cambian.
