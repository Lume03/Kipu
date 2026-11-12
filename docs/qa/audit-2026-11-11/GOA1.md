# GOA1 — goals

Esta porción gestiona metas de ahorro en PEN/USD, reservas respaldadas por cuentas y comandos locales que luego se sincronizan. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos y uso offline frecuente. Auditoría estática de main: 72 archivos y 9.868 líneas leídos completos, en bloques de hasta 280 líneas; no ejecuté código ni accedí a red.

## Hallazgos

Se revalidaron GOA1-01 a GOA1-08 conservando sus IDs; GOA1-04 queda descartado como defecto demostrado. Resultado: **13 hallazgos activos: 1 crítico, 7 altos y 5 medios**; ningún bajo. Los nuevos comienzan en GOA1-09. No se omitieron hallazgos menores confirmados. Los problemas de sincronización se refieren al contrato local versionado, no prueban el estado desplegado; los RPC están habilitados por defecto en `app/build.gradle.kts:26-29`.

### [GOA1-09] Un comando puede enviarse con la sesión de otro usuario
- Severidad: crítica
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalsSyncScheduler.kt:74-100; app/src/main/java/com/kipu/app/feature/goals/data/remote/GoalsApi.kt:56-65
- Qué está mal: El worker valida el propietario una sola vez al arrancar. Cada RPC obtiene la sesión vigente sin comprobar que su usuario sea el dueño del comando; el payload tampoco transporta un propietario que permita detectar ese cambio.
- Escenario concreto de fallo: A tiene pendiente CREATE_GOAL sin cuenta vinculada; el worker verifica A y queda suspendido antes de obtener la sesión del RPC. Se cierra A y se abre B; GoalsApi toma el token de B y create_goal_v1 crea bajo B la meta privada de A, mientras el recibo local se confirma bajo A. El contrato versionado deriva el propietario de auth.uid(), por lo que RLS no distingue esta petición legítimamente autenticada como B.
- Fix mínimo: Pasar el userId esperado a execute y rechazar antes del POST cualquier sesión que no coincida; comprobar también el propietario antes de reclamar comandos y antes de aplicar sus resultados, reutilizando requireSyncOwner. Conservar el comando pendiente al cambiar de propietario.
- Confianza: alta
- Cross-módulo: no

### [GOA1-01] La cola no respeta dependencias de la meta secundaria
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/local/GoalOutboxDao.kt:30-40; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:364-375
- Qué está mal: Una reasignación o reemplazo modifica dos metas, pero el orden de envío solo compara el goal_id principal. No considera destination_goal_id ni replacement_goal_id.
- Escenario concreto de fallo: Una reasignación A→B queda RETRYABLE durante 30 segundos; después se edita B offline. La edición tiene como revisión esperada el resultado optimista de la reasignación, pero puede enviarse antes y recibir REVISION_MISMATCH porque B aún conserva su revisión anterior en el servidor.
- Fix mínimo: Al elegir el siguiente comando, bloquearlo también por operaciones previas que afecten a cualquiera de sus metas secundarias. Añadir un caso Room con la reasignación en reintento y una edición del destino.
- Confianza: alta
- Cross-módulo: no

### [GOA1-02] Eliminar con varias cuentas deja liberaciones parciales
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailViewModel.kt:144-165; app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt:344-365
- Qué está mal: delete confirma cada liberación por separado y vuelve a pasar el mismo reemplazo en todas ellas. El repositorio rechaza ese reemplazo si una liberación concreta no necesita reactivar la meta; los cambios anteriores ya están persistidos.
- Escenario concreto de fallo: Una meta COMPLETED tiene reserva en dos cuentas, dos metas activas Free y se elige una de ellas como reemplazo. La primera liberación baja de la cantidad objetivo y bloquea el reemplazo; la segunda devuelve UNEXPECTED_REPLACEMENT_GOAL. La eliminación falla después de liberar parte del dinero y bloquear otra meta.
- Fix mínimo: Validar y aplicar la eliminación lógica con todas sus liberaciones y el reemplazo en una única operación atómica. Usar el reemplazo solo en la transición que lo requiere y conservar toda la historia.
- Confianza: alta
- Cross-módulo: no

