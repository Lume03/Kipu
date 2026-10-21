# Issue #62 — Investigación de autoridad de tokens y overrides

**Estado:** investigación estática; autoridad global y precedencia pendientes de confirmación del Owner. No se modifica paleta, código ni el issue de GitHub.

## Baseline y método

- Repositorio: `Lume03/Kipu`; issue #62, abierto, consultado en modo lectura con `gh issue view 62 --repo Lume03/Kipu --json title,body,comments,url,state`. El issue reporta una discrepancia documental/código, no un defecto renderizado confirmado ni una evaluación WCAG.
- Checkout de investigación: `6441851db93b0c23655283a2c4a257845592875f` (`HEAD`, detached intencional), fecha del commit `2026-10-20T15:55:41-05:00`; worktree limpio antes de crear este informe. No se cambió rama.
- Se comprobó que este archivo no existía antes de redactarlo. Único archivo nuevo autorizado.
- Fuentes examinadas: `AGENTS.md` no figura entre los archivos versionados del checkout; se siguieron las reglas explícitas de esta tarea. Se revisaron `docs/README.md`, `.specify/memory/constitution.md`, `docs/control-documental.md`, `docs/DESIGN.md`, índices UI/UX y mockups, catálogo Stitch, contratos EP-PLA, guía de ejecución de mockups y tema Compose. Búsquedas estáticas buscaron autoridades, valores, nombres de token y usos en `docs/` y `app/src/main/java/`.
- No se corrieron builds, pruebas, ADB ni contrastes calculados. No se evaluaron píxeles, pantallas, estados de runtime ni conformidad WCAG.

## Hallazgo principal

DOC-03 reconoce expresamente dos declaraciones en tensión: `docs/DESIGN.md` llama Andean Modernist «única fuente de verdad visual normativa» (líneas 5–15) y `docs/ux-ui/mockups/README.md` declara Calm Emerald oficial para los mockups, basado en un proyecto Stitch diferente (líneas 1–7). `docs/ux-ui/README.md:14–16` y `docs/control-documental.md:53–59` mantienen la contradicción abierta y ordenan no escoger por antigüedad o apariencia. La reorganización/renombre de `docs/stitch-design-system.md` a `docs/DESIGN.md` no decidió ni resolvió el conflicto (`docs/control-documental.md:100`).

Existe una evidencia relevante que debe ponerse ante el Owner, no ignorarse: `docs/ux-ui/implementation/APPROVED_MOCKUPS_EXECUTION.md:1–5` afirma que el brief aprobado para la ejecución de mockups hace prevalecer Calm Emerald sobre la paleta Stitch histórica, conservando tipografía, espaciado y shapes. Es una precedencia explícita en ese brief de ejecución; los documentos de control actualizados el 20/10 aún preservan DOC-03 sin resolver, y la guía no identifica una decisión global por token, pantalla y tema que revoque la declaración normativa de `DESIGN.md`. Por ello la evidencia sustenta un override de alcance de la ejecución referida, pero no permite afirmar una autoridad global inequívoca.

La Constitución no define una paleta; sí exige decisiones fundamentales explícitas y trazables: `.specify/memory/constitution.md:187–203, 238–247, 289–300`. La especificación EP-PLA es una excepción de alcance fuerte y concreta: `specs/012-ep-pla-planes-monetizacion/spec.md:217–223` prescribe Andean Modernist para Pantalla 1B; `plan.md:88–100` identifica su proyecto/asset y lo trata como referencia normativa para esa pantalla. No se infiere de ello una autoridad global para todas las pantallas.

El catálogo `docs/ux-ui/references/stitch-redesign-catalog.md:1–7` declara alcance funcional congelado hasta S3. Se cita para procedencia y alcance histórico, no se convierte aquí en requisito para pantallas S4–S7 o futuras.

## Matriz de los siete roles reportados por #62

Los hex de la columna «actual» son la definición/valor fuente en el tema Compose actual del baseline. La tabla separa el esquema claro configurado en `Theme.kt` del oscuro; la especificación `DESIGN.md` enumera principalmente tokens claros y no proporciona un set oscuro completo para estos siete roles. «No definido» significa que no hallé un equivalente oscuro documentado para ese rol en `DESIGN.md`, no que falte diseño oscuro en toda la aplicación.

