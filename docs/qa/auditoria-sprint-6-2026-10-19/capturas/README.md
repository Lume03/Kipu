# Evidencia visual

Las imágenes versionadas están agrupadas por propósito y nombradas en minúsculas con guiones (`kebab-case`). La carpeta padre identifica la auditoría y fecha; los prefijos de archivo mantienen la HU o issue.

| Carpeta | Contenido |
|---|---|
| [`funcional/`](funcional/) | Capturas aprobadas de recorridos/casos HU. |
| [`incidencias/`](incidencias/) | Capturas aprobadas vinculadas a bugs/investigaciones. |
| [`ui-ux/`](ui-ux/) | Derivados sanitizados de capturas recientes, ligados a issues #70–#74. |
| `_local-only/` | Material pendiente/no publicable; ignorado por Git. No usar en informes versionados. |
| `Capturas/` | Originales del Owner, preservados sin cambios y excluidos de Git. |

## Privacidad, integridad y límites

- Revisar [el registro de privacidad](../issues/revision-privacidad.md) antes de compartir o adjuntar una imagen. La aprobación se aplica a los bytes/hashes revisados, no a cualquier archivo con nombre parecido.
- Los originales en `Capturas/` no se alteran ni se suben. Los recortes publicados son derivados separados y tienen sus propios hashes.
- `SAFE` indica aptitud de publicación dentro del contexto descrito; no certifica el flujo funcional ni la ausencia de otros datos fuera del encuadre.
- Los archivos `NEEDS_CONTEXT`, `NEEDS_REDACTION` o sin dictamen se excluyen del bundle versionado y permanecen en `_local-only/` cuando ya estaban en el repositorio de trabajo.
- Los reportes deben describir lo demostrado y explicitar los límites: no atribuir causas, concurrencia, frecuencia, sincronización remota o saldos ajenos a partir de una imagen sola.
