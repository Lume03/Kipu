## Clasificación
- **Tipo:** mejora de consistencia visual y reconocimiento; no fallo de navegación confirmado.
- **Prioridad:** P3 / baja. Los dos accesos están en cabeceras distintas; no se reporta pérdida de funcionalidad.
- **Evidencia:** recortes sanitizados de las capturas provistas; se excluyeron saldo, movimientos y avatar. Los originales permanecen intactos.

## Observado
En **Mi dinero** el acceso a configuración usa un icono de controles deslizantes; en **Movimientos** usa un engranaje. En el checkout revisado ambos callbacks navegan a Configuración (`DashboardScreen.kt`, `MovementHistoryScreen.kt`). El propósito parece ser el mismo, aunque la captura por sí sola no prueba la ruta.

## Resultado esperado
Si ambos controles llevan a la misma configuración, usar iconografía y descripción accesible consistentes. Si representan acciones diferentes, ajustar el nombre/tooltip para que la diferencia sea comprensible.

## Propuesta
Acordar un icono canónico para configuración y aplicarlo a ambos accesos, conservando hit target, callback y etiqueta semántica. No cambiar navegación ni permisos.

## Criterios de aceptación
- Se confirma que ambos accesos tienen el mismo destino/propósito o se documenta la distinción.
- Accesos equivalentes usan el mismo icono y content description coherente.
- QA abre Configuración desde ambas pantallas y confirma el mismo destino.

Este issue es una mejora de consistencia, no una afirmación de que los botones estén rotos.
