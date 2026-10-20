## Clasificación
- **Tipo:** investigación/mejora de feedback y prevención de guardados no intencionales; no bug financiero confirmado.
- **Prioridad sugerida:** P2 por la posible confusión al reemplazar un plan; no se demostró duplicación de principal/pagos ni daño financiero.
- **Relación:** complementa la investigación técnica de taps rápidos en [#69](https://github.com/Lume03/Kipu/issues/69), sin duplicarla.

## Observado / reportado
El Owner informa que al guardar el cronograma no percibió una confirmación de éxito y volvió a pulsar, produciendo varias revisiones/historial de cuotas canceladas. La captura adjunta muestra nueve filas `Cuota cancelada` y tres `Pendiente de pago`; es un recorte de contexto y **no prueba por sí solo** que faltara un mensaje ni la secuencia de taps. La auditoría previa informó que el plan activo seguía siendo una terna por el principal y no observó duplicación financiera. Las filas canceladas son historial, no cuotas activas.

## Nota de discrepancia con el checkout
El fuente inspeccionado actualmente deshabilita el botón mientras `isSaving` (`DebtScheduleScreen.kt`) y muestra un mensaje inline desde `successMessage`; `DebtScheduleViewModel` define mensajes de guardado local/remoto pendiente. El reporte de falta de feedback podría corresponder a visibilidad/posición/duración, a que no se vio en el APK usado o a diferencia de artefacto. Reproducir y registrar el resultado real antes de declarar ausente el mecanismo.

## Propuesta
Hacer evidente y persistente el éxito del guardado, incluyendo si quedó local pendiente de sincronización; si ya existe un plan activo y un nuevo guardado lo reemplaza, pedir confirmación clara o explicar el efecto antes de aplicarlo. Mantener el bloqueo mientras se guarda; no duplicar una confirmación modal innecesaria si una señal inline accesible es suficiente.

## Criterios de aceptación
- En el APK identificado, cada guardado produce una señal de resultado visible y anunciable (guardado local/pendiente o confirmado remoto solo con señal real).
- Mientras se procesa, no se aceptan taps adicionales.
- Reemplazar un cronograma activo comunica que sus cuotas vigentes pasarán a historial cancelado y requiere intención inequívoca.
- Repetir guardado sin cambios no crea revisiones nuevas, o se explica/confirmar la operación si el contrato permite reemplazos.
- QA registra taps, respuesta y filas activas/históricas; una terna activa suma el principal pendiente y no aparecen efectos duplicados.

No se propone eliminar historial `CANCELLED` ni inferir un bug de sincronización.
