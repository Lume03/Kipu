# CAP1 — capture (incluye el listener de notificaciones: privacidad)

Esta porción interpreta avisos bancarios, prepara candidatos y permite registrarlos o descartarlos, con conservación local de evidencia y correlación con comprobantes. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y uso frecuente sin conexión. En esta continuación leí completos los archivos y pruebas enumerados como pendientes en la versión anterior.

## Hallazgos

### [CAP1-01] Una referencia repetida puede ocultar otra operación
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/data/local/RoomCaptureCorrelation.kt:15-23; app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt:19-22; app/src/main/java/com/kipu/app/feature/capture/data/local/RoomCaptureStores.kt:45-57
- Qué está mal: La asociación automática compara referencia, monto, moneda y dirección, pero no exige fuentes distintas ni comprueba la fecha. Ante una coincidencia se descarta el nuevo candidato como duplicado sin pedir confirmación.
- Escenario concreto de fallo: Dos avisos distintos del mismo banco llevan la referencia 123456, ambos son gastos de S/ 50 y corresponden a fechas diferentes: el segundo se adjunta al primero y no aparece como movimiento pendiente.
- Fix mínimo: Exigir fuentes complementarias y validar una coincidencia temporal razonable antes de unir automáticamente; si no hay certeza, conservar ambos candidatos para revisión. Añadir un test Room con dos avisos de la misma fuente y referencia repetida en fechas distintas.
- Confianza: alta
- Cross-módulo: no

### [CAP1-02] Al descartar se conserva el texto extraído
- Severidad: alta
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/data/local/CaptureDaos.kt:114-123; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureComponents.kt:193
- Qué está mal: El diálogo promete borrar el texto detectado, pero el descarte solo borra `evidence_snippet`; deja `merchant_source_text`, extraído literalmente de la notificación. La purga de siete días tampoco elimina ese campo.
- Escenario concreto de fallo: Una notificación «Yapeaste S/ 10 a Ana Pérez» crea el texto de contraparte «Ana Pérez»; después de descartarla y vencida la evidencia, ese nombre sigue guardado en la fila local descartada.
- Fix mínimo: Borrar `merchant_source_text` al descartar, y ajustar la retención de los demás fragmentos derivados conforme a la intención de producto. Añadir un test Room que descarte un candidato con nombre y compruebe el contenido real de la fila.
- Confianza: alta
- Cross-módulo: no

### [CAP1-04] Elegir otra cuenta cambia la moneda sin convertir el monto
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt:395-400; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewViewModel.kt:498-499; app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureReviewScreen.kt:745
- Qué está mal: Al elegir una cuenta o tarjeta, la pantalla sustituye la moneda detectada por la de ese instrumento, pero deja intacto el monto. Si el aviso ya tenía moneda, no se ofrece el selector de moneda para corregirla independientemente.
- Escenario concreto de fallo: Un aviso por S/ 120 se revisa seleccionando una cuenta en dólares: el formulario pasa a US$ 120 y, al confirmar, envía 12000 centavos USD, aunque el aviso era en soles.
- Fix mínimo: No cambiar silenciosamente la moneda detectada: impedir la selección incompatible o exigir una corrección explícita de monto y moneda antes de guardar. Añadir un test del ViewModel con candidato PEN y cuenta USD que compruebe que no se registra USD 120 sin intervención expresa.
- Confianza: alta
- Cross-módulo: no
### [CAP1-05] Un espacio de miles trunca el importe
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureParser.kt:68-75,174
- Qué está mal: La expresión termina el monto al encontrar un espacio y acepta el prefijo sin comprobar si continúa con más dígitos.
- Escenario concreto de fallo: «Compra S/ 1 000 en TIENDA con tu tarjeta débito terminada en 1234 el 28/10/2026 10:35» produce S/ 1 en vez de S/ 1 000; con cuenta y categoría resueltas puede registrarse automáticamente.
- Fix mínimo: Reconocer la agrupación con espacios o rechazar el monto completo; probar ese aviso.
- Confianza: alta
- Cross-módulo: no

### [CAP1-06] Un fallo temporal antes de guardar pierde el aviso
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/data/listener/KipuNotificationListenerService.kt:35-56; app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt:53-58,120-130
- Qué está mal: Se hacen consultas antes de persistir y el listener ignora sus excepciones. El worker solo reanuda candidatos ya guardados.
- Escenario concreto de fallo: Si el resolutor de cuenta falla temporalmente y Android no vuelve a publicar el aviso, no se crea candidato y el movimiento no aparece en la bandeja.
- Fix mínimo: Conservar de forma segura un trabajo mínimo antes de esas consultas y reintentarlo respetando la sesión, el permiso y la retención; probar sin un segundo callback.
- Confianza: alta
- Cross-módulo: no

### [CAP1-03] La bandeja marca lista una captura sin cuenta
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/presentation/CapturePresentation.kt:85-93; app/src/main/java/com/kipu/app/feature/capture/data/RoomCaptureInstrumentResolver.kt:18-26
- Qué está mal: La lista de datos faltantes solo revisa el tipo de instrumento y la ambigüedad, no que exista la cuenta o tarjeta identificada. El resolutor puede devolver el tipo sin identificador cuando no encuentra los últimos cuatro dígitos.
- Escenario concreto de fallo: Un aviso de débito tiene monto y categoría conocidos, pero su tarjeta no coincide con ninguna cuenta activa: la bandeja lo presenta como listo aunque al abrirlo hay que elegir una cuenta.
- Fix mínimo: Marcar instrumento faltante cuando falte el identificador requerido por su tipo; agregar un test con tipo débito y `accountId = null`.
- Confianza: alta
- Cross-módulo: no

