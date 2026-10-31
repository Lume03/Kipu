# Cinco procesos que se automatizarán en Kipu

## 1. Objetivo y base del análisis

Este documento identifica cinco procesos de gestión financiera personal que Kipu puede automatizar de acuerdo con su alcance V4.2. Para cada uno explica qué problema resuelve, por qué conviene automatizarlo, cómo funcionará y qué decisiones seguirán a cargo de la persona usuaria.

Se revisó el alcance del catálogo de 32 procesos en `Kipu_Documentacion/Procesos` y se contrastaron los procesos seleccionados con la especificación del producto, el Product Backlog, la arquitectura y datos, la trazabilidad y el cronograma de `Kipu_Documentacion/Kipu md`. Se priorizaron tareas repetitivas, con reglas verificables y un beneficio directo en el registro y seguimiento del dinero.

**Los cinco procesos seleccionados son:**

1. Captura y registro de movimientos desde fuentes compatibles.
2. Normalización de comercios y sugerencia de categorías personales.
3. Generación y seguimiento de obligaciones recurrentes.
4. Cálculo del consumo y renovación de presupuestos.
5. Generación de alertas y recordatorios financieros.

Son capacidades **previstas en la documentación**, no cinco propuestas nuevas fuera del alcance. Este análisis explica su funcionamiento esperado; no certifica su implementación ni su disponibilidad actual. La revisión puntual del código muestra una base de búsqueda de comercios y un centro de notificaciones, pero eso no demuestra que los motores completos de automatización estén terminados.

Kipu registra y organiza hechos financieros. Automatizar en esta aplicación significa extraer datos, proponer clasificaciones, calcular, programar y avisar; no ejecutar pagos bancarios ni mover dinero fuera de la aplicación.

## 2. Proceso 1: captura y registro de movimientos

**Proceso principal:** P25 — Configurar y extraer captura automática.

**Procesos complementarios:** P26 — Resolver candidatos y aprender correcciones; P27 — Confirmar compra con crédito detectada; P28 — Capturar operaciones Yape por canales híbridos; P13 — Registrar movimiento manual seguro.

**Historias principales:** HU-45 a HU-49 y HU-51. HU-05 cubre consentimiento y privacidad; HU-58 y HU-59 condicionan el acceso.

### Qué se automatiza y por qué

Actualmente, registrar una operación a mano exige copiar importe, moneda, fecha, cuenta y otros datos. La automatización extraerá los campos disponibles de notificaciones compatibles y, para Yape, de comprobantes compartidos voluntariamente. Con ello se reduce la digitación y se facilita registrar operaciones que la persona podría olvidar.

La extracción produce primero un **candidato**, es decir, una propuesta de movimiento. Solo después de las validaciones puede convertirse en un movimiento confirmado.

### Cómo se automatizará

1. La persona habilita la captura, acepta la explicación de privacidad, concede acceso Android y selecciona las fuentes compatibles. La nueva captura y el OCR requieren Premium o Trial vigente y una concesión offline válida cuando corresponda.
2. Antes de leer cada señal, Kipu comprueba cuenta activa, permiso, consentimiento, acceso vigente, paquete habilitado y fecha posterior a la activación.
3. El listener recibe la notificación y el extractor local busca monto, moneda, dirección, instrumento, comercio, fecha y referencia. Conserva el origen y la incertidumbre de cada campo.
4. Si la persona comparte una imagen de Yape, un OCR local extrae los datos legibles. Compartir la imagen es una acción humana; su lectura se automatiza. No se recorre la galería ni se sube el comprobante por defecto.
5. Kipu calcula huellas y compara referencias, fechas, dirección y otras evidencias para evitar procesar dos veces una señal o registrar por separado una notificación y un comprobante de la misma operación. Dos pagos iguales en importe pueden ser operaciones diferentes.
6. P26 evalúa la confianza. Un candidato no crediticio con todos los campos requeridos resueltos, sin conflictos y con acceso vigente puede autorregistrarse mediante el mismo caso de uso financiero del registro manual.
7. Un candidato de confianza media o baja pasa a la bandeja. Toda compra con tarjeta de crédito requiere confirmación humana, incluso si la confianza es alta.
8. Al confirmar, el movimiento y sus efectos contables se guardan de manera consistente. Los reintentos recuperan el resultado existente sin crear otro movimiento.

### Intervención humana y excepciones