| Rol de #62 | Documentado (`DESIGN.md`, claro) | Compose actual claro (`Color.kt` → `Theme.kt`) | Compose actual oscuro (`Theme.kt` y referencia `Color.kt`) | Evidencia y excepción/uso localizado |
|---|---|---|---|---|
| `background` | `#F7F9FB` (`DESIGN.md:41–45`) | `#F8FAFC` (`Color.kt:23`; `Theme.kt:67`) | `#0B1220` (`Color.kt:37`; `Theme.kt:31`), coincide con el token semántico `dark-background` documentado en `DESIGN.md:128–129, 325` | Diferencia clara confirmada en archivos fuente. `CalmEmeraldTokens.kt:91–101` usa también fondo claro `#F8FAFC` y gradiente `#0F766E`/`#115E59`; provider reemplaza `background` por `scheme.background` (`:133–145`). Esto evidencia implementación Calm, no aprobación global por sí sola. |
| `on_surface` | `#191C1E` (`DESIGN.md:41–44`) | `#0F172A` (`Color.kt:24`; `Theme.kt:68,70`) | `#F8FAFC` (`Theme.kt:34`; valor `KipuInverseOnSurface` declarado en `Color.kt:36`) | Diferencia clara confirmada. El tema oscuro ofrece rol inverso; no es una aplicación directa del token claro a dark. `DESIGN.md:116–117` sólo documenta `inverse_surface`/`inverse_on_surface`, no define un mapa completo de `on_surface` dark. |
| `surface_variant` | `#E0E3E5` (`DESIGN.md:52`) | `#F1F5F9` (`Color.kt:31`; `Theme.kt:71`) | `#0F172A` (`Color.kt:35`; `Theme.kt:35`, asignado desde `KipuInverseSurface`) | Diferencia clara confirmada; el valor oscuro difiere además de `KipuDarkSurfaceVariant = #1A263B` (`Color.kt:39`), que es `surfaceContainer` dark en `Theme.kt:39`. Son roles distintos; no se deben equiparar. Sin token oscuro homólogo explícito hallado en `DESIGN.md`. Usos directos de `MaterialTheme.colorScheme.surfaceVariant` aparecen, por ejemplo, en `KipuFilterChip.kt:29` y `ProfileSettingsScreen.kt:171,248`; el grep amplio también encuentra otros consumidores. |
| `on_surface_variant` | `#3E4947` (`DESIGN.md:53`) | `#475569` (`Color.kt:32`; `Theme.kt:72`) | `#94A3B8` (`Color.kt:40`; `Theme.kt:36`) | Diferencia clara confirmada. Uso en claro/oscuro depende del `ColorScheme` (`KipuEmptyState.kt:51`; `PlanSelectionScreen.kt:95,171,198,240,290,320,326`). `CalmEmeraldTokens.kt:99` usa `#475569` claro y `#94A3B8` oscuro para texto secundario. Sin token oscuro homólogo explícito en `DESIGN.md`. |
| `primary_container` | `#0F766E` (`DESIGN.md:68`; CTA base en `:276,317`) | `#CCFBF1` (`Color.kt:8`; `Theme.kt:57`) | `#134E4A` (`Theme.kt:21`) | Diferencia clara confirmada; también distinto en oscuro, para el cual `DESIGN.md` no prescribe par dark. No confundir con `primary`: el `primary` claro es `#0F766E` y el oscuro `#14B8A6` (`Theme.kt:19,55`). Consumidores directos del rol incluyen `BiometricSettingsScreen.kt:148`, `NotificationCenterScreen.kt:215` y `DebtSettlementSheet.kt:138,187`; varían por estado/pantalla. |
| `on_primary_container` | `#A3FAEF` (`DESIGN.md:69`) | `#134E4A` (`Color.kt:9`; `Theme.kt:58`) | `#BCEBDD` (`Theme.kt:22`) | Diferencia clara confirmada; mapa oscuro no documentado para el rol. `KipuThemeContrastTest.kt:70` expresa el par semántico probado por el test como `onPrimaryContainer`/`primaryContainer`; ese código de test no es aquí evidencia de salida ejecutada ni medición de pantalla. |
| `surface_tint` / anillo de selección | `#006A63` (`DESIGN.md:75`; uso `:319`) | `KipuSelectionRing = #0F766E` (`Color.kt:11`); `Theme.kt:87` lo asigna a `surfaceTint` claro | `#0F766E` también, por `Theme.kt:51` y el mismo token fijo | Diferencia confirmada en ambos temas frente al valor documentado. `PlanPurchaseScreen.kt:161` usa el token fijo como borde animado de selección. No hallé override por tema en la definición de `KipuSelectionRing`. |

