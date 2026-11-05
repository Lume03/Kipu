# Plan de Implementación: EP-AUT — Captura Automática e Import Inbox

**Feature Branch**: `011-ep-aut-captura-inbox`  
**Autoridad**: `spec.md`, verification-report Sprint 8 (2026-10-29), decisiones del Owner del 2026-10-28 (S8), 2026-11-03 [2026-11-04 UTC] (S9 D1/D2/D3 y Room v37) y 2026-11-05.  
**Estado**: Arquitectura Room-only aprobada y validada en Sprint 8 y Sprint 9; esquema consolidado en Room v37.

---

## 1. Arquitectura General y Principios de Diseño (Room-Only)

1. **Persistencia exclusivamente local**: Los candidatos, señales, eventos fuente y evidencias de captura residen únicamente en SQLite local vía Room (`KipuDatabase`). No se crean tablas en Supabase, no se encolan registros en `outbox`, ni se sincroniza evidencia o contenido sensible a la nube.
2. **Aislamiento multiusuario**: Todas las entidades y consultas Room incluyen `owner_id` (o `user_id`) como primer componente de clave primaria o índice, garantizando aislamiento estricto por usuario en el dispositivo. Un cambio de sesión activa o bloqueo de pantalla invalida de inmediato cualquier lectura o mutación en curso.
3. **Privacidad estricta por diseño (Constitución III / FR-009)**: Ningún contenido sensible (textos íntegros de notificaciones, números de tarjeta PAN, nombres de comercios o montos) se escribe en logs de sistema (`Logcat`). El acceso a `extras` de notificaciones Android se realiza de forma perezosa (*lazy*) únicamente tras validar el catálogo de paquetes y el gate de autorización.

---

## 2. Incremento Sprint 8: Pipeline de Captura, Bandeja y Confirmación Contable

### 2.1 Esquema Room v29 (`MIGRATION_28_29`) y Entidades Base
- **Migración 28→29**: Incorpora las tablas base sin alterar el historial contable existente (`accounts`, `transactions`, `ledger_entries`):
  - `capture_source_events`: PK compuesta `(owner_id, event_fingerprint)`, registra paquetes recibidos y su resultado (`CANDIDATE_CREATED`, `DUPLICATE`, `IGNORED_UNPARSEABLE`, `IGNORED_UNSUPPORTED`).
  - `capture_candidates`: PK `id`, almacena el candidato prellenado con estados `PENDING`, `CONFIRMED`, `AUTO_REGISTERED`, `DISCARDED`. Índices en `(owner_id, status, created_at)` y único en `(owner_id, source_event_fingerprint)`. Contiene campos de confianza, razones, modo de crédito, cuotas, snippet enmascarado y fecha de expiración de evidencia.
  - `recurrence_expected_payments`: PK `id`, desacopla ocurrencias de recurrencias (HU-34) vinculables a candidatos sin alterar saldos ni presupuestos.
- **DAOs y Repositorios locales**: `CaptureSourceEventDao`, `CaptureCandidateDao`, implementados en `RoomCaptureCandidateStore` y `RoomCaptureSourceEventStore`. Los eventos de notificación para revisión (`CAPTURE_REVIEW`) se gestionan en `OfflineFirstNotificationsRepository` marcados como locales para no entrar a la outbox remota.

### 2.2 Gate de Acceso Verificado y Entitlement Premium (HU-45)
- **Componentes**: `CaptureAccessGate`, `VerifiedCaptureAccessGate`.
- **Evaluación en 4 barreras deterministas (nunca un flag de UI)**:
  1. Sesión activa y desbloqueada (`SessionCoordinator.localAccess` en `LocalAccess.Available`).
  2. Permiso del servicio Android Notification Listener (`NotificationListenerService` autorizado vía `PermissionSourceGateway`).
  3. Consentimiento de cuenta explícito y vigente (`OTHER_APP_NOTIFICATION_CONTENT=GRANTED` y capability no denegada vía `PermissionConsentDao`).
  4. Arrendamiento / Entitlement Premium vigente verificado (`EffectiveEntitlementEvaluator` con caché `FeatureAccessCacheDao`).
- **Resiliencia ante condiciones de carrera**: Método sincrónico `liveDenial(ownerId)` ejecutado en la frontera perezosa de lectura de `extras` y revalidación de acceso suspendido tras resolver lookups asíncronos.