### [GOA1-03] La reasignación omite al destino al reconocer resultados
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:137-147,168-204; app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalChangeFeedApplier.kt:128-141
- Qué está mal: El conjunto de metas afectadas reconoce la principal y replacement_goal_id, pero ignora destination_goal_id. La propagación de conflictos del feed repite esa omisión.
- Escenario concreto de fallo: Se aplica optimistamente A→B y el servidor rechaza por una revisión obsoleta de B. Solo A queda CONFLICT/FAILED; B conserva su aporte y estado PENDING y puede recibir nuevas operaciones basadas en una reserva que el servidor no aceptó. En una respuesta aplicada sin lista goals, B tampoco se marca confirmada en este reconocimiento.
- Fix mínimo: Incluir destination_goal_id al calcular metas afectadas tanto en el reconocimiento como en el feed y resolver ambas proyecciones en cada resultado. Probar éxito y conflicto de una reasignación real con Room.
- Confianza: alta
- Cross-módulo: no

### [GOA1-05] Un traslado rechazado sigue alterando los saldos locales
- Severidad: alta
- Categoría: datos
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:194-200,288-344; app/src/main/java/com/kipu/app/feature/goals/data/sync/GoalChangeFeedApplier.kt:61-66
- Qué está mal: El traslado optimista se guarda ACTIVE con asientos que afectan saldos. Un rechazo remoto solo cambia el estado de la meta y del recibo; ni transacción, ni asientos, ni evento se excluyen del cálculo efectivo. Descartar conflictos financieros también se rechaza, por lo que esa acción no recupera los saldos.
- Escenario concreto de fallo: Otro dispositivo consume el saldo de origen mientras este teléfono está offline; aquí se traslada y reserva dinero y el servidor lo rechaza por respaldo insuficiente. Este teléfono conserva el débito, el crédito y la reserva locales como efectivos aunque no exista el traslado remoto.
- Fix mínimo: Implementar una resolución explícita que, en una transacción local, conserve la evidencia rechazada y la excluya de saldos y reservas efectivos. Mostrar el motivo y permitir resolverla; preservar el historial no exige seguir contando una operación rechazada como dinero confirmado.
- Confianza: alta
- Cross-módulo: no

### [GOA1-06] El reemplazo Free excluye metas de otra moneda
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionSheet.kt:118-124; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:132-134,308-312
- Qué está mal: Las pantallas cuentan y ofrecen como reemplazo solo metas activas de la moneda actual. El repositorio cuenta todas las metas activas para el cupo y no impone esa restricción monetaria al reemplazo.
- Escenario concreto de fallo: Una persona Free tiene una meta completada PEN y dos metas activas USD. Al retirar de la meta PEN o eliminarla, no aparece el selector de reemplazo y el repositorio devuelve REPLACEMENT_REQUIRED; la interfaz no permite proporcionar el dato exigido.
- Fix mínimo: Usar todas las metas activas para el cupo y el reemplazo. Mantener el filtro por moneda exclusivamente para reasignar dinero entre metas.
- Confianza: alta
- Cross-módulo: no

### [GOA1-10] Un recibo antiguo sobrescribe el estado de cambios posteriores
- Severidad: alta
- Categoría: concurrencia
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:176-186; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalDao.kt:158-162
- Qué está mal: Si la respuesta trae goals, se escribe incondicionalmente su status y SYNCED. Se conserva la revisión máxima, pero no el estado optimista de comandos posteriores; el control de countPending se ejecuta únicamente cuando no existe esa lista.
- Escenario concreto de fallo: Offline se libera una meta completada con reemplazo Free y luego se reduce su objetivo para completarla otra vez. La respuesta de la primera operación llega con ACTIVE/revisión 5 cuando la proyección local ya es COMPLETED/revisión 6: queda ACTIVE/revisión 6/SYNCED. El reconocimiento simple de la segunda operación no restaura el status y el feed de revisión 6 puede fallar con GOAL_REVISION_REUSED.
- Fix mínimo: Aplicar el status remoto solo si no hay una proyección posterior pendiente para esa meta; mantener PENDING y el estado optimista cuando la revisión local es superior. Añadir un test Room con dos comandos locales y respuestas sucesivas.
- Confianza: alta
- Cross-módulo: no

