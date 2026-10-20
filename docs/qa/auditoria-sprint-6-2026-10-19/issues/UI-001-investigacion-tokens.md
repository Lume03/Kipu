# UI-001 — Investigación: reconciliar los tokens visuales normativos con el tema Compose

## Clasificación y alcance

- **Tipo:** investigación de consistencia documental/código del sistema visual; no bug renderizado confirmado ni incumplimiento WCAG medido.
- **Estado:** discrepancias de valores confirmadas en las fuentes inspeccionadas; motivo/override aprobado e impacto por pantalla pendientes de confirmar.
- **Prioridad:** P3 / INFO. Puede generar decisiones inconsistentes de mantenimiento y contraste; no hay perjuicio visual o financiero demostrado.
- **Módulo:** tema Compose claro / sistema visual; revisión de la aplicación disponible hasta S6, no requisito funcional de una pantalla futura.
- **Responsable sugerido:** responsable del sistema de diseño y Android UI; sin asignación arbitraria de personas.

## Fuente esperada y fundamento

`docs/DESIGN.md` declara en sus primeras líneas que Kipu Andean Modernist es la única fuente visual normativa. Especifica que los tokens del tema prevalecen sobre discrepancias de prosa. La expectativa verificable es que el tema utilice los valores aprobados o que las diferencias se documenten como overrides deliberados con alcance y precedencia claros.

El catálogo de rediseño `docs/ux-ui/references/stitch-redesign-catalog.md` está congelado hasta S3; no se convierte aquí en requisito funcional S6/S7. Esta investigación no obliga a volver a un mockup ni a alterar colores sin reconciliar la autoridad vigente.

## Reproducción estática

1. Consultar el checkout base `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`.
2. Comparar los roles de `docs/DESIGN.md` con `app/src/main/java/com/kipu/app/ui/theme/Color.kt`.
3. Consultar `Theme.kt` y su asignación a `lightColorScheme`.
4. Buscar una decisión/override aprobado que concilie cada diferencia antes de decidir qué archivo se debe actualizar.

| Rol | Token documentado | Valor en Color.kt |
|---|---|---|
| background | `#F7F9FB` | `KipuBackground = #F8FAFC` |
| on_surface | `#191C1E` | `KipuOnSurface = #0F172A` |
| surface_variant | `#E0E3E5` | `KipuSurfaceVariant = #F1F5F9` |
| on_surface_variant | `#3E4947` | `KipuOnSurfaceVariant = #475569` |
| primary_container | `#0F766E` | `KipuPrimaryContainer = #CCFBF1` |
| on_primary_container | `#A3FAEF` | `KipuOnPrimaryContainer = #134E4A` |
| surface_tint / anillo | `#006A63` | `KipuSelectionRing = #0F766E` |

La discrepancia es determinista en esos archivos, no una frecuencia de interacción del dispositivo. No se afirma que el color aparente de una captura demuestre igualdad exacta de hex, contraste incorrecto o uso efectivo de cada rol en una pantalla.

## Trazabilidad del artefacto y límites

QA informó igualdad de SHA-256 entre el `base.apk` actual y el candidato local QA Premium: `e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864`. El candidato se asocia al worktree `012-ep-pla-qa-premium`, HEAD `d5943e8748490765caec95638802bef160e50d30`, con merge-base en el SHA anterior y delta de provisioning/documentación QA, no de pantallas. Esto aporta contexto del artefacto disponible, no una atestación reproducible ni atribución automática de síntomas históricos.

No se midieron contraste, dp/sp, hit targets o rendimiento, ni se verificó manifestación en todos los estados claro/oscuro. No hay capturas necesarias para demostrar la diferencia entre estos valores textuales; las imágenes de la auditoría no se usan como evidencia de un defecto visual no demostrado.

## Causa y qué falta confirmar

La diferencia entre archivos está constatada. No se ha demostrado si es documentación desactualizada, una decisión de paleta no registrada, overrides autorizados o un desalineamiento no intencional. Falta recuperar la decisión de autoridad y verificar manifestación/impacto de los roles usados. No se adjudica una causa funcional al APK.

## Propuesta — no aplicada

1. Identificar el canon/override actualmente aprobado y documentar precedencia por tema/estado.
2. Conciliar tabla y fuentes según esa decisión; no reemplazar tokens a ciegas por preferencias personales.
3. En una futura corrección autorizada, verificar las combinaciones y pantallas afectadas en el APK identificado, con mediciones reproducibles.

## Criterios de aceptación y verificación futura

- Cada rol coincide con el canon vigente o tiene excepción aprobada y documentada con tema/uso/estado.
- No se transforma una diferencia textual en reclamación de contraste sin medirlo.
- Las pantallas y estados relevantes se contrastan en claro/oscuro con evidencia del artefacto correcto.
- Las mediciones de contraste aplicables se registran con herramienta, pares de colores/estado y umbral aprobado.
- Se conservan las reglas financieras, acceso y comportamiento; sin introducir requisitos de sprints posteriores.

## Deduplicación y origen

Fuente local: `docs/qa/auditoria-sprint-6-2026-10-19/revision-ui.md`, UI-001. Se revisaron los cuerpos de #39 (colores forzados en modo oscuro), #46 (divulgación progresiva del registro rápido) y #48 (términos financieros); sus problemas no equivalen por sí solos a reconciliar esta tabla normativa. Repetir búsqueda de duplicados inmediatamente antes de publicar y enlazar cualquier decisión encontrada. No duplicar #60 ni #61, que son defectos de navegación/acciones distintos.

La auditoría no implementa la propuesta, no modifica diseños de producción y no hace commits/builds/instalaciones. La apertura de este seguimiento no confirma un defecto visual renderizado ni aprueba el release.
