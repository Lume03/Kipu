# L11b - Correcciones de auditoria

## Archivos por bug

1. Presentacion de categorias: `app/src/test/java/com/kipu/app/feature/categories/domain/CategoryPresentationUseCasesTest.kt` verifica la ultima edicion y ausencia de conflicto.
2. Correlacion Yape: `app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt` y `app/src/test/java/com/kipu/app/feature/capture/domain/CaptureCorrelationTest.kt` permiten cuenta ausente y exigen igualdad cuando ambas son conocidas.
3. P10: `supabase/migrations/20261112140000_restore_card_overutilization.sql` y `supabase/tests/database/allow_card_purchase_overutilization_p10.test.sql` eliminan el rechazo y los TODO.
4. Metas: `app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt`, `GoalDetailViewModel.kt` y `app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalLifecycleFlowTest.kt` ofrecen resolucion de reservas y bloquean acciones financieras en metas archivadas; se retiraron los dos Ignore.

## Fixture Visa

`app/src/test/resources/statements/visa/visa_sample.pdf` tenia encabezados superpuestos que impedian identificar las columnas. Se ajustaron dichos encabezados; `app/src/test/java/com/kipu/app/feature/statements/PdfBoxStatementDocumentReaderTest.kt` verifica el fixture sintetico por separado, incluyendo ocho movimientos, totales facturados y conciliacion de PEN y USD. Las muestras privadas se buscan solo en `docs/EECC` y se omiten si no existen.

## Funciones SQL reemplazadas

- `public.register_transaction_v1_pre_liability_s3(jsonb)`: se conserva la definicion de L9c salvo la comprobacion obsoleta del limite. Se revisaron las migraciones L9a y L9c y las apariciones de `CREDIT_LIMIT_EXCEEDED`; la funcion `internal.register_transaction_pre_hash_v2(jsonb)` ya fue corregida por P10.

## Verificacion

- `npx --no-install supabase db reset`: exit 0, base local.
- `npx --no-install supabase test db`: PASS, 82 archivos y 1602 pruebas; P10 sin TODO.
- `./gradlew.bat :app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL (exit 0).
- `./gradlew.bat :app:testDebugUnitTest`: BUILD SUCCESSFUL (exit 0), 1747 pruebas, 0 fallos y 1 omitidas.
- Las pruebas instrumentadas se compilaron solamente; no se uso dispositivo.
- No se aplicaron cambios remotos ni se crearon commits.
