# Verificación — OCR Yape compartido, entrega V2

**Fecha:** 2026-10-21  
**Veredicto:** APPROVED para el alcance adelantado Share/OCR/revisión/confirmación; no cierra toda HU-49.

## Implementación

- `ACTION_SEND image/*`, validación `content://`, reentrada y espera de sesión/desbloqueo.
- Consentimiento explícito y autorización Premium mediante la política y lease offline existentes antes de abrir la imagen. Free conserva la alternativa manual; no se añadió un bypass.
- ML Kit latino empaquetado: OCR local desde la primera ejecución, límites de bytes/dimensiones, downsampling y EXIF. Reagrupación geométrica de filas para relacionar etiquetas y valores de columnas diferentes.
- Extracción de centavos exactos, fecha/hora de Lima, contraparte y referencia, conservando ceros iniciales. Distingue `Yapeaste` y `Te Yapearon`; no usa el código de seguridad como referencia ni inventa fechas.
- Candidatos Room por propietario, pendientes/confirmados/descartados; revisión editable y acceso desde Ajustes → Comprobantes Yape. Migración 24 → 25 sin borrar historial.
- Deduplicación por imagen y referencia, incluidas referencias corregidas. Un importe igual no establece identidad. Los reintentos conservan la identidad del candidato.
- Reutilización de `QuickMovementBottomSheet`, `RegisterTransaction`, el repositorio offline-first, ledger, proyección y outbox existentes. Se mantienen la afiliación Yape/cuenta y las validaciones financieras; no hay una ruta paralela de saldos.
- No se conservan imágenes ni texto OCR en producción. Los originales de `docs/referencias/OCR-YAPE/` quedan locales/excluidos de Git; los tests versionados usan ejemplos sintéticos o anonimizados. Solo los datos del movimiento confirmado siguen la sincronización financiera existente.

## Modelo ECA

| Etapa | Evidencia |
|---|---|
| Evento | Imagen compartida por Android |
| Condición | URI/imagen válida, consentimiento, sesión, Premium/lease, campos y deduplicación |
| Decisión | Revisar/corregir, descartar, continuar manualmente o confirmar una similitud legítima |
| Acción automática | OCR, normalización y candidato; al confirmar, registro financiero atómico existente |
| Control y retroalimentación | Errores y advertencias visibles, candidato durable, resultado local/sincronización y saldo actualizado |

## Pruebas ejecutadas

Validación incremental focalizada: se conservaron los escenarios verdes y se repitieron solo los afectados por correcciones. No se ejecutó la suite legacy global.

- **69 escenarios JVM únicos en verde:** parser (31), coordinación/restauración de sesión (4), Share Intent (3), editor financiero (26), consentimiento/acceso (2), identidad (2) y confirmación/idempotencia (1).
- **10 escenarios instrumentados en verde**, Samsung SM-A165M, Android 16/API 36, aplicación aislada `com.kipu.app.lab`:
  - OCR real de una imagen sintética con el modelo empaquetado.
  - Room: dedup por imagen/referencia, pagos diferentes del mismo importe, corrección de referencia, aislamiento por propietario y transiciones de estado (3).
  - Share Intent con proveedor `content://` sintético → OCR real → candidato → `RegisterTransaction` real → ledger/proyección: gasto aplicado exactamente una vez.
  - Ingresos distintos del mismo monto: advertencia de similitud sin segundo efecto financiero; aceptación explícita registra el segundo pago.
  - Revisión Compose: corregir monto prellenado y completar monto ilegible; no se llama a confirmar antes del botón (2).
  - Migración Room 24 → 25 conservando transacciones y ledger.
  - Las **8 imágenes privadas proporcionadas**: extracción de monto positivo, fecha/hora, contraparte, referencia y dirección; incluye ingresos y gastos. Sin omisiones ni logs con el contenido OCR.
