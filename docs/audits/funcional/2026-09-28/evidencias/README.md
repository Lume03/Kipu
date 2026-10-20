# Evidencias de la auditoría funcional — 28/09/2026

Se conservan los nombres y bytes originales de capturas, jerarquías, diagnósticos y snapshots. La agrupación sigue los números de recorrido del [informe](../informe.md), no modifica sus resultados ni certifica cierre o privacidad de toda la evidencia antigua.

| Recorrido | Carpeta |
| --- | --- |
| 000–029: inicio, pagos e historial | [01-inicio-pagos-historial](01-inicio-pagos-historial/) |
| 030–057: compras con crédito y cuotas | [02-compras-credito-cuotas](02-compras-credito-cuotas/) |
| 058–085: validación y recuperación de pagos | [03-validacion-pagos](03-validacion-pagos/) |
| 086–126: instrumentos, duplicados y archivo | [04-instrumentos-duplicados-archivo](04-instrumentos-duplicados-archivo/) |
| 127–149: transferencias | [05-transferencias](05-transferencias/) |
| 150–184: cuentas y saldo inicial | [06-cuentas-saldo-inicial](06-cuentas-saldo-inicial/) |
| 185–200: privacidad | [07-privacidad](07-privacidad/) |
| 201–226: productos y tasas | [08-productos-tasas](08-productos-tasas/) |
| 227–280: umbrales de crédito | [09-umbrales-credito](09-umbrales-credito/) |
| 281 en adelante: cierre y diagnóstico | [10-cierre-diagnostico](10-cierre-diagnostico/) |

Dentro de cada recorrido, `capturas/` contiene las imágenes distribuidas y `diagnosticos/` los resúmenes de texto. Los snapshots SQLite (`.db`, WAL y SHM) se conservan solo bajo `_local-only/snapshots-room/`, excluidos de Git y de la documentación distribuida por privacidad. Se retienen solo cuatro volcados XML de jerarquía, porque son los citados por el informe: `08-productos-tasas/jerarquias/219-archive-reactivated-dialog.xml`, `224-credit-form-state.xml`, y `09-umbrales-credito/jerarquias/249-hu10-purchase-result.xml`, `251-hu10-notifications-screen.xml`. Se eliminaron los demás XML de recorridos al no estar referenciados.

Los once archivos sin número de recorrido quedan en `_local-only/sin-clasificar/`, ignorados por Git como antes de esta reorganización. No se eliminan ni se publican como evidencia aprobada.

**Privacidad:** los snapshots SQLite se retiran de la documentación versionada y quedan locales ignorados. Esta reorganización no elimina su presencia del historial previo de Git; no distribuir bases, WAL o volcados sin una revisión específica. Las reglas para nuevas ejecuciones están en [QA](../../../../qa/README.md).
