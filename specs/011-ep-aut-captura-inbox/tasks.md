# Tareas: EP-AUT — Captura Automática e Import Inbox

## Incremento Sprint 8: Captura, Scoring, Bandeja y Confirmación Contable (HU-45, 46, 47, 48, 50, 51)

### HU-45: Activación del listener y Gate de Acceso
- [x] **T045-1 Gate de acceso y evaluación de sesión/permisos/consentimiento/entitlement Premium.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureAccessGate.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/VerifiedCaptureAccessGate.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/data/VerifiedCaptureAccessGateTest.kt` (13 tests: sesión activa/bloqueada, permisos, consentimiento, expiración lease Premium), `app/src/test/java/com/kipu/app/feature/categories/domain/CaptureCandidateAuthorizationTest.kt`.
- [x] **T045-2 Ingress seguro de notificaciones y frontera de privacidad perezosa (lazy extras).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureIngress.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/listener/KipuNotificationListenerService.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidateTest.kt` (`ingress does not read extras until supported source and owner pass the gate`, `ingress checks live permission again after the suspended gate`).
- [x] **T045-3 Interfaz de solicitud y aceptación de consentimiento de captura.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureConsentSheet.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/feature/capture/presentation/CaptureConsentSheetTest.kt`.
- [x] **T045-4 Validación de expiración Premium antes de lectura/guardado (HU-45 E4).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureAccessGate.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/VerifiedCaptureAccessGate.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt::manualConversionAfterPremiumExpiryAndReconfirmationReturnExistingMovement` (ejecutado PASS A16 2026-11-05); `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureSignedExpiryAcceptanceTest.kt::signedPremiumExpiresAtBoundaryButManualEntryAndDedupeRemainAvailable` y `expiredSignedGrantBlocksFreshNotificationBeforeAnyEvidenceOrCandidateWrite` (PASS unitarios 2026-11-05).

### HU-46: Extracción y Candidato
- [x] **T046-1 Catálogo versionado de proveedores autorizados y allowlist de paquetes.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureSourceCatalog.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureSourceCatalogTest.kt`.
- [x] **T046-2 Parsers especializados por proveedor (Yape, Plin, BCP, Interbank, BBVA, Scotiabank, Google Wallet).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureParser.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/NotificationCaptureParserTest.kt` (fixtures de importes, monedas, fechas, direcciones, tarjetas y estados rechazados).
- [x] **T046-3 Resolución determinista de cuentas de débito, tarjetas de crédito y billeteras por últimos 4 dígitos.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureInstrumentResolver.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureWalletChannelResolver.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/data/RoomCaptureInstrumentResolverTest.kt`, `app/src/test/java/com/kipu/app/feature/capture/data/RoomCaptureWalletChannelResolverTest.kt`.
- [x] **T046-4 Pipeline de extracción de candidatos y persistencia inicial PENDING en Room v29.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt`, `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt` (`MIGRATION_28_29`), `app/src/main/java/com/kipu/app/feature/capture/data/local/CaptureDaos.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidateTest.kt`, `app/src/androidTest/java/com/kipu/app/core/database/CaptureRoomMigrationTest.kt`.
- [ ] **T046-5 [PENDIENTE ABIERTA] Compatibilidad real de notificaciones en vivo por proveedor externo.**
  - *Motivo*: Los parsers operan con fixtures y pruebas unitarias, pero no se certifica integración viva universal con las aplicaciones bancarias externas en hardware real.