### Excepciones, overrides y prevalencia local

- **Confirmado:** `APPROVED_MOCKUPS_EXECUTION.md:1–5` señala Calm Emerald prevalente sobre la paleta Stitch histórica para ese brief, y conserva tipografía/espaciado/shapes. Su lista de tareas (`:35–50`) incluye trabajo histórico de tema, sin ser por sí sola una matriz de aprobación permanente de cada rol actual.
- **Confirmado:** `DESIGN.md:33` afirma que `namedColors`/`designTheme` del tema es canon respecto de discrepancias en la prosa del propio designMd. Esa regla resuelve una discrepancia interna del documento fuente (ej. notas `:119,277`); no documenta que el `Color.kt` actual esté aprobado como override frente a otra fuente de autoridad.
- **Confirmado:** el tema activa esquemas separados por tema en `Theme.kt:18–52,54–88`; el proveedor Calm Emerald hereda algunos roles Material con `scheme` pero conserva tokens semánticos específicos (`CalmEmeraldTokens.kt:59–121,131–147`). Por tanto, los siete roles no son necesariamente la única fuente de color consumida por componentes.
- **No hallado en la búsqueda local acotada:** decisión aprobada que asigne precedencia a cada uno de estos siete roles por tema y pantalla, o una tabla que declare los valores actuales de `Color.kt` como overrides aprobados de `DESIGN.md`. Esto no prueba inexistencia en conversaciones externas/no incorporadas.
- **Fuentes auxiliares que reiteran la tensión:** `docs/ux-ui/mockups/README.md:3–7,115–117`, `docs/ux-ui/README.md:14–16`, `docs/control-documental.md:53–59`, e informe QA previo `docs/qa/auditoria-sprint-6-2026-10-19/issues/UI-001-investigacion-tokens.md:11–15,42–50` (hallazgo secundario, no reemplaza decisión del Owner).

## Evidencia vs hipótesis vs recomendación

**Evidencia:** hay diferencias textuales de valores en fuente; los esquemas claro/oscuro y los consumidores son localizables en los archivos indicados. Hay dos declaraciones globales incompatibles; además existe un brief de ejecución que favorece Calm Emerald dentro de su alcance. La propia documentación de control mantiene el conflicto abierto.

**Hipótesis no verificadas:** que el código represente la paleta global aprobada; que Calm Emerald sea un override limitado a mockups/épicas ya implementadas; que `DESIGN.md` sea antiguo para todas las pantallas salvo EP-PLA; o que alguna variación sea deliberada por legibilidad en dark. Ninguna se eleva a decisión por fechas, implementación, capturas o gusto.

**Recomendación provisional (no aplicada):** mantener intactos ambos documentos/tokens hasta decisión. Para trabajo futuro, usar overrides Calm Emerald sólo donde un contrato/brief aprobado de pantalla lo declare explícitamente; preservar requisitos locales explícitos como EP-PLA/1B y registrar una matriz owner-aprobada de canon, excepciones y pares light/dark. A continuación reconciliar documentos e implementación según esa resolución. Esta ruta minimiza la inferencia global a partir de una guía de ejecución de alcance limitado; no es aprobación ya existente.

## Pregunta concreta para el Owner

¿Confirma cuál será la política normativa del sistema visual para los siete roles del issue #62 y sus variantes claro/oscuro?

