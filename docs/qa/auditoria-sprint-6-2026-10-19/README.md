# Auditoría Sprint 6 — 2026-10-19

**Estado:** auditoría parcial; no equivale a aceptación integral del Sprint 6. La matriz actual registra 53 casos: 33 PASS, 2 FAIL, 1 BLOCKED y 17 NOT TESTED. El resultado remoto de sincronización permanece sin confirmar.

## Índice

- [Informe funcional y hallazgos](informe.md)
- [Revisión visual UI](revision-ui.md)
- [Revisión heurística UX](revision-ux.md)
- [Diagnóstico acotado de navegación/sincronización](diagnostico-sync-nav.md)
- [Análisis del cronograma](analisis-cronograma.md)
- [Registro de publicación de issues](issues-publicacion.md)
- [Dictámenes de privacidad y SHA-256](issues/revision-privacidad.md)
- [Borradores y seguimientos de issues](issues/)
- [Agregados QA sanitizados](logs/)
- [Evidencia visual y convención de nombres](capturas/README.md)
- [Checkpoint de coordinación](checkpoint-coordinacion.md)
- [Continuación técnica](continuacion-tecnica-2026-10-20.md)

## Reglas de interpretación

- Los resultados `PASS`/`FAIL` aplican solo a las acciones y el entorno descritos en `informe.md`; los casos bloqueados o no probados no se infieren.
- Las observaciones UI/UX no se convierten automáticamente en defectos contractuales.
- Ninguna captura aislada prueba causalidad, orden temporal, sincronización remota ni una secuencia de interacción.
- La tabla de privacidad es la autoridad para decidir qué imagen se puede compartir. Las fuentes excluidas se conservan localmente y no deben incluirse en commits o bundles.

## Issues

Los números y estados se registran en [issues-publicacion.md](issues-publicacion.md). Referencias principales: [#60](https://github.com/Lume03/Kipu/issues/60)–[#74](https://github.com/Lume03/Kipu/issues/74). Los issues existentes mantienen su propio ciclo de vida en GitHub.