- `assembleDebug` y `assembleLabAndroidTest`: exitosos. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check`: sin errores de whitespace.

Se corrigieron durante la validación una incompatibilidad de regex JVM/Android, la asociación de columnas OCR y glifos de exclamación en encabezados. Las pruebas finales afectadas terminaron con `OK` y código de salida 0.

Para repetir la QA privada, instalar la variante lab y ejecutar `python scripts/android/push-yape-private-samples.py` desde la raíz; copia las imágenes con `run-as` al almacenamiento privado del lab y verifica su integridad, sin permisos generales de almacenamiento. Luego ejecutar `YapePrivateSamplesAcceptanceTest`. Sin las imágenes locales esta prueba es opcional en CI.

## HU y límites de entrega

- HU-49: adelanto del camino compartido/OCR/deduplicación; HU-46: extracción/candidato; HU-51: revisión focalizada Yape.
- Reutilizadas HU-23 (similitud), HU-18/HU-19 (registro/saldo), e integración con HU-58/HU-59 y cuentas/afiliación existentes.
- Pendiente del sprint correspondiente: captura de notificaciones, convergencia de identidad entre todas las fuentes, autorregistro y bandeja multifuente completa; ampliación del corpus y QA de esas integraciones.
- No se modificaron Supabase ni sus migraciones; pgTAP no aplica a esta migración SQLite. La sincronización remota existente no se certifica aquí como despliegue nuevo.
- No se ejecutó Spec Kit ni se crearon/modificaron sus artefactos, conforme a la indicación del usuario.
- AGy implementó/investigó parser y pruebas de edición. Los subagentes nativos se ejecutaron exclusivamente con `openai/gpt-6-luna#high`; OpenCode conservó integración y validación.

## Refinamiento de acceso Free y limpieza de override QA — 2026-10-21

**Veredicto: APPROVED** para el refinamiento acotado que habilita el OCR local de comprobantes Yape en Kipu Free y retira la asignación QA Premium del propietario. Este addendum complementa el alcance histórico de arriba; no cambia sus resultados ni certifica HU-49 completa.

### Cambios verificados

- `Capability.YapeReceiptOcr` se permite sin entitlement; las demás capacidades Premium mantienen la verificación vigente.
- El OCR requiere consentimiento explícito. La imagen se procesa localmente, no se sube ni se conserva, y el candidato debe revisarse antes de confirmar un movimiento.
- Supabase: se desactivó `internal.test_plan_override_gate` y se retiró el único override mediante las funciones internas auditadas. Verificación posterior: gate `false`, `0` overrides y dos eventos de auditoría (`GATE_DISABLED`, `RESET`). `v_feature_access` y `get_effective_plan_for_refresh` devuelven `FREE` / `COMMERCIAL` para la cuenta; no se alteraron compras ni billing. El esquema QA queda instalado e inactivo; no se aplicó migración.
- El build instalado usa `TEST_PLAN_OVERRIDES_ENABLED=false`, por lo que tampoco acepta grants QA antiguos.

### Validación de este refinamiento

- `:app:testDebugUnitTest --tests com.kipu.app.feature.plans.domain.FeatureAccessPolicyTest --tests com.kipu.app.feature.receipts.domain.ReceiptScanAccessPolicyTest --tests com.kipu.app.feature.receipts.domain.ImportYapeReceiptTest` — `BUILD SUCCESSFUL`.
- `:app:assembleDebug` — `BUILD SUCCESSFUL`.
- `git diff --check` — sin errores.
- Instalación de actualización `adb install -r -t` en Samsung SM-S926B (`R5CX10W7CZD`), `com.kipu.app` 0.1.0. La aplicación quedó abierta y `firstInstallTime` se conservó; no se desinstaló ni se borraron datos locales.
- PgTAP no aplica a este refinamiento: no hubo DDL ni cambio de esquema; el estado administrativo se validó con consultas de lectura tras ejecutar las funciones internas.
- No se modificaron movimientos, saldos, compras ni suscripciones.
