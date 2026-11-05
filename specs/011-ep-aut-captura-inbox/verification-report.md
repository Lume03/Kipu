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

Seguimiento documental del hallazgo LOW (corrección HU-06, 2026-11-03): se contrastaron D1/opción A de `spec.md`, FR-010/FR-012, `plan.md:5` y `RoomMigrations.kt` MIGRATION_36_37. D1 aprueba correlación en ambos órdenes y unión sugerida con candidato único sin referencia; no define explícitamente la cardinalidad de evidencia con igual referencia/fuente pero hechos incompatibles. FR-012 y plan siguen exigiendo UNIQUE(owner, referencia, fuente), mientras v37 conserva varios eventos con PK(owner, fuente, event_id) e índice no único. La revisión de código acredita la diferencia técnica, pero no constituye aprobación de producto para modificar FR-012. Contradicción específica pendiente: permitir varias evidencias con igual referencia y fuente cuando no deben fusionarse, o imponer unicidad de esa combinación. No se modifica Yape ni se inventa una decisión del Owner; las verificaciones reales pendientes se conservan.

Revisión directa (Opus) del rango `35858567..e7702012`, sin ejecutar suites nuevas. Revisado: correlación por referencia validada por importe, moneda y dirección en ambos órdenes; sugerencia sin referencia solo con candidato único y confirmación explícita; exclusión de separados, descartados y reembolsos; consultas acotadas por propietario. Sin defecto bloqueante de código. Hallazgo documental histórico: FR-012 y Key Entities de la [spec](spec.md) describían un índice único, mientras Room v37 lo convirtió en no único para que una misma referencia con hechos distintos no se fusione.

## Reconciliación de FR-012 y resolución de contradicción — 2026-11-03 [2026-11-04 UTC]

El Product Owner aprobó explícitamente: **«Conservar eventos incompatibles para revision»** ([decisiones del Owner](../sprint-9-owner-decisions.md)).
Se resuelve formalmente la discrepancia documental señalada en el hallazgo LOW de la revisión Opus:
1. FR-012, la entidad `capture_evidence`, `plan.md` y `tasks.md` quedan reconciliados con el esquema Room v37 implementado (clave primaria compuesta `(owner_id, source, event_id)`, índice no único `(owner_id, reference, source)` e índice `(owner_id, root)`).
2. Se formaliza que eventos con la misma referencia normalizada pero datos incompatibles se conservan separados para revisión del usuario, sin fusión automática y sin duplicación de efectos contables en el ledger. Misma referencia solo fusiona eventos compatibles según invariantes actuales.
3. Se retira formalmente la contradicción de FR-012 de la lista de pendientes del sprint.
4. Permanecen abiertos los criterios NO VERIFICADO en hardware: flujo completo desde app Yape real, notificación viva de Yape en ambos órdenes y 8 muestras privadas ausentes.
5. Veredicto: **Aceptación PARCIAL; no APPROVED integral.**

## Ronda de corrección 2026-11-05

