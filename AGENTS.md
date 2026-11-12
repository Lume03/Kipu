# Kipu — Instrucciones para Agentes (Codex / Claude / Gemini)

## Documentación principal y fuentes de autoridad

- La fuente principal de documentación es `docs/README.md`, dentro de este repositorio. Consultar sus enlaces de producto, arquitectura, planificación y referencias según el alcance.
- La constitución vive en `.specify/memory/constitution.md`; los refinamientos aprobados y artefactos compartidos por épica viven en `specs/<epica>/`. `docs/` no duplica sus modelos ni contratos.
- Antes de usar una definición, revisar su vigencia y `docs/control-documental.md`. No resolver contradicciones entre fuentes por suposición ni convertir el código o una investigación en aprobación de producto.
- Obsidian Mind es una herramienta opcional de recuperación, no una fuente independiente que prevalezca sobre estos archivos. No exigir acceso MCP `om` para leer la documentación integrada.
- Los originales y el material no seleccionado permanecen en `KipuApp`; no editar allí nuevas versiones de los documentos integrados. Bizagi y el modelado BPMN del curso quedan fuera de `docs/`.
- Las citas históricas a nombres de notas o rutas antiguas se localizan mediante `docs/control-documental.md`; no son un requisito de consultar otra bóveda.
- Usar este repositorio para implementación y validar el estado desplegado por separado: un contrato o una migración local no prueba despliegue remoto.

## Spec Kit / SDD

Este proyecto utiliza Spec Kit. Antes de una HU o cambio funcional significativo, revisa `docs/README.md`, `.specify/memory/constitution.md`, la spec de la épica, y su plan, tasks y artefactos relevantes. Sigue el workflow/comandos instalados de Kipu (incluida la aclaración previa al plan cuando corresponda). Las referencias globales adicionales pueden recuperarse bajo demanda, sin convertir Obsidian Mind en un requisito para consultar el producto. No cargues la guía completa innecesariamente. No añadas requisitos sin respaldo ni resuelvas por suposición una contradicción entre código y spec: identifica la autoridad/evidencia y sigue el proceso aprobado para actualizar los artefactos afectados antes de propagar cambios a implementación.

### Unidad de trabajo: épica

- En Kipu, la **épica es la unidad de rama y de artefactos Spec Kit**. El sprint agrupa las HU que se entregarán; no determina el nombre de la rama.
- Usa la convención existente `{NNN}-ep-{sigla}-{slug}`, alineada con la carpeta `specs/` (por ejemplo, `012-ep-pla-planes-monetizacion`). Si la épica ya tiene rama, continúa allí; para una épica nueva, crea su rama siguiendo esa convención y el flujo Git instalado.
- Un sprint puede abarcar varias épicas. Identifica las épicas de cada HU en el cronograma/Product Backlog y trabaja cada cambio en la rama de su épica. No crees una rama ni una copia de especificación por sprint o por integrante.

### Artefactos y refinamiento incremental

- Mantén un conjunto compartido de artefactos por épica en `specs/<epica>/`: `spec.md`, `plan.md`, `tasks.md` y los artefactos relacionados. La primera planificación de la épica los crea; los sprints posteriores refinan los existentes en el mismo lugar, preservando historia y trazabilidad.
- Antes de iniciar el trabajo del sprint, identifica HU, criterios de aceptación, dependencias y épicas afectadas; revisa el estado actual de los artefactos de cada épica.
- Para una épica existente, cambia primero a su rama y usa el flujo `refine` instalado: `speckit.refine.update` para incorporar al `spec.md` los requisitos aprobados; `speckit.refine.diff` para revisar el impacto; `speckit.refine.propagate` para propagar los cambios a `plan.md` y `tasks.md` y a otros artefactos afectados; y `speckit.refine.status` para verificar la sincronización.
- `refine` modifica los artefactos en la rama actual; no crea ni cambia ramas. Selecciona la rama de la épica antes de ejecutarlo. Para una épica sin especificación inicial, sigue el flujo de creación Spec Kit una sola vez y luego continúa refinando esa especificación entre sprints.
- Avanza la implementación por las tareas acordadas y conserva el vínculo HU → spec → plan/tasks → código. Si falta una decisión de producto o las fuentes se contradicen, aclárala antes de propagar el cambio.

## Directrices de Ejecución Ágil y Anti-Burocracia (Mandato del Owner)

Para maximizar la velocidad de entrega y evitar parálisis por hiper-burocracia o bucles de descomposición:

1. **Pruebas en Hardware / Android:**
   - No te detengas ni solicites intervención humana por problemas ambientales o desconexión de ADB.
   - Las pruebas instrumentadas deben otorgar sus propios permisos requeridos (`pm grant` vía `InstrumentationRegistry.getInstrumentation().uiAutomation`) en su bloque `@Before` o helper de setup.
   - Si los tests de comportamiento focalizados ya pasaron en verde (exit code 0), acéptalos y continúa. No repitas ejecuciones redundantes ni re-ejecutes la suite global de 413 pruebas legacy para validar cambios acotados.

2. **Prohibición de Micro-Descomposición y Roles Redundantes:**
   - Para cambios funcionales, refactors o correcciones de incidencias, NO crees subtareas circulares de diseño "read-only", "revisión de Tech Lead", "revisión de Arquitecto", ni re-auditorías múltiples entre sub-agentes.
   - El agente asignado debe implementar directamente, validar con pruebas focalizadas y entregar.

3. **Convergencia y Merge Directos:**
   - Una vez que las pruebas de base de datos (pgTAP) y pruebas focalizadas de código (unitarias/instrumentadas) estén en GREEN:
     a) Genera el `verification-report.md` inmediatamente con veredicto `APPROVED`.
     b) Ejecuta los commits convencionales por capas (`docs(specs)`, `feat(db)`, `feat(app)`).
     c) Abre el PR y realiza el merge a `main` de forma autónoma.