La persona concede permisos, comparte imágenes cuando sea necesario y corrige o confirma candidatos dudosos. En crédito elige compra directa o cuotas y confirma expresamente. Si la fuente cambia de formato, faltan datos o se revoca el acceso, Kipu conserva la alternativa manual y no inventa información.

### Ejemplo y resultado esperado

Una notificación compatible informa un ingreso Yape de S/ 80. Kipu extrae los campos y propone el instrumento correspondiente. Si todos los datos obligatorios están resueltos y se cumplen las condiciones de autorregistro, registra un solo ingreso. Si hay dos cuentas posibles, solicita elegir una antes de confirmar.

**Cómo comprobarlo:** reprocesar una misma señal no crea otro movimiento; un candidato pendiente no altera el saldo; una compra de crédito permanece pendiente hasta la confirmación.

**Fuente:** [P25 — Captura automática](../../Kipu_Documentacion/Procesos/25-configurar-y-extraer-captura-automatica.md), [P26 — Resolución de candidatos](../../Kipu_Documentacion/Procesos/26-resolver-candidatos-y-aprender-correcciones.md) y [P28 — Yape híbrido](../../Kipu_Documentacion/Procesos/28-capturar-operaciones-yape-por-canales-hibridos.md).

## 3. Proceso 2: normalización de comercios y sugerencia de categorías

**Proceso principal:** P12 — Normalizar y clasificar comercios.

**Proceso complementario:** P26 — Resolver candidatos y aprender correcciones.

**Historias principales:** HU-15, HU-16, HU-17 y HU-50.

### Qué se automatiza y por qué

Las fuentes pueden representar un mismo comercio con textos diferentes. Además, elegir una categoría en cada compra repetida consume tiempo. Kipu reutilizará alias y preferencias personales para proponer el comercio y la categoría de futuras operaciones.

El beneficio es reducir decisiones repetidas y mantener una clasificación coherente con las preferencias de cada persona. El aprendizaje documentado se basa en reglas y correcciones confirmadas; no requiere IA generativa.

### Cómo se automatizará

1. Kipu conserva por separado el texto original de la fuente y una copia normalizada para comparación. Normalizar mayúsculas, acentos o espacios no modifica la evidencia original.
2. Evalúa primero las reglas personales del propietario y luego las reglas generales permitidas.
3. Si las coincidencias confiables apuntan a un mismo comercio, propone su identidad canónica. Si apuntan a comercios distintos, solicita revisión.
4. Para un comercio identificado, consulta la preferencia privada `comercio → categoría`. La regla explícita de la persona prevalece sobre una sugerencia general.
5. Comprueba que la categoría sea compatible con el tipo de movimiento, que ella y su raíz estén activas y que pueda utilizarse según el plan.
6. Prellena la propuesta para el proceso de registro o revisión. Comercio y categoría siguen siendo campos independientes: cambiar uno no modifica automáticamente el otro.
7. Cuando la persona confirma una corrección reutilizable, P26 puede guardarla para futuras sugerencias. En la administración de alias, la persona revisa y confirma el patrón y su alcance antes de guardarlo.
8. Las reglas permanecen asociadas a su propietario y se sincronizan con identidad y revisión para evitar duplicaciones o sobrescrituras silenciosas.

### Intervención humana y excepciones

La persona decide sus preferencias, resuelve ambigüedades y confirma las correcciones. Sin coincidencia confiable se conserva texto provisional. Una categoría inactiva o bloqueada exige otra selección. Las nuevas reglas no reclasifican movimientos históricos y la clasificación de nueva captura exige acceso vigente.

### Ejemplo y resultado esperado

La persona confirma que un texto de captura corresponde a Tambo y define “Alimentación” como categoría habitual. En una operación futura compatible, Kipu propone ese comercio y esa categoría. La persona puede elegir otra categoría sin perder la identificación del comercio.

**Cómo comprobarlo:** una regla personal se aplica únicamente al propietario; una categoría bloqueada no se propone para nuevas asignaciones; editar una preferencia no cambia la historia.

**Fuente:** [P12 — Comercios y clasificación](../../Kipu_Documentacion/Procesos/12-normalizar-y-clasificar-comercios.md) y [P26 — Aprendizaje de correcciones](../../Kipu_Documentacion/Procesos/26-resolver-candidatos-y-aprender-correcciones.md).

## 4. Proceso 3: generación y seguimiento de obligaciones recurrentes

**Proceso principal:** P19 — Gestionar ciclo de recurrencias.