- **Fecha:** 2026-11-05
- **Alcance:** HU-45, HU-46, HU-47, HU-48, HU-49, HU-50 y HU-51.
- **Evidencia automatizada:**
  - **Unitarios JVM:** 1.678 totales (1.675 PASS, 0 FAIL, 3 omitidos) en suite `:app:testDebugUnitTest`. Incluye:
    - `com.kipu.app.feature.capture.domain.CaptureSignedExpiryAcceptanceTest`: 3 PASS (`signedPremiumExpiresAtBoundaryButManualEntryAndDedupeRemainAvailable`, `premiumExpiresBetweenCaptureAndAutomaticSaveAndLeavesPendingEvidence`, `expiredSignedGrantBlocksFreshNotificationBeforeAnyEvidenceOrCandidateWrite`). Cubre expiración Premium HU-45 E4 y HU-47 E5.
    - `com.kipu.app.feature.capture.data.EvidencePurgeWorkerTest`: 3 PASS (`purgesAtInjectedTimeWithoutSessionOrNetworkAndSucceedsWhenNothingExpired`, `storageFailureRetriesAndSuccessfulRetryPurgesAtSameDeadline`, `cancellationPropagatesInsteadOfSchedulingFalseSuccess`). Cubre purga HU-51 E5.
    - `com.kipu.app.feature.capture.domain.ConfirmCaptureCandidateTest`: PASS (`notificationSourceIsSentForReviewedAndAutomaticConfirmation`).
  - **Instrumentados en Galaxy A16 (`SM-A165M`):** 142 PASS, 0 FAIL en 22 clases focalizadas:
    - `com.kipu.app.core.database.CaptureAtomicResolutionTest`: **11 PASS** (ejecutado PASS A16 2026-11-05), con:
      - `exactEventReplayAfterAutomaticRegistrationDoesNotCreateAnotherMovement`: Replay exacto de autorregistro (HU-47, HU-51 E3/E4).
      - `manualConversionAfterPremiumExpiryAndReconfirmationReturnExistingMovement`: Conversión manual ante expiración Premium y reconfirmación idempotente sin nuevo movimiento contable (HU-45 E4, HU-47 E5, HU-51 E3/E4).
      - `resolvingAndDiscardingImmediatelyPurgeEvidenceWhilePendingEvidenceRemains`: Purga inmediata de evidencia al descartar/resolver mientras candidatos pendientes conservan evidencia (HU-51 E5).
      - `evidencePurgeUsesInclusiveDeadlineAcrossOwnersAndDoesNotAlterCandidatesOrLedger`: Purga de evidencia con plazo inclusivo entre propietarios sin alterar candidatos ni ledger (HU-51 E5).
      - `freeUserRegistersExpectedPaymentManuallyWithoutCaptureOrDuplicateLedgerEffects`: Registro manual de usuario Free sin efectos duplicados ni bypass de captura.
  - **Base de datos pgTAP local:** 74 archivos / 1422 tests PASS (exit code 0). Migraciones sincronizadas remoto = local = 114.
- **Evidencia QA previa:** Informes de Sprint 8 (2026-10-29) y Sprint 9 (2026-11-03).
- **Cambios de esta ronda:**
  - Cobertura y ejecución de tests para expiración Premium (HU-45 E4, HU-47 E5), replay exacto de autorregistro y reconfirmación (HU-47, HU-51 E3/E4) y matriz de purga/privacidad (HU-51 E5) en Galaxy A16.
  - Decisión Owner (2026-11-05): OCR local de comprobantes Yape PERMITIDO en plan Free con consentimiento explícito y revisión obligatoria (excepción FR-011 EP-AUT), corrigiendo el backlog «OCR denegado en Free» y manteniendo la protección Premium del resto del pipeline de captura.
- **Pendientes y bloqueos:**
  - T046-5 (Compatibilidad real de notificaciones en vivo por proveedor externo) sigue abierta (requiere interacción en vivo con apps bancarias externas en hardware real).
  - Flujo completo desde app Yape real (HU-49 escenarios 1-3) permanece abierto / pendiente del Owner.
  - S24 no ejecutado en esta ronda por firma incompatible (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Veredicto por HU:**
  - **HU-45:** CUMPLIDO (gate, consentimiento y expiración Premium HU-45 E4 validados en A16 con `CaptureAtomicResolutionTest` y unitarios).
  - **HU-46:** PARCIAL (pipeline de extracción y parsers validados; compatibilidad real universal con proveedores externos en vivo abierta en T046-5).
  - **HU-47:** CUMPLIDO (scoring determinista, autorregistro, replay exacto y expiración HU-47 E5 validados en A16 con `CaptureAtomicResolutionTest`).
  - **HU-48:** CUMPLIDO (confirmación crédito/débito validada en Room y A16 sin efectos colaterales al salir/descartar).
  - **HU-49:** PARCIAL (correlación híbrida, Room v37 y migración validadas; OCR local Free autorizado por decisión Owner 2026-11-05; Yape real escenarios 1-3 pendiente del Owner).
  - **HU-50:** CUMPLIDO (aprendizaje desde correcciones confirmadas y followups transaccionales validados).
  - **HU-51:** CUMPLIDO (bandeja, revisión, descarte, replay exacto de reconfirmación y conversión manual E3/E4 y purga/privacidad E5 validados en A16 con `CaptureAtomicResolutionTest` y `EvidencePurgeWorkerTest`).
