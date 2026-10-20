## Clasificación
- **Tipo:** aclaración de regla de producto/documentación y copy; no bug de cuota confirmado.
- **Estado/frecuencia:** una secuencia QA de alta/archivo observada.
- **Prioridad:** P3 / baja. No se bloqueó una operación ni se excedió un límite observado.
- **Alcance:** cupo Free de instrumentos, cuentas de efectivo y tarjetas.

## Observación
Con contador Free `3/4`, QA creó una cuenta de efectivo propia con saldo PEN 0.00 y luego la archivó. La sección de cuentas mostró 5 y luego 4 activos, mientras el contador Free permaneció `3/4` y mostró un espacio disponible. Las dos capturas adjuntas son SAFE y muestran estados QA; no establecen la regla contractual ni sincronización.

## Incertidumbre / deduplicación
No se encontró un oráculo concluyente sobre si efectivo cuenta dentro de «cuentas y tarjetas». El issue cerrado #48 trata consistencia de términos financieros; se registra aquí la pregunta más concreta sobre qué tipos consumen cupo, no una duplicación de su propuesta general. No se afirma que el contador sea incorrecto.

## Propuesta
Product debe definir los tipos incluidos, efecto de archivar/reactivar y relación con el contador de instrumentos; documentar el contrato y alinear el texto visible.

## Criterios para cerrar
- La regla identifica explícitamente qué tipos consumen cupo y cuándo se libera.
- El contador y los límites del producto coinciden con esa regla.
- Prueba QA controlada de cuenta de efectivo y tarjeta, sin eliminar ni modificar instrumentos ajenos.

El resultado observado no prueba incumplimiento de entitlement ni cuotas remotas.
