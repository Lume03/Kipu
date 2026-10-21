# Tareas — Correcciones en el celular

Actualizado: 21/10/2026. Rama `main`. Sin Spec Kit, nuevas ramas ni worktrees.

Este es el seguimiento principal. El [checklist inicial](../correcciones-celular-comprobantes-metas.md) conserva el registro de inicio. `[x]` significa completado; `[ ]` significa pendiente. Las pruebas reales están autorizadas por el propietario y usan nombres cortos.

## 1. Referencias y diagnóstico — COMPLETADO
- [x] Revisar `docs/DESIGN.md`, control documental y las referencias de Comprobantes y metas.
- [x] Conectar el Samsung S24+ y resolver el conflicto entre versiones de ADB.
- [x] Identificar los errores de validación, navegación, elegibilidad y doble cupo.
- [x] Crear el checklist por fases antes de implementar.

## 2. Navegación y barra inferior — COMPLETADO
- [x] Mantener visibles y alineadas las cuatro etiquetas; selección alrededor del icono.
- [x] Sustituir el icono de encendido por un objetivo en Planificación.
- [x] Hacer que + abra Nueva meta desde Planificación.
- [x] Evitar que restaurar una pestaña vuelva a abrir un historial secundario encima de ella.
- [x] Reutilizar la misma pantalla de Movimientos desde comprobantes y cuentas.
- [x] Verificar en la sesión real Cuenta → Movimientos → Planificación.
- [x] Verificar en la sesión real Comprobantes → Ver movimientos → Planificación.
- [x] Probar barra de navegación con texto ampliado y ancho de 360 dp.
- [x] Corregir la salida del alta de tarjeta: al ir al detalle se retira el formulario de creación del historial de navegación.

## 3. Comprobantes — COMPLETADO
- [x] Separar etiquetas de ejemplos y alinear Fecha/Hora en columnas iguales.
- [x] Usar calendario y reloj nativos; mostrar fecha como DD/MM/AAAA.
- [x] Distinguir gasto en rojo e ingreso en verde usando tokens semánticos.
- [x] Validar selección de dirección y apertura del calendario en el Samsung.
- [x] Revisar formulario y bandeja en la sesión real.
- [x] Descartar el borrador manual temporal utilizado para comprobar el flujo; conservar el pendiente original.

## 4. Metas — COMPLETADO localmente
- [x] Evitar errores rojos al abrir Nueva meta vacía.
- [x] Aplicar validación después de editar el campo.
- [x] Agrupar progreso, importes y acciones del detalle en una tarjeta.
- [x] Mostrar el respaldo en tarjetas por cuenta, con espacios y bordes consistentes.
- [x] Quitar el indicador final de las barras de progreso para evitar un punto engañoso en 0%.
- [x] Revisar capturas claras y oscuras; comprobar el detalle de la meta existente en el celular.
- [x] Ejecutar pruebas focalizadas del formulario, opciones, cupo y edición de metas.
- [x] Reservar S/10 de BBVA en la meta existente y liberarlos: patrimonio sin cambios y reserva final cero.
- [x] Excluir billeteras afiliadas del respaldo para ofrecer únicamente el saldo de la cuenta bancaria.

## 5. Cuentas, billeteras y saldo — IMPLEMENTADO y probado localmente
- [x] Permitir afiliación a cuenta bancaria sin exigir tarjeta ni cuatro últimos dígitos.
- [x] Compartir una identidad de cupo entre cuenta, débito y billetera afiliada.
- [x] Excluir billeteras afiliadas del subtotal bancario para no duplicar dinero.
- [x] Indicar «Saldo compartido» en las billeteras afiliadas.
- [x] Crear YAPE afiliada a BCP sin tarjeta: S/1,000 y cupo 2/4 se conservan.
- [x] Registrar gasto Transporte de S/10 en YAPE: banco y billetera pasan a S/990 una sola vez.
- [x] Crear BBVA con saldo inicial S/100 y sin cuatro dígitos.
- [x] Crear PLIN afiliada a BBVA: ambas muestran S/100; cupo sigue 3/4 y total S/1,090.
- [x] Crear tarjeta de crédito y cambiar su alias a VISA; línea S/1,000, cierre 15, pago 5, últimos dígitos 4242.
- [x] Verificar que VISA ocupa el cuarto cupo y que su línea no se suma al dinero propio.
- [x] Corregir el historial contextual de cuenta/billetera en Free: conservar el contexto sin exigir filtros Premium.
- [x] Pasar pruebas unitarias focalizadas de SQL, historial y ViewModel tras esa corrección.
- [x] Confirmar filtro YAPE en APK final: un movimiento del canal, sin saldos iniciales bancarios.
- [x] Anular gasto Transporte de S/10: BCP vuelve a S/1,000; anulación confirmada en servidor.
- [x] Registrar y anular ingreso de S/1 en PLIN: BBVA vuelve a S/100; anulación confirmada en servidor.

