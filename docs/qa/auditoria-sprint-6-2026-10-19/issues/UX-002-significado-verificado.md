## Clasificación
- **Tipo:** definición de producto/microcopy y claridad de estados; no bug de sincronización confirmado.
- **Estado/frecuencia:** observación en un movimiento QA (`QA20261020MOVCTAA`).
- **Prioridad:** P3 / baja; no se ha demostrado que haya inducido una decisión errónea.
- **Alcance:** detalle de movimiento Android 0.1.0; evidencia sanitizada QA.

## Observado
El detalle muestra la etiqueta `Verificado` y un comprobante «Pendiente de adjuntar». En la sesión QA se informó que el registro estaba guardado localmente y pendiente de sincronización. La captura adjunta muestra solo las etiquetas visibles; no prueba el estado del servidor ni el significado contractual de `Verificado`.

## Pregunta pendiente
¿`Verificado` significa validación local, comprobante, revisión de datos u otra cosa? No debe interpretarse como sincronización remota sin una definición y señal verificable.

## Propuesta
Documentar el significado de cada estado. Si validación, comprobante y sincronización son conceptos independientes, representarlos con etiquetas distintas y mostrar remoto solo cuando exista confirmación observable.

## Criterios de aceptación
- `Verificado` tiene definición de producto documentada.
- La UI no implica éxito remoto a partir de una validación local.
- Comprobante y sincronización se distinguen cuando sean estados distintos.
- QA verifica estados pendiente/confirmado solo con señales autorizadas; no se fuerzan reintentos.

No se afirma que exista error contable o remoto; la semántica actual queda por definir.