### [GOA1-11] Reasignar desde una meta completada puede exceder el cupo Free
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/OfflineFirstGoalRepository.kt:394-468; app/src/main/java/com/kipu/app/feature/goals/domain/usecase/ReassignGoalReserve.kt:34-45
- Qué está mal: La reasignación puede reactivar la fuente completada, pero no comprueba el cupo ni solicita un reemplazo. A diferencia de releaseGoalReserve, este camino carece incluso de los parámetros para esa selección.
- Escenario concreto de fallo: Free tiene A completada con S/100 reservados y B/C activas; B tiene un objetivo alto. Reasigna S/10 de A a B: A pasa a ACTIVE, B sigue ACTIVE y C también, dejando tres metas activas sin selección de reemplazo. SC-007 de la spec exige mantener como máximo dos.
- Fix mínimo: Antes de persistir, comprobar si la reasignación reactiva la fuente y excede el cupo; exigir y aplicar el reemplazo atómicamente como en la liberación. El contrato RPC también debe soportar esa decisión antes de permitir el envío.
- Confianza: alta
- Cross-módulo: no

### [GOA1-07] La lista y el detalle recomiendan importes mensuales distintos
- Severidad: media
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/presentation/GoalListScreen.kt:491-501; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionChartData.kt:32-38
- Qué está mal: La lista divide por la diferencia entre meses; el detalle incluye además el mes actual. La lista también recomienda para fechas vencidas, mientras el detalle las oculta.
- Escenario concreto de fallo: En noviembre quedan S/300 para enero: la lista recomienda S/150 mensuales y el detalle S/100 durante noviembre, diciembre y enero.
- Fix mínimo: Reutilizar goalMonthlySuggestion en la tarjeta, incluida la regla para fechas vencidas. El test existente comprueba el cálculo del detalle, pero no su uso por la lista.
- Confianza: alta
- Cross-módulo: no

### [GOA1-08] Las pruebas de gestión esperan controles retirados
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/goals/presentation/GoalManagementFlowTest.kt:42-50,89-121,285-310; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalPlanLimitSheet.kt:32-35
- Qué está mal: Pruebas activas buscan Ver mis metas activas, 2 de 2 activas y Eliminar meta vacía. La hoja actual usa Entendido; la lista ya no muestra esos contadores ni ese texto de eliminación. La expectativa de un botón crear con metas existentes también quedó obsoleta.
- Escenario concreto de fallo: rootCreateRequestHonoursGoalQuotaAndCanBeDismissedWithoutCreating intenta pulsar Ver mis metas activas, un control inexistente, y no alcanza su aserción de que no se creó una meta. Es una incompatibilidad estática comprobada, no un resultado de ejecución.
- Fix mínimo: Actualizar los selectores a los controles actuales sin quitar las aserciones de comportamiento. Test mínimo: abrir la hoja al cupo, pulsar Entendido y verificar cero llamadas de creación; validar el cupo real con repositorio, no solo con un estado de UI fabricado.
- Confianza: alta
- Cross-módulo: no

### [GOA1-12] Los selectores exponen saldos aunque estén ocultos
- Severidad: media
- Categoría: seguridad
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/presentation/GoalFormScreen.kt:353-355,463-466,575-576; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalContributionSheet.kt:292,321-326; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:398
- Qué está mal: Varias etiquetas convierten saldos reales/libres en texto directamente y no consultan LocalBalanceMasked. Algunos de esos importes también se incorporan al contentDescription del selector.
- Escenario concreto de fallo: Con la opción de ocultar saldos activa, se abre Vincular cuenta en el formulario o Cuenta origen al confirmar un traslado. Los demás MoneyText muestran puntos, pero estas etiquetas muestran y anuncian el saldo exacto de la cuenta.
- Fix mínimo: Renderizar esos importes con MoneyText o su formateo enmascarado compartido y excluir el valor crudo de la semántica cuando se oculta. Test mínimo: proporcionar LocalBalanceMasked=true y comprobar que el saldo no aparece en texto ni descripciones.
- Confianza: alta
- Cross-módulo: no