## 6. Sincronización descubierta durante pruebas reales — ENVÍOS CONFIRMADOS; PULL BLOQUEADO
- [x] Distinguir implementación local de contratos realmente desplegados.
- [x] Desplegar afiliación y cupo compartido; 9/9 pgTAP.
- [x] Desplegar registro V2 y canal Yape/Plin que faltaban en el servidor; 8/8 pgTAP.
- [x] Corregir la restricción que impedía emitir ARCHIVE al archivar tarjetas; 2/2 pgTAP.
- [x] Identificar cinco tarjetas antiguas remotas, invisibles localmente, que agotaban el cupo.
- [x] Archivar esas cinco tarjetas tras verificar que no tenían movimientos, con autorización del propietario.
- [x] Implementar recuperación puntual por propietario de envíos rechazados por los contratos anteriores, manteniendo sus identificadores de idempotencia.
- [x] Esperar a la sincronización de cuenta y afiliación antes de enviar un movimiento de billetera.
- [x] Probar en Room que la recuperación conserva payload/identidad y no afecta a otros propietarios ni errores ajenos.
- [x] Corroborar las cinco cuentas y dos tarjetas reenviadas al servidor.
- [x] Desplegar edición de crédito y persistencia de alias/dígitos: 12/12 pgTAP; alias VISA remoto, revisión 3.
- [x] Desplegar revisión/anulación: 42/42 pgTAP; corregir tipo de comando en la bandeja local.
- [x] Corregir serialización explícita de campos obligatorios nulos/default en revisión/anulación.
- [x] Renovar sesión expirada antes de enviar operaciones financieras; pruebas unitarias verdes.
- [x] Recuperar ediciones anteriores al alta enviadas con revisión cero; regresión focalizada verde.
- [x] Unificar restricción contable del servidor y cupo compartido; contrato de presentación idempotente: 14/14 pgTAP.
- [x] Evitar que un nuevo movimiento espere el backoff de un envío antiguo; preservar comandos Room e idempotencia.
- [x] Confirmar una sola transacción por canal y neto contable cero tras ambas anulaciones reales.
- [x] Inspeccionar una última vez las colas y el checkpoint: instrumentos 11 SYNCED, movimientos 4 SYNCED, sin pendientes en esas dos colas; checkpoint de descarga ausente. El servidor entrega CATEGORY y saltos de secuencia incompatibles con el consumidor financiero; no se certifica sincronización completa entre dispositivos.

## 7. Verificación y entrega — COMPLETADO para el alcance verificado
- [x] Compilar e instalar APK debug sin borrar los datos del usuario.
- [x] Ejecutar pruebas unitarias focalizadas de cupo, cuentas, tarjetas, comprobantes e historial.
- [x] Pasar 16 pruebas focalizadas de afiliación, metas y navegación.
- [x] Pasar 7 pruebas nuevas de UI, incluidas capturas claras/oscuras y texto ampliado.
- [x] Pasar pruebas de Room de recuperación y leasing.
- [x] Cerrar los tres fallos focalizados: completar fixtures y corregir despacho real de revisión/anulación; casos afectados verdes.
- [x] Compilar e instalar APK final; compilación y pruebas HTTP finales verdes.
- [x] Revisar el diff final y corregir el caso de recuperación omitida cuando el alta aún estaba pendiente: regresión reproducida y 2 pruebas focalizadas verdes; nueva compilación e instalación sin borrar datos.
- [x] Actualizar este archivo y escribir [verification-report.md](verification-report.md) con resultados y límites reales; corregir tildes dañadas del checklist inicial.
- [x] Crear commits convencionales por capas en `main`, sin incorporar otros worktrees ni archivos ajenos; hashes en la entrega final.
- [x] Entregar informe breve, archivos de seguimiento y detalle de los datos de prueba que permanecen, recogidos en el informe y la respuesta final.

## 8. Ajustes visuales solicitados después del cierre — COMPLETADO

Solicitud del propietario mediante diez capturas el 21/10/2026; trabajo directo en `main`, sin nuevos worktrees, Spec Kit ni subagentes.

