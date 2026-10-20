## Clasificación
- **Tipo:** mejora de navegación/usabilidad observada; no bug financiero ni fallo de archivo confirmado.
- **Estado/frecuencia:** recorrido observado por QA en 1 secuencia; no repetido para evitar crear otra tarjeta innecesaria.
- **Prioridad:** P3 / baja. Puede generar duda sobre si terminó la operación; no se observó duplicación, compra, pago ni pérdida de datos.
- **Entorno:** Kipu Android 0.1.0, `versionCode=1`, Samsung SM-A165M / Android 16. Fixture ficticio `QA20261020CARD1`, sin consumos.

## Pasos observados
1. Desde el flujo de alta, abrir el detalle de una tarjeta QA propia sin consumos.
2. Elegir **Archivar tarjeta** y confirmar el diálogo.
3. Observar el destino; luego pulsar Atrás y consultar los estados activo/archivado.

## Resultado
Tras confirmar, la app mostró «Configura tu tarjeta · Paso 3 de 3». Atrás llevó a «Qué tarjeta tienes? · Paso 2 de 3», con búsqueda vacía y «No encontramos «» en Otra entidad». QA comprobó después que la tarjeta dejó de aparecer entre activas y que el contador de archivadas cambió: **el archivo sí se observó efectivo**. No se confirmó recreación ni error financiero. La sincronización remota no se verificó.

Las capturas adjuntas documentan los pasos visibles; la acción previa y el orden proceden del registro QA. La captura del contador global de archivados se omite porque su privacidad no quedó aprobada.

## Impacto e hipótesis
El destino inesperado puede hacer dudar del resultado o sugerir que se debe completar/guardar otra vez. La duplicación es solo un riesgo hipotético, no observada. Una pila de navegación que conserva el wizard es una hipótesis; causa y repetibilidad pendientes.

## Cambio sugerido
Después de archivar, mostrar confirmación y navegar a una lista/destino coherente con el nuevo estado, retirando del back stack el wizard concluido si corresponde al contrato de navegación.

## Criterios de aceptación
- La tarjeta propia deja de figurar como activa y conserva historial/estado archivado.
- El resultado se comunica sin invitar a volver a guardar la tarjeta.
- Atrás no presenta como pendiente el wizard ya concluido.
- No se crea otra tarjeta ni aparecen efectos financieros al volver.
- Verificar con fixture sin compras, consumos ni pagos externos.

**No se presenta como bug confirmado.** No se implementó ni se volvió a probar; la auditoría observó una sola secuencia.
