# ACC1 — accounts: domain + data/local + data/remote + di

Esta porción define cuentas, tarjetas, cuotas y pagos, sus consultas Room y el contrato HTTP de sincronización. Asumí miles de usuarios, uno o dos dispositivos por usuario, miles de movimientos por usuario y uso offline frecuente; Room contiene los datos locales de cada dispositivo. Auditoría estática sobre `main`, sin red, builds, tests ejecutados ni dispositivos.

## Hallazgos

4 hallazgos: 0 críticos, 2 altos, 2 medios y 0 bajos. No se omitieron hallazgos menores confirmados.

### [ACC1-01] Las cuotas guardan intereses pero el saldo pendiente los descarta
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/data/local/CreditEntities.kt:124-145
- Qué está mal: `before_refund_minor` resta pagos únicamente de `principal_minor`; nunca incorpora `interest_minor`. Esta misma consulta alimenta deuda, cuotas pendientes y estado PAID. El repositorio guarda capital e interés separados (OfflineFirstFinancialInstrumentsRepository.kt:1436-1437) y muestra el total como su suma (:1871-1874), por lo que no hay compensación en el mapeo.
- Escenario concreto de fallo: Una cuota confirmada con 1.000 céntimos de capital y 100 de interés muestra total 1.100 pero pendiente 1.000. Un pago de 1.000 la marca pagada y oculta los 100 restantes; intentar pagar 1.100 se rechaza por exceder la deuda calculada. El asignador recibe precisamente este saldo en OfflineFirstFinancialInstrumentsRepository.kt:1154-1164.
- Fix mínimo: Para cuotas financiadas confirmadas, calcular el pendiente desde `principal_minor + interest_minor` antes de pagos y devoluciones. Añadir un caso Room con capital 1.000, interés 100 y pago 1.000: pendiente 100 y estado PARTIAL. No sumar intereses de una vista previa; la contradicción documental sobre financiación confirmada se registra en Dudas.
- Confianza: alta
- Cross-módulo: no

### [ACC1-02] Reactivar débito exige un cupo adicional aunque su banco ya lo ocupa
- Severidad: alta
- Categoría: bug
- Ubicación: app/src/main/java/com/kipu/app/feature/accounts/domain/model/Card.kt:53-54; app/src/main/java/com/kipu/app/feature/accounts/domain/usecase/AccountUseCases.kt:118-124
- Qué está mal: `Card.isComputableForQuota` devuelve true también para débito. El detalle transmite ese valor a `ReactivateInstrument`, que rechaza antes de llegar al repositorio cuando hay cuatro cupos. El cálculo local real agrupa débito y banco en un mismo cupo (InstrumentQuotaGroups.kt:11-12); el registro de débito ya respeta esa regla.
- Escenario concreto de fallo: Usuario Free con cuatro bancos activos y una tarjeta de débito archivada vinculada a uno de ellos → pulsa Reactivar → recibe QuotaExceededException, aunque la reactivación mantiene cuatro grupos. Llamadores comprobados: presentation/detail/AccountDetailScreen.kt:327-328 y presentation/AccountsViewModel.kt:423-430; se citan solo como contrato.
- Fix mínimo: Hacer que la decisión de reactivar compruebe si agrega un grupo al cupo, usando el banco activo vinculado. No basta cambiar siempre débito a false: InstrumentQuotaGroupsTest comprueba que un padre archivado ya no absorbe su cupo. Añadir el caso de cuatro bancos activos y débito archivado de uno de ellos, que debe reactivarse.
- Confianza: alta
- Cross-módulo: no