### [GOA1-13] El historial compone todos los eventos al abrir el detalle
- Severidad: media
- Categoría: rendimiento
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/presentation/GoalEventHistory.kt:32-62; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailScreen.kt:199-200,248-249,363-366
- Qué está mal: El historial usa Column con forEach dentro de un scroll que compone todas las filas, incluidas las que están fuera de pantalla. Se inserta completo en el detalle y de nuevo en la hoja de historial si se abre.
- Escenario concreto de fallo: Una meta de larga duración acumula 3.000 aportes/liberaciones. Entrar al detalle crea miles de filas de texto, fechas e importes antes de poder recorrerlas; abrir el historial conserva esa composición de fondo y crea otra completa. El coste crece con todo el historial y no con las filas visibles.
- Fix mínimo: Usar una lista perezosa como contenedor del detalle y colocar allí las filas de eventos, o mostrar un resumen acotado y cargar la historia completa solo en una LazyColumn independiente. No anidar una LazyColumn sin límite dentro del scroll actual.
- Confianza: alta
- Cross-módulo: no

### [GOA1-14] El test de recibos inválidos no alcanza la validación de estados
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/goals/data/GoalReplacementRoomIntegrationTest.kt:369-400; app/src/main/java/com/kipu/app/feature/goals/data/local/GoalLocalDataSource.kt:145-146,176-181
- Qué está mal: Las tres respuestas del test usan source/replacement, pero las metas creadas se llaman source-0/replacement-0 y así sucesivamente. Todos los casos se rechazan antes por IDs distintos; el caso NOT_A_STATUS nunca comprueba la validación del status ni el rollback tras actualizar la primera meta.
- Escenario concreto de fallo: Se elimina por error la validación de validStatus en finishRemoteCommand: el test sigue pasando, porque su caso supuestamente dirigido al estado inválido retorna false al comparar los conjuntos de IDs.
- Fix mínimo: Construir cada respuesta con los IDs del caso correspondiente. Test mínimo: dos IDs correctos, primera meta con resultado válido y segunda con NOT_A_STATUS; comprobar que no cambia ninguna meta, recibo ni comando.
- Confianza: alta
- Cross-módulo: no

### [GOA1-04] Descartado: no se acreditó que la organización visual deba sincronizarse
- Severidad: baja
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/goals/data/GoalDisplayPreferences.kt:15-29; app/src/main/java/com/kipu/app/feature/goals/presentation/GoalDetailViewModel.kt:159-165
- Qué está mal: Descartado como defecto demostrado: la persistencia es explícitamente por propietario y dispositivo, y no encontré un requisito aprobado que obligue a sincronizar removed. La pasada anterior convirtió una duda de producto en un bug confirmado.
- Escenario concreto de fallo: Se conserva la observación: eliminar visualmente una meta aquí y abrir otro dispositivo puede mostrarla como pausada, pues solo se sincroniza el archivo. Sin una regla de alcance de esa preferencia, no es posible afirmar que ese resultado sea incorrecto.
- Fix mínimo: Ningún cambio de código justificado por este hallazgo; aclarar el alcance de la preferencia antes de diseñar sincronización adicional.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- GoalsApi.execute / SyncGoalsWorker → cambiar de A a B entre reclamar un CREATE_GOAL y obtener la sesión; no enviar datos con B ni confirmar el comando de A.
- GoalOutboxDao.getReady → reasignación en RETRYABLE seguida de edición de destino; impedir que la segunda adelante a la primera.
- GoalDetailViewModel.delete → dos cuentas, meta completada y reemplazo Free; finalizar todo o no alterar reservas ni otras metas. Las pruebas actuales cubren solo una cuenta o mocks que devuelven éxito.
- GoalLocalDataSource.finishRemoteCommand → conflicto de reasignación debe afectar ambos destinos; rechazo de traslado debe permitir recuperar el saldo efectivo sin borrar la evidencia.
- GoalLocalDataSource.finishRemoteCommand → recibo compuesto anterior a una edición posterior no debe revertir el estado ni marcarla SYNCED.
- OfflineFirstGoalRepository.reassignGoalReserve → fuente completada y dos metas activas Free; no producir tres activas.
- GoalContributionSheet / GoalDetailScreen → completada PEN y dos activas USD deben ofrecer reemplazo.
- GoalFormScreen / selectores de fondos → ocultación activa debe suprimir saldos de texto y semántica.
- GoalReplacementRoomIntegrationTest → corregir los IDs del caso de status inválido para probar realmente el rollback descrito en GOA1-14.

