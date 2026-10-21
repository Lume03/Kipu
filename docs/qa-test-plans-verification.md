# Verificación Android — planes administrativos QA

Fecha: 2026-10-21. Alcance: alternar Free/Premium desde administración DB, sin compras ficticias ni Google Play.

- Subagentes nativos: exclusivamente `openai/gpt-6-luna#high`.
- Pruebas Kotlin focalizadas: 28 escenarios únicos aprobados (API, refresh, firma, lease y restauración de compras), con repetición solo de la restauración afectada al adaptar su mock de caché.
- Compilación sin opt-in: pruebas del verificador aprobadas; rechaza grants con fuente firmada `TEST_OVERRIDE`.
- BuildConfig release mantiene `TEST_PLAN_OVERRIDES_ENABLED=false` incluso pasando el opt-in Gradle.
- Android instrumentado Samsung SM-A165M/API 36: `FeatureAccessCacheDaoTest`, `OK (1 test)`. El refresh manual reemplaza Premium/Free QA; billing no sobrescribe QA cuando corresponde y una build normal permite recuperar la autoridad comercial.
- APK debug opt-in compilado e instalado con actualización sin borrar almacenamiento. No se asignó automáticamente ningún plan a la cuenta del dispositivo.
- Deno: 10 pruebas aprobadas del endpoint y emisor, incluyendo fuente firmada, expiración, autenticación, errores y ausencia de Google Play.
- Clave privada del emisor configurada como secreto del servidor; solo la clave pública de verificación entra al APK. No hay claves privadas versionadas.

El estado SQL/despliegue y los comandos administrativos se documentan en `docs/qa-test-plans.md`. Esta verificación Android no implica una compra real ni prueba UI end-to-end sobre la cuenta personal.
