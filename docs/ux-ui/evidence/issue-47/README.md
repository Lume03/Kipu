# Issue #47 — Banner financiero del Dashboard

## Evidencia comparable

Las capturas muestran la pantalla productiva completa `DashboardScreen` antes y después de retirar el banner de salud financiera, con el mismo fixture, emulador, viewport, escala de texto, tema y posición de scroll.

| Tema | Antes (`main` en `d5667ab`) | Después (implementación en `762f876`) |
|---|---|---|
| Claro | ![Antes, tema claro](dashboard-before-light.png) | ![Después, tema claro](dashboard-after-light.png) |
| Oscuro | ![Antes, tema oscuro](dashboard-before-dark.png) | ![Después, tema oscuro](dashboard-after-dark.png) |

### Entorno y fixture

- AVD `Pixel_10`, Android 17 / API 37.
- Resolución: 1080 × 2424; densidad: 420 dpi; escala de fuente: 1.0.
- Fixture sintético idéntico: cuenta de efectivo “Cuenta de evidencia”, saldo S/0.00, sin tarjetas y sin deuda.
- Cada imagen mide 1080 × 2424 px. En los dos temas, la comparación muestra la pantalla completa y el cambio visible es la ausencia del banner.

### Procedencia y ejecución

La captura “Antes” se tomó con `main` en `d5667abdf66d85a32fae43d10de8233381e84051`. Para poder usar el mismo ancla de scroll y test de captura, se copió localmente el test instrumentado de la PR y se añadió un `testTag` solo al espaciador del contenido activo en ese worktree temporal; no se modificó código productivo de `main`.

La captura “Después” corresponde a la implementación de la PR en `762f8762a952c3a74306b54d731cf39c693363a7`. En ambos heads, `DashboardFinancialMessagingTest` pasó para tema claro y oscuro en el mismo AVD. El test monta `DashboardScreen` dentro de `KipuTheme`, desplaza al mismo ancla y captura la raíz completa del `Scaffold`.

Los PNG versionados se extrajeron de `Pictures/issue-47` después de la ejecución instrumentada. Android MediaStore puede añadir sufijos `(1)` al repetir una captura; se seleccionó el archivo de la ejecución más reciente para cada tema.