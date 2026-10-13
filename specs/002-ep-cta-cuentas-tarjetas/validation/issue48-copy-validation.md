# Issue #48 — Matriz y validación de copy

Issue: https://github.com/Lume03/Kipu/issues/48
Base normativa: EP-CTA (SC-004, SC-009, SC-013, SC-015), el contrato UI EP-CTA Sprint 3, EP-MOV y EP-PLA.

## Matriz de términos aplicada

| Concepto | Copy acordado para la interfaz | Dónde se usa | Regla / estado ilustrativo |
|---|---|---|---|
| Dinero propio líquido | **Dinero real disponible**; explicación: «Dinero propio en cuentas y efectivo · No incluye crédito» | Dashboard, estado con y sin instrumentos | Saldos PEN/USD separados; no incluir crédito ni sumar monedas distintas. El vacío invita a agregar una cuenta o efectivo. |
| Dinero de una cuenta | **Saldo** / **Disponible** | Fila de cuenta en Dashboard y selector de movimientos | Representa saldo de una cuenta propia; no se usa para capacidad de tarjeta. |
| Deuda de tarjeta | **Deuda actual**; **Deuda pendiente** solo en estados archivados/pendientes | Resumen y detalle de tarjeta, selector de movimientos | La deuda confirmada es pasivo. Pagarla no genera otro gasto. |
| Límite de crédito | **Línea total de crédito** | Configuración, edición, resumen y detalle | Es el límite autorizado por el emisor; no es un activo ni dinero disponible en cuentas. |
| Capacidad no utilizada | **Crédito disponible, no es dinero propio** | Dashboard, resumen/detalle de crédito, confirmación de compra y selector | Capacidad de endeudamiento; nunca se suma al dinero real disponible. |
| Límite del plan | **Cupo Free de instrumentos** | Dashboard, formulario de cuenta y formulario de tarjeta | El conteo numérico es de instrumentos computables (cuentas/tarjetas), no de efectivo. El estado completo indica que no quedan cupos. |
| Tipos principales | **Gasto**, **Ingreso**, **Transferencia** | Registro y filtros de movimientos | Una transferencia entre cuentas propias no es ingreso ni gasto. Se conservan los labels y contratos existentes. |
| Pago de tarjeta | **Pago de tarjeta** / **abono a la deuda** en contexto de crédito | Historial y flujo especializado | Reduce efectivo y principal; no agrega un segundo gasto. No se recategoriza como transferencia genérica en copy de tipo. |

## Antes → Después (copy de interfaz)

| Pantalla/elemento | Antes | Después |
|---|---|---|
| Dashboard, encabezado (vacío y con saldos) | `TOTAL DISPONIBLE` | `DINERO REAL DISPONIBLE` |
| Dashboard, aclaración del total | `Activos líquidos reales · Excluye líneas de crédito` | `Dinero propio en cuentas y efectivo · No incluye crédito` |
| Dashboard, encabezado de tarjetas | `Líneas de crédito (no suman al saldo disponible)` | `Crédito disponible, no es dinero propio` |
| Dashboard, cupo (vacío y con instrumentos) | `Cupo Free · n de 4 cuentas y tarjetas` | `Cupo Free de instrumentos · n de 4` |
| Dashboard, espacios restantes / lleno | `n cuentas y tarjetas disponibles` / `Cupo de cuentas y tarjetas completo` | `n espacios para cuentas y tarjetas` / `Cupo de instrumentos completo` |
| Formulario de cuenta | `Esta cuenta consumirá 1 cupo de los 4 permitidos en el plan Free.` | `Esta cuenta ocupa 1 de los 4 cupos para instrumentos de Kipu Free.` |
| Formulario de tarjeta | `Esta tarjeta consumirá 1 cupo de los 4 permitidos en el plan Free.` | `Esta tarjeta ocupa 1 de los 4 cupos para instrumentos de Kipu Free.` |
| Alta/detalle de tarjeta, límite | `Línea total` / `Línea:` | `Línea total de crédito` / `Línea total de crédito:` |
| Detalle de tarjeta, capacidad restante | `Línea liberada` / `Disponible de crédito` / `Crédito disponible`; se mostraba además `Línea total` bajo el monto disponible | `Crédito disponible, no es dinero propio`; se retiró la etiqueta `Línea total` que no correspondía a ese monto |
| Resumen de tarjeta | `Disponible en línea` | `Crédito disponible, no es dinero propio` |
| Selector de tarjeta en movimientos | `Disp:` / descripción accesible `Disponible:` | `Créd. disp.:` / `Crédito disponible, no es dinero propio:` |