### [ACC1-03] Los tests de compensación no alcanzan el rechazo simulado
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/androidTest/java/com/kipu/app/feature/accounts/FinancialRejectionCompensationTest.kt:64-67,191-195,371-375
- Qué está mal: El setup fija `currentOwner = null` mientras el input lleva un usuario concreto. El worker devuelve retry al comprobar que ese usuario no coincide con el propietario verificado (data/sync/SyncInstrumentCommandsWorker.kt:102-114), antes de procesar comandos. Además los stubs de API usan el argumento opcional por defecto, pero el worker pasa `expectedOwner = cmd.userId` (:328-329).
- Escenario concreto de fallo: Ejecutar cualquiera de los dos tests con el código actual → el worker retorna retry sin invocar el rechazo, y la aserción de success falla. Aunque se corrija el propietario, los stubs no corresponden al parámetro owner del llamado real; no queda validada la compensación que anuncian los tests.
- Fix mínimo: Simular un propietario verificado con el mismo userId, ajustar ambos stubs al owner explícito y verificar que cada API fue invocada una vez. Conservar las aserciones de movimientos, cuotas y asientos después del rechazo: ese es el caso mínimo que falta hacer alcanzable. El worker se leyó para entender el contrato, sin atribuirle un defecto.
- Confianza: alta
- Cross-módulo: no

### [ACC1-04] La prueba de integridad verifica un objeto que nunca se persiste
- Severidad: media
- Categoría: test-faltante
- Ubicación: app/src/test/java/com/kipu/app/feature/accounts/MovementClassificationIntegrityTest.kt:75-114,118-136
- Qué está mal: `originalMovement` se construye como un valor local y no se entrega al repositorio ni se inserta en una base. Las aserciones monetarias consultan ese mismo objeto inmutable después del comando. La segunda prueba anuncia preservación del ledger pero solo consulta clasificación en un fake.
- Escenario concreto de fallo: Una regresión modifica importe o asientos al reclasificar un movimiento → estas pruebas continúan comprobando los literales del objeto original y un mapa de clasificación, sin observar la fila o los asientos afectados. No afirman que exista tal regresión; no pueden detectarla.
- Fix mínimo: Sustituir el chequeo del objeto local por un caso Room: insertar movimiento y asientos, reclasificar con el repositorio real y volver a leer importe, moneda, estado, fechas y asientos. Para el borrado de comercio, reutilizar ese mismo caso y verificar que solo cambia la clasificación.
- Confianza: alta
- Cross-módulo: no

## Código de riesgo sin tests

- CreditEntities.kt / CREDIT_INSTALLMENT_BALANCES_SQL → cuota con interés no nulo y pago que cubre solo capital (ACC1-01). Los fixtures Room revisados usan interés cero; el test de resumen y el asignador reciben saldos ya calculados y no cubren esta consulta.
- AccountUseCases.kt / ReactivateInstrument → reactivar débito con banco activo cuando ya existen cuatro grupos (ACC1-02). Los tests actuales pasan un booleano manual sin reproducir el contrato del débito.
- FinancialRejectionCompensationTest.kt → rechazo permanente de compra y pago alcanzando realmente el worker autenticado y el stub de API (ACC1-03).
- MovementClassificationIntegrityTest.kt → persistir y releer movimiento y asientos tras reclasificar y quitar comercio (ACC1-04).

## Lo que no revisé

Ninguno de los archivos asignados quedó sin lectura completa: 34 main, 19 test y 8 androidTest. Se leyeron fragmentos externos solo para comprobar contratos y llamadores; no se auditó la implementación completa del repositorio, los workers ni la UI, asignados a ACC5/ACC3/ACC4. No ejecuté pruebas, Gradle, ADB, consultas Supabase ni red; los fallos descritos se deducen del código, no de una ejecución. No se comprobó el despliegue remoto ni el rendimiento con mediciones.

`git status --short` inicial ya mostraba `.agents/skills/ponytail/`, `.claude/` y `skills-lock.json` sin seguimiento; no fueron creados ni modificados por esta auditoría.

## Dudas

- La spec EP-CTA (spec.md:69-70,318-319 y contracts/credit-commands.md:15) mantiene la separación entre simulación y deuda real; el código actual permite confirmar PurchaseTerms WITH_INTEREST y los disclaimers hablan de guardar la obligación. No encontré en las referencias consultadas la aprobación que reconcilia esa evolución. ACC1-01 describe la inconsistencia interna de una cuota ya persistida con interés; la decisión de qué financiación puede confirmarse debe resolverla el propietario antes de propagar reglas contables.