**Procesos complementarios:** P20 — Conciliar pago esperado con captura; P24 — Gestionar notificaciones financieras.

**Historias principales:** HU-33, HU-34 y HU-35. HU-44 cubre los recordatorios relacionados.

### Qué se automatiza y por qué

Alquileres, servicios y suscripciones requieren recordar fechas repetidas y comprobar qué obligaciones ya se atendieron. Kipu calculará las próximas fechas, generará ocurrencias esperadas y mantendrá su seguimiento.

Esto evita reconstruir el calendario cada mes y permite retirar recordatorios cuando se vincula el movimiento real. Una ocurrencia esperada representa planificación: todavía no es un gasto, ingreso ni pago confirmado.

### Cómo se automatizará

1. La persona configura una recurrencia desde cero o desde un movimiento existente: concepto, importe fijo o aproximado, moneda, frecuencia, fecha inicial, fin opcional y anticipación.
2. Kipu calcula una vista previa. Para fechas mensuales o anuales cuyo día no existe, utiliza el último día válido del mes.
3. Tras confirmar la regla, el programador temporal genera cada ocurrencia esperada y calcula la siguiente fecha desde la regla original, evitando desplazar el calendario por retrasos de ejecución.
4. La combinación `recurrencia + vencimiento` es única. Reinicios o reintentos no crean varias ocurrencias del mismo periodo.
5. La ocurrencia queda disponible en la planificación y se comunica a P24 para mantener el recordatorio.
6. Cuando existe un movimiento confirmado compatible, P20 compara la evidencia y propone o realiza el vínculo conforme a sus condiciones. Una coincidencia ambigua requiere confirmación; un candidato todavía pendiente debe resolverse primero.
7. El vínculo marca atendida la ocurrencia y retira los avisos pendientes, sin registrar un segundo movimiento por el mismo hecho.
8. Pausar o cancelar detiene futuras programaciones y conserva la historia. Reanudar calcula una fecha futura válida sin inventar pagos de los periodos omitidos.

### Intervención humana y excepciones

La persona configura y confirma la recurrencia, decide pausarla o cancelarla y realiza el pago fuera de Kipu. Un importe aproximado no se convierte automáticamente en un importe real. La asociación automática con captura requiere que esa captura esté autorizada.

### Ejemplo y resultado esperado

Se configura un alquiler mensual esperado de S/ 1 200. Kipu genera la expectativa y recuerda el vencimiento. Una vez registrado el pago real y guardado su vínculo, deja de recordarlo. Si el pago no se registró, mantiene la expectativa sin descontar dinero de la cuenta.

**Cómo comprobarlo:** generar una ocurrencia no cambia saldos; conciliar no duplica gastos; una recurrencia pausada no genera nuevas ocurrencias por un disparo tardío.

**Fuente:** [P19 — Recurrencias](../../Kipu_Documentacion/Procesos/19-gestionar-ciclo-de-recurrencias.md) y [P20 — Conciliación](../../Kipu_Documentacion/Procesos/20-conciliar-pago-esperado-con-captura.md).

## 5. Proceso 4: cálculo del consumo y renovación de presupuestos

**Proceso principal:** P21 — Gestionar ciclo del presupuesto.

**Procesos complementarios:** P13 — Registro; P14 — Corrección o anulación; P16 — Reembolsos; P24 — Notificaciones.

**Historias principales:** HU-36, HU-37 y HU-38; HU-25 cubre el efecto presupuestario de reembolsos.

### Qué se automatiza y por qué

Controlar un presupuesto manualmente exige sumar gastos, descontar devoluciones, calcular el disponible y abrir una nueva vigencia cada periodo. Kipu derivará estos valores de los movimientos confirmados y renovará los presupuestos recurrentes.

Se reduce el cálculo repetido y se mantiene una explicación verificable de cuánto se consumió. El presupuesto sigue el gasto: no aparta dinero ni bloquea una compra cuando se excede el límite.

### Cómo se automatizará

