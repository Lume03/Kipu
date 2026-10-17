# Issue #47 — Dashboard financial banner

## Harness actualizado para las capturas después

`DashboardFinancialMessagingTest` monta la pantalla de producción `DashboardScreen` dentro de `KipuTheme`, con una cuenta de efectivo sintética de saldo cero, sin tarjetas y sin deuda. La cuenta mantiene activa la ruta de contenido del Dashboard. El modificador de prueba identifica la raíz de pantalla completa; el harness desplaza el contenido hasta el mismo marcador inferior y captura esa raíz, que incluye el `Scaffold` y su superficie opaca `emeraldColors.background`.

Las capturas después se generan por separado en tema claro y oscuro, con los límites del dispositivo/emulador y su escala de texto. El test comprueba que la esquina inferior derecha de la captura coincide con el color de fondo del tema activo y tiene alfa opaca. Los archivos se escriben como `dashboard-after-light.png` y `dashboard-after-dark.png` en el almacenamiento externo de la app y en `Pictures/issue-47`.

## Estado y procedencia de los PNG versionados

| Tema | Antes versionado | Después versionado |
|---|---|---|
| Claro | ![Captura histórica anterior, tema claro](dashboard-before-light.png) | ![Captura histórica posterior, tema claro](dashboard-after-light.png) |
| Oscuro | ![Captura histórica anterior, tema oscuro](dashboard-before-dark.png) | ![Captura histórica posterior, tema oscuro](dashboard-after-dark.png) |

Los cuatro PNG versionados son artefactos históricos de 1080 × 2400 px. El origen de `dashboard-before-*.png` no se puede verificar: no hay metadatos ni registro en el repositorio que identifique su commit, dispositivo, fixture o harness, y la prueba no genera esos nombres. La versión anterior del test sí montaba `ActiveDashboardContent` directamente para sus capturas `dashboard-after-*`, sin la raíz `Scaffold` de producción. Tampoco quedaron registrados el dispositivo, viewport o escala de texto de una ejecución que vincule esas imágenes con los archivos actuales. Las imágenes oscuras muestran el área exterior de las tarjetas clara. Por eso estos PNG no demuestran la pantalla completa de producción ni constituyen una comparación antes/después verificablemente comparable.

Este cambio actualiza el harness, pero no ejecuta instrumentación ni reemplaza los PNG. Para publicar una comparación comparable, hay que capturar la revisión anterior a la eliminación del banner y la revisión actual con el mismo harness de raíz completa, fixture, dispositivo/emulador, viewport, escala de texto, tema y posición de scroll. Después se deben copiar las capturas a los cuatro nombres de archivo de la tabla. Hasta entonces, la comparación versionada permanece histórica; las nuevas capturas `dashboard-after-*.png` solo estarán disponibles tras ejecutar la instrumentación.
