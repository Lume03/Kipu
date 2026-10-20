# Arquitectura de Kipu

**Revisión documental:** 2026-10-20. Síntesis de los principios de la constitución, la especificación de producto y los artefactos por épica; no certifica implementación completa ni despliegue remoto.

## Principios

Kipu es Android nativo y local-first. La interfaz observa datos locales; una operación manual confirmada debe persistirse atómicamente sin depender de conectividad continua. El dominio financiero es independiente de la presentación y de proveedores externos.

| Límite | Responsabilidad | Referencia |
| --- | --- | --- |
| Presentación | Compose, ViewModels, estados y navegación; sin cálculos monetarios autoritativos | [UI/UX](../ux-ui/README.md) |
| Dominio | Dinero en unidades menores, validaciones, efectos y deduplicación | [Constitución](../../.specify/memory/constitution.md) |
| Persistencia local | Room, escritura atómica, revisiones y colas por propietario | [Modelo de datos](modelo-de-datos.md) |
| Sincronización | Comandos estables, reintentos, conflictos y separación por usuario | [Contratos EP-MOV](../../specs/004-ep-mov-movimientos-ledger/contracts/sync-and-deduplication.md) |
| Backend | Autorización, aislamiento y validación de operaciones privilegiadas | [Artefactos por épica](../../specs/) |
| Captura | Candidatos antes del ledger, evidencia mínima y confirmación obligatoria de crédito detectado | [Backlog HU-45–HU-51](../producto/backlog.md) |
| Planes y compras | Separar intención, compra, ciclo comercial y acceso efectivo verificado | [EP-PLA](../../specs/012-ep-pla-planes-monetizacion/spec.md) |

La captura, los presupuestos y las recurrencias no inventan hechos financieros. Una transferencia no genera ingreso/gasto; pagar crédito no vuelve a reconocer el gasto; las reservas de metas no duplican patrimonio. La pérdida de Premium no borra historia ni bloquea el núcleo manual previamente autenticado.

## Dónde consultar el detalle

- [Modelo de datos](modelo-de-datos.md), [entidades](entidades.md) y [MER](modelo-entidad-relacion.md): documentos globales completos de la base V4.2.
- [Refinamientos por épica](README.md): modelos y contratos incrementales mantenidos por dominio.
- [ADR](../adr/README.md): decisiones registradas; revisar su estado y alcance, no asumir aprobación por existir un archivo.
- [Especificación](../producto/especificacion.md): alcance y reglas generales.
- [Catálogo de versiones](../../gradle/libs.versions.toml), [configuración de app](../../app/build.gradle.kts) y [wrapper Gradle](../../gradle/wrapper/gradle-wrapper.properties): configuración versionada. Evitar otra tabla manual de versiones que se vuelva obsoleta.

El documento `KipuApp/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` se integra completo en el modelo global, conservando su versión de septiembre y sus diagramas. Su conteo de tablas y sus contratos no se presentan como una fotografía actual del despliegue. Ver [selección y diferencias](../control-documental.md).