- [x] Mostrar duplicados confirmados en amarillo, con título, operación, importe/fecha disponibles y botón de cierre; distinguir un comprobante descartado de uno registrado.
- [x] Unificar el aviso al revisar comprobantes y resumirlo; evitar mostrar simultáneamente el resultado de OCR y la misma instrucción repetida.
- [x] Quitar «Metas y reservas» del dashboard y usar amarillo en «Mejorar a PRO».
- [x] Pedir teclado decimal en aportes, mantener rechazo de letras y permitir desplazar el formulario con el teclado abierto.
- [x] Simplificar capacidad de metas, contador y acceso a Pro; quitar mensajes rutinarios de guardado local/sincronización de la lista, conservando conflictos/errores que requieren revisión.
- [x] Permitir elegir entre ocho iconos al crear/editar; guardar `iconKey` con la meta y su comando, y usarlo en tarjetas/opciones.
- [x] Separar las metas archivadas al final, contraídas inicialmente y desplegables mediante flecha, sin cambiar el cupo de activas.
- [x] Verificar 12 unitarias y 23 instrumentadas focalizadas; revisión visual de capturas claras/oscuras. Tras los últimos ajustes de texto/accesibilidad, pasar 3 casos afectados, sin repetir toda la suite.
- [x] Compilar debug/lab/androidTest e instalar la APK final con `-r`, conservando datos; actualizar informe y commits del alcance.

Los importes/datos del apartado «Estado actual de los datos de prueba» corresponden al recorrido anterior. Las capturas posteriores del propietario muestran nuevas operaciones y reservas; esta ampliación no restablece aquellos importes ni modifica sus metas para recrear el fixture.

## 9. Categorías y fechas de deudas — COMPLETADO

Solicitud posterior del propietario el 21/10/2026: referencias de Categorías y campo de fecha de ambas altas de deuda. Se autoriza explícitamente push directo a `main` con commits separados y coherentes.

- [x] Contraer categorías al entrar y al cambiar entre Gastos/Ingresos.
- [x] Compactar tarjetas y cupo de la pestaña seleccionada; Gastos en coral e Ingresos en verde, con metadatos de categoría personalizada/predeterminada y uso de cupo.
- [x] Conservar las líneas laterales de las subcategorías; resaltar coincidencias de búsqueda y permitir limpiar el filtro.
- [x] Agrupar inactivas en una sección plegable al final y conectar Reactivar con la acción existente.
- [x] Abrir calendario al tocar cualquier parte de la fecha en Registrar deuda y Registrar dinero prestado; mostrar DD/MM/AAAA y conservar selección, cancelación, borrado opcional e ISO interno.
- [x] Reproducir las tres regresiones antes de corregir; verificar 27 casos instrumentados focalizados, sin repetir los que ya estaban verdes. El único fallo intermedio era una expectativa antigua de texto/expansión; el caso actualizado y dos casos visuales afectados pasaron después.
- [x] Revisar capturas de categorías y formularios/calendarios claros/oscuros; compilar debug/lab/androidTest e instalar con `-r`, conservando datos.
- [x] Crear commits por Categorías, fechas de deuda y documentación; publicar los commits pendientes de esta entrega en `origin/main`, excluyendo referencias originales, worktrees y archivos privados.

## Seguimiento abierto fuera de la aprobación acotada

- [ ] Reconciliar el contrato de descarga financiera y el flujo remoto: CATEGORY desde secuencia 1 y salto 16 → 23 impiden aplicar la primera página. No adelantar manualmente el cursor ni omitir eventos desconocidos. Validar bootstrap/otro dispositivo después de resolverlo.
- [ ] Resolver y verificar el pendiente de preferencias y el conflicto de selección de cupos antes de certificar esos dominios (1 PENDING y 1 CONFLICT, respectivamente).
- [ ] Validar metas remotas cuando se active su configuración: actualmente 3 comandos PENDING y ningún checkpoint; las reservas solo están verificadas localmente.

## Estado actual de los datos de prueba

| Elemento | Estado |
| --- | --- |
| YAPE | Creada y afiliada a BCP sin tarjeta; saldo compartido |
| BBVA | Cuenta nueva, saldo inicial S/100, sin tarjeta |
| PLIN | Creada y afiliada a BBVA sin tarjeta |
| VISA | Crédito S/1,000; alias VISA confirmado remoto, revisión 3 |
| Transporte S/10 | Anulado; saldo bancario restituido a S/1,000 y total a S/1,100 |
| Cinco tarjetas antiguas remotas | Archivadas; no tenían movimientos |
| Ingreso PLIN S/1 | Anulado y sincronizado; BBVA y PLIN vuelven a S/100 |
| Meta existente | Conservada; reserva S/10 y liberación completadas, reserva final S/0 |

## Límites del cierre

Los envíos de instrumentos y movimientos sí están confirmados, pero la ausencia del checkpoint impide afirmar descarga completa. Véase el [informe](verification-report.md) para la causa y las demás colas pendientes. Las capturas de la sesión y las bases locales se mantienen fuera del contenido versionado. La sincronización remota de metas está desactivada por la configuración actual de compilación; debe distinguirse del funcionamiento local de reservas.
