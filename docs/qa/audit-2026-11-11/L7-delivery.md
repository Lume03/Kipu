# L7 — Billing y Edge Functions

Fecha: 2026-11-12. Rama: `Lume03/audit-fixes`. Resultado local: **APPROVED**.

Implementados BIL1-01–06, SQL4-05, MOV4-05 y UI2-03. No se hicieron commits, push, despliegues ni cambios remotos; no se usaron Spec Kit ni ADB. El único documento modificado/añadido por este lote es esta entrega.

## Archivos por hallazgo

Las rutas Kotlin de producción parten de `app/src/main/java/com/kipu/app/`; las de pruebas, de `app/src/test/java/com/kipu/app/`.

| Hallazgo | Archivos | Comportamiento final |
| --- | --- | --- |
| BIL1-01 | `supabase/functions/verify-purchase/index.ts`, `google-play-api.ts`, `index_test.ts`, `google-play-api_test.ts` en ese mismo directorio | Conserva la identidad devuelta por Play y exige SHA-256 hexadecimal UTF-8 de `com.kipu.app:<UUID autenticado>`, igual que Android, antes de persistir o reconocer. Falta: `PURCHASE_ACCOUNT_ID_MISSING`; desacuerdo: `PURCHASE_ACCOUNT_ID_MISMATCH`, ambos terminales HTTP 400. El control también cubre el verificador compartido con RTDN. Comentario `shortcut:` incluido, sin excepción legacy. |
| BIL1-02 | `supabase/functions/verify-purchase/google-play-api.ts`, `google-play-api_test.ts` | Lifetime usa `purchaseStateContext.purchaseState`, priorizándolo sobre el campo plano conservado como fallback. Estado ausente/desconocido se rechaza; PURCHASED, PENDING y CANCELLED mantienen estados coherentes. Tests del adaptador real con HTTP simulado, sin reemplazarlo por un `ProviderPurchase` ya normalizado. |
| BIL1-03 | `feature/plans/data/billing/BillingRepository.kt`, `KipuApplication.kt`, `feature/plans/data/sync/PlanEntitlementRefreshCoordinator.kt`; pruebas `feature/plans/data/BillingRecoveryTest.kt`, `feature/plans/data/sync/PlanEntitlementRefreshCoordinatorTest.kt` | Un único colector iniciado por Application procesa callbacks en su scope, aunque no exista pantalla. Las pantallas observan resultados sin ejecutar la verificación; el último resultado en memoria se entrega únicamente a su cuenta. Recuperación de compras al disponer de sesión y al volver a primer plano. Una excepción de verificación produce un resultado reintentable sin matar el colector. |
| BIL1-04 | `supabase/functions/plans/index.ts`, `index_test.ts` | Permite hasta diez raíces en CUSTOM_CATEGORIES; la RPC conserva la validación de cinco por tipo. Tests aceptan 6 y 10 IDs distintos y rechazan 11 antes de RPC. |
| BIL1-05 | `supabase/functions/verify-purchase/index.ts`, `index_test.ts`; prueba `feature/plans/data/BillingRecoveryTest.kt` | Todo candidato no pendiente con Premium efectivo persistido puede recibir grant, incluida una suscripción CANCELLED/CANCELED_ACTIVE vigente. La emisión usa el fin agregado persistido, y Android instala el grant restaurado en la caché. |
| BIL1-06 | `supabase/migrations/20261112110000_billing_fixes.sql`, `supabase/tests/database/billing_fixes_test.sql` | Actualiza las definiciones vigentes de `get_effective_plan_for_refresh` y `v_feature_access`: admite PURCHASED/ACTIVE, PURCHASED/IN_GRACE_PERIOD y CANCELLED/CANCELED_ACTIVE con vencimiento futuro para suscripciones. Lifetime conserva PURCHASED/ACTIVE sin vencimiento. Mantiene precedencia Developer y overrides. |
| SQL4-05 | La misma migración y test; `supabase/tests/database/dev_plan_mode_test.sql` | Auditoría global con sujeto NULL, normalización de eventos globales históricos y constraint que sigue exigiendo sujeto para eventos de usuario. `set_test_plans_enabled` deja de insertar el UUID ficticio; permisos de operador conservados. El test nuevo activa y desactiva sin centinela, y se retiró el centinela del test de dev mode. |
| MOV4-05 | `feature/movements/presentation/MovementFiltersSheet.kt` | Observa el catálogo del ViewModel de planes y presenta el `formattedPrice` anual de Play. Excluye productos `dev:`. Sin oferta comercial anual no muestra tarjeta de precio; se eliminaron precio fijo, equivalente mensual fijo y «AHORRA 40%». |
| UI2-03 | `feature/plans/presentation/PlanPurchaseViewModel.kt`; prueba `feature/plans/presentation/PlanPurchaseViewModelTest.kt` | `selectOffer` conserva selección y estado durante LAUNCHING y VERIFYING. El test intenta cambiar oferta y lanzar otra compra en ambos estados; solo hay un lanzamiento. |

