# Quickstart de validación futura — EP-INF

**Importante**: guía para la futura implementación/aceptación; en la tarea actual no se ejecutan builds, tests de app, migraciones ni despliegues. Los pasos son casos de verificación requeridos, no evidencia de que ya se hayan corrido.

## Preparación

1. Revisar gates abiertos en `spec.md`; no iniciar implementación hasta aprobación de diseño, contratos de consulta, métricas y acceso.
2. Confirmar dependencias de HU-39/40 disponibles; HU-41 solo cuando sus dependencias S9 estén aceptadas.
3. Usar únicamente fixtures sintéticos y cuenta de prueba aislada; no incorporar datos financieros reales a logs ni datasets versionados.
4. Confirmar con android-core los modelos/UiState, eventos y casos de uso existentes, y con propietarios de módulos las rutas destino.

## Escenarios de aceptación HU-39 (S7)

- Fixture: activos reales 100000 céntimos PEN; por cobrar 20000; pasivos pendientes 30000; reserva incluida dentro de esos activos; línea disponible sin efecto patrimonial. Esperado patrimonio PEN 90000 céntimos, sin sumar reservas/crédito.
- Cuenta nueva: lista vacía y acción registrar; distinguir loading, error y cero confirmado.
- Free: navegar meses anteriores y abrir/corregir un movimiento accesible.
- Activar máscara y verificar visualmente importes ocultos en componentes de Inicio sin cambio de datos.
- Aplicar reembolso al movimiento fuente con el tratamiento de fecha establecido por HU-25; revisar consistencia de métricas.

## Escenarios HU-40 (S7)

- Mes con eventos reales y previstos de tarjeta/deuda/recurrencia: ambos tipos distinguibles y fechas correctas.
- Abrir cuota y verificar destino de deuda/plan de cuotas que originó el evento; destino no disponible debe tratarse según contrato aprobado, no inventarse.
- Dos eventos mismo día: aparecen ambos y navegación independiente.
- Cuenta Free consulta periodo pasado; ningún evento dispara cobro ni crea movimiento.

## Escenarios HU-41 (S9 futuro)

- Gasto 10000 céntimos y transferencia 20000: gasto operativo 10000.
- Free actual: solo resumen básico del periodo actual; histórico agregado/comparación Premium requiere entitlement efectivo verificado y alternativa de historia básica.
- Periodo base cero no divide; mes abierto se etiqueta parcial con fechas.
- Revisar patrimonio con receivable, deuda, reserva y crédito usado sin sumar representaciones redundantes.

## Evidencias futuras

Registrar versión/fixture, plataforma/estado de entitlement, resultado esperado/observado y evidencia accesible sin PII. Complementar con pruebas de dominio, UI/accessibility y rutas; confirmar offline y aislamiento según la frontera donde puede fallar. No marcar esta guía como ejecutada hasta que exista evidencia real y revisión correspondiente.
