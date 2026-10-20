## Clasificación
- **Tipo:** mejora de usabilidad de entrada de fechas; no incumplimiento funcional confirmado.
- **Prioridad:** P3 / baja. La fecha es opcional y el formato está indicado, pero escribir ISO manualmente aumenta fricción y errores de formato.
- **Alcance:** alta/edición de deuda, registro de dinero prestado y formulario de nueva meta.

## Observado
Las capturas muestran campos de texto con placeholder `AAAA-MM-DD` para vencimiento de deuda/dinero prestado y fecha objetivo de meta. En el código revisado son `OutlinedTextField` (`PayableDebtFormScreen.kt`, `DebtEditDetailsScreen.kt`, `GoalFormScreen.kt`). Las imágenes se recortaron al campo para excluir identificadores/importes no necesarios.

La especificación consultada hace opcionales estas fechas, pero no exige específicamente un calendario; por eso se propone como mejora y no como bug contractual. La captura tampoco prueba por sí sola si al tocar se abre alguna ayuda adicional.

## Cambio propuesto
Usar un control con affordance de calendario y `DatePicker` localizado para seleccionar la fecha, manteniendo entrada manual accesible como alternativa si el producto la desea. Permitir limpiar la fecha opcional y conservar validación/rango actuales; no autocompletar una fecha ni cambiar vencimientos existentes.

## Criterios de aceptación
- Cada campo deja claro que es opcional y ofrece un selector de fecha reconocible.
- Seleccionar, editar manualmente (si se conserva esa vía) y limpiar produce el mismo valor ISO validado.
- Cancelar el selector no modifica el formulario; guardar sin fecha sigue permitido.
- Teclado, foco, TalkBack y límites de fecha son accesibles y coherentes en crear/editar.
- QA verifica deuda, dinero prestado y meta con fixtures, sin cambiar vencimientos ajenos.

No se solicita hacer obligatoria ninguna fecha.