1. La persona define nombre, moneda, límite, alcance total/categoría/subcategoría, periodicidad y umbrales. Kipu valida la configuración y el cupo de presupuestos activos; Free admite dos.
2. Al confirmar, crea la definición y su primer periodo, conservando fechas y límite de esa vigencia.
3. Cada confirmación, corrección, reclasificación, anulación o reembolso identifica los periodos afectados y actualiza el consumo correspondiente.
4. Incluye gastos operativos confirmados de la moneda, fecha y alcance aplicables. Excluye candidatos pendientes, transferencias, pagos de principal, aportes a metas, anulaciones y ocurrencias esperadas.
5. Un gasto de subcategoría puede contribuir al presupuesto de su padre y al total. En el total se cuenta una sola vez; no se suman los consumos de presupuestos superpuestos como si fueran gastos distintos.
6. Calcula `restante = límite − consumo` y `porcentaje = consumo / límite × 100`. El restante puede ser negativo y el porcentaje superar el 100 %.
7. Al cruzar un umbral, comunica la condición a P24. Los umbrales iniciales son 50 %, 80 % y 100 %, ajustables según la configuración admitida.
8. Al cerrar una vigencia recurrente activa, abre la siguiente sin arrastrar sobrante o exceso por defecto. Un rango personalizado termina y no se renueva automáticamente.
9. Un reembolso reduce el consumo del periodo de la compra original, aunque el dinero se reciba después. Si cambia la fecha del gasto, recalcula el periodo anterior y el nuevo.

### Intervención humana y excepciones

La persona elige límites y alcance, confirma modificaciones y decide finalizar o eliminar un presupuesto. Los cambios muestran qué vigencias afectan. El presupuesto no tiene pausa/reanudación manual. Un gasto real se registra aun cuando exceda el límite.

### Ejemplo y resultado esperado

Un presupuesto mensual de alimentación tiene un límite de S/ 500. Con S/ 390 consumidos, una compra confirmada de S/ 20 lleva el consumo a S/ 410: quedan S/ 90 y se alcanza el 82 %. Kipu detecta el cruce del 80 % y solicita el aviso. En la siguiente vigencia recurrente vuelve a aplicar el límite configurado sin sumar el sobrante anterior.

**Cómo comprobarlo:** un reintento no suma dos veces una compra; pagar una tarjeta no vuelve a consumir el presupuesto; un reembolso ajusta la vigencia original; la renovación crea un solo periodo.

**Fuente:** [P21 — Presupuestos](../../Kipu_Documentacion/Procesos/21-gestionar-ciclo-del-presupuesto.md) y [P16 — Reembolsos](../../Kipu_Documentacion/Procesos/16-registrar-reembolsos-vinculados.md).

## 6. Proceso 5: generación de alertas y recordatorios financieros

**Proceso principal:** P24 — Gestionar notificaciones financieras.

**Procesos emisores:** crédito, deudas, recurrencias, presupuestos y candidatos pendientes.

**Historias principales:** HU-42, HU-43 y HU-44.

### Qué se automatiza y por qué

Revisar manualmente cada tarjeta, deuda, presupuesto y captura pendiente exige atención constante. Kipu generará avisos cuando ocurra una condición relevante y recordatorios antes de una fecha configurada.

El beneficio es reunir las acciones pendientes en un centro y reducir olvidos y mensajes repetidos. Este proceso es distinto del anterior: el presupuesto calcula la condición; P24 administra su aviso, entrega, lectura y navegación, además de los avisos de otros módulos.

### Cómo se automatizará

1. La persona configura la anticipación por tipo de obligación y los permisos de notificación Android.
2. Los módulos emisores confirman su hecho y después envían el evento: cruce presupuestario, utilización de crédito, vencimiento o candidato pendiente.
3. Para fechas futuras, Kipu calcula `fecha del aviso = vencimiento − anticipación` y guarda la programación local.
4. Construye una clave estable por propietario, objeto, tipo y condición o vencimiento. Reprocesar el mismo evento devuelve el aviso existente.
5. Antes de un disparo programado, vuelve a comprobar que el objeto siga vigente, que la fecha no haya cambiado y que la obligación no esté atendida ni la recurrencia pausada o cancelada.
6. Guarda el aviso interno y actualiza el centro y su contador. Después solicita la entrega Android si el permiso y el canal están disponibles.
7. Si Android difiere o rechaza la entrega, el aviso permanece en el centro. La programación no garantiza entrega exacta al segundo.
8. Al abrir el aviso, marca su lectura y navega al objeto correspondiente tras validar su disponibilidad. Cuando otro proceso resuelve la condición, retira o cancela los avisos pendientes relacionados.

### Intervención humana y excepciones

La persona configura anticipación, consulta avisos y decide qué acción realizar. Leer o descartar un mensaje no significa pagar una deuda ni confirmar un candidato. Los avisos ordinarios de objetos activos permanecen en Free; un aviso de captura requiere que su captura original haya estado autorizada y no concede acceso Premium por sí mismo.

