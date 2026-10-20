# Arquitectura y modelado de datos

**Revisión documental:** 2026-10-20. La documentación global está disponible completa en esta carpeta, junto con las referencias a sus refinamientos por épica.

## Documentos globales

- [Visión general de arquitectura](vision-general.md).
- [Arquitectura y modelo de datos](modelo-de-datos.md): documento global V4.2, con diccionario y diagramas originales.
- [Entidades](entidades.md): catálogo completo de atributos y restricciones del modelo base.
- [MER: modelo entidad-relación](modelo-entidad-relacion.md): diagramas Mermaid y cardinalidades del modelo base.

Los tres documentos detallados conservan la versión de modelado 2.1 del 20/09/2026. Están integrados como base global, no como certificación del esquema actualmente desplegado. Las advertencias de sus cabeceras identifican refinamientos posteriores; no se oculta el contenido detrás de un índice ni se reescribe el modelo sin validación.

## Refinamientos por dominio

| Dominio | Modelo | Requisitos |
| --- | --- | --- |
| Acceso, perfil y seguridad | [EP-APS](../../specs/001-ep-aps-acceso-perfil-seguridad/data-model.md) | [Spec](../../specs/001-ep-aps-acceso-perfil-seguridad/spec.md) |
| Cuentas y tarjetas | [EP-CTA](../../specs/002-ep-cta-cuentas-tarjetas/data-model.md) | [Spec](../../specs/002-ep-cta-cuentas-tarjetas/spec.md) |
| Categorías y comercios | [EP-CCO](../../specs/003-ep-cco-categorias-comercios/data-model.md) | [Spec](../../specs/003-ep-cco-categorias-comercios/spec.md) |
| Movimientos y ledger | [EP-MOV](../../specs/004-ep-mov-movimientos-ledger/data-model.md) | [Spec](../../specs/004-ep-mov-movimientos-ledger/spec.md) |
| Metas y reservas | [EP-MET](../../specs/006-ep-met-metas-reservas/data-model.md) | [Spec](../../specs/006-ep-met-metas-reservas/spec.md) |
| Recurrencias | [EP-REC](../../specs/007-ep-rec-recurrencias-obligaciones/data-model.md) | [Spec](../../specs/007-ep-rec-recurrencias-obligaciones/spec.md) |
| Notificaciones | [EP-NOT](../../specs/010-ep-not-notificaciones-avisos/data-model.md) | [Spec](../../specs/010-ep-not-notificaciones-avisos/spec.md) |
| Planes y monetización | [EP-PLA](../../specs/012-ep-pla-planes-monetizacion/data-model.md) | [Spec](../../specs/012-ep-pla-planes-monetizacion/spec.md) |
| Deudas y préstamos | [EP-DEU](../../specs/013-ep-deu-deudas-prestamos/data-model.md) | [Spec](../../specs/013-ep-deu-deudas-prestamos/spec.md) |

En este checkout no hay artefactos propios de EP-PRE, EP-INF o EP-AUT en `specs/`. Su alcance de base sigue en el [backlog](../producto/backlog.md). No inventar carpetas, columnas ni contratos para completar visualmente este índice.

## Diseño, implementación y despliegue no son lo mismo

- Room versionado: [KipuDatabase.kt](../../app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt), sus entidades y migraciones.
- Backend versionado: [migraciones](../../supabase/migrations/). Su presencia no significa que estén aplicadas en un entorno remoto.
- Refinamientos de metas: [decisión sobre datos heredados y gate de activación](../../specs/006-ep-met-metas-reservas/legacy-data-decision.md).
- Recurrencias: revisar los gates explícitos de [EP-REC](../../specs/007-ep-rec-recurrencias-obligaciones/spec.md) antes de cualquier ejecución de infraestructura o SQL. Esta reorganización no autoriza ninguna activación.

Se integran los documentos V4.2 de entidades y MER, pero no los de `Entidades antiguas/` ni diagramas Bizagi. Su versión y alcance siguen visibles; los originales permanecen intactos en `KipuApp`.