## Verificación local

| Comando | Resultado |
| --- | --- |
| `deno test --allow-all supabase/functions/verify-purchase supabase/functions/plans supabase/functions/play-rtdn supabase/functions/refresh-entitlement supabase/functions/reconcile-billing` | Exit 0; **65 passed, 0 failed**. |
| `npx --no-install supabase db reset` | Exit 0; migraciones completas, incluida `20261112110000_billing_fixes.sql`, aplicadas localmente. CLI 2.117.0, sin `--linked` ni URL remota. |
| `npx --no-install supabase test db` | Exit 0; **80 archivos, 1.536 tests, todos satisfactorios; Result: PASS**. |
| `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests com.kipu.app.feature.plans.data.BillingRecoveryTest --tests com.kipu.app.feature.plans.presentation.PlanPurchaseViewModelTest --tests com.kipu.app.feature.plans.data.sync.PlanEntitlementRefreshCoordinatorTest` | Exit 0; **BUILD SUCCESSFUL**. Tres compilaciones solicitadas y **17 unitarios**, sin fallos ni errores: BillingRecoveryTest 11, PlanPurchaseViewModelTest 5, PlanEntitlementRefreshCoordinatorTest 1. |
| `git diff --check` | Sin errores de whitespace. |

La documentación oficial consultada confirma el estado anidado y la identidad externa de ProductPurchaseV2: [Google Play Developer API — purchases.productsv2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2), consultada el 2026-11-12. No se usó una fixture de compra comercial real; los tests simulan respuestas HTTP y el intercambio OAuth con una clave RSA generada localmente.

## Despliegues posteriores necesarios

1. Aplicar la migración `20261112110000_billing_fixes.sql` mediante el proceso de despliegue autorizado.
2. Desplegar **verify-purchase**, **plans** y **play-rtdn**. RTDN incorpora los módulos compartidos modificados de verify-purchase, aunque su archivo de entrada no cambie.
3. Publicar una nueva versión Android para el colector de aplicación, recuperación automática, oferta y bloqueo del selector.

`refresh-entitlement` recibe el cambio mediante su RPC SQL; sus fuentes no cambiaron. `reconcile-billing` tampoco cambió. Ambos pasaron sus tests locales. No se ejecutó ningún despliegue en este lote.

## Riesgos y límites

- Toda compra antigua sin identidad de cuenta queda rechazada, conforme a la decisión 11 y a la instrucción de este lote. Antes de comercializar, verificar en Play Console si existe alguna; si apareciera, requeriría una vinculación por posesión del token con fecha de corte aprobada, no un bypass general.
- Sigue pendiente validar compra, cancelación, restauración, Lifetime y reconocimiento contra un track real de Play con productos/testers configurados. Los tests locales no acreditan Play Console ni estado remoto.
- Los tokens permanecen únicamente en memoria. Si Android mata el proceso, la recuperación consulta Play en la siguiente sesión/entrada en primer plano; no se introduce almacenamiento persistente de tokens ni procesamiento con el proceso muerto.
- El precio se oculta cuando no hay catálogo comercial anual, incluido modo de desarrollo. No se inventa precio ni descuento alternativo.
- No se ejecutaron pruebas instrumentadas ni renderizado visual en dispositivo, por el alcance sin ADB. Sí compilaron los instrumentados; la revisión de la oferta fue de código.
- Gradle conserva avisos de opt-in de coroutines en tests y de OpenJDK; no impidieron el build ni las pruebas.

Evidencia adicional de ejecución en `%TEMP%/kipu-l7-reset.log`, `%TEMP%/kipu-l7-pgtap.log` y `%TEMP%/kipu-l7-gradle-final.log`; resultados XML de los 17 unitarios en `app/build/test-results/testDebugUnitTest/`.