### Ejemplo y resultado esperado

Una tarjeta vence el 20 de octubre y la persona configura tres días de anticipación. Kipu programa el recordatorio para el 17. Si el vencimiento cambia, sustituye la programación; si la obligación ya se resolvió, evita un aviso obsoleto. Abrirlo lleva al contexto de la tarjeta y no ejecuta un pago.

**Cómo comprobarlo:** repetir un evento no duplica el aviso; revocar el permiso Android conserva el aviso interno; resolver una obligación cancela sus programaciones pendientes; marcar leído no cambia el saldo.

**Fuente:** [P24 — Notificaciones financieras](../../Kipu_Documentacion/Procesos/24-gestionar-notificaciones-financieras.md).

## 7. Integración y orden previsto

Las automatizaciones se conectan mediante hechos confirmados y propuestas claramente separadas:

- Una señal autorizada produce un candidato; las reglas de comercios ayudan a completarlo.
- El autorregistro permitido o la confirmación humana produce el movimiento real.
- Ese movimiento puede actualizar presupuestos y atender una ocurrencia recurrente sin duplicarse.
- Los módulos envían a P24 las condiciones que requieren atención y las señales para retirar avisos ya resueltos.

La arquitectura documentada utiliza Kotlin/Android, Room para persistencia local, sincronización mediante una cola de operaciones pendientes, WorkManager y programación Android; la captura Yape incorpora OCR local. Las identidades estables y las transacciones consistentes permiten reintentar sin duplicar efectos. Esto describe el diseño previsto, no componentes cuya terminación se haya auditado en este trabajo.

Según el roadmap del cronograma, el orden de cierre esperado es:

1. **S5:** alias y preferencias de comercio, HU-16 y HU-17.
2. **S6:** recurrencias y estados, HU-33 y HU-35; recordatorios, HU-44.
3. **S7:** presupuestos, HU-36 a HU-38, y reembolsos presupuestarios, HU-25.
4. **S8:** captura, bandeja, confianza, confirmación de crédito y aprendizaje, HU-45 a HU-48, HU-50 y HU-51; conciliación, HU-34; alertas integradas, HU-43.
5. **S9:** captura híbrida Yape y OCR, HU-49.

El centro base HU-42 se ubica en S3. Estos sprints expresan planificación, no evidencia de entregas completadas.

### Observaciones de consistencia documental

El diccionario de arquitectura conserva menciones a SMS en las descripciones de `import_candidates` y `capture_source_events`, mientras la especificación V4.2 excluye expresamente la lectura de SMS y correos. Este análisis sigue el alcance explícito del producto y los procesos P25/P28: notificaciones compatibles y comprobantes compartidos.

La sección de despliegue incremental de Arquitectura y Datos también ubica algunas HU en sprints diferentes del roadmap, por ejemplo HU-16/HU-17 y HU-50. El orden anterior se toma del cronograma y del Product Backlog; antes de implementar conviene alinear esa sección de arquitectura.

## 8. Fuentes generales

- [Especificación del producto Kipu V4.2](../../Kipu_Documentacion/Kipu md/01_Kipu_V4.2_Especificacion_Producto.md): alcance, límites financieros, planes y funcionamiento offline.
- [Product Backlog Kipu V4.2](../../Kipu_Documentacion/Kipu md/02_Kipu_V4.2_Product_Backlog.md): historias, dependencias y reglas de negocio citadas.
- [Arquitectura y Datos Kipu V4.2](../../Kipu_Documentacion/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md): persistencia local, sincronización, candidatos, recurrencias, presupuestos y programaciones.
- [Trazabilidad y Dependencias](../../Kipu_Documentacion/Kipu md/04_Kipu_V4.2_Trazabilidad_y_Dependencias.md): relaciones entre las capacidades del producto.
- [Cronograma Scrum](../../Kipu_Documentacion/Kipu md/05_Cronograma_Scrum_Kipu_V4.1.md): roadmap de cierre de historias por sprint.

**Evidencia puntual del repositorio:** [búsqueda de comercios](../app/src/main/java/com/kipu/app/feature/categories/domain/usecase/SearchMerchantCatalog.kt) y [centro de notificaciones](../app/src/main/java/com/kipu/app/feature/notifications/presentation/NotificationCenterScreen.kt). Se citan como base observable, no como prueba de que las cinco automatizaciones estén completas.