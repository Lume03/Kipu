# Revisiones de Pull Request

Aquí se guardan los informes de revisión de PR como evidencia de QA. Estos informes evalúan cambios contra issues y criterios existentes; **no reemplazan ni modifican** las fuentes aprobadas de producto en `specs/` o en GitHub.

## Estructura

- [`TEMPLATE.md`](TEMPLATE.md): plantilla común para cada revisión.
- [`2026/`](2026/README.md): índice de revisiones del año.
- Cada revisión se guarda en `AAAA/PR-<número>-<slug>/review.md`.
- Si el PR incluye cambios visuales, sus capturas sanitizadas van en `AAAA/PR-<número>-<slug>/evidence/` y se enlazan desde el informe. No crear esa carpeta si no hay evidencia visual necesaria.

Ejemplo de destino: `2026/PR-75-revision-contabilidad/review.md`.

## Flujo mínimo

1. Identificar el PR, rama, commits revisados y issues relacionados antes de evaluar los cambios. Si la relación con un issue es incierta o no existe, dejarlo explícito y no inventar criterios.
2. Tomar los criterios de aceptación de la fuente vigente (issue/spec aprobada) y marcar cada uno como `Cumple`, `Parcial`, `No cumple` o `No verificable`, con evidencia.
3. Resumir los cambios, pruebas, hallazgos y mejoras por prioridad. Separar defectos verificables de sugerencias.
4. Revisar capturas solo si hay cambios de UI. Publicar únicamente evidencia aprobada por privacidad; una captura no demuestra por sí sola el comportamiento completo.
5. Mantener el dictamen del informe separado de las acciones formales de GitHub. No aprobar, solicitar cambios ni publicar comentarios sin autorización explícita.

Los informes son una evaluación acotada al commit y entorno que registran; no certifican el estado posterior del PR ni el despliegue remoto.