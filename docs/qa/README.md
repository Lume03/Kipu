# Evidencia de QA

## Auditorías

- [Sincronización remota de Planificación (2026-10-25)](sincronizacion-planificacion-2026-10-25.md): contraste teléfono/servidor, correcciones de fechas y arranque, bloqueos de Metas y Deudas y matriz pendiente de certificación.
- [Correcciones de Planificación (2026-10-25)](planificacion-2026-10-25.md): Metas, Presupuestos, Deudas, navegación retenida y validación en teléfono.
- [Auditoría funcional, UI y UX — Sprint 6 (2026-10-19)](auditoria-sprint-6-2026-10-19/README.md): matriz de casos, observaciones, seguimiento de issues y evidencia revisada.
- [Incidente de sincronización y restauración tras reinstalar (2026-10-25)](incidente-sync-restauracion-2026-10-25/README.md): causas raíz, correcciones de cliente y migraciones remotas pendientes.
- [Revisiones de Pull Request](pr-reviews/README.md): informes por PR, plantilla y evidencia visual cuando aplique.

## Convenciones de publicación

- Cada ejecución mantiene su fecha/alcance en una carpeta propia; no mezclar evidencias de distintas corridas.
- En informes, distinguir resultado funcional de revisión heurística, investigación y caso no probado. Una captura demuestra solo lo que se ve; la secuencia, causa, persistencia remota y frecuencia deben venir de un registro verificable.
- Publicar únicamente imágenes aprobadas por privacidad. Mantener fuentes originales o archivos pendientes en carpetas locales ignoradas por Git; no adjuntar bases de datos, WAL, payloads, sesiones, identificadores personales ni capturas con datos no atribuibles al fixture.
- Los informes usan rutas Markdown relativas y captions con el alcance y las limitaciones relevantes.

## Mantenimiento

Antes de versionar evidencia, revisar `git status`, comprobar los hashes y dictámenes del registro de privacidad de la auditoría, ejecutar la validación de enlaces y confirmar que ningún archivo bajo `_local-only/` o `Capturas/` quedó staged.
