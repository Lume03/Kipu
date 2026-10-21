# Revisión local Issue #64 — explicación de meta archivada

- **PR:** No aplica; revisión local solicitada por Kanban para [Issue #64](https://github.com/Lume03/Kipu/issues/64)
- **Repositorio:** Lume03/Kipu
- **Autor / asignado:** android-ui
- **Fecha de revisión:** 2026-10-20
- **Rama:** `feat/issue-64-archived-goal-guidance` → `main` (rama PR limpia basada en `6441851`)
- **Commit revisado:** `4e39ff2` (`feat(goals): explain archived goal actions`)

## Dictamen breve

**Resultado actualizado (2026-10-20):** Retest lab focalizado PASS para ARCHIVED y ACTIVE (1/1 cada uno, ejecuciones separadas), y verificación manual acotada PASS en el candidato integrado exacto. BUG-064-002, test-only, quedó resuelto. Esta rama pre-PR contiene solo el cambio focalizado sobre `main`; no es aprobación de release. TalkBack y persistencia Room/ledger siguen pendientes.

La ejecución histórica de ARCHIVED pasó (1/1) y ACTIVE falló inicialmente por igualdad exacta frente a espacios iniciales. Después de cambiar solo el matcher del test a substring, el retest independiente de ACTIVE pasó (1/1). Se conserva la ejecución inicial fallida y su XML sin reescribir; ver la sección de retest para nueva evidencia. No se detectó defecto productivo por ese falso negativo. La verificación manual del candidato integrado quedó acotadamente PASS. La rama de épica `006-ep-met-metas-reservas` permanece divergida; esta rama nueva se creó desde `main` con solo los dos archivos del cambio #64.

Este dictamen revisa el diff aislado sobre el baseline indicado; no es aprobación de release ni de integración a `main`.

## Issues y alcance

| Issue | Relación | Criterios de aceptación consultados |
| --- | --- | --- |
| [#64](https://github.com/Lume03/Kipu/issues/64) | Confirmado por especificación asignada | Estado ARCHIVED textual; explicación y alternativa segura; interacciones informativas sin mutación financiera; regresión focalizada. |

- **Issues no encontrados o relación incierta:** Ninguno para el alcance recibido.
- **Fuente de autoridad usada:** Especificación `specs/006-ep-met-metas-reservas/spec.md` (HU-32, escenarios 1–6), contrato `specs/006-ep-met-metas-reservas/contracts/goal-command-contract.md` y requisitos de Kanban. La regla de aportes solo para metas ACTIVE se consigna en el handoff/spec; la revisión no presupone reactivación.

## Cumplimiento de criterios

| Issue / criterio | Estado | Evidencia y observaciones |
| --- | --- | --- |
| Mostrar claramente el estado archivado | Cumple estáticamente | `GoalDetailScreen.kt:101-102` renderiza la etiqueta de estado y condicional ARCHIVED. Test añadido en `GoalLifecycleFlowTest.kt:191`. |
| Explicar por qué no están disponibles aportes y ofrecer alternativa segura | Cumple estáticamente | `GoalDetailScreen.kt:102-111` comunica que solo metas activas aceptan aportes y ofrece volver a la lista/escoger una meta activa. La alternativa no implica reactivación. |
| Interacción informativa sin crear reserva/movimiento | Cumple en runtime lab | El CTA nuevo invoca únicamente `onNavigateBack`; el escenario ARCHIVED pasó y verificó `backCalls=1` y handlers financieros en cero (`GoalLifecycleFlowTest.kt:174-207`). |
| Regresión focalizada: ARCHIVED | Cumple en runtime lab | `archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands`: 1 passed; `backCalls=1` y callbacks financieros en cero. XML sanitizado enlazado abajo. |
| Regresión focalizada: ACTIVE conserva CTA | Cumple en retest runtime lab | La ejecución inicial falló por comparación exacta del matcher; tras corregir solo el matcher a substring, `activeGoalStillOffersContributionAction` pasó 1/1. Evidencia nueva y serial redactado en la sección de retest. |

## Cambios realizados

- `GoalDetailScreen.kt`: texto informativo y botón “Volver a mis metas” para ARCHIVED.
- `GoalLifecycleFlowTest.kt`: cobertura propuesta para estado, navegación, ausencia de callbacks financieros y CTA ACTIVE.

## Hallazgos y mejoras

### Bloqueantes / importantes

#### BUG-064-002 — El test ACTIVE falló inicialmente por comparación exacta del texto del CTA — RESUELTO EN RETEST

- **Severidad:** MEDIUM (test-only; no hay evidencia de fallo funcional del CTA)
- **Estado:** Confirmado en la corrida inicial; resuelto en retest lab
- **Ubicación:** `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalLifecycleFlowTest.kt:221`; texto en `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:146`.
- **Descripción:** El caso ACTIVE espera `Aportar o reservar`, pero el `Text` incluye dos espacios iniciales. El matcher exacto de Compose no considera displayed el nodo.
- **Resultado esperado:** El caso de regresión ACTIVE pasa y confirma que el CTA se conserva.
- **Resultado observado:** `AssertionError: ... contains 'Aportar o reservar' ... is not displayed!`; 1 test, 1 failure.
- **Reproducción:** Ejecutar el comando focalizado del escenario ACTIVE registrado en la sección de pruebas.
- **Impacto histórico:** Antes del ajuste, la regresión de conservación del CTA no podía usarse como evidencia verde; el retest posterior ya quedó verde.
- **Recomendación:** Ajustar el matcher para tolerar/seleccionar el texto de forma robusta (p. ej. substring o un tag de test acordado) sin cambiar el comportamiento productivo; volver a solicitar QA.
- **Verificación:** Retest ejecutado el 2026-10-20 con el mismo filtro en `:app:connectedLabAndroidTest`; 1/1 PASS. Se conserva como cronología, no como defecto abierto.

Hallazgo previo BUG-064-001 sigue corregido (`substring = true` en los matchers ARCHIVED, líneas 193-194). No se confirmó bug productivo del CTA ACTIVE.

### Menores / sugerencias

- Ninguna adicional identificada.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
| --- | --- | --- |
| `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` | Pasa | `BUILD SUCCESSFUL`; 33 tareas, todas up-to-date. Confirma compilación, no comportamiento runtime. |
| `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` sin `ANDROID_HOME` | Falla por entorno | Gradle informa “SDK location not found”; el reintento con `ANDROID_HOME` explícito pasó. |
| `git diff --check` | Pasa | Exit code 0; sin errores de whitespace en el diff revisado. |
| Revisión de diff y handlers | Inspección | Diff restringido a `GoalDetailScreen.kt` y `GoalLifecycleFlowTest.kt`; el callback de retorno no invoca callbacks financieros. |
| `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest#archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands,com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest#activeGoalStillOffersContributionAction' :app:connectedLabAndroidTest` | Pasa, 1 test | Se solicitó ambos filtros en una invocación; el XML demuestra que el runner ejecutó solo ARCHIVED (1 test, 0 failures/errors/skips), no el ACTIVE. SM-A165M/API 36. Copia sanitizada: [QA-64-archived-lab.xml](evidence/QA-64-archived-lab.xml), SHA-256 `f693acdd691bb1c3a8e41a1822abfb65349f61ec50b0459fd48c6566f9bc5711`. |
| `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest#activeGoalStillOffersContributionAction' :app:connectedLabAndroidTest` | Falla | 1 test, 1 failure, 0 errors/skips; mismatch de texto en línea 221. Copia XML sanitizada: [QA-64-active-lab.xml](evidence/QA-64-active-lab.xml), SHA-256 `f4cf0f6432bab8780113030f47aa53911540eed6dae62bb561e5e562f8fc38c4`. No se preserva logcat bruto por ruido/privacidad; stack/error exacto queda en XML. |
| Construcción lab previa a instalación | Pasa | `:app:assembleLab :app:assembleLabAndroidTest`, `BUILD SUCCESSFUL`; AAPT confirmó app `com.kipu.app.lab` v0.1.0/versionCode 1 y runner `com.kipu.app.lab.test`. APK SHA-256 app `67164af2ae3cff8c554dd9f32ad8297906cf7652134fc048ebd305285015cb09`; test APK `f7d836642b81454a764c0699d60f2058d31dfe2d388066c40bcefece7166c0e4`. |
| Estado de instalación antes/después | Verificado | Lab `com.kipu.app.lab` no estaba instalado antes ni quedó instalado después; Gradle ejecutó y limpió el runner lab. App personal `com.kipu.app` permaneció en versionCode 1 y SHA-256 `e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864`; no se actualizó, limpió ni abrió. |
| SHA-256 de source/tests | Verificado | Screen: `ca01f90c6f897ef77829bd632c32feeac9e39aa72e4ea05ad929d281a0e44012`; test: `fb9d08fc7f14e17ba8cb6073ec309192980d288ed82f6e3a40604e8e191f4324`. |

## Revisión visual (solo si aplica)

- **¿Hay cambios visuales?** Sí
- **Pantallas y escenarios revisados:** Pruebas Compose instrumentadas lab ARCHIVED/ACTIVE y recorrido manual read-only de ambos estados en el candidato integrado.
- **Evidencia:** XML sanitizados enlazados arriba y crops manuales ARCHIVED/ACTIVE en el addendum.
- **Privacidad:** Los crops conservan fixtures QA y excluyen otras cuentas/listados; sin seriales. La app se actualizó in-place solo bajo el GO acotado registrado.

## Pendientes / límites de la revisión

- BUG-064-002 queda resuelto en retest lab 1/1 PASS; las ejecuciones históricas permanecen registradas.
- El recorrido manual del candidato integrado ya se completó: APK `com.kipu.app` v0.1.0/code 1 con SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce`; crops en el addendum. TalkBack y persistencia Room/ledger no se verificaron.
- La rama fuente `006-ep-met-metas-reservas` sigue divergida (HEAD histórico `872ecdb857a1dae6a361cb30676f3d46cbbec085`). El cambio #64 se aisló en esta rama nueva basada en `main`; la review no aprueba release y mantiene pendientes TalkBack/persistencia.
- No se consultó/publicó nada en GitHub; no se hizo aprobación formal ni se ejecutaron acciones de PR.

## Retest focalizado posterior — ACTIVE (2026-10-20)

- **Cambio bajo prueba:** solo se ajustó el matcher AndroidTest a `substring = true`; no se modificó `GoalDetailScreen.kt`. SHA-256 test actual `cfc4febd4aaa2911661de15b8ba8422f5d329f9486e250843b93dbbd8d9a3436`; SHA-256 screen `ca01f90c6f897ef77829bd632c32feeac9e39aa72e4ea05ad929d281a0e44012`.
- **Preflight:** ADB explícito `C:/Users/Alume/AppData/Local/Android/Sdk/platform-tools/adb.exe`; dispositivo conectado SM-A165M, Android 16/API 36 (serial omitido). App lab `com.kipu.app.lab`, v0.1.0/versionCode 1, APK SHA-256 `67164af2ae3cff8c554dd9f32ad8297906cf7652134fc048ebd305285015cb09`; runner `com.kipu.app.lab.test`, APK SHA-256 `b609d8f98c06ce8deb0988112933e548f3cc88e8972f0898415cf5d536a8a9e1`. BuildConfig lab: remote commands disabled y Supabase URL vacía. App y runner lab no quedaron instalados tras el test. El paquete personal continuó en v0.1.0/versionCode 1; no se abrió ni actualizó.
- **Build/preflight:** `:app:assembleLab :app:assembleLabAndroidTest` no existe; Gradle informó tarea inexistente. La alternativa comprobada `:app:assembleLab :app:assembleDebugAndroidTest` compiló exitosamente. Se inspeccionaron manifiestos del artefacto lab real y runner lab antes de instrumentar; paquetes `com.kipu.app.lab` y `com.kipu.app.lab.test`.
- **Comando:** `ANDROID_HOME='C:/Users/Alume/AppData/Local/Android/Sdk' ./gradlew -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.goals.presentation.GoalLifecycleFlowTest#activeGoalStillOffersContributionAction' :app:connectedLabAndroidTest` — exit 0, `BUILD SUCCESSFUL`; 1 test, 0 failures, 0 errors, 0 skipped.
- **Resultado:** `activeGoalStillOffersContributionAction` PASS. XML sanitizada [QA-64-active-lab-retest-20261020.xml](evidence/QA-64-active-lab-retest-20261020.xml), SHA-256 `a959713f8cff51a10d8249fb42a5aa3d06ff339c182a1470310089f5b43d76d9`; se omitió el serial del original.
- **Cobertura #64 lab en este retest:** ARCHIVED 1/1 PASS (ejecución histórica preservada) y ACTIVE 1/1 PASS (retest nuevo, separado). En ese punto seguían pendientes manual/TalkBack; el addendum posterior registra la verificación manual del candidato integrado, mientras TalkBack continúa pendiente.

## Acciones en GitHub

- **Aprobación formal:** No enviada
- **Comentarios publicados:** No
- **Solicitudes de cambio:** BUG-064-002 quedó resuelto localmente en retest; no se publicó en GitHub.

## Addendum — verificación manual del candidato integrado (2026-10-20)

- **Candidato / instalación:** tras `GO_BOUNDED` de `t_7f5569ab`, se instaló in-place el APK integrado SHA-256 `38fe1061668aeae18ad9ccbff520bac9eac2388d0d30ceb3043613ae3e97abce` (`com.kipu.app` 0.1.0/code 1) con ADB explícito. El readback posterior confirmó el mismo SHA-256 en `base.apk` y el signer esperado. No se reinstaló desde cero, limpió datos ni cambió sesión/red. La app abrió y mantuvo el dashboard/sesión existentes.
- **Caso ARCHIVED:** se abrió en modo read-only el fixture QA preexistente `QA_AUDIT_20261020_META`. La UI mostró estado `Archivada`, explicación de que solo metas activas aceptan aportes y CTA `Volver a mis metas`. El CTA volvió a la lista. No se ejecutó ni se intentó una operación financiera en el estado archived.
- **Caso ACTIVE:** se abrió en modo read-only `QA_AUDIT_20261020_GOAL`; estado `Activa` y CTA `Aportar o reservar` eran visibles. El CTA abrió el formulario para seleccionar una cuenta de respaldo; se volvió atrás sin confirmar. Al regresar, el estado seguía activo y el CTA presente. No se modificó el fixture ni se creó movimiento.
- **Capturas privadas/sanitizadas:** [detalle ARCHIVED y explicación/CTA](evidence/manual-qa64-archived-detail-crop.png), SHA-256 `e94bcb44125e9355b514abdf8720336b7aabdcde3a3dfcbb06640840bc6f40de`; [detalle ACTIVE y CTA](evidence/manual-qa64-active-cta-crop.png), SHA-256 `0e5bbc3fce4839fb3ad3f9914f6c263733ec0cf2ef845758578d6832f8cefcec`. Los crops conservan el fixture QA y contenido relevante; excluyen las secciones inferiores con otros instrumentos/listados. Solo muestran valores del fixture QA; originales no se conservaron en el review.
- **Resultado manual:** PASS acotado para mensaje/CTA ARCHIVED, retorno a lista, preservación del CTA ACTIVE y apertura/cancelación del formulario en el APK hash identificado. Los retests lab previos permanecen: ARCHIVED 1/1 y ACTIVE 1/1 PASS, independientes de esta sesión.
- **Límites:** no se verificó persistencia Room/historial ni ledger/ningún callback financiero después de un aporte real; tampoco se probó TalkBack. La UI archivada comunicó preservación de historial, pero no se inspeccionaron filas/historial real. Esta ejecución sobre candidato integrado no aprueba ni integra automáticamente la rama divergente `006-ep-met-metas-reservas`, ni constituye aprobación de issue/release. Los fixtures QA existentes no se editaron.

## Revalidación secuencial del worktree #64 (2026-10-20)

- **Worktree/estado:** `006-ep-met-metas-reservas`, HEAD `872ecdb857a1dae6a361cb30676f3d46cbbec085`, con los cambios locales existentes. No se hizo commit/rebase/cherry-pick.
- **Compilación lab:** `$env:ANDROID_HOME='C:\Users\Alume\AppData\Local\Android\Sdk'; .\gradlew.bat --no-daemon -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest :app:compileLabAndroidTestKotlin` — `BUILD SUCCESSFUL`, 82 tareas `UP-TO-DATE`.
- **ARCHIVED, teléfono conectado:** ejecución secuencial de `GoalLifecycleFlowTest#archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands` mediante `:app:connectedLabAndroidTest` — `BUILD SUCCESSFUL`, 1 test focalizado. La prueba usa estado Compose sintético y no accedió a la cuenta de `com.kipu.app`.
- **ACTIVE, teléfono conectado:** después del ARCHIVED se ejecutó `GoalLifecycleFlowTest#activeGoalStillOffersContributionAction` en una llamada separada — `BUILD SUCCESSFUL`, 1 test focalizado. No se usó la sesión ni se guardaron datos de la app normal.
- **Resultado:** ambos tests de comportamiento del worktree vuelven a pasar de forma secuencial en la variante lab. La compilación de código Android/androidTest también pasa. Esto no quita la divergencia de la rama ni amplía el alcance de la verificación manual del candidato.

## Addendum — revalidación en candidato integrado (2026-10-20)

- En el worktree `kipu-validation-candidate` se compiló la variante `lab` y `labAndroidTest`; `:app:testLabUnitTest` focalizado en #69 terminó `BUILD SUCCESSFUL`, 2 tests sin fallos.
- En el teléfono, en secuencia y con tests Compose sintéticos, pasaron `AccountDetailScreenTest` (16/16), ARCHIVED #64 (1/1), ACTIVE #64 (1/1) y reemplazo #74 (1/1). La app lab (`com.kipu.app.lab`) quedó desinstalada; el APK normal `com.kipu.app` siguió instalado con el mismo hash de candidato.
- No se reconstruyó el APK debug con configuración pública ni se modificó el fixture/cuenta; la prueba integrada es una validación adicional de los archivos cotejados, no integración de la rama `006` ni aprobación de release.

## Revalidación de la rama PR limpia — #64 (2026-10-20)

- **Rama/commit:** `feat/issue-64-archived-goal-guidance`, commit `4e39ff2`, basada directamente en `origin/main` (`6441851`). Los SHA-256 de `GoalDetailScreen.kt` y `GoalLifecycleFlowTest.kt` coinciden con el candidato integrado revisado.
- **Build:** `ANDROID_HOME=C:/Users/Alume/AppData/Local/Android/Sdk ./gradlew.bat --no-daemon -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest :app:compileLabAndroidTestKotlin` — `BUILD SUCCESSFUL`, 82 tareas ejecutadas.
- **ARCHIVED:** test focalizado `archivedGoalExplainsUnavailableActionsAndOffersSafeReturnWithoutFinancialCommands`, `:app:connectedLabAndroidTest` — `BUILD SUCCESSFUL`, 1/1.
- **ACTIVE:** test focalizado `activeGoalStillOffersContributionAction`, ejecutado después en una llamada separada al teléfono — `BUILD SUCCESSFUL`, 1/1.
- Ambas pruebas usan Compose sintético/variante lab; no tocan la cuenta ni la DB de `com.kipu.app`. No se ejecutó la suite global ni se hizo una operación financiera.
