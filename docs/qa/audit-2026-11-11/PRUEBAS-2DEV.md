# Pruebas en dos emuladores — 2026-11-14

**Veredicto: NO APROBADO para el flujo completo entre dispositivos.** Se ejecutaron los doce escenarios; el conflicto no se puede resolver desde su acción y el reembolso hidratado no reduce la deuda mostrada en B. Varias descargas requirieron cerrar y abrir el proceso de la app.

## Entorno y método

- Rama `Lume03/audit-fixes`, HEAD `0326c13c`. Sin cambios de producción, commits, push ni consultas SQL/MCP a Supabase.
- A: `emulator-5554`; B: `emulator-5556`, dos instancias de Pixel_10 / Android 37. No se operó sobre el A16.
- APK debug instalada con `adb install -r` en ambos. `:app:assembleDebug`: **BUILD SUCCESSFUL**, 43 tareas al día. SHA-256 de la APK: `9cd7157b37e034056986f970fc117c7240dd9a3412cce8d5fa549394f0f7b515`.
- Cuenta QA Free indicada en el encargo; inicio de sesión mediante la UI. No se concedieron permisos adicionales.
- Todas las mutaciones de negocio se hicieron mediante la app: UI Automator, taps y entrada de texto. Se usó `cmd connectivity airplane-mode` para los tramos offline.
- Un consumidor externo reiniciaba `C:\Windows\adb.exe` v40, incompatible con el adb v41 del SDK. Se aisló el servidor del SDK con `-P 5038`, conectando los transportes locales `localhost:5555` (A) y `localhost:5557` (B). Cada llamada tuvo `-s` explícito. El coordinador autorizó el aislamiento.
- Ambos AVD devolvieron el mismo Android ID. Se conserva como condición del entorno; **no se acreditó que cause ningún hallazgo**.
- Evidencia externa: `C:\Users\Alume\AppData\Local\Temp\kipu-2dev-2026-11-14\` (en adelante **E**). Cada captura citada tiene un XML homónimo. `events.txt` contiene los hitos medidos; `assembleDebug.txt`, el build.
- Horas del informe en America/Lima. Logcat y el reloj de los emuladores muestran UTC. Los tiempos de la tabla son **cotas de observación**, incluyen navegación/arranque y no representan latencia de red aislada. Cuando se indica reinicio, fue `am force-stop` + apertura de la app, sin borrar datos ni reinstalar.
- Se desactivó temporalmente `animator_duration_scale` para facilitar los dumps; al cierre se restauró a 1. Hubo arranques lentos y dumps fallidos; se descartaron los XML anteriores cuando no hubo un dump válido.

## Resultados

Los nombres de evidencia de esta tabla son archivos dentro de **E**. Los resultados con reinicio **no acreditan actualización automática con ambos procesos abiertos**.

| Escenario | Resultado A | Resultado B | Evidencia en E | Tiempo de sync observado |
|---|---|---|---|---|
| 1. Restauración inicial | Cuenta `QA2-ahorro`, apertura S/ 100; tarjeta `QA2-tarjeta`, línea S/ 1.000; gastos S/ 1, 2 y 3. Saldo S/ 94. | Primer login restauró cuenta, tarjeta y los tres gastos; saldo S/ 94 y deuda S/ 0. **PASS**. | `01-A-initial-dashboard.png`, `01-B-restored.png`, `01-B-movements.png` | ≤25 s desde el comienzo del login de B hasta la primera comprobación. |
| 2. Corrección de apertura L1 | Dos correcciones: 200 y 300. Disponible S/ 294 = 300 − 6. Hechos de reversión/ajuste en historial. | Permaneció en S/ 94 al navegar y volver desde Inicio; tras reiniciar recibió S/ 294. Al cierre, apertura S/ 300. Sin duplicación financiera observada. **PASS con reinicio; falla de actualización normal**. | `02-A-opening300.png`, `02-03-B-after-write.png`, `02-03-B-relaunch.png`, `final-A-opening-label.png` | A comprobado 17:01:09; B aún atrasado 17:05:52; convergencia comprobada 17:07:31 tras reinicio. |
| 3. Compra en tres cuotas y eliminación L2 | Compra S/ 9, 3 cuotas sin interés; anulación admitida sin pagos aplicados. Se repitió el alta para observar ambos estados en B. | Recibió deuda S/ 9; tras anular en A y reiniciar B volvió a S/ 0 y línea S/ 1.000. **PASS financiero con reinicio**. El registro permanece marcado `Anulado`, no desaparece físicamente del historial. | `03-A-purchase.png`, `03-B-purchase9.png`, `03-A-deleted.png`, `03-B-deleted-zero.png` | Alta ≤11 s; anulación ≤11 s, ambas con reinicio receptor. |
| 4. Compra S/ 4, reembolso S/ 1, historial y anulación | Compra directa S/ 4; reembolso S/ 1 deja deuda local S/ 3. Tras anular en B, A volvió a S/ 4. | Recibió el reembolso `Sincronizado` y su historial cargó, pero siguió mostrando deuda S/ 4, no S/ 3. Anulación del reembolso admitida. **FAIL deuda hidratada; PASS historial y reversión**. | `04-A-stable-debt3.png`, `04-B-refund-synced-detail.png`, `04-B-refund-history-final.png`, `04-B-refund-debt4-stable.png`, `final-A-dashboard.png`, `final-B-dashboard.png` | Repetición: reembolso creado 17:35:21; B seguía en S/ 4 a las 17:37:20 (118 s), con el reembolso sincronizado. Tras anularlo, A volvió a S/ 4 con reinicio. |
| 5. Dos ediciones y dos altas offline L3/L4 | En modo avión: `QA2-m1` pasó de 1 a 11 y después 12; gastos nuevos 5 y 6. Reconexión sin rechazo de estos comandos. | Recibió S/ 12, 5 y 6; movimiento S/ 12 `Sincronizado`, saldo S/ 272. **PASS con reinicio receptor**. | `05-A-offline.png`, `05-A-reconnected.png`, `05-B-received.png`, `05-B-m1-status.png` | Reconexión 17:15:52; altas visibles en B en ≤50 s y detalle sincronizado en ≤61 s. |
| 6. Conflicto real, decisión 3 | Ambos partieron de S/ 12. Offline A editó a 21 y B a 22. A conectó primero y quedó `Sincronizado` con S/ 21. | Al conectar segundo, mostró `No sincronizado`; la proyección visible ya era 21. `Usar la versión del servidor` falló dos veces y el aviso persistió hasta el cierre. **FAIL resolución**. | `06-A-first-synced.png`, `06-B-actions.png`, `06-B-resolution-failed.png`, `06-B-resolve-retry-failed.png`, `final-B-unresolved-conflict.png`, `06-B-conflict-logcat.txt` | A sincronizado ≤15 s tras conectar; aviso de B ≤7 s. Segundo intento de resolución fallido unos 50 s después del primero. |
| 7. Categorías, último escritor | Renombró Salud a `QA2-Salud-A`. Más adelante recibió el nombre de B. | Guardó después `QA2-Salud-B`; ambos terminaron con ese nombre, sin pantalla de conflicto. **PASS eventual, con arranques intermedios**. | `07-A-category.png`, `07-B-category.png`, `07-A-final-category.png` | B comprobado 17:05:45; A comprobado 17:18:41. No se muestreó el instante exacto de convergencia. |
| 8. Regla comercio→categoría CAT1-04 | Guardó `Bembos → Alimentación › Fast food`. | Sin regla al consultarla con el proceso abierto; tras reiniciar la recibió sin reinstalación ni restauración manual. **PASS transporte al arranque; actualización en sesión no acreditada**. | `08-A-rule.png`, `08-B-rule-before-restart.png`, `08-B-rule-after-restart.png` | Ausente a los 24 s; visible a los 78 s tras reinicio. |
| 9. Deuda y condonación offline L6 | Offline creó `QA2-deuda-A` por 50 y **confirmó offline** condonación de 10. Quedó 40, hecho fechado 14 nov. Recibió después la deuda de B. | Recibió deuda 40 y condonación 10 fechada 14 nov. Creó después `QA2-deuda-B` por 20, recibida en A. **PASS feed y fecha de calendario con reinicios**. | `09-A-actual-condone-offline.png`, `09-B-offline-debt-received.png`, `09-B-later-debt.png`, `09-A-later-debt-received.png` | A reconectó 17:23:59; B comprobó 40 a las 17:24:40 (≤41 s). Deuda posterior de B comprobada en A ≤207 s, incluyendo arranque lento. |
| 10. Meta, reserva y liberación L5/FIX-A16 | Primera meta de control llegó a objetivo en A y quedó con reserva 10. Para cumplir el tramo B, creó `QA2-meta-B` objetivo 10 y reservó 5. Después recibió la liberación de B: reserva 0 en esta meta; cuenta nominal conservó 263. | Recibió reserva 5, aportó otros 5, mostró `¡Meta lograda!` con 2 aportes y liberó 10 a la misma cuenta. **PASS con reinicio receptor**. | `10-A-partial-reserve.png`, `10-B-partial-received.png`, `10-B-complete.png`, `10-B-reserve-released.png`, `10-A-after-release-goals.png` | Reserva parcial recibida ≤25 s. Liberación de B comprobada en A ≤111 s, incluyendo arranque. |
| 11. Cupo Free GOA1-11 | Con dos metas activas bloqueó la tercera: `Tienes 2 de 2 metas activas. Completa una o revisa los planes.` | Mismo bloqueo y mensaje. **PASS variante de tercera meta en ambos**. No se ejecutó por separado la variante de reasignación desde una completada. | `11-A-third-blocked.png`, `11-B-third-blocked.png` | Bloqueo local; A recibió la segunda activa durante el sync provocado por otra escritura, antes de 90 s. |
| 12. Estabilidad | 0 `FATAL EXCEPTION`, 0 `History read failed` en logs capturados y buffer de crash. | Iguales contadores. Historial de reembolso hidratado abierto dos veces. **PASS dentro de la ventana capturada**. | `A-final-logcat.txt`, `B-final-logcat.txt`, `A-crash-buffer.txt`, `B-crash-buffer.txt`, logs intermedios y `04-B-refund-history-final.png` | No aplica. |

## Bugs reproducidos

### 2DEV-01 — Cambios remotos no se descargan al volver al dashboard

**Impacto:** los dispositivos muestran saldos/datos diferentes con ambas apps abiertas. La prueba no acredita actualización normal en sesión.

1. Iniciar sesión en B después de crear los datos iniciales: restaura S/ 94 correctamente.
2. En A corregir apertura a 200 y después 300.
3. En B volver al dashboard, cambiar de pestaña, intentar gesto de recarga y salir a Inicio/volver a la app.
4. B sigue mostrando S/ 94; también siguió atrasado después de guardar su propia edición de categoría.
5. Cerrar el proceso de B y abrir de nuevo: recibe S/ 294.

Evidencia: `02-03-B-after-write.png` a las 17:05:52 frente a `02-03-B-relaunch.png` a las 17:07:31; logs `A-logcat-mid.txt`, `B-logcat-mid.txt` y `B-app-logcat.txt`. La regla de comercio repite el patrón en `08-B-rule-before-restart.png` / `08-B-rule-after-restart.png`. No se determinó la causa ni se afirma que ningún sync periódico futuro pueda actualizarlo.

### 2DEV-02 — La acción de aceptar servidor no resuelve el rechazo

**Impacto alto:** un conflicto real queda marcado indefinidamente y la acción de recuperación ofrecida falla.

1. Tener `QA2-m1` sincronizado en 12 en ambos.
2. Poner ambos offline; guardar 21 en A y 22 en B.
3. Conectar A y esperar a `Sincronizado`; conectar B.
4. Abrir el movimiento `No sincronizado` en B y tocar `Usar la versión del servidor`.
5. Aparece `No se pudo completar. Tus cambios se conservan.`. Repetir después: mismo resultado.
6. Reiniciar B en pruebas posteriores: el aviso continúa, aunque el monto visible es el valor servidor 21.

Evidencia: `06-B-actions.png`, `06-B-resolution-failed.png`, `06-B-resolve-retry-failed.png`, `final-B-unresolved-conflict.png` y `06-B-conflict-logcat.txt`. La tarjeta de detalle muestra simultáneamente el aviso superior y una etiqueta interior `Sincronizado`.

No apareció `Reintentar` para este rechazo. Aunque el guion pedía ambas acciones, la decisión 3 aprobada limita `Reintentar` a errores temporales: su ausencia en un conflicto de versión **no se clasifica como bug adicional**.

### 2DEV-03 — El reembolso recibido no reduce la deuda de tarjeta en B

**Impacto alto:** el receptor ya tiene el movimiento pero muestra una deuda y crédito disponible distintos.

1. En A registrar compra directa de S/ 4 y reembolso de S/ 1.
2. A muestra deuda S/ 3 y disponible S/ 997.
3. Reiniciar B para descargar; abrir el reembolso: ingreso S/ 1 al pasivo de la tarjeta, estado `Sincronizado`.
4. Abrir su historial: carga sin error. Volver al dashboard.
5. B sigue mostrando deuda S/ 4 y disponible S/ 996, al menos 118 s después del alta. A conserva S/ 3.
6. Anular el reembolso en B; después de descargar, A vuelve a S/ 4. Esto sí funciona.

Evidencia: `04-A-stable-debt3.png`, `04-B-refund-synced-detail.png`, `04-B-refund-history-final.png`, `04-B-refund-debt4-stable.png`; logs `04-A-refund-logcat.txt` y `04-B-refund-logcat.txt`. No se consultó el estado remoto directamente ni se atribuye una causa concreta.

## Observaciones y límites

- Después de las correcciones locales de apertura, A mostraba S/ 294 disponibles pero el rótulo `Saldo inicial` seguía en 100 (`02-A-opening300.png`). Después de hidratar, el rótulo sí quedó en 300 (`final-A-opening-label.png`). Es una inconsistencia transitoria de presentación, no saldo corrupto persistente.
- Anular compras y reembolsos conserva una fila `Anulado`. Se verificó el efecto financiero; no se interpreta la conservación de auditoría como eliminación física fallida.
- La fecha de la condonación se comprobó hasta el día expuesto por la UI. No se acreditó por UI la igualdad exacta de segundos de `occurred_at`.
- Al retirar toda la reserva de `QA2-meta-B`, la app la volvió a mostrar activa con reserva 0. Se describe el comportamiento observado sin aprobar una nueva regla de producto.
- Arranques largos y pausas de renderizado constan en `09-A-startup-spinner-logcat.txt`, `10-A-startup-logcat.txt` y sus capturas. Un arranque registra 1.358 frames omitidos. No se aisló cuánto corresponde al emulador o al build debug.
- El log de B también registra `ProfilePreferencesRepo: Active session does not match the profile owner` durante un arranque, sin crash. No se acreditó relación causal con los bugs anteriores.
- Los buffers de logcat son finitos; los contadores de estabilidad describen los logs conservados, no prueban ausencia universal de fallos.
- Hubo una exposición accidental de la clave QA en una salida de herramienta durante el primer intento de login: un tap prematuro escribió en el campo de correo. Se eliminó el XML afectado y se redactaron los archivos de evidencia. La clave no se incluye en este documento. El coordinador fue informado y confirmó que la rotará al terminar.

## Estado final

- Ambos emuladores quedan encendidos, con sesión QA, conectividad activa y las apps abiertas en el dashboard. No se borraron datos ni se reinstaló para restaurar durante los escenarios.
- Ambos dashboards: cuenta `QA2-ahorro` **S/ 263**, tarjeta `QA2-tarjeta` **deuda S/ 4 / crédito disponible S/ 996**. Apertura efectiva **S/ 300**.
- Gastos bancarios activos: 21 (`QA2-m1`), 2, 3, 5 y 6. Dos compras de prueba de 9 anuladas; compra de 4 activa; dos reembolsos de 1 anulados. Se conservaron hechos de corrección/apertura.
- B conserva el rechazo de su edición a 22, con `No sincronizado`; el monto visible del movimiento es 21.
- Categoría: `QA2-Salud-B`. Regla: `Bembos → Alimentación › Fast food`.
- Deudas: `QA2-deuda-A`, original 50, condonación 10, pendiente 40; `QA2-deuda-B`, pendiente 20.
- Metas: `QA2-meta-reserva`, completada y reserva 10; `QA2-meta-B`, activa y reserva 0 después de completar/liberar; `QA2-activa-1`, activa, objetivo 20 y reserva 0. No se creó tercera activa.
- Los datos QA pueden permanecer. Quedan por corregir los tres bugs, repetir sus escenarios y rotar la clave QA; la variante de reasignación de cupo no se probó.

Las capturas finales son `final-A-dashboard.png`, `final-B-dashboard.png`, `final-A-opening-label.png` y `final-B-unresolved-conflict.png`. Solo se añadió este informe al repositorio.
