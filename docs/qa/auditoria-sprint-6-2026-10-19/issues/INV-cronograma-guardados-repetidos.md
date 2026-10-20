## Clasificación
- **Tipo:** investigación de interacción/idempotencia; no bug de duplicación financiera confirmado.
- **Prioridad:** P2 para aclarar el comportamiento de guardado de un plan de deuda; no se ha demostrado perjuicio financiero.
- **Fixture:** deuda histórica QA propia, principal PEN 100.00; no usar datos preexistentes.

## Observado por QA
En secuencias controladas, un doble tap rápido produjo una revisión adicional; cuatro taps consecutivos, sin esperar confirmación, produjeron dos mutaciones nuevas. La lectura final mostró una sola terna activa de tres cuotas que sumaba PEN 100.00. Tras relanzar, la proyección seguía siendo una terna. Un guardado posterior explícito creó otra revisión, consistente con reemplazar un plan terminado.

Snapshot local final reportado: rev. 9, deuda `ACTIVE`, principal PEN 100.00, tres cuotas `PENDING` por PEN 100.00 y 21 filas históricas `CANCELLED` por PEN 700.00. No cambió principal, pagos ni saldo visible durante las pruebas. El backend no se verificó. Las filas CANCELLED son historial y no se presentan como cuotas activas duplicadas.

## Pregunta pendiente
¿Dos mutaciones de la ráfaga corresponden a callbacks de una misma intención que se solaparon antes de completar, o a nuevos guardados aceptados después de completarse el anterior? El contrato permite reemplazar un plan y conservar historia; no se encontró regla inequívoca que prohíba una nueva confirmación intencional.

## Investigación necesaria
En una futura reproducción autorizada, correlacionar timestamps monotónicos de gesto/callback, estado `isSaving`, identidad/recuento de operación-recibo y revisión aceptada/completion. Sin esa temporalidad no atribuir causa ni incumplimiento. Mantener invariante una sola terna activa y principal total.

## Límites
No repetir taps sin obtener nueva observabilidad; no ejecutar SQL de escritura/RPC, no alterar planes ajenos ni afirmar sync remoto. Cerrar con evidencia de solapamiento/incumplimiento o disposición como comportamiento permitido/documentado.