### 2.3 Pipeline de Ingress y Extracción de Candidatos (HU-46)
- **Ingress y Servicio Listener**: `KipuNotificationListenerService` intercepta `StatusBarNotification`. Descarta flags de resumen o eventos en curso (`FLAG_GROUP_SUMMARY`, `FLAG_ONGOING_EVENT`). Invoca a `NotificationCaptureIngress` que verifica paquete en catálogo y evalúa el gate antes de invocar la lambda extractora de `extras`. No efectúa lectura histórica al reconectar (`onListenerConnected`).
- **Catálogo versionado (`CaptureSourceCatalog`)**: Admite 6 proveedores autorizados:
  - Yape (`com.bcp.innovacxion.yapeapp`).
  - Plin dentro de aplicaciones bancarias (`com.bcp.bank.bcp`, `pe.com.interbank.mobilebanking`, `com.bbva.nxt_peru`, `pe.com.scotiabank.blpm.android.client`).
  - Bancos: BCP, Interbank, BBVA, Scotiabank.
  - Google Wallet (`com.google.android.apps.walletnfcrel`).
  - Modo debug: `com.android.shell` solo en builds de depuración para simular señales mediante título.
- **Parsers y Extracción (`NotificationCaptureParsers`, `ExtractCaptureCandidate`)**:
  - Extrae dirección (`EXPENSE`, `INCOME`), monto en unidades menores (`amountMinor`), moneda ISO (`PEN`, `USD`), fecha/hora y últimos 4 dígitos de tarjeta.
  - Inferencia de fecha: Si la notificación no incluye fecha explícita fiable, se usa la marca de tiempo de la señal y se marca `occurredAtInferred = true` con razón `DATE_INFERRED`.
  - Huella criptográfica idempotente: SHA-256 length-prefixed sobre paquete, clave de notificación, timestamp, título normalizado y cuerpo.
  - Resolución de instrumentos (`RoomCaptureInstrumentResolver`): Identifica cuentas de débito o tarjetas de crédito registradas por últimos 4 dígitos. Si coinciden 2 o más instrumentos, marca `instrumentAmbiguous = true` con `INSTRUMENT_AMBIGUOUS`.
  - Asociación con catálogo de comercios y alias (`EvaluateMerchantAlias`, `MerchantCategoryPreferenceRules`): Prefiltra comercio y categoría preferida si existe regla previa del propietario.
  - Persistencia: Crea el registro en `capture_candidates` en estado `PENDING`. Nunca inserta directamente en el ledger contable.

### 2.4 Scoring de Confianza y Autorregistro Seguro (HU-47)
- **Scorer Determinista (`CaptureConfidenceScorer`)**:
  - `CaptureConfidenceLevel`:
    - **LOW**: Falta monto, moneda o dirección, o concurren 2 o más elementos dudosos (instrumento no resuelto/ambiguo, fecha inferida, comercio ambiguo, posible duplicado).
    - **MEDIUM**: Exactamente un elemento dudoso no crítico.
    - **HIGH**: Completamente resuelto, sin ambigüedades ni carencias críticas.
  - Compras con tarjeta de crédito: Aunque alcancen puntuación alta, siempre reciben la razón `CREDIT_CARD_REQUIRES_REVIEW` (RN-17) y nunca se autorregistran.
- **Cadena de Post-Procesamiento (`ScoreAndAutoRegisterPostProcessor`)**:
  - Ejecuta en orden 0 tras persistir el candidato: calcula scoring definitivo y evalúa elegibilidad de autorregistro.
  - `AutoRegisterCaptureCandidate`:
    - Solo autorregistra si el candidato es `HIGH`, gasto (`EXPENSE`), instrumento único de tipo cuenta de débito o billetera (`DEBIT_ACCOUNT` o `WALLET`), con fecha no inferida y cuenta/categoría resueltas sin sospecha de duplicado.
    - Revalida el gate de acceso antes de escribir al ledger (`accessDenied(ownerId, AUTOMATIC)`). Si el plan Premium venció, marca `AUTO_REGISTER_DENIED` y lo conserva en `PENDING` para la bandeja.
    - Si el ledger emite advertencia de similitud (`SimilarTransactionWarning`), marca `POSSIBLE_DUPLICATE` y lo conserva en `PENDING`.

### 2.5 Confirmación Financiera e Idempotencia Ledger (HU-48)
- **Única compuerta al ledger (`ConfirmCaptureCandidate`)**:
  - Movimientos de débito, efectivo, billetera e ingresos: Se canalizan a través de `RegisterTransaction` reutilizando las validaciones contables y el cálculo de saldo proyectado (`BalanceProjectionStore`).
  - Compras con tarjeta de crédito: Se canalizan a través de `ConfirmCreditPurchase`, admitiendo pago directo (1 cuota) o en cuotas (rango 2..36). Si el usuario cambia el instrumento a débito, las cuotas se descartan automáticamente (`normalized()`).
- **Clave de resolución determinista e idempotencia**: `CaptureResolutionKey.of(ownerId, candidateId)`. Protege contra doble confirmación o concurrencia. Si ya existe un movimiento registrado con esa clave (`CaptureRegistrationReceipts`), devuelve el resultado existente (`alreadyResolved = true`) sin duplicar en ledger.
- **Transaccionalidad atómica (`DatabaseTransactionRunner`)**: La inserción contable y la actualización del candidato a `CONFIRMED` o `AUTO_REGISTERED` se ejecutan en una transacción atómica. Una falla en la resolución revierte la transacción contable.