La cobertura existente sí verifica signos, importes, monedas, agregación por cuenta, reservas concurrentes, rollback local al fallar outbox, reparación de revisiones legacy y algunos reemplazos atómicos. No confundí esas pruebas de persistencia local con pruebas de rechazo remoto o aislamiento del worker durante un cambio de sesión.

## Lo que no revisé

**Leí completos todos los archivos de la lista: 52 main, 13 test y 7 androidTest; no falta ninguno.** Usé lecturas numeradas de hasta 280 líneas por archivo; los archivos mayores se dividieron en bloques y mantuve la siguiente lista de control. No ejecuté Gradle, builds, tests, ADB, SQL, servicios Supabase ni red; tampoco certifiqué despliegues. Las lecturas fuera de goals se limitaron a los contratos de sesión, enmascarado y RPC necesarios para comprobar los caminos descritos; no constituyen una auditoría de esos módulos.

### Lista de control: main — 52 archivos

Base: `app/src/main/java/com/kipu/app/feature/goals/`.

- [x] `data/GoalDisplayPreferences.kt` — 1-38.
- [x] `data/GoalMovementIntegration.kt` — 1-29.
- [x] `data/local/GoalBackingAccountRow.kt` — 1-17.
- [x] `data/local/GoalDao.kt` — 1-166.
- [x] `data/local/GoalEntities.kt` — 1-87.
- [x] `data/local/GoalEventWithTransactionStatusRow.kt` — 1-9.
- [x] `data/local/GoalLocalDataSource.kt` — 1–270, 271–429.
- [x] `data/local/GoalLocalMappings.kt` — 1-96.
- [x] `data/local/GoalOutboxDao.kt` — 1–87.
- [x] `data/local/GoalOutboxEntity.kt` — 1-41.
- [x] `data/local/GoalSyncCheckpointEntity.kt` — 1-11.
- [x] `data/OfflineFirstGoalRepository.kt` — 1–270, 271–533.
- [x] `data/remote/GoalDtos.kt` — 1-48.
- [x] `data/remote/GoalsApi.kt` — 1-89.
- [x] `data/sync/GoalChangeFeedApplier.kt` — 1-280, 281-348.
- [x] `data/sync/GoalConflictResolver.kt` — 1-33.
- [x] `data/sync/GoalHydration.kt` — 1-45.
- [x] `data/sync/GoalsSyncScheduler.kt` — 1-145.
- [x] `di/GoalsModule.kt` — 1-15.
- [x] `domain/GoalBackingCalculator.kt` — 1-59.
- [x] `domain/GoalCommandHasher.kt` — 1-40.
- [x] `domain/GoalProgressCalculator.kt` — 1-49.
- [x] `domain/GoalRepository.kt` — 1-58.
- [x] `domain/model/Goal.kt` — 1-61.
- [x] `domain/model/GoalCommandModels.kt` — 1-56.
- [x] `domain/model/GoalEvent.kt` — 1-47.
- [x] `domain/usecase/ArchiveGoal.kt` — 1-27.
- [x] `domain/usecase/CreateGoal.kt` — 1-53.
- [x] `domain/usecase/DeleteEmptyGoal.kt` — 1-20.
- [x] `domain/usecase/ReassignGoalReserve.kt` — 1-78.
- [x] `domain/usecase/ReleaseGoalReserve.kt` — 1-47.
- [x] `domain/usecase/ReserveGoalFunds.kt` — 1-78.
- [x] `domain/usecase/UpdateGoal.kt` — 1-65.
- [x] `presentation/GoalAchievedScreen.kt` — 1-129.
- [x] `presentation/GoalActionDialogs.kt` — 1-114.
- [x] `presentation/GoalBackingStatus.kt` — 1-72.
- [x] `presentation/GoalContributionChart.kt` — 1-111.
- [x] `presentation/GoalContributionChartData.kt` — 1-39.
- [x] `presentation/GoalContributionSheet.kt` — 1-280, 281-364.
- [x] `presentation/GoalContributionUiState.kt` — 1-14.
- [x] `presentation/GoalContributionViewModel.kt` — 1-131.
- [x] `presentation/GoalDetailScreen.kt` — 1-280, 281-443.
- [x] `presentation/GoalDetailUiState.kt` — 1-23.
- [x] `presentation/GoalDetailViewModel.kt` — 1-279.
- [x] `presentation/GoalEventHistory.kt` — 1-78.
- [x] `presentation/GoalFormScreen.kt` — 1-280, 281-560, 561-617.
- [x] `presentation/GoalIcons.kt` — 1-49.
- [x] `presentation/GoalIdeas.kt` — 1-95.
- [x] `presentation/GoalListScreen.kt` — 1-280, 281-560, 561-577.
- [x] `presentation/GoalListViewModel.kt` — 1-263.
- [x] `presentation/GoalManagementUiState.kt` — 1-59.
- [x] `presentation/GoalPlanLimitSheet.kt` — 1-38.