1. **Calm Emerald global:** reemplaza la autoridad de color Andean Modernist en todas las pantallas; conservar de Stitch únicamente tipografía, espaciado y shapes donde se indique.
2. **Andean Modernist global:** permanece como canon; Calm Emerald sólo es excepción de las pantallas/briefs que lo aprueban expresamente.
3. **Canon por pantalla (recomendación provisional):** mantener ambos sistemas con matriz de precedencia por pantalla/épica/tema; aplicar Calm Emerald sólo con aprobación local explícita y conservar excepciones ya normadas, como EP-PLA/1B.

Si elige la opción 2 o 3, ¿el Owner confirma que la frase de `APPROVED_MOCKUPS_EXECUTION.md:3–5` es una aprobación acotada al alcance de esa ejecución y no una sustitución global de `DESIGN.md`? ¿Quién aprueba/actualiza la matriz y qué referencia sirve como registro trazable de esa decisión? Sin respuesta no se cambia ningún hex ni se declara reconciliado DOC-03/#62.

## Comandos y hashes de fuentes

Comandos de investigación ejecutados en el checkout:

```text
git status --short --branch
git rev-parse HEAD
git diff --name-only
git ls-files 'AGENTS.md' 'docs/ux-ui/*' 'docs/ux-ui/references/*' '.specify/memory/constitution.md' 'app/src/main/java/com/kipu/app/ui/theme/*' 'specs/*'
git log --oneline --all -- docs/DESIGN.md docs/ux-ui/mockups/README.md
gh issue view 62 --repo Lume03/Kipu --json title,body,comments,url,state
sha256sum <lista de fuentes de abajo>
```

`gh` devolvió issue abierto y sin comentarios, y se consultó en modo lectura. Hashes SHA-256 obtenidos localmente (no son hashes de artefacto Android):

| Archivo | SHA-256 |
|---|---|
| `docs/DESIGN.md` | `b566db18d0118f90ac52534b48ecaae5c346c38a2d3a4219910ee411dc1c47cb` |
| `docs/control-documental.md` | `133792ae4ee2198fef69706e615e855817be12f4e9e0824a10a749cf0309fdbd` |
| `docs/ux-ui/README.md` | `ba9b30c3bf7d3ac1d9941812fe9ee8ddfb66f57139cb9d462785ae0d56c81945` |
| `docs/ux-ui/mockups/README.md` | `f21b66b7114212bd2dfe388cedf26ac406151cf00b7b6b41e45d6242e03328e6` |
| `docs/ux-ui/references/stitch-redesign-catalog.md` | `7e7ad4c1da700a3a18d85901459a30dc95f6461bfc27fd79bbf4530eb1652bb7` |
| `Color.kt` | `9f8faac9241bf5a7c7c5080d1ba53153c75e37bd359bd6ee43428bf12c0b7991` |
| `Theme.kt` | `2713a1b37a74442038dfa88ff3baa444991b4e33f0380fa58c0f6b8b90e18896` |
| `CalmEmeraldTokens.kt` | `0812c4007313d942339b1062ac9ab8b1210f76255728e64c4f0894e0621090fb` |
| `KipuUiColors.kt` | `b16023621bef62f9fe40476c8c089e72f86f9ca4299bcb31aed6165e2ef94bb2` |
| `APPROVED_MOCKUPS_EXECUTION.md` | `1d8b20e6ff7837f103edf4c143343a60d108fb6e5145ca5e44af15eab77f3018` |
| `EP-PLA spec.md` | `499b9878a95f8f6ab9fa07e56067394f3a76aecae09fba0cf1f6dd17cce8db12` |
| `UI-001-investigacion-tokens.md` | `ac099e40668b0e7ca751e1b2e3a79a5300aaae0f534a0b8dda98ee459c893901` |

## Verificación de este informe

Se comprobó que existen los destinos fuente citados: `../../DESIGN.md`, `../mockups/README.md`, `stitch-redesign-catalog.md`, `../../control-documental.md`, `../../qa/auditoria-sprint-6-2026-10-19/issues/UI-001-investigacion-tokens.md`, `../implementation/APPROVED_MOCKUPS_EXECUTION.md` y `../../../specs/012-ep-pla-planes-monetizacion/spec.md`, relativos a este informe. `git diff --check` no reportó errores; el único cambio del worktree es este archivo nuevo. No se hizo commit.