### 2.6 Aprendizaje desde Correcciones de Usuario (HU-50)
- **Listener posterior a confirmación (`LearnFromCaptureCorrection`)**:
  - Escucha eventos con flag `CaptureFollowup.LEARN`.
  - Solo procesa candidatos en estado `CONFIRMED` resueltos por el propietario de sesión actual.
  - Comprueba Premium verificado antes de persistir reglas de aprendizaje.
  - Si el usuario corrigió el comercio (`userCorrectedFields` contiene `MERCHANT`) y marcó recordar alias (`rememberMerchantAlias`), guarda el alias mediante `SaveMerchantAliasRule`.
  - Si corrigió la categoría (`userCorrectedFields` contiene `CATEGORY`) y marcó recordar categoría (`rememberMerchantCategory`), guarda la preferencia mediante `SetMerchantCategoryPreference`.
  - No altera el catálogo general de comercios ni reclasifica transacciones históricas previas.

### 2.7 Bandeja de Entrada, Revisión, Descarte, Conversión Manual y Purga (HU-51)
- **Bandeja de Entrada (`CaptureInboxViewModel`, `CaptureInboxScreen`)**: Muestra lista reactiva de candidatos `PENDING` del propietario actual, indicador de automatización derivado del gate, y acciones de descarte o apertura de revisión.
- **Pantalla de Revisión (`CaptureReviewViewModel`, `CaptureReviewScreen`)**: Permite editar monto, fecha, comercio, categoría e instrumento; configurar cuotas si es crédito; y confirmar o descartar. Maneja resolución explícita ante advertencias de duplicidad (`confirmDespiteSimilar`).
- **Descarte seguro (`DiscardCaptureCandidate`)**: Marca el estado como `DISCARDED`, borra de inmediato el snippet de evidencia, no produce ningún efecto en el ledger y opera de forma idempotente.
- **Conversión a Borrador Manual (`ConvertCandidateToManual`)**: Ante la expiración de la suscripción Premium, el candidato pendiente no se destruye; se ofrece su conversión a borrador manual (`CaptureManualDraft`) reutilizando los datos previamente extraídos y la misma clave determinista de resolución para registrarlo en modo `MANUAL_DRAFT` sin requerir suscripción activa.
- **Purga de Evidencia Temporal (`EvidencePurgeWorker`, `WorkManagerEvidencePurgeScheduler`)**:
  - Snippet enmascarado (máximo 280 caracteres, PAN anonimizado `••••`).
  - Retención máxima de 7 días (`EVIDENCE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000`).
  - Borrado automático de snippet y expiración al resolver o descartar.
  - Worker periódico cada 6 horas (`capture-evidence-purge-periodic`) ejecuta `purgeExpiredEvidence(now)` en SQLite sin requerir sesión activa ni red.

---

## 3. Incremento Sprint 9: HU-49 — Yape Híbrido, OCR y Deduplicación Simétrica

Autoridad: spec.md, D1/D2/D3 del Owner, 2026-11-03 [2026-11-04 UTC] ([decisiones del Owner](../sprint-9-owner-decisions.md)); checkout main por mandato del coordinador.

Crear evidencia Room local por owner/fuente/identidad bajo Room v37 con clave primaria compuesta `(owner_id, source, event_id)`, índice no único `(owner_id, reference, source)` (reconciliado con decisión del Owner: conservar eventos incompatibles para revisión sin fusión automática ni duplicación contable) e índice por raíz `(owner_id, root)`, nunca importe. La raíz apunta al candidato original capture/receipt; la confirmación financiera sigue en los casos existentes. Serializar evidencia e inserción en una misma transacción Room. La segunda fuente conserva la primera extracción y completa campos vacíos sin sobrescribir ediciones. Las raíces confirmadas siguen siendo destinos de evidencia.

La similitud sin referencia solo propone unión explícita; revalidar al aceptar. Mantener separado persiste la elección. Descartar el borrador redundante nunca escribe ledger. Mostrar controles en ambas revisiones conservando el contenido Compose sin estado. Mantener OCR Free con consentimiento y gates Premium de captura.

Room v37 agrega y consolida capture_evidence y fields_edited en receipt_candidates (migración histórica 35→36 y migración 36→37 con HybridYapeMigrationTest y esquema 37.json). Pruebas Room locales con Robolectric verifican correlación, aislamiento, rollback e índices. Validación en dispositivos reales comprobó migración 33→37 en hardware (A16 y S24), OCR sintético local en A16 (importe 46.70 prellenado, candidato DISCARDED) y concesión de URI Android en S24 (ReceiptShareUriGrantTest); compartir desde app Yape real, notificación viva de Yape y ocho muestras privadas se mantienen como NO VERIFICADO.
