## Clasificación
- **Tipo:** control de interfaz aparentemente interactivo sin respuesta, reportado por el Owner y corroborado por el callback vacío en el checkout inspeccionado.
- **Frecuencia:** no se proporcionó conteo de intentos; no inventar una frecuencia.
- **Prioridad sugerida:** P3. La acción informativa no bloquea el registro financiero, pero deja un affordance sin resultado.
- **Alcance:** `Cuotas y cronograma` y `Ajustes y cierre contable` en gestión de deuda.

## Pasos reportados
1. Abrir el detalle de una deuda propia.
2. Entrar en **Cuotas y cronograma** y tocar el icono `?` superior.
3. Cambiar a **Ajustes y cierre contable** y tocar el icono `i` superior.
4. Observar que no se abre ayuda, diálogo ni navegación (según reporte del Owner).

## Evidencia y diagnóstico
Las capturas recortadas muestran ambos iconos. En `DebtScheduleScreen.kt` el `IconButton` comparte `onClick = { /* Help dialog */ }`, sin acción implementada; el símbolo cambia según la pestaña. La captura no demuestra por sí misma el resultado del toque, y la procedencia del APK del screenshot no se atestigua aquí.

## Resultado esperado
Cada control debe abrir una explicación contextual pertinente a su pestaña. Si aún no existe contenido aprobado, no debe aparentar una acción disponible: quitar el control o reemplazarlo con ayuda visible y accesible.

## Propuesta
Implementar una hoja/diálogo contextual con definiciones claras (plan de cuotas no equivale a pago; ajustes/cierre preservan historial y recalculan saldo según contrato), o eliminar los botones hasta tener contenido. Evitar promesas financieras no respaldadas.

## Criterios de aceptación
- `?` e `i` producen una respuesta contextual y accesible, o no se muestran como botones accionables.
- El contenido distingue plan, pago, ajuste y cierre; no genera mutación financiera.
- La acción puede activarse por toque y accesibilidad semántica; Atrás/cerrar devuelve al mismo estado.
- QA verifica ambas pestañas en build identificada.