Los pares de texto anteriores ayudan a leer el diff; las capturas comparativas renderizadas a continuación muestran esos fragmentos en Compose.

## Capturas comparativas de los fragmentos de copy

Capturadas desde Compose en el mismo Pixel 8 AVD (API 36, viewport 1080×2400, densidad 420 dpi), tema claro y escala predeterminada. Los importes/estados son idénticos en ambas imágenes. Son capturas de fragmentos comparativos de copy, no pantallas completas de los recorridos de la aplicación.

### Antes

![Antes: etiquetas originales](issue48-before.png)

### Después

![Después: etiquetas unificadas](issue48-after.png)

## Revisión de estados

- **Dashboard vacío:** mantiene el monto de PEN en cero y el CTA existente; el encabezado identifica que se trata de dinero propio disponible.
- **Dashboard con saldo:** mantiene PEN y USD separados; la explicación debajo del monto excluye crédito explícitamente.
- **Sin tarjetas:** el copy existente de estado vacío invita a agregar una tarjeta; no atribuye valor a crédito no registrado.
- **Cuenta en selector:** «Saldo disponible» describe solo el saldo de esa cuenta.
- **Tarjeta en selector:** el crédito disponible lleva la aclaración explícita «no es dinero propio» y la deuda se presenta por separado.
- **Movimiento de pago/transferencia:** sin cambios en la naturaleza contractual; pagos siguen siendo amortización y transferencias entre cuentas propias conservan ambos extremos.

## Evidencia pendiente antes de cerrar

### Revisión de producto

Estado: **aprobada por el responsable de producto en esta sesión**. La aprobación de revisión de código queda sujeta al reviewer del PR.

### Capturas Antes/Después

Estado: **pendiente**. No se adjuntan mockups como si fueran capturas de la aplicación. La compilación e instalación en Pixel 8 (AVD, API 36, 1080×2400, 420 dpi) fueron posibles, pero el arranque llega a la pantalla de autenticación y no hay una sesión de prueba disponible para abrir Dashboard, instrumentos y movimientos con los mismos datos. Para producir evidencia comparable hace falta una cuenta/sesión de QA y registrar el tema y escala de texto usados. Capturar al menos Dashboard (vacío y con datos), formulario/detalle de tarjeta y selector/registro de movimientos, antes y después, con el mismo dispositivo/viewport/datos.

### Prueba breve de comprensión — SC-013 EP-CTA

Estado: **pendiente de participantes**. Protocolo propuesto: reclutar 10 participantes que representen el perfil definido por Kipu, mostrarles las pantallas finales sin explicar el vocabulario y hacer estas preguntas abiertas, sin pistas:

1. «¿El crédito disponible es dinero que ya tienes en tus cuentas? ¿Por qué?»
2. «Si registras una compra con la tarjeta, ¿qué efecto tiene en tus gastos?»
3. «Cuando pagas la tarjeta desde tu cuenta, ¿se registra un gasto nuevo además de la compra?»

Registrar primera respuesta por pregunta, sin corregir hasta completar las tres. Criterio: al menos 9 de 10 identifican correctamente en el primer intento (a) que el crédito disponible no es dinero propio, (b) que la compra genera el gasto y (c) que pagar la tarjeta no genera otro gasto. Si el resultado no alcanza el umbral, revisar el copy y repetir la prueba. No se inventan resultados: esta evidencia requiere participantes humanos.

| Participante | Crédito no propio | Compra genera gasto | Pago no duplica gasto | Observaciones |
|---|---|---|---|---|
| P01–P10 | Pendiente | Pendiente | Pendiente | Registrar respuestas textuales y resultado de primer intento. |