### Lista de control: test — 13 archivos

Base: `app/src/test/java/com/kipu/app/feature/goals/`.

- [x] `data/local/GoalBackingAccountMappingTest.kt` — 1-45.
- [x] `data/local/GoalReserveRevalidationTest.kt` — 1-115.
- [x] `data/OfflineFirstGoalRepositoryTest.kt` — 1-222.
- [x] `domain/GoalBackingCalculatorTest.kt` — 1-121.
- [x] `domain/GoalCommandHasherTest.kt` — 1-39.
- [x] `domain/GoalLifecycleTest.kt` — 1-159.
- [x] `domain/GoalManagementTest.kt` — 1-165.
- [x] `domain/ReserveGoalFundsTest.kt` — 1-103.
- [x] `presentation/GoalContributionChartDataTest.kt` — 1-52.
- [x] `presentation/GoalDetailViewModelTest.kt` — 1-122.
- [x] `presentation/GoalDisplaySectionsTest.kt` — 1-24.
- [x] `presentation/GoalListViewModelTest.kt` — 1-129.
- [x] `presentation/GoalTotalsTest.kt` — 1-42.

### Lista de control: androidTest — 7 archivos

Base: `app/src/androidTest/java/com/kipu/app/feature/goals/`.

- [x] `data/GoalReplacementRoomIntegrationTest.kt` — 1-280, 281-560, 561-821.
- [x] `presentation/GoalContributionFlowTest.kt` — 1-128.
- [x] `presentation/GoalLifecycleFlowTest.kt` — 1-280, 281-336.
- [x] `presentation/GoalManagementFlowTest.kt` — 1-280, 281-350.
- [x] `presentation/GoalReleaseThenTransferFlowTest.kt` — 1-121.
- [x] `presentation/GoalVisualRefinementTest.kt` — 1-75.
- [x] `presentation/PlanningCorrectionsTest.kt` — 1-200.

## Dudas

- La organización visual de pausa/eliminación está implementada como preferencia local. Falta una decisión explícita sobre si debe compartirse entre dispositivos; por eso GOA1-04 no se cuenta como bug.
- La UI actual permite pausar conservando reserva mediante preferencias, mientras la spec describe archivo con reserva cero. No equiparé ambas acciones ni convertí esa diferencia en un requisito nuevo.
- El contrato de reasignación no ofrece reemplazo Free, aunque SC-007 exige mantener el cupo en todas las transiciones. La corrección de GOA1-11 necesita alinear el contrato con esa regla existente; el propietario del SQL correspondiente es SQL3.

