# Documentación de Kipu

`docs/` es la fuente principal de documentación del proyecto. `KipuApp` conserva los originales y el material no seleccionado, pero no debe recibir nuevas revisiones de los documentos integrados aquí. Obsidian Mind puede servir como herramienta de consulta; sus copias no sustituyen los archivos versionados de este repositorio.

## Por dónde empezar

| Necesito consultar… | Documento |
| --- | --- |
| Alcance y reglas generales | [Especificación de producto](producto/especificacion.md) |
| Historias HU-01–HU-60 y aceptación de base | [Product Backlog](producto/backlog.md) |
| Dependencias y asignación de historias | [Trazabilidad](producto/trazabilidad-y-dependencias.md) |
| Calendario de sprints | [Cronograma Scrum](planificacion/cronograma-scrum.md) |
| Arquitectura y límites entre capas | [Visión general](arquitectura/vision-general.md) |
| Modelado global y diccionario de datos | [Modelo de datos](arquitectura/modelo-de-datos.md) |
| Entidades, atributos y restricciones | [Entidades](arquitectura/entidades.md) |
| Diagramas y cardinalidades | [Modelo entidad-relación (MER)](arquitectura/modelo-entidad-relacion.md) |
| Refinamientos de datos por épica | [Índice de arquitectura](arquitectura/README.md) |
| Decisiones de arquitectura | [ADR](adr/README.md) |
| Flujos funcionales, sin modelado académico | [Procesos](procesos/README.md) |
| Catálogos e investigación de referencia | [Referencias](referencias/README.md) |
| Diseño visual, mockups e implementación UI | [UI/UX](ux-ui/README.md) |
| Guía de diseño y tokens | [DESIGN.md](DESIGN.md) |
| Informes y acciones de auditoría | [Auditorías](audits/README.md) |
| Verificación funcional y evidencia | [QA](qa/README.md) |
| Selección realizada y diferencias pendientes | [Control documental](control-documental.md) |

## Autoridad y vigencia

- La [constitución](../.specify/memory/constitution.md) gobierna los invariantes del proyecto.
- Los documentos de producto describen la base V4.2; los refinamientos aprobados de cada épica viven en [specs/](../specs/), junto a su plan, tareas, modelo y contratos. No se duplican esos artefactos en `docs/`.
- Un refinamiento explícito y aprobado aplica a su alcance; una fecha posterior, una tarea completada o el código por sí solos no prueban una nueva decisión de producto. Ante una contradicción no resuelta, registrarla y aclararla antes de implementar.
- Los modelos y migraciones locales describen el diseño o implementación versionada, no prueban despliegue remoto ni aceptación funcional.
- Una investigación no es un requisito aprobado; una captura o un informe antiguo tampoco prueba el estado actual de la app.
- Las diferencias detectadas están en [control documental](control-documental.md). La integración no certifica que todas las HU estén implementadas ni que los documentos estén reconciliados íntegramente.

## Mantenimiento

1. Editar el documento de este repositorio, sin sincronización manual hacia `KipuApp`.
2. Conservar identificadores de HU y épica. Cambiar alcance mediante el flujo Spec Kit instalado; actualizar backlog, trazabilidad y planificación cuando corresponda.
3. Usar enlaces relativos, nombres descriptivos y fechas de corte para datos externos. No llamar «vigente» a una tasa sin nueva verificación.
4. No agregar borradores alternativos, logs o capturas por defecto. La evidencia sigue las [reglas de privacidad de QA](qa/README.md).
5. Bizagi y el modelado BPMN del curso permanecen fuera. No se crea un archivo histórico dentro de `docs/`: el material no seleccionado se conserva en `KipuApp`.
