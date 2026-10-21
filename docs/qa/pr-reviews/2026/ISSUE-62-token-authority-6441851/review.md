# Revisión local del issue #62 — autoridad de tokens UI

- **PR:** No aplica; investigación documental previa a cualquier PR.
- **Repositorio:** Lume03/Kipu
- **Autor / asignado:** UI/UX local
- **Fecha:** 2026-10-20
- **Checkout:** `kipu-ui-issue62`, detached intencional
- **Commit base:** `6441851db93b0c23655283a2c4a257845592875f`
- **Archivo investigado:** `docs/ux-ui/references/issue-62-token-authority-investigation.md`
- **SHA-256 del informe:** `557f089eaa906313ce510062627eb02c1e3af7fc14bc8097071baf045e5ccfd4`

## Dictamen breve

**Resultado:** Inconcluso

La investigación documenta una contradicción verificable entre la autoridad normativa de color en `docs/DESIGN.md`, el sistema Calm Emerald de mockups y el alcance específico del brief de ejecución aprobado. No hay evidencia suficiente para convertir una excepción de una ejecución en autoridad global. El informe solicita una decisión explícita del Owner; mientras no exista, no se deben modificar tokens ni declarar reconciliado #62.

## Issues y alcance

| Issue | Relación | Criterios/expectativa consultada |
|---|---|---|
| [#62](https://github.com/Lume03/Kipu/issues/62) | Confirmado como investigación documental; no defecto renderizado confirmado | Reconciliar autoridad/precedencia de tokens y overrides; no declarar impacto visual o WCAG sin medición. |

- **Issues no encontrados o relación incierta:** No se propone ni confirma un PR de código.
- **Fuente de autoridad:** `docs/README.md`, `docs/control-documental.md` DOC-03, `docs/DESIGN.md`, `docs/ux-ui/mockups/README.md`, `APPROVED_MOCKUPS_EXECUTION.md`, constitución y EP-PLA citados en el informe.

## Cumplimiento de criterios

| Criterio | Estado | Evidencia / límite |
|---|---|---|
| Identificar declaraciones en conflicto y precedencia documentada | Cumple como investigación estática | El informe distingue contradicción global de override limitado al brief, con citas por archivo/línea. |
| Determinar autoridad global por rol/pantalla/tema | No verificable | Los documentos versionados mantienen DOC-03 pendiente; se requiere decisión del Owner. |
| Probar manifestación visual/renderizada o WCAG | No ejecutado | No hubo build, ADB, screenshots, medición de contraste ni prueba de accesibilidad. |
| Recomendar un cambio de tokens | No aplica todavía | No se cambiaron hex, tema Compose, documentación normativa ni issue GitHub. |

## Cambios realizados

- Solo se añadió el informe de investigación estática citado arriba en el worktree de #62.
- No se modificó paleta, código, requisitos, `docs/DESIGN.md` ni el issue.

## Hallazgos y mejoras

### Bloqueantes / importantes

- **Decisión de producto pendiente:** el Owner debe escoger política global o por pantalla y aclarar el alcance de `APPROVED_MOCKUPS_EXECUTION.md`. Sin esa respuesta no se puede cerrar #62 ni iniciar una unificación de tokens con autoridad aprobada.

### Menores / sugerencias

- Ninguna identificada; la matriz provisional y alternativas están en el informe investigado.

## Pruebas y validaciones

| Validación | Resultado | Evidencia / limitación |
|---|---|---|
| Inspección de autoridad documental y referencias | Pasa como análisis estático acotado | Fuentes y hashes constan en el informe. No se infiere aprobación a partir de código o fecha. |
| Build/tests/ADB/medición UI | No ejecutados | No hubo cambio de código ni autorización de producto para cambiar tokens. |
| `git diff --check` | Pasa | El informe local registra cero errores de whitespace. |

## Revisión visual

- **¿Hay cambios visuales?** No.
- **Pantallas y escenarios revisados:** No se inspeccionó UI runtime.
- **Evidencia:** No se adjuntan capturas ni métricas.
- **Privacidad:** Investigación solo de fuentes/documentos; no usó datos de usuario.

## Pendientes / límites

- Respuesta explícita del Owner a las alternativas y autoridad propuestas en el informe.
- Reconciliar documentación/código solo después de la decisión aprobada.
- #62 no forma parte del candidato integrado #63/#64/#69/#74 y no debe bloquear esos PRs si no tocan tokens; sí bloquea una modificación visual que dependa de esa autoridad.

## Acciones en GitHub

- **Aprobación formal:** No enviada
- **Comentarios publicados:** No
- **Solicitudes de cambio:** No

Esta review local no sustituye la decisión de producto ni cierra #62.
