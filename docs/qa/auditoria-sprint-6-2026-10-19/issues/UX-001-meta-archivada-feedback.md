## Clasificación
- **Tipo:** observación de usabilidad / aclaración de criterio; no defecto financiero confirmado.
- **Estado/frecuencia:** observado por QA 1 vez en la meta propia archivada `QA_AUDIT_20261020_META`.
- **Prioridad:** P3 / baja. No hubo mutación, pérdida de datos ni intento de guardar un aporte.
- **Alcance:** detalle de meta archivada, HU-24; Samsung SM-A165M / Android 16, app 0.1.0.

## Observado
La meta archivada mostraba `Liberar`, `Reasignar` y el control `Meta archivada` deshabilitados. Al tocar el control no se abrió diálogo ni ruta adicional. La captura adjunta acredita solo el estado visual; el resultado del toque procede del registro QA. No se alcanzó un formulario de aporte y no se probó el escenario de rechazo previo al guardado.

## Incertidumbre
No está establecido que una meta archivada deba aceptar aportes. HU-24 contempla explicar qué hacer cuando falta cupo activo, pero no se demostró que este estado y esa ruta sean equivalentes. No se declara incumplido ese criterio ni se infiere una mutación financiera.

## Impacto y propuesta
La persona puede no saber si debe reactivar la meta, elegir otra o salir. Product/UX debería definir el contrato y, si corresponde, explicar por qué la acción está deshabilitada y cuál es la alternativa segura.

## Criterios para cerrar
- El estado archivado se identifica textualmente.
- La razón de no disponibilidad y la alternativa compatible con producto son comprensibles.
- No se crea reserva ni movimiento al interactuar con controles deshabilitados.
- QA comprueba el flujo únicamente con una meta propia, sin repetir reservas ya verificadas.

**No se clasifica como bug funcional confirmado.** Causa y criterio aplicable siguen pendientes.