### HU-47: Confianza y Autorregistro
- [x] **T047-1 Motor de scoring determinista y razones explicables (HIGH, MEDIUM, LOW).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureConfidenceScorer.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureConfidenceScorerTest.kt`.
- [x] **T047-2 Caso de uso de autorregistro de candidatos elegibles (HIGH débito/billetera) y revalidación de acceso.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/AutoRegisterCaptureCandidate.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/AutoRegisterCaptureCandidateTest.kt`, `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt`.
- [x] **T047-3 Validación de expiración Premium antes de guardar autorregistro (HU-47 E5).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/AutoRegisterCaptureCandidate.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt::manualConversionAfterPremiumExpiryAndReconfirmationReturnExistingMovement` (ejecutado PASS A16 2026-11-05); `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureSignedExpiryAcceptanceTest.kt::premiumExpiresBetweenCaptureAndAutomaticSaveAndLeavesPendingEvidence` (PASS unitarios 2026-11-05).
- [x] **T047-4 Replay exacto E2E de autorregistro en hardware (HU-47).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/AutoRegisterCaptureCandidate.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureRegistrationReceipts.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt::exactEventReplayAfterAutomaticRegistrationDoesNotCreateAnotherMovement` (ejecutado PASS A16 2026-11-05).

### HU-48: Confirmación de Crédito
- [x] **T048-1 Confirmación financiera al ledger contable para débito/ingreso e idempotencia con clave determinista.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidate.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureRegistrationReceipts.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidateTest.kt`, `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt`.
- [x] **T048-2 Confirmación de compras con tarjeta de crédito en modo directo o cuotas (2 a 36 cuotas).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidate.kt`, `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidateTest.kt` (`credit purchase uses ConfirmCreditPurchase`, `installments outside 2 to 36 are rejected`), `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModelTest.kt`.
- [x] **T048-3 Transaccionalidad atómica de confirmación e invariancia ante descarte o salida sin confirmar.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidate.kt`, `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt` (`confirmationAndAutomaticRegistrationSerializeWithDiscard`, `discardWinningTheTransactionPreventsAnyFinancialWrite`).

### HU-50: Aprendizaje desde Correcciones
- [x] **T050-1 Aprendizaje de alias de comercios y preferencias de categoría desde correcciones de usuario confirmadas.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/LearnFromCaptureCorrection.kt`, `app/src/main/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidate.kt` (`correctedFields`).
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureReviewAndLearningTest.kt` (`failed preference result is not acknowledged and replay retries learning`, `category opt out leaves alias learning enabled`), `app/src/test/java/com/kipu/app/feature/capture/domain/ConfirmCaptureCandidateTest.kt`.
- [x] **T050-2 Opciones independientes de recordar alias y recordar categoría en pantalla de revisión.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewScreen.kt`, `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModelTest.kt` (`review exposes checked learning options and sends independent choices`).
- [x] **T050-3 Aislamiento por propietario y retención de followups pendientes ante reinicios.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureFollowups.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/work/CaptureFollowupWorker.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureReviewAndLearningTest.kt`, `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt` (`followupsSurviveClosingRoomAndEachAcknowledgedEffectStaysDone`).

### HU-51: Bandeja y Evidencia Mínima
- [x] **T051-1 Bandeja reactiva de candidatos pendientes (`CaptureInboxViewModel` y `CaptureInboxScreen`).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureInboxViewModel.kt`, `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureInboxScreen.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureInboxViewModelTest.kt`, `app/src/androidTest/java/com/kipu/app/feature/capture/presentation/CaptureFlowScreenshotTest.kt`.
- [x] **T051-2 Pantalla de revisión detallada, edición y confirmación/descarte (`CaptureReviewViewModel` y `CaptureReviewScreen`).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt`, `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewScreen.kt`.
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModelTest.kt`, `app/src/androidTest/java/com/kipu/app/feature/capture/presentation/CaptureFlowScreenshotTest.kt`.
- [x] **T051-3 Descarte idempotente sin impacto contable con borrado inmediato de snippet de evidencia.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidate.kt` (`DiscardCaptureCandidate`).
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidateTest.kt` (`discard erases evidence without a ledger and is idempotent`), `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureInboxViewModelTest.kt`.
- [x] **T051-4 Conversión a borrador manual en caso de vencimiento de Premium con preservación de clave determinista.**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidate.kt` (`ConvertCandidateToManual`).
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidateTest.kt` (`manual conversion keeps the candidate and reuses its idempotency key`), `app/src/test/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModelTest.kt` (`premium expired offers manual conversion and then confirms as manual draft`).
- [x] **T051-5 Enmascaramiento de datos sensibles y purga periódica de evidencia expirada (7 días).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt` (`maskEvidence`), `app/src/main/java/com/kipu/app/feature/capture/data/work/EvidencePurgeWorker.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/local/CaptureDaos.kt` (`purgeExpiredEvidence`).
  - *Evidencia Tests*: `app/src/test/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidateTest.kt` (`post time is explicitly inferred and evidence is masked and expires after seven days`), `app/src/test/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidateTest.kt` (`expired evidence is purged`).
- [x] **T051-6 Replay exacto E2E de reconfirmación y conversión manual en hardware (HU-51 E3/E4).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidate.kt` (`ConvertCandidateToManual`), `ConfirmCaptureCandidate.kt`.
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt::manualConversionAfterPremiumExpiryAndReconfirmationReturnExistingMovement` (ejecutado PASS A16 2026-11-05); `app/src/test/java/com/kipu/app/feature/capture/domain/ResolveCaptureCandidateTest.kt` (PASS unitarios 2026-11-05).
- [x] **T051-7 Matriz física de purga y privacidad en hardware (HU-51 E5).**
  - *Evidencia Código*: `app/src/main/java/com/kipu/app/feature/capture/data/work/EvidencePurgeWorker.kt`, `app/src/main/java/com/kipu/app/feature/capture/data/local/CaptureDaos.kt` (`purgeExpiredEvidence`).
  - *Evidencia Tests*: `app/src/androidTest/java/com/kipu/app/core/database/CaptureAtomicResolutionTest.kt::resolvingAndDiscardingImmediatelyPurgeEvidenceWhilePendingEvidenceRemains` y `evidencePurgeUsesInclusiveDeadlineAcrossOwnersAndDoesNotAlterCandidatesOrLedger` (ejecutado PASS A16 2026-11-05); `app/src/test/java/com/kipu/app/feature/capture/data/EvidencePurgeWorkerTest.kt` con 3 tests PASS (purga at injected time, reintento ante fallo, propagación de cancelación, PASS unitarios 2026-11-05).

---

## Incremento Sprint 9: Tareas HU-49 (Yape Híbrido, OCR y Deduplicación Simétrica)

- [x] T049-1 Política de correlación y pruebas de referencias, ambos órdenes, ventana, ambigüedad y reembolsos.
- [x] T049-2 Evidencia Room 37 (capture_evidence con PK compuesta `owner_id, source, event_id` e índice no único `owner_id, reference, source` reconciliado con decisión del Owner, fields_edited, migraciones 35→36 y 36→37 con HybridYapeMigrationTest y validación de esquema 37.json).
- [x] T049-3 Integración simétrica notificaciones/OCR, raíz confirmada y ediciones preservadas.
- [x] T049-4 Unión o separación explícita en ambas revisiones.
- [x] T049-5 Tests y builds debug/androidTest; commits locales sin push.

> Nota validación hardware Sprint 9 (2026-11-03): Migración 33→37 exitosa en A16 y S24. OCR sintético local verificado en A16 (Photo Picker, candidato DISCARDED sin alterar ledger/saldos); concesión de URI Android aislada verificada en S24 (ReceiptShareUriGrantTest). Casos que permanecen NO VERIFICADO: flujo completo desde app Yape real, notificación viva en ambos órdenes y 8 muestras privadas ausentes.

> Decisión Owner (2026-11-03 [2026-11-04 UTC]): El Product Owner aprobó formalmente **«Conservar eventos incompatibles para revision»** ([decisiones del Owner](../sprint-9-owner-decisions.md)), reconciliando FR-012 y la entidad `capture_evidence` con el esquema Room v37 implementado (PK compuesta e índice no único, sin fusión automática de incompatibles ni duplicación contable en ledger).

> Decisión Owner (2026-11-05): OCR local de comprobantes Yape PERMITIDO en plan Free con consentimiento explícito y revisión obligatoria (excepción FR-011 EP-AUT), conservando la protección Premium del resto del pipeline de captura automática.