### [CAP1-07] Una actualización del aviso crea otra captura
- Severidad: media
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/domain/ExtractCaptureCandidate.kt:47-52,158-165; app/src/main/java/com/kipu/app/feature/capture/domain/CaptureCorrelation.kt:23-25
- Qué está mal: La huella incluye fecha y texto además de la clave. Una actualización sin referencia cambia la huella y la sugerencia excluye señales de la misma fuente.
- Escenario concreto de fallo: El banco publica y actualiza el mismo aviso de S/ 25 con texto distinto y sin número de operación: se generan dos candidatos pendientes independientes, que el usuario puede registrar por separado.
- Fix mínimo: Presentar actualizaciones de una misma clave como posible duplicado si no puede probarse su identidad; probar dos entregas con la misma clave y texto distinto.
- Confianza: alta
- Cross-módulo: no

### [CAP1-08] Se ignora el signo anterior al símbolo de moneda
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/capture/domain/NotificationCaptureParser.kt:68-75,138-140,174
- Qué está mal: Se rechaza el signo negativo después del símbolo, pero la búsqueda empieza en el símbolo y omite el signo situado antes.
- Escenario concreto de fallo: «Compra -S/ 10 en TIENDA» se interpreta como gasto positivo de S/ 10 y no se marca como importe inválido.
- Fix mínimo: Rechazar también el signo negativo inmediatamente anterior al símbolo; probar `-S/ 10` y `S/ -10`.
- Confianza: alta
- Cross-módulo: no

## Recorrido de privacidad y captura

- El listener filtra antes de leer extras: Yape, BCP, Interbank, BBVA Perú, Scotiabank y Google Wallet; en debug admite también `com.android.shell`. Ignora la propia app, otros paquetes, resúmenes y avisos permanentes; prefiere `EXTRA_BIG_TEXT` a `EXTRA_TEXT` (`KipuNotificationListenerService.kt:30-49`; `CaptureSourceCatalog.kt:4-31`).
- Exige sesión, permiso, consentimiento y Premium verificado. Si se revoca el permiso se rechaza la entrada y no se revisa el historial al reconectar (`VerifiedCaptureAccessGate.kt:46-99`; `NotificationCaptureIngress.kt:11-17`; listener:60). En el recorrido revisado no se registran contenidos ni errores de parsing en logs.
- Guarda en Room hasta 280 caracteres del cuerpo, con secuencias de cinco o más dígitos enmascaradas; también nombre extraído, importe, últimos cuatro dígitos, referencia y metadatos. El fragmento vence a los siete días y un trabajo periódico cada seis horas intenta borrarlo, sujeto a la programación del sistema (`ExtractCaptureCandidate.kt:104-117,152-156`; `EvidencePurgeWorker.kt:38-43,51-67`). Room usa el constructor estándar sin cifrado de base configurado (`core/di/CoreModule.kt:27-28`).
- Confirmar o descartar borra el fragmento; el descarte conserva el nombre (CAP1-02). Cerrar sesión no borra estas filas (`feature/auth/data/SupabaseAuthRepository.kt:250-268`); la eliminación de cuenta intenta borrar transaccionalmente las filas con `owner_id` o `user_id`, incluidas estas tablas (`feature/portability/AccountDeletionAdapters.kt:102-155,172-185`). No verifiqué borrado físico de SQLite ni copias de seguridad.
- La huella exacta incluye paquete, clave, fecha, título y texto. La referencia igual no tiene ventana temporal (CAP1-01); la sugerencia débil exige fuentes distintas, importe, moneda, dirección y contraparte iguales y una ventana inclusiva de diez minutos (`CaptureCorrelation.kt:19-38`). Los workers reintentan fallos; el de seguimiento toma al dueño activo y lo comprueba entre candidatos. No demostré escritura del dueño A bajo B.
## Código de riesgo sin tests

- `RoomCaptureCorrelation.attach` → dos avisos de la misma fuente con igual referencia y monto, pero fechas distintas, deben conservar dos candidatos.
- `CaptureCandidateDao.discard` → usar Room real y verificar que el nombre extraído ya no quede en la fila descartada; el test existente usa un almacén falso y solo comprueba `evidenceSnippet`.
- `CapturePresentation.missingFields` → tipo de débito conocido sin cuenta debe figurar como instrumento faltante.

- `CaptureReviewViewModel.onInstrumentSelected` → captura en soles y cuenta en dólares no deben producir el mismo monto en dólares sin confirmación explícita.

- PeruNotificationParser.parse → probar agrupación con espacio y signo anterior a moneda.
- KipuNotificationListenerService → fallo transitorio antes de guardar sin segundo callback.
- CaptureFollowupWorker.doWork → cambio de dueño durante un procesador suspendido y solapamiento de trabajos.

## Lo que no revisé

En esta pasada dirigida releí completos listener, ingress, parser, extracción, correlación, stores, DAO, workers, gate, catálogo, notifier, ambos ViewModels y los tests de parser, correlación, extracción, purga, gate, HybridYapeRoom e inbox; no terminé de releer completo CaptureReviewViewModelTest. Los demás archivos de alcance no se releyeron en esta pasada; el informe anterior declara haberlos leído completos. No ejecuté builds, tests, dispositivos, red ni Supabase, según las restricciones.

## Dudas

- ¿Qué plazo corresponde al texto de contraparte de un candidato todavía pendiente, y qué fragmentos deben conservarse tras su resolución para aprendizaje y auditoría?
- ¿Los proveedores garantizan unicidad global y permanente de sus referencias, o solo por cuenta, fuente o periodo? La implementación actual no comprueba esas dimensiones.
