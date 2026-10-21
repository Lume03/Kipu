# KIPU V4.2 - Product Backlog

> **Integración documental — 2026-10-20.** Base HU-01–HU-60 procedente de `KipuApp/Kipu md/02_Kipu_V4.2_Product_Backlog.md`. Se conservan los identificadores y escenarios de origen; no se declaran todas las HU implementadas. Consultar los refinamientos aprobados en [specs/](../../specs/) y las [diferencias documentales](../control-documental.md). En particular, las menciones genéricas de «cinco categorías» en HU-14/HU-57 deben leerse con la aclaración por tipo de EP-CCO, no como un límite global nuevo.

> **60 Historias de Usuario específicas agrupadas en 12 épicas, con prioridad, puntos, Sprint de cierre, dependencias, reglas de negocio y criterios de aceptación Gherkin.**

| Base | Decisión |
| :--- | :--- |
| **Alcance funcional** | Kipu V4.2 \| 12 épicas \| 60 Historias de Usuario \| 10 sprints \| 9 integrantes |
| **Regla de uso** | La HU es la unidad funcional del Product Backlog. El Responsable de Épica mantiene coherencia y spec.md; la implementación concreta se distribuye en Sprint Planning según capacidad y dependencias. |
| **Estructura por HU** | Metadatos, Dependencias y orden, Precondiciones, Descripción Ágil, Reglas de Negocio, Criterios de Aceptación (Gherkin) |
| **Metodología** | Scrum ágil con especificaciones ejecutables Gherkin y trazabilidad bidireccional |
| **Versión** | 4.2 - 12/09/2026 |

---

## 1. Product Backlog por épica

### EP-APS | Acceso, Perfil y Seguridad
**Responsable de Épica:** Integrante 9 - Desarrollo de pruebas, integración y herramientas

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-01 Registro e inicio de sesión | Ninguna | S1 | 5 |
| HU-02 Recuperación y ciclo de sesión | HU-01 | S1 | 5 |
| HU-03 Desbloqueo local con biometría | HU-01 | S1 | 3 |
| HU-04 Preferencias y privacidad de saldos | HU-01 | S1 | 3 |
| HU-05 Permisos y fuentes de automatización | HU-01 | S1 | 3 |
| HU-06 Portabilidad, eliminación de cuenta y restauración | HU-01, HU-07, HU-18 | S9 | 8 |

---

### EP-CTA | Cuentas y Tarjetas
**Responsable de Épica:** Integrante 6 - Desarrollo backend y sincronización

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-07 Administración de cuentas | HU-01, HU-57 | S2 | 5 |
| HU-08 Registro y vínculo de tarjetas | HU-07, HU-57 | S2 | 5 |
| HU-09 Línea y saldos de crédito | HU-08, HU-18, HU-19 | S3 | 8 |
| HU-10 Umbrales de crédito | HU-09 | S3 | 3 |
| HU-11 Catálogo y tasas referenciales | HU-08 | S3 | 5 |
| HU-12 Pago de tarjeta | HU-07, HU-09, HU-18, HU-19 | S3 | 5 |
| HU-13 Compras con crédito y cuotas | HU-08, HU-18, HU-19, HU-23 | S3 | 8 |

---

### EP-CCO | Categorías, Subcategorías y Comercios
**Responsable de Épica:** Integrante 2 - Desarrollo de interfaces y consultas

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-14 Categorías y subcategorías | HU-01, HU-57 | S2 | 8 |
| HU-15 Comercios y servicios | HU-01 | S2 | 5 |
| HU-16 Texto original y alias de comercio | HU-15 | S5 | 5 |
| HU-17 Preferencias personales de categoría | HU-14, HU-15 | S5 | 5 |

---

### EP-MOV | Movimientos y Ledger
**Responsable de Épica:** Integrante 4 - Desarrollo de reglas financieras

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-18 Registro manual de movimientos | HU-07, HU-14 | S2 | 8 |
| HU-19 Saldos inmediatos y atomicidad | HU-18 | S2 | 8 |
| HU-20 Edición de movimientos | HU-18, HU-19 | S4 | 5 |
| HU-21 Anulación y borrado lógico | HU-18, HU-19 | S4 | 5 |
| HU-22 Búsqueda y filtros | HU-18 | S4 | 5 |
| HU-23 Detección de duplicados | HU-18 | S2 | 5 |
| HU-24 Aplicar movimientos a obligaciones y metas | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | S6 | 8 |
| HU-25 Reembolsos vinculados | HU-18, HU-19, HU-36 | S7 | 8 |

---

### EP-DEU | Deudas y Préstamos
**Responsable de Épica:** Integrante 5 - Desarrollo de persistencia local

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-26 Deudas propias: creación y administración | HU-07, HU-19, HU-57 | S5 | 8 |
| HU-27 Dinero prestado: creación y administración | HU-07, HU-19, HU-57 | S5 | 8 |
| HU-28 Pagos y cobros de principal | HU-18, HU-19, HU-26, HU-27 | S5 | 8 |
| HU-29 Cuotas, vencimientos y cierre de deudas | HU-26, HU-27, HU-28 | S5 | 5 |

---

### EP-MET | Metas y Reservas Virtuales
**Responsable de Épica:** Integrante 3 - Desarrollo de planificación y formularios

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-30 Creación y edición de metas | HU-57 | S6 | 8 |
| HU-31 Aportes y reservas de metas | HU-18, HU-19, HU-30 | S6 | 8 |
| HU-32 Liberación, finalización y archivo de metas | HU-30, HU-31 | S6 | 5 |

---

### EP-REC | Recurrencias y Obligaciones
**Responsable de Épica:** Integrante 7 - Desarrollo de eventos y avisos

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-33 Registro de recurrencias | HU-18 | S6 | 8 |
| HU-34 Pagos esperados y coincidencia con captura | HU-33, HU-46, HU-51 | S8 | 5 |
| HU-35 Estados de recurrencias | HU-33 | S6 | 3 |

---

### EP-PRE | Presupuestos y Consumos
**Responsable de Épica:** Integrante 3 - Desarrollo de planificación y formularios

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-36 Crear y consultar presupuestos | HU-14, HU-18, HU-57 | S7 | 8 |
| HU-37 Renovación, consumo jerárquico y alertas | HU-36, HU-25, HU-42 | S7 | 8 |
| HU-38 Edición, finalización y eliminación de presupuestos | HU-36, HU-37 | S7 | 5 |

---

### EP-INF | Inicio e Información Financiera
**Responsable de Épica:** Integrante 2 - Desarrollo de interfaces y consultas

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-39 Inicio e historial financiero | HU-07, HU-18, HU-19 | S7 | 8 |
| HU-40 Calendario financiero | HU-09, HU-29, HU-33 | S7 | 5 |
| HU-41 Estadísticas y analítica financiera | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | S9 | 8 |

---

### EP-NOT | Centro de Notificaciones y Avisos
**Responsable de Épica:** Integrante 7 - Desarrollo de eventos y avisos

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-42 Centro de notificaciones | HU-01 | S3 | 5 |
| HU-43 Alertas presupuestarias y de revisión | HU-42, HU-37, HU-51 | S8 | 5 |
| HU-44 Recordatorios y deduplicación | HU-42, HU-09, HU-29, HU-33 | S6 | 5 |

---

### EP-AUT | Captura Automática e Import Inbox
**Responsable de Épica:** Integrante 8 - Desarrollo de captura y normalización

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-45 Activación del listener | HU-05, HU-58, HU-59 | S8 | 5 |
| HU-46 Extracción y candidato | HU-45 | S8 | 8 |
| HU-47 Confianza y autorregistro | HU-46, HU-18, HU-23 | S8 | 8 |
| HU-48 Confirmación de crédito detectado | HU-46, HU-08, HU-13 | S8 | 5 |
| HU-49 Yape híbrido, OCR y deduplicación | HU-45, HU-46, HU-23, HU-58, HU-59 | S9 | 13 |
| HU-50 Aprendizaje desde correcciones | HU-16, HU-17, HU-46, HU-51 | S8 | 8 |
| HU-51 Bandeja y evidencia mínima | HU-46 | S8 | 5 |

---

### EP-PLA | Planes, Límites y Monetización Freemium
**Responsable de Épica:** Integrante 1 - Desarrollo Android y planes

| HU / resultado | Dependencias bloqueantes | Cierre | Pts |
| :--- | :--- | :---: | :---: |
| HU-52 Elegir Free o prueba Premium | HU-01 | S1 | 5 |
| HU-53 Compra mensual, anual y Lifetime | HU-52 | S3 | 8 |
| HU-54 Verificación y reconocimiento de compras | HU-01, HU-52 | S3 | 8 |
| HU-55 Reconciliación y restauración de compras | HU-54, HU-56 | S5 | 13 |
| HU-56 Estados de compra y cancelación | HU-54 | S3 | 5 |
| HU-57 Cupos, selección y downgrade | HU-01, HU-52 | S2 | 13 |
| HU-58 Acceso Free y Premium por capacidad | HU-52, HU-57 | S4 | 8 |
| HU-59 Concesión offline y expiración | HU-54, HU-58 | S4 | 8 |
| HU-60 Mi plan, Lifetime y auditoría | HU-53, HU-54, HU-55, HU-56, HU-58, HU-59 | S10 | 8 |

---

## HU-01 | Registro e inicio de sesión

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | Ninguna | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | Ninguna | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-02, HU-03, HU-04, HU-05, HU-06, HU-07, HU-14, HU-15, HU-42, HU-52, HU-54, HU-57 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. La aplicación está instalada; el usuario está en Acceso y dispone de conexión para autenticarse.

### Descripción Ágil

Como usuario de Kipu, quiero crear mi cuenta e iniciar sesión con correo y contraseña, para acceder a mis finanzas de forma privada.

### Reglas de Negocio

1. Solo se usan correo y contraseña de Kipu, nunca credenciales bancarias.
2. No se muestra información de otra sesión; cada usuario tiene datos separados.
3. Un fallo de autenticación no crea una sesión local válida.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Registro
- **Dado que** el correo cumple el formato y la contraseña cumple la política configurada,
- **Cuando** solicita crear la cuenta,
- **Entonces** se procesa el registro y se indica si debe verificar el correo antes de ingresar.

#### Escenario 2: Acceso correcto
- **Dado que** la cuenta está habilitada y las credenciales son válidas,
- **Cuando** inicia sesión,
- **Entonces** accede a su espacio y no al de otro usuario.

#### Escenario 3: Credenciales erróneas
- **Dado que** la contraseña no corresponde a la cuenta,
- **Cuando** intenta acceder,
- **Entonces** recibe un error neutro y no obtiene acceso.

#### Escenario 4: Sin conexión
- **Dado que** no existe una sesión local previa,
- **Cuando** intenta iniciar sesión sin red,
- **Entonces** se explica la necesidad de conexión y se conserva el formulario sin guardar la contraseña en logs.

---

## HU-02 | Recuperación y ciclo de sesión

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | Ninguna | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. El usuario está en Recuperar acceso o tiene una sesión propia iniciada.

### Descripción Ágil

Como usuario de Kipu, quiero recuperar mi acceso y gestionar mi sesión, para volver a entrar sin exponer mi identidad.

### Reglas de Negocio

1. La respuesta de recuperación no revela si existe el correo.
2. El enlace tiene caducidad definida por la configuración de autenticación y es de un solo uso.
3. Cerrar sesión no promete recuperar cambios que nunca se sincronizaron; se advierten pendientes.
4. Una sesión renovada no cambia de propietario.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Correo registrado
- **Dado que** existe el correo ingresado,
- **Cuando** solicita recuperación,
- **Entonces** se envían instrucciones y se muestra confirmación neutra.

#### Escenario 2: Correo inexistente
- **Dado que** el correo no está registrado,
- **Cuando** solicita recuperación,
- **Entonces** se muestra la misma confirmación neutra sin enviar instrucciones.

#### Escenario 3: Enlace válido
- **Dado que** el enlace está vigente y no ha sido usado,
- **Cuando** confirma una contraseña que cumple la política,
- **Entonces** la clave se actualiza y permite iniciar sesión.

#### Escenario 4: Enlace inválido
- **Dado que** el enlace venció o fue alterado,
- **Cuando** lo abre,
- **Entonces** se rechaza el cambio y se ofrece pedir otro enlace.

#### Escenario 5: Sesión persistente
- **Dado que** la sesión propia sigue siendo válida,
- **Cuando** cierra y abre la app,
- **Entonces** recupera su espacio sin exponer otra cuenta.

#### Escenario 6: Cierre con pendientes
- **Dado que** hay cambios locales no enviados,
- **Cuando** solicita cerrar sesión,
- **Entonces** se advierten los pendientes antes de confirmar y no se muestran datos al siguiente usuario.

---

## HU-03 | Desbloqueo local con biometría

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Media |
| **Puntos de Historia** | 3 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-02 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una sesión propia y el dispositivo informa sus capacidades de autenticación local.

### Descripción Ágil

Como usuario de Kipu, quiero habilitar un desbloqueo biométrico opcional, para proteger el acceso local a mis saldos.

### Reglas de Negocio

1. Es opcional y no sustituye el inicio remoto.
2. La app no almacena plantillas biométricas.
3. Si no hay hardware o registro compatible se ofrece mantener el acceso protegido mediante credencial admitida del dispositivo.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Activación
- **Dado que** el dispositivo tiene biometría configurada,
- **Cuando** activa la protección y se autentica,
- **Entonces** la preferencia queda guardada.

#### Escenario 2: Desbloqueo
- **Dado que** la protección está activa,
- **Cuando** regresa a la app y supera la autenticación local,
- **Entonces** se muestran los datos de su sesión.

#### Escenario 3: Cancelación
- **Dado que** aparece el diálogo biométrico,
- **Cuando** cancela o falla,
- **Entonces** los saldos permanecen ocultos y puede reintentar.

#### Escenario 4: Sin compatibilidad
- **Dado que** no hay biometría disponible,
- **Cuando** abre la preferencia,
- **Entonces** se explica la limitación sin bloquear el registro manual de forma permanente.

---

## HU-04 | Preferencias y privacidad de saldos

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 3 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | Ninguna | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. El usuario accedió a Configuración de su cuenta.

### Descripción Ágil

Como usuario de Kipu, quiero configurar moneda, inicio de mes, tema y ocultamiento de montos, para adaptar la presentación a mi uso diario.

### Reglas de Negocio

1. PEN es la moneda inicial; cambiar la preferencia no convierte movimientos antiguos.
2. La máscara oculta valores sin modificar cálculos.
3. La personalización ordinaria permanece en Free.
4. La fecha inicial del mes debe ser válida.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Guardar preferencias
- **Dado que** el usuario elige un inicio de mes válido,
- **Cuando** guarda y reabre la aplicación,
- **Entonces** se mantienen las preferencias.

#### Escenario 2: Ocultar saldos
- **Dado que** Inicio muestra importes,
- **Cuando** activa la máscara,
- **Entonces** los importes se ocultan sin modificar sus valores.

#### Escenario 3: Cambio de moneda
- **Dado que** existen movimientos en PEN,
- **Cuando** elige otra moneda principal,
- **Entonces** los registros conservan su moneda original sin conversión ficticia.

#### Escenario 4: Día inválido
- **Dado que** el inicio solicitado no es válido,
- **Cuando** intenta guardar,
- **Entonces** se señala el campo y se conserva la configuración anterior.

---

## HU-05 | Permisos y fuentes de automatización

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 3 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-52, HU-58 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-45 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-45 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. El usuario abrió Configuración; la lectura de notificaciones permanece apagada hasta estar autorizada.

### Descripción Ágil

Como usuario de Kipu, quiero conocer y configurar los permisos de captura, para decidir qué señales puede procesar Kipu.

### Reglas de Negocio

1. Explicar beneficio, alcance y límites antes de pedir acceso.
2. El permiso de avisos propios es distinto del acceso a notificaciones de otras apps.
3. Free puede consultar ajustes; no activar nueva captura.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Explicación
- **Dado que** no se concedió acceso al listener,
- **Cuando** abre el ajuste de captura,
- **Entonces** ve qué se leería y cómo revocarlo antes de ir a ajustes Android.

#### Escenario 2: Rechazo
- **Dado que** Android ofrece conceder acceso,
- **Cuando** el usuario lo rechaza,
- **Entonces** Kipu mantiene disponibles las operaciones manuales.

#### Escenario 3: Usuario Free
- **Dado que** el plan no permite captura,
- **Cuando** intenta activar una fuente,
- **Entonces** se explica Premium y no se procesa contenido.

#### Escenario 4: Revocación
- **Dado que** existía autorización de una fuente,
- **Cuando** la desactiva,
- **Entonces** las señales posteriores se ignoran y se conserva el historial legítimo.

---

## HU-06 | Portabilidad, eliminación de cuenta y restauración

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-APS - Acceso, Perfil y Seguridad |
| **Responsable de Épica** | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S9 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01, HU-07, HU-18 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-19, HU-55 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-59 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una cuenta propia; la exportación usa datos accesibles y la restauración remota requiere conexión.

### Descripción Ágil

Como usuario de Kipu, quiero exportar mis datos, solicitar su eliminación o recuperarlos en otro dispositivo, para mantener el control de mi información.

### Reglas de Negocio

1. Exportación CSV/JSON de datos personales, no informes PDF/Excel.
2. Solo se restaura información efectivamente sincronizada.
3. La eliminación de cuenta es un flujo distinto de borrar un presupuesto y exige reautenticación y confirmación explícita.
4. Eliminar Kipu no cancela por sí solo una suscripción de Play: se explica cómo gestionarla.
5. No se incluyen secretos ni datos ajenos en exportaciones.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Exportación
- **Dado que** hay movimientos propios,
- **Cuando** solicita CSV o JSON,
- **Entonces** obtiene sus datos con moneda, fechas e identificadores sin tokens.

#### Escenario 2: Restauración
- **Dado que** los cambios están sincronizados,
- **Cuando** accede desde otro teléfono y restaura,
- **Entonces** recupera los mismos movimientos sin duplicados.

#### Escenario 3: Pendientes locales
- **Dado que** hay datos que nunca se enviaron,
- **Cuando** consulta restauración,
- **Entonces** se advierte que esos cambios no están disponibles remotamente.

#### Escenario 4: Cancelar eliminación
- **Dado que** se muestra el resumen de pérdida de datos,
- **Cuando** cancela,
- **Entonces** no se elimina nada.

#### Escenario 5: Eliminar cuenta
- **Dado que** superó reautenticación y confirmó la pérdida de datos,
- **Cuando** ejecuta la solicitud,
- **Entonces** se procesa la eliminación de datos personales aplicable y se informa su estado sin simular éxito ante fallos.

#### Escenario 6: Fallo remoto
- **Dado que** se interrumpe el servicio durante restauración o eliminación,
- **Cuando** consulta el resultado,
- **Entonces** se muestra pendiente o error reintentable y no se afirma haber terminado.

---

## HU-07 | Administración de cuentas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-19 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-06, HU-08, HU-12, HU-18, HU-26, HU-27, HU-39 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sesión propia; formulario de cuentas disponible y política de cupos cargada.

### Descripción Ágil

Como usuario de Kipu, quiero crear y administrar mis cuentas y billeteras, para saber dónde está mi dinero.

### Reglas de Negocio

1. Alias, tipo, moneda y saldo inicial auditable; preset o genérico.
2. Efectivo y Cuenta Metas virtual no consumen cupo de instrumentos.
3. Modificar alias no cambia el saldo; un ajuste de apertura debe quedar trazado.
4. Cuenta con historia se archiva, no borra sus movimientos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Crear cuenta
- **Dado que** hay cupo y saldo inicial de S/100,
- **Cuando** guarda una cuenta bancaria,
- **Entonces** aparece con S/100 y permanece tras reiniciar offline.

#### Escenario 2: Preset genérico
- **Dado que** no existe el banco en el catálogo,
- **Cuando** elige tarjeta o cuenta genérica,
- **Entonces** puede personalizar alias e icono sin inventar afiliación oficial.

#### Escenario 3: Límite
- **Dado que** Free ya ocupa cuatro instrumentos,
- **Cuando** intenta crear otro limitado,
- **Entonces** se informa el cupo y no se crea un quinto activo.

#### Escenario 4: Edición
- **Dado que** existen movimientos en la cuenta,
- **Cuando** cambia su alias,
- **Entonces** la historia conserva sus vínculos y saldos.

#### Escenario 5: Archivo
- **Dado que** la cuenta contiene operaciones,
- **Cuando** solicita retirarla del uso,
- **Entonces** se conserva la historia y se impide seleccionarla para nuevas operaciones.

---

## HU-08 | Registro y vínculo de tarjetas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-09, HU-13 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-09, HU-11, HU-13, HU-48 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una cuenta propia compatible y cupo de instrumentos.

### Descripción Ágil

Como usuario de Kipu, quiero registrar mis tarjetas de débito y crédito, para identificarlas sin guardar datos bancarios sensibles.

### Reglas de Negocio

1. Guardar solo alias, emisor, red, tipo y últimos cuatro dígitos.
2. Débito reutiliza el saldo de la cuenta vinculada.
3. La cuenta interna contable de crédito no consume un segundo cupo.
4. PAN completo y CVV nunca se solicitan.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Débito vinculado
- **Dado que** el banco tiene S/300,
- **Cuando** registra su débito,
- **Entonces** ambos muestran el mismo activo sin sumar S/600.

#### Escenario 2: Crédito
- **Dado que** hay cupo de instrumento,
- **Cuando** registra una tarjeta de crédito,
- **Entonces** se crea su vínculo contable sin añadir dinero líquido.

#### Escenario 3: Datos sensibles
- **Dado que** el usuario intenta ingresar más de cuatro dígitos en identificación,
- **Cuando** guarda,
- **Entonces** el campo rechaza el formato y no almacena el número completo.

#### Escenario 4: Ambigüedad
- **Dado que** dos tarjetas comparten últimos cuatro dígitos,
- **Cuando** selecciona un instrumento,
- **Entonces** se muestran alias y emisor para elegir sin adivinar.

---

## HU-09 | Línea y saldos de crédito

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-08, HU-18, HU-19 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-10, HU-12, HU-13 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-10, HU-12, HU-40, HU-41, HU-44 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Tarjeta propia registrada y ledger manual operativo.

### Descripción Ágil

Como usuario de Kipu, quiero configurar línea, cierre y vencimiento y consultar crédito utilizado, para distinguir deuda de dinero propio.

### Reglas de Negocio

1. Crédito utilizado no se suma al dinero disponible.
2. Días 29 a 31 se ajustan al último día del mes cuando corresponda.
3. Línea cero no permite dividir por cero.
4. Pago mínimo es dato de planificación, no cobro automático.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Utilización
- **Dado que** la línea es S/1000 y la deuda S/200,
- **Cuando** consulta el detalle,
- **Entonces** ve utilizado S/200 y crédito disponible S/800 separados del banco.

#### Escenario 2: Febrero
- **Dado que** el cierre configurado es día 31,
- **Cuando** consulta febrero,
- **Entonces** el evento usa el último día válido de ese mes.

#### Escenario 3: Línea cero
- **Dado que** no hay línea informada,
- **Cuando** consulta porcentaje,
- **Entonces** ve No disponible sin error matemático.

#### Escenario 4: Cambio de límite
- **Dado que** la deuda vigente es S/500,
- **Cuando** reduce la línea a S/400,
- **Entonces** la deuda no desaparece y se muestra sobreutilización.

---

## HU-10 | Umbrales de crédito

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 3 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-09 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-42 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Tarjeta configurada; permisos de avisos disponibles o rechazados explícitamente.

### Descripción Ágil

Como usuario de Kipu, quiero configurar alertas de utilización, para advertir cuándo estoy cerca de mi límite.

### Reglas de Negocio

1. Umbrales iniciales 50, 80 y 100 por ciento, editables.
2. No repetir la misma condición sin un nuevo cruce válido.
3. Un aviso no bloquea registrar un hecho financiero.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Cruce
- **Dado que** la utilización pasa de 49 a 51 por ciento,
- **Cuando** confirma la compra,
- **Entonces** se genera una alerta de 50 por ciento vinculada a la tarjeta.

#### Escenario 2: Sin cruce nuevo
- **Dado que** ya se notificó ese umbral,
- **Cuando** reabre el detalle,
- **Entonces** no se repite el aviso.

#### Escenario 3: Editar umbrales
- **Dado que** ingresa porcentajes válidos y ordenados,
- **Cuando** guarda,
- **Entonces** se aplican a futuras evaluaciones.

#### Escenario 4: Avisos denegados
- **Dado que** Android no permite mostrar notificaciones,
- **Cuando** ocurre el cruce,
- **Entonces** el registro financiero se guarda y el aviso queda consultable dentro de Kipu.

---

## HU-11 | Catálogo y tasas referenciales

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Media |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-08 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-09, HU-13 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Detalle de tarjeta abierto; catálogo con fuente y fecha o fallback genérico.

### Descripción Ágil

Como usuario de Kipu, quiero consultar productos y configurar mi tasa, para obtener estimaciones identificables y ajustables.

### Reglas de Negocio

1. Las tasas son referenciales, no promesa bancaria.
2. La tasa personal prevalece para esa tarjeta.
3. No descargar ni inventar tasas sin evidencia.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Catálogo
- **Dado que** el producto tiene fuente y fecha,
- **Cuando** lo consulta,
- **Entonces** ve ambos datos y la advertencia de estimación.

#### Escenario 2: Tasa personal
- **Dado que** el usuario conoce su TEA,
- **Cuando** la guarda,
- **Entonces** se usa para futuras simulaciones sin cambiar compras previas.

#### Escenario 3: Sin producto
- **Dado que** no hay coincidencia en catálogo,
- **Cuando** elige genérico,
- **Entonces** puede continuar configurando la tarjeta.

#### Escenario 4: Tasa inválida
- **Dado que** se ingresa texto o una tasa negativa,
- **Cuando** guarda,
- **Entonces** se rechaza con explicación sin alterar la tasa anterior.

---

## HU-12 | Pago de tarjeta

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-09, HU-18, HU-19 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-13 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe deuda de tarjeta y cuenta origen propia activa.

### Descripción Ágil

Como usuario de Kipu, quiero registrar el pago de mi tarjeta desde una cuenta, para reducir mi deuda sin duplicar el gasto.

### Reglas de Negocio

1. Es transferencia amortizadora: reduce banco y pasivo.
2. Ambos efectos se confirman juntos.
3. No pagar principal mayor al pendiente sin flujo explícito de corrección.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Pago completo
- **Dado que** hay S/100 de deuda y S/300 en banco,
- **Cuando** paga S/100,
- **Entonces** banco queda en S/200, deuda en cero y gasto operativo no aumenta.

#### Escenario 2: Pago parcial
- **Dado que** debe S/100,
- **Cuando** registra pago de S/40,
- **Entonces** deuda queda en S/60 y cuenta disminuye S/40.

#### Escenario 3: Reintento
- **Dado que** el comando del pago ya fue confirmado,
- **Cuando** lo reenvía,
- **Entonces** recibe el mismo resultado sin otro descuento.

#### Escenario 4: Fallo
- **Dado que** se interrumpe el guardado de los efectos,
- **Cuando** consulta los saldos,
- **Entonces** no existe media transferencia ni pago parcial accidental.

---

## HU-13 | Compras con crédito y cuotas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CTA - Cuentas y Tarjetas |
| **Responsable de Épica** | Integrante 6 - Desarrollo backend y sincronización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-08, HU-18, HU-19, HU-23 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-11 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-48 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-48 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Tarjeta de crédito activa; ledger manual disponible; la captura reutilizará este flujo.

### Descripción Ágil

Como usuario de Kipu, quiero registrar compras directas o en cuotas, para planificar pagos sin duplicar el gasto.

### Reglas de Negocio

1. Reconocer principal una sola vez.
2. Cuotas solo para crédito; estimaciones no son gastos reales.
3. Distribuir céntimos de redondeo preservando la suma.
4. Toda compra detectada con crédito requiere confirmación antes de afectar saldos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Sin intereses
- **Dado que** la compra es S/100 en tres cuotas,
- **Cuando** confirma,
- **Entonces** gasto es S/100 una vez y cuotas 33.34, 33.33 y 33.33.

#### Escenario 2: Con intereses
- **Dado que** se informa tasa y plazo válidos,
- **Cuando** simula cuotas,
- **Entonces** ve método, redondeo y condición referencial sin registrar intereses reales automáticamente.

#### Escenario 3: Instrumento incorrecto
- **Dado que** selecciona débito,
- **Cuando** solicita cuotas,
- **Entonces** no se habilita esa opción.

#### Escenario 4: Pendiente detectada
- **Dado que** un candidato de crédito tiene confianza alta,
- **Cuando** se recibe para revisión,
- **Entonces** no modifica saldos hasta confirmar directo o cuotas.

#### Escenario 5: Plazo inválido
- **Dado que** el número de cuotas es cero,
- **Cuando** intenta guardar,
- **Entonces** se solicita un número válido y no se crea la compra.

---

## HU-14 | Categorías y subcategorías

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CCO - Categorías, Subcategorías y Comercios |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-17, HU-36 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-17, HU-18, HU-36 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sesión propia y catálogo inicial disponible.

### Descripción Ágil

Como usuario de Kipu, quiero organizar y editar categorías en dos niveles, para clasificar mis movimientos con claridad.

### Reglas de Negocio

1. Máximo dos niveles y sin ciclos.
2. Nombre, icono y color editables también en Free.
3. Cinco categorías personalizadas raíz activas en Free; sus subcategorías heredan disponibilidad.

   **Aclaración existente por tipo:** [EP-CCO](../../specs/003-ep-cco-categorias-comercios/spec.md) documenta 5 de gastos y 5 de ingresos. La redacción y los escenarios de origen que omiten el tipo requieren explicitar ese contexto; esta integración no introduce otro cupo.
4. No existen etiquetas ni tags en formularios, filtros o datos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Crear subcategoría
- **Dado que** Alimentación es raíz,
- **Cuando** crea Restaurantes debajo,
- **Entonces** aparece como segundo nivel.

#### Escenario 2: Tercer nivel
- **Dado que** Restaurantes ya es subcategoría,
- **Cuando** intenta agregarle un hijo,
- **Entonces** se rechaza sin modificar el árbol.

#### Escenario 3: Editar catálogo
- **Dado que** una categoría predeterminada tiene movimientos,
- **Cuando** cambia nombre e icono,
- **Entonces** se actualiza su presentación y conserva origen e historia.

#### Escenario 4: Cupo
- **Dado que** Free tiene cinco raíces personalizadas,
- **Cuando** crea otra,
- **Entonces** se explica el límite sin bloquear predeterminadas.

#### Escenario 5: Sin etiquetas
- **Dado que** abre registro y búsqueda,
- **Cuando** revisa los campos,
- **Entonces** solo encuentra categorías, subcategorías y comercio, no tags.

---

## HU-15 | Comercios y servicios

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CCO - Categorías, Subcategorías y Comercios |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-18 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-16, HU-17 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-16, HU-17 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Formulario de movimiento o selector de comercios disponible.

### Descripción Ágil

Como usuario de Kipu, quiero buscar y asignar comercios a mis movimientos, para identificar dónde gasté sin confundirlo con la categoría.

### Reglas de Negocio

1. Comercio y categoría son relaciones independientes.
2. Catálogo general controlado por Kipu.
3. No inventar coincidencias cuando falta evidencia.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Asignación
- **Dado que** existe Tambo en el catálogo,
- **Cuando** lo selecciona en un gasto,
- **Entonces** se conserva el comercio junto con la categoría elegida.

#### Escenario 2: Cambiar categoría
- **Dado que** el gasto pertenece a Tambo y Alimentación,
- **Cuando** elige otra categoría,
- **Entonces** Tambo permanece asignado.

#### Escenario 3: Sin resultado
- **Dado que** busca una marca desconocida,
- **Cuando** termina la búsqueda,
- **Entonces** se muestra vacío y permite conservar texto provisional.

#### Escenario 4: Búsqueda
- **Dado que** hay nombres similares,
- **Cuando** escribe un fragmento,
- **Entonces** obtiene coincidencias seleccionables sin asignación automática ambigua.

---

## HU-16 | Texto original y alias de comercio

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CCO - Categorías, Subcategorías y Comercios |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-15 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-46, HU-49, HU-50 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-50 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen candidatos o textos de prueba propios y reglas versionadas.

### Descripción Ágil

Como usuario de Kipu, quiero normalizar nombres usando alias, para reconocer un comercio sin perder la señal original.

### Reglas de Negocio

1. Texto fuente y nombre canónico se guardan separados.
2. Un alias no modifica el catálogo de todos los usuarios.
3. Las reglas de nueva captura exigen Premium.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Alias conocido
- **Dado que** existe IZIPAY*TAMBO hacia Tambo,
- **Cuando** se normaliza ese texto,
- **Entonces** se propone Tambo y se mantiene la cadena original.

#### Escenario 2: Sin coincidencia
- **Dado que** ningún alias coincide,
- **Cuando** procesa la señal,
- **Entonces** conserva el nombre provisional para revisión.

#### Escenario 3: Ambigüedad
- **Dado que** dos reglas coinciden con distintos comercios,
- **Cuando** evalúa el texto,
- **Entonces** solicita revisión sin elegir arbitrariamente.

#### Escenario 4: Free
- **Dado que** el plan expiró,
- **Cuando** llega otra señal,
- **Entonces** no se ejecuta nueva captura ni reglas sobre contenido no autorizado.

---

## HU-17 | Preferencias personales de categoría

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-CCO - Categorías, Subcategorías y Comercios |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-14, HU-15 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-16 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-50 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-50 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen categorías utilizables y un comercio identificado.

### Descripción Ágil

Como usuario de Kipu, quiero definir reglas personales por comercio, para reutilizar mis decisiones de clasificación.

### Reglas de Negocio

1. Regla explícita del usuario prevalece sobre sugerencia general.
2. No reclasificar la historia al cambiar una preferencia.
3. No usar una categoría bloqueada en nuevas operaciones.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Preferencia
- **Dado que** el usuario confirmó Tambo como Alimentación,
- **Cuando** llega otro candidato compatible,
- **Entonces** se sugiere su preferencia.

#### Escenario 2: Precedencia
- **Dado que** la sugerencia general difiere de su regla explícita,
- **Cuando** se clasifica el candidato,
- **Entonces** prevalece la regla personal.

#### Escenario 3: Cambio
- **Dado que** modifica su preferencia a otra categoría,
- **Cuando** consulta movimientos anteriores,
- **Entonces** siguen con su clasificación original.

#### Escenario 4: Categoría bloqueada
- **Dado que** la preferencia apunta a una raíz bloqueada,
- **Cuando** intenta aplicar la regla,
- **Entonces** se solicita elegir una categoría activa.

---

## HU-18 | Registro manual de movimientos

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-14 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-15 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-19, HU-23 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-06, HU-09, HU-12, HU-13, HU-19, HU-20, HU-21, HU-22, HU-23, HU-24, HU-25, HU-28, HU-31, HU-33, HU-36, HU-39, HU-47 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Cuenta propia activa; categorías disponibles cuando aplican.

### Descripción Ágil

Como usuario de Kipu, quiero registrar gastos, ingresos y transferencias, para mantener mis finanzas actualizadas incluso sin internet.

### Reglas de Negocio

1. Solo tres tipos principales: Gasto, Ingreso y Transferencia.
2. Monto e instrumento obligatorios; fecha, nota y opciones en Detalles adicionales.
3. Transferencia exige cuenta origen de Kipu.
4. No registrar el mismo comando dos veces.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Gasto
- **Dado que** hay S/100 en la cuenta,
- **Cuando** guarda gasto de S/20 con categoría,
- **Entonces** aparece el gasto y quedan S/80.

#### Escenario 2: Ingreso
- **Dado que** hay S/100 en la cuenta,
- **Cuando** registra ingreso operativo de S/30,
- **Entonces** saldo queda S/130 e ingresos aumentan S/30.

#### Escenario 3: Transferencia
- **Dado que** hay dos cuentas de la misma moneda,
- **Cuando** transfiere S/40,
- **Entonces** origen baja y destino sube S/40 sin ingreso ni gasto.

#### Escenario 4: Campos incompletos
- **Dado que** falta instrumento o monto válido,
- **Cuando** guarda,
- **Entonces** se marcan campos y no se crea movimiento.

#### Escenario 5: Offline
- **Dado que** las referencias existen localmente,
- **Cuando** registra sin conexión y reinicia,
- **Entonces** el movimiento confirmado permanece disponible.

---

## HU-19 | Saldos inmediatos y atomicidad

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-20, HU-21, HU-25 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-09, HU-12, HU-13, HU-20, HU-21, HU-24, HU-25, HU-26, HU-27, HU-28, HU-31, HU-39 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe un comando financiero validado para cuentas propias.

### Descripción Ágil

Como usuario de Kipu, quiero ver saldos actualizados al confirmar, para confiar en mis cifras sin esperar la red.

### Reglas de Negocio

1. Evento, efectos y cola de envío se guardan juntos.
2. Dinero en céntimos enteros, no coma flotante.
3. El servidor no confirma medias transferencias.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Actualización
- **Dado que** la cuenta tiene S/50,
- **Cuando** confirma gasto S/10,
- **Entonces** ve S/40 inmediatamente sin esperar sincronización.

#### Escenario 2: Interrupción
- **Dado que** una transferencia requiere dos efectos,
- **Cuando** falla antes de confirmar la transacción,
- **Entonces** ninguna cuenta queda modificada parcialmente.

#### Escenario 3: Reapertura
- **Dado que** se confirmó el guardado,
- **Cuando** el proceso termina y se reabre,
- **Entonces** los efectos permanecen completos.

#### Escenario 4: Redondeo
- **Dado que** se registran 10 operaciones de S/0.10,
- **Cuando** consulta la suma,
- **Entonces** el total es exactamente S/1.00.

---

## HU-20 | Edición de movimientos

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S4 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-21, HU-22 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe un movimiento propio editable con revisión conocida.

### Descripción Ágil

Como usuario de Kipu, quiero corregir mis movimientos, para mantener saldos e historia coherentes.

### Reglas de Negocio

1. Conservar revisión previa suficiente para resolver sincronización.
2. Una corrección recalcula efectos, presupuesto y métricas.
3. Objetos bloqueados pueden conservarse como relación histórica; no habilitan nuevas operaciones.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Corregir monto
- **Dado que** un gasto de S/20 debió ser S/15,
- **Cuando** guarda corrección,
- **Entonces** la cuenta recupera S/5 y el gasto neto queda en S/15.

#### Escenario 2: Fecha
- **Dado que** el gasto estaba en un periodo equivocado,
- **Cuando** corrige la fecha,
- **Entonces** consumos de ambos periodos se recalculan.

#### Escenario 3: Conflicto
- **Dado que** otro dispositivo cambió la misma revisión,
- **Cuando** envía una edición incompatible,
- **Entonces** se muestra conflicto sin mezclar importes.

#### Escenario 4: Relación bloqueada
- **Dado que** la categoría original quedó bloqueada por plan,
- **Cuando** corrige la nota del movimiento,
- **Entonces** se permite conservar la relación sin reclasificarlo.

---

## HU-21 | Anulación y borrado lógico

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S4 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-20, HU-25 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe un movimiento propio confirmado.

### Descripción Ágil

Como usuario de Kipu, quiero anular movimientos equivocados, para corregir mis cifras sin romper sus relaciones.

### Reglas de Negocio

1. Eliminar un movimiento es borrado lógico, no destrucción del ledger.
2. Anulación excluye efectos válidos en métricas y conserva referencias.
3. Un reintento no anula dos veces.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Anular gasto
- **Dado que** se descontaron S/20 por error,
- **Cuando** confirma anulación,
- **Entonces** se revierte el efecto y se excluye de gastos operativos.

#### Escenario 2: Cancelar acción
- **Dado que** la app pide confirmar anulación,
- **Cuando** elige volver,
- **Entonces** no cambia el movimiento.

#### Escenario 3: Transferencia
- **Dado que** existe transferencia entre dos cuentas,
- **Cuando** la anula,
- **Entonces** se revierten juntos ambos efectos.

#### Escenario 4: Sync retrasado
- **Dado que** el movimiento ya está anulado,
- **Cuando** llega una edición anterior,
- **Entonces** no resucita silenciosamente el registro.

---

## HU-22 | Búsqueda y filtros

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S4 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-39, HU-41 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe historial propio o estado vacío.

### Descripción Ágil

Como usuario de Kipu, quiero encontrar movimientos mediante búsqueda y filtros, para revisar mi historia y analizar operaciones.

### Reglas de Negocio

1. Free conserva todo el historial legible, búsqueda textual, fechas y filtro G/I/T.
2. Premium habilita combinación por cuenta, tarjeta, categoría, comercio, monto, fuente y estado.
3. La política se valida también al abrir un enlace directo.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Búsqueda Free
- **Dado que** existe un movimiento antiguo de Tambo,
- **Cuando** busca Tambo en Free,
- **Entonces** lo encuentra aunque no sea del mes actual.

#### Escenario 2: Filtros Premium
- **Dado que** tiene Premium y gastos en distintas cuentas,
- **Cuando** combina cuenta, categoría y rango,
- **Entonces** solo ve los que satisfacen todas las condiciones.

#### Escenario 3: Sin resultados
- **Dado que** ningún registro cumple la búsqueda,
- **Cuando** aplica filtros,
- **Entonces** ve estado vacío y opción de limpiarlos.

#### Escenario 4: Enlace restringido
- **Dado que** Free abre un enlace con filtros avanzados,
- **Cuando** intenta ejecutarlo,
- **Entonces** se explica el beneficio y se ofrece el historial básico.

---

## HU-23 | Detección de duplicados

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-46, HU-49 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-13, HU-47, HU-49 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe un nuevo comando o candidato con identidad de origen.

### Descripción Ágil

Como usuario de Kipu, quiero recibir advertencias de movimientos repetidos, para evitar descontar dinero dos veces.

### Reglas de Negocio

1. Clave exacta repetida devuelve resultado previo.
2. Similitud no equivale a identidad: dos compras reales iguales pueden coexistir.
3. Dedupe de integridad funciona en ambos planes.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Doble toque
- **Dado que** el primer envío fue guardado,
- **Cuando** repite el mismo comando,
- **Entonces** no se crea una segunda operación.

#### Escenario 2: Parecido
- **Dado que** existe compra similar cercana,
- **Cuando** intenta guardar otra,
- **Entonces** se advierte la coincidencia antes de confirmar.

#### Escenario 3: Compras distintas
- **Dado que** son dos compras reales del mismo monto,
- **Cuando** confirma que son diferentes,
- **Entonces** se conservan ambas con identidades distintas.

#### Escenario 4: Free
- **Dado que** la cuenta usa Free,
- **Cuando** se reenvía un comando confirmado,
- **Entonces** la protección sigue evitando duplicados.

---

## HU-24 | Aplicar movimientos a obligaciones y metas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-28, HU-32 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Deuda, préstamo o meta propios ya existen y están utilizables.

### Descripción Ágil

Como usuario de Kipu, quiero vincular pagos, cobros y transferencias a mis objetivos, para evitar duplicar gastos o ingresos.

### Reglas de Negocio

1. Gasto aplicado a deuda reduce principal sin gasto operativo.
2. Ingreso aplicado a préstamo reduce por cobrar sin ingreso operativo.
3. Aporte se realiza desde Transferencia, no como cuarto tipo.
4. Las relaciones y efectos se confirman juntos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Pago de deuda
- **Dado que** el pendiente es S/100,
- **Cuando** aplica Gasto de S/30 a principal,
- **Entonces** pendiente queda S/70 sin aumentar gasto operativo.

#### Escenario 2: Cobro
- **Dado que** hay S/100 por cobrar,
- **Cuando** aplica Ingreso de S/20 al préstamo,
- **Entonces** cuenta aumenta S/20 y por cobrar baja a S/80.

#### Escenario 3: Aporte
- **Dado que** existe meta y S/200 libres,
- **Cuando** elige Transferencia, Cuenta Metas y objetivo por S/50,
- **Entonces** reserva S/50 sin crear gasto.

#### Escenario 4: Objeto bloqueado
- **Dado que** la meta no tiene cupo activo,
- **Cuando** intenta un aporte nuevo,
- **Entonces** se explica cómo seleccionar cupo antes de guardar.

---

## HU-25 | Reembolsos vinculados

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MOV - Movimientos y Ledger |
| **Responsable de Épica** | Integrante 4 - Desarrollo de reglas financieras |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19, HU-36 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-37, HU-41 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-37 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una compra propia con saldo reembolsable.

### Descripción Ágil

Como usuario de Kipu, quiero registrar devoluciones totales o parciales, para corregir gastos sin crear ingresos falsos.

### Reglas de Negocio

1. Reembolso siempre vinculado al origen.
2. No superar monto pendiente de reembolso.
3. Caja cambia en fecha real; gasto y presupuesto se ajustan al periodo original.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Parcial
- **Dado que** la compra original es S/100,
- **Cuando** registra devolución S/40,
- **Entonces** gasto neto queda S/60 y cuenta recupera S/40.

#### Escenario 2: Total
- **Dado que** no hubo devoluciones anteriores,
- **Cuando** reembolsa los S/100,
- **Entonces** el gasto neto queda en cero.

#### Escenario 3: Exceso
- **Dado que** ya devolvieron S/40 de S/100,
- **Cuando** intenta devolver S/70,
- **Entonces** se rechaza porque solo quedan S/60.

#### Escenario 4: Entre periodos
- **Dado que** la compra fue en septiembre y devolución en octubre,
- **Cuando** registra devolución,
- **Entonces** caja cambia en octubre y la imputación original se ajusta explicando ambas fechas.

---

## HU-26 | Deudas propias: creación y administración

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-DEU - Deudas y Préstamos |
| **Responsable de Épica** | Integrante 5 - Desarrollo de persistencia local |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-19, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-28, HU-29 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-24, HU-28, HU-29, HU-41 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sesión propia; cupo disponible para deuda nueva.

### Descripción Ágil

Como usuario de Kipu, quiero registrar y mantener mis deudas propias, para conocer capital pendiente y condiciones.

### Reglas de Negocio

1. Preguntar si la deuda generó dinero recibido.
2. Si hubo desembolso, cuenta y pasivo aumentan sin ingreso operativo.
3. Editar alias o nota no altera capital; correcciones financieras son auditables.
4. Eliminar físicamente solo sin operaciones relacionadas; con historia se cierra o archiva.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Desembolso nuevo
- **Dado que** recibe préstamo S/500,
- **Cuando** crea deuda indicando cuenta receptora,
- **Entonces** banco y pasivo aumentan S/500 sin ingreso operativo.

#### Escenario 2: Histórica
- **Dado que** la deuda existe pero no recibió dinero ahora,
- **Cuando** elige no generar entrada,
- **Entonces** se registra pasivo sin inflar banco.

#### Escenario 3: Edición
- **Dado que** hay pagos previos,
- **Cuando** cambia alias y nota,
- **Entonces** conserva pagos y pendiente.

#### Escenario 4: Eliminar sin operaciones
- **Dado que** la deuda de registro histórico no tiene movimientos relacionados,
- **Cuando** confirma eliminación,
- **Entonces** se retira el objeto y su efecto de apertura de forma consistente.

#### Escenario 5: Eliminar con pagos
- **Dado que** existen pagos vinculados,
- **Cuando** intenta borrarla físicamente,
- **Entonces** se impide la pérdida de historia y se ofrece gestionar cierre.

---

## HU-27 | Dinero prestado: creación y administración

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-DEU - Deudas y Préstamos |
| **Responsable de Épica** | Integrante 5 - Desarrollo de persistencia local |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-19, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-28, HU-29 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-24, HU-28, HU-29 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una cuenta origen propia activa y cupo para el préstamo.

### Descripción Ágil

Como usuario de Kipu, quiero registrar el dinero que presto, para controlar cuánto me deben.

### Reglas de Negocio

1. Cuenta origen obligatoria; no preguntar si salió de Kipu.
2. Prestar reduce banco y aumenta por cobrar sin gasto operativo.
3. Un desembolso relacionado impide eliminar físicamente la historia.
4. No descontar dos veces un préstamo histórico ya incluido en apertura.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Nuevo préstamo
- **Dado que** presta S/100 desde cuenta con S/300,
- **Cuando** confirma,
- **Entonces** banco queda S/200 y por cobrar S/100.

#### Escenario 2: Sin origen
- **Dado que** el formulario no tiene cuenta,
- **Cuando** guarda,
- **Entonces** se exige seleccionarla sin crear préstamo.

#### Escenario 3: Editar nota
- **Dado que** hay un préstamo con cobros,
- **Cuando** modifica descripción,
- **Entonces** los cobros y saldo permanecen.

#### Escenario 4: Borrado con desembolso
- **Dado que** existe movimiento de salida relacionado,
- **Cuando** solicita eliminar,
- **Entonces** se ofrece cierre auditable y no se borra el desembolso.

#### Escenario 5: Histórico
- **Dado que** el saldo inicial ya descontaba el préstamo,
- **Cuando** registra la operación histórica,
- **Entonces** se exige conciliación de fecha y apertura evitando otro descuento silencioso.

---

## HU-28 | Pagos y cobros de principal

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-DEU - Deudas y Préstamos |
| **Responsable de Épica** | Integrante 5 - Desarrollo de persistencia local |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19, HU-26, HU-27 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-24, HU-29 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-29 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay deuda o préstamo propio con pendiente y una cuenta activa.

### Descripción Ágil

Como usuario de Kipu, quiero registrar amortizaciones y recuperaciones, para actualizar obligaciones sin doble contabilización.

### Reglas de Negocio

1. Principal separado de interés real.
2. No liquidar principal mayor que el pendiente.
3. El saldo deriva de apertura y liquidaciones confirmadas.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Pago parcial
- **Dado que** la deuda es S/200,
- **Cuando** paga S/50 de principal,
- **Entonces** pendiente queda S/150 y banco baja S/50 sin gasto operativo.

#### Escenario 2: Cobro parcial
- **Dado que** hay S/200 por cobrar,
- **Cuando** recibe S/50 de principal,
- **Entonces** por cobrar queda S/150 y banco aumenta S/50 sin ingreso operativo.

#### Escenario 3: Interés
- **Dado que** paga S/50 de principal y S/5 de interés real,
- **Cuando** confirma conceptos separados,
- **Entonces** solo S/5 se reconoce como gasto operativo.

#### Escenario 4: Exceso
- **Dado que** el pendiente es S/30,
- **Cuando** intenta liquidar S/40 de principal,
- **Entonces** se rechaza y solicita corregir el importe.

---

## HU-29 | Cuotas, vencimientos y cierre de deudas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-DEU - Deudas y Préstamos |
| **Responsable de Épica** | Integrante 5 - Desarrollo de persistencia local |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-26, HU-27, HU-28 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-42 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-40, HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-40, HU-44 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe deuda o préstamo con saldo derivado de sus operaciones.

### Descripción Ágil

Como usuario de Kipu, quiero planificar cuotas y cerrar obligaciones saldadas, para seguir vencimientos sin ocultar saldos pendientes.

### Reglas de Negocio

1. Cronograma no prueba pago real.
2. Cerrar exige saldo cero o ajuste explícito auditable.
3. Cancelar no equivale automáticamente a pagada.
4. Con historia se conserva consulta; sin operaciones se permite eliminación física según política.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Cuotas
- **Dado que** el capital es S/100 en tres pagos sin interés,
- **Cuando** genera cronograma,
- **Entonces** la suma del principal es S/100 sin crear tres gastos.

#### Escenario 2: Vencimiento
- **Dado que** se aproxima una cuota,
- **Cuando** llega la anticipación elegida,
- **Entonces** aparece recordatorio sin descontar dinero.

#### Escenario 3: Liquidada
- **Dado que** el pendiente es cero,
- **Cuando** solicita cerrar,
- **Entonces** se marca pagada y no consume cupo activo.

#### Escenario 4: Saldo restante
- **Dado que** quedan S/20,
- **Cuando** intenta marcar pagada,
- **Entonces** se rechaza salvo ajuste explícito y auditable.

#### Escenario 5: Corrección
- **Dado que** se anula un pago de una deuda cerrada,
- **Cuando** recalcula pendiente,
- **Entonces** se muestra el saldo real y no se oculta por el estado anterior.

---

## HU-30 | Creación y edición de metas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MET - Metas y Reservas Virtuales |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-07 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-31, HU-32 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-24, HU-31, HU-32, HU-41 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe sesión propia y cupo de meta activa.

### Descripción Ágil

Como usuario de Kipu, quiero crear y editar metas de ahorro, para organizar objetivos con o sin cuenta dedicada.

### Reglas de Negocio

1. Una Cuenta Metas virtual agrupa varios objetivos.
2. Nombre, icono, importe objetivo y fecha opcional.
3. Cuenta real vinculada es opcional; explicar ambos modos.
4. Editar no mueve dinero ni borra aportes; con historia se archiva en lugar de eliminar físicamente.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Reserva virtual
- **Dado que** no quiere cuenta dedicada,
- **Cuando** crea objetivo S/500,
- **Entonces** se explica reserva y la meta se crea sin mover fondos.

#### Escenario 2: Cuenta vinculada
- **Dado que** elige su cuenta de ahorro,
- **Cuando** guarda meta,
- **Entonces** queda vinculada sin duplicar ese activo.

#### Escenario 3: Editar
- **Dado que** hay S/100 aportados,
- **Cuando** cambia nombre y objetivo,
- **Entonces** se conservan aportes y se recalcula avance.

#### Escenario 4: Eliminar vacía
- **Dado que** no hay aportes ni operaciones,
- **Cuando** confirma borrar,
- **Entonces** la meta se elimina físicamente.

#### Escenario 5: Con historia
- **Dado que** la meta tiene aportes,
- **Cuando** solicita eliminación,
- **Entonces** se informa que corresponde liberar o reasignar y archivar conservando historia.

---

## HU-31 | Aportes y reservas de metas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MET - Metas y Reservas Virtuales |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18, HU-19, HU-30 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-07 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-24, HU-32 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-24, HU-32 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay meta activa y saldo libre en cuenta propia.

### Descripción Ágil

Como usuario de Kipu, quiero reservar dinero o transferirlo a una cuenta vinculada, para separar ahorro sin aumentar artificialmente mi patrimonio.

### Reglas de Negocio

1. Las reservas no se cuentan como activo adicional.
2. No reservar dos veces el mismo respaldo.
3. Si origen y cuenta vinculada coinciden, solo reservar.
4. La app registra transferencias: no ejecuta pagos bancarios reales.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Reserva
- **Dado que** hay S/1000 y ninguna reserva,
- **Cuando** asigna S/200 sin cuenta vinculada,
- **Entonces** banco sigue S/1000, libre S/800 y patrimonio S/1000.

#### Escenario 2: Transferencia real representada
- **Dado que** origen y ahorro son cuentas distintas,
- **Cuando** registra traslado S/200 y aporte,
- **Entonces** origen baja, ahorro sube y reserva no crea tercer activo.

#### Escenario 3: Misma cuenta
- **Dado que** la cuenta de respaldo ya es destino,
- **Cuando** aporta S/50,
- **Entonces** solo reserva sin transferencia a sí misma.

#### Escenario 4: Sin respaldo
- **Dado que** quedan S/20 libres,
- **Cuando** intenta reservar S/30,
- **Entonces** se rechaza o solicita corregir asignación sin doble reserva.

#### Escenario 5: Gasto posterior
- **Dado que** el saldo cae por debajo del reservado,
- **Cuando** registra el hecho financiero,
- **Entonces** se muestra insuficiencia de respaldo para corregir metas, no se oculta el gasto.

---

## HU-32 | Liberación, finalización y archivo de metas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-MET - Metas y Reservas Virtuales |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-30, HU-31 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-24 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una meta propia con aportes identificables.

### Descripción Ágil

Como usuario de Kipu, quiero liberar aportes y completar o archivar mis metas, para recuperar disponibilidad conservando mi historia.

### Reglas de Negocio

1. Completar al 100 por ciento no gasta ni libera dinero automáticamente.
2. Liberación reduce reserva sin ingreso operativo.
3. Archivo con historia requiere resolver reservas; traslado bancario nunca se inventa.
4. Eliminar físicamente solo sin operaciones relacionadas.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Completar
- **Dado que** aporta hasta llegar al objetivo,
- **Cuando** se recalcula avance,
- **Entonces** queda Completada y la reserva permanece.

#### Escenario 2: Liberar
- **Dado que** hay S/200 reservados,
- **Cuando** libera S/50,
- **Entonces** reserva queda S/150 y libre aumenta S/50 sin ingreso.

#### Escenario 3: Exceso
- **Dado que** la reserva es S/30,
- **Cuando** intenta liberar S/40,
- **Entonces** se rechaza sin alterar importes.

#### Escenario 4: Archivar
- **Dado que** hay aportes históricos y reserva vigente,
- **Cuando** confirma liberación o reasignación y archivo,
- **Entonces** se conserva historia sin reserva huérfana.

#### Escenario 5: Sin traslado
- **Dado que** el dinero está en cuenta de ahorro,
- **Cuando** libera su asignación,
- **Entonces** no se inventa una devolución a otro banco.

---

## HU-33 | Registro de recurrencias

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-REC - Recurrencias y Obligaciones |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-18 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-34, HU-35, HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-34, HU-35, HU-40, HU-44 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Formulario de movimiento o regla recurrente propio disponible.

### Descripción Ágil

Como usuario de Kipu, quiero marcar un movimiento como pago recurrente, para planificar obligaciones repetidas.

### Reglas de Negocio

1. Suscripción financiera y recurrencia son la misma capacidad.
2. Frecuencia semanal, quincenal, mensual, anual o personalizada.
3. Monto fijo o aproximado, próxima fecha y anticipación.
4. Kipu no ejecuta el pago real de Netflix ni de otro servicio.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Alta
- **Dado que** registra un servicio mensual,
- **Cuando** activa Pago recurrente en Detalles,
- **Entonces** se crea la regla con próxima fecha.

#### Escenario 2: Monto aproximado
- **Dado que** el recibo varía cada mes,
- **Cuando** marca monto aproximado,
- **Entonces** se etiqueta estimado sin tratarlo como pago real.

#### Escenario 3: Fecha
- **Dado que** la recurrencia cae al fin de mes,
- **Cuando** se calcula el siguiente vencimiento,
- **Entonces** usa una fecha válida sin duplicar periodos.

#### Escenario 4: Datos inválidos
- **Dado que** falta próxima fecha,
- **Cuando** guarda,
- **Entonces** se solicita completar sin crear una regla incompleta.

---

## HU-34 | Pagos esperados y coincidencia con captura

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-REC - Recurrencias y Obligaciones |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-33, HU-46, HU-51 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-47 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-44, HU-49 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay recurrencia vigente y, para asociación automática, captura Premium disponible.

### Descripción Ágil

Como usuario de Kipu, quiero recibir pagos esperados y asociarlos a operaciones detectadas, para evitar recordatorios duplicados sin inventar gastos.

### Reglas de Negocio

1. Un esperado no es movimiento confirmado.
2. Coincidencia ambigua requiere revisión.
3. Asociar no vuelve a registrar un movimiento existente.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Pago esperado
- **Dado que** vence una recurrencia,
- **Cuando** se genera el esperado,
- **Entonces** aparece recordatorio sin cambiar banco.

#### Escenario 2: Coincidencia
- **Dado que** un candidato confirmado coincide con una obligación,
- **Cuando** lo vincula,
- **Entonces** se marca atendida sin duplicar gasto.

#### Escenario 3: Ambigüedad
- **Dado que** dos esperados coinciden en importe,
- **Cuando** intenta vincular automáticamente,
- **Entonces** se solicita elegir.

#### Escenario 4: Free
- **Dado que** existe regla activa sin Premium,
- **Cuando** llega su fecha,
- **Entonces** el aviso ordinario funciona y permite registrar manualmente.

---

## HU-35 | Estados de recurrencias

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-REC - Recurrencias y Obligaciones |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 3 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-33 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe una regla recurrente propia.

### Descripción Ágil

Como usuario de Kipu, quiero pausar, reanudar o cancelar recurrencias, para controlar avisos sin perder pagos anteriores.

### Reglas de Negocio

1. Estados Activa, Pausada y Cancelada.
2. Pausar y cancelar conservan historia.
3. Reanudar recalcula próxima fecha sin crear gastos de periodos omitidos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Pausa
- **Dado que** la regla está activa,
- **Cuando** la pausa,
- **Entonces** no genera nuevos avisos y conserva pagos previos.

#### Escenario 2: Reanudar
- **Dado que** estaba pausada,
- **Cuando** la reactiva con próxima fecha,
- **Entonces** se reanuda sin cargar gastos atrasados ficticios.

#### Escenario 3: Cancelar
- **Dado que** ya no utiliza el servicio,
- **Cuando** confirma cancelación,
- **Entonces** cesan futuros avisos y la historia permanece.

#### Escenario 4: Cancelar diálogo
- **Dado que** abrió confirmación de cancelación,
- **Cuando** regresa,
- **Entonces** se conserva el estado activo.

---

## HU-36 | Crear y consultar presupuestos

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PRE - Presupuestos y Consumos |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-14, HU-18, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-37, HU-38, HU-43 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-25, HU-37, HU-38, HU-41 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sección Plan abierta; referencias propias y cupo activo disponible.

### Descripción Ágil

Como usuario de Kipu, quiero definir límites por total, categoría o subcategoría, para comparar mis gastos con lo planificado.

### Reglas de Negocio

1. Periodos diario, semanal, quincenal, mensual o rango propio.
2. Mostrar presupuestado, consumido, restante y porcentaje.
3. Dos presupuestos activos en Free.
4. Presupuesto no retiene ni mueve fondos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Categoría
- **Dado que** define Alimentación por S/500 al mes,
- **Cuando** guarda,
- **Entonces** ve límite y consumo del periodo.

#### Escenario 2: Subcategoría
- **Dado que** elige Restaurantes,
- **Cuando** guarda un presupuesto,
- **Entonces** su alcance se limita a esa subcategoría.

#### Escenario 3: Rango
- **Dado que** selecciona fecha final anterior a inicial,
- **Cuando** guarda,
- **Entonces** se rechaza el rango.

#### Escenario 4: Cupo
- **Dado que** Free ya tiene dos activos,
- **Cuando** crea un tercero,
- **Entonces** se explica el límite conservando los existentes.

#### Escenario 5: Consulta
- **Dado que** gastó S/120 de S/500,
- **Cuando** abre Plan,
- **Entonces** ve consumido S/120, restante S/380 y 24 por ciento.

---

## HU-37 | Renovación, consumo jerárquico y alertas

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PRE - Presupuestos y Consumos |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-36, HU-25, HU-42 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-43 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-38, HU-43 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Presupuesto recurrente activo y gastos propios confirmados.

### Descripción Ágil

Como usuario de Kipu, quiero renovar automáticamente mis periodos y recibir alertas, para controlar el consumo sin crear presupuestos a mano cada vez.

### Reglas de Negocio

1. Renovar crea nueva vigencia; no suma sobrante al límite por defecto.
2. Rango propio termina y no renueva salvo nueva configuración.
3. Umbrales iniciales 50, 80 y 100 editables; un aviso por cruce/periodo.
4. Subcategoría consume su padre; total no suma dos veces el gasto.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Nuevo mes
- **Dado que** septiembre consumió S/420 de S/500,
- **Cuando** inicia octubre,
- **Entonces** se abre periodo con límite S/500 y consumo propio conservando septiembre.

#### Escenario 2: Rango cerrado
- **Dado que** terminó un rango personalizado,
- **Cuando** pasa la fecha final,
- **Entonces** no nace otro periodo solo.

#### Escenario 3: Jerarquía
- **Dado que** hay presupuestos Restaurantes, Alimentación y total,
- **Cuando** confirma gasto S/50 en Restaurantes,
- **Entonces** cada alcance refleja S/50 y el gasto global sigue S/50.

#### Escenario 4: Umbral
- **Dado que** pasa de 49 a 51 por ciento,
- **Cuando** confirma gasto,
- **Entonces** se genera una alerta; reabrir no la repite.

#### Escenario 5: Reintento de renovación
- **Dado que** el periodo ya existe,
- **Cuando** se repite el proceso,
- **Entonces** no se crea otra vigencia para las mismas fechas.

---

## HU-38 | Edición, finalización y eliminación de presupuestos

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PRE - Presupuestos y Consumos |
| **Responsable de Épica** | Integrante 3 - Desarrollo de planificación y formularios |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-36, HU-37 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | Ninguna | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe presupuesto propio; la acción destructiva requiere confirmación.

### Descripción Ágil

Como usuario de Kipu, quiero editar, finalizar o eliminar mis presupuestos, para mantener mi planificación sin modificar gastos reales.

### Reglas de Negocio

1. Excluye transferencias, principal, metas, pendientes y anulaciones; sin predicciones.
2. Editar límite afecta periodo actual y futuros; los cerrados conservan su límite. Cambio de alcance o calendario se aplica a siguiente vigencia, o requiere finalizar y crear otro.
3. Finalizar detiene renovaciones y avisos; conserva historia.
4. Eliminar físicamente definición y periodos presupuestarios, nunca movimientos financieros.
5. No hay acciones manuales Pausar ni Reanudar en presupuestos V4.1.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Excluir
- **Dado que** se paga principal de tarjeta S/100,
- **Cuando** se recalcula presupuesto,
- **Entonces** no aumenta consumo operativo.

#### Escenario 2: Editar límite
- **Dado que** el presupuesto activo es S/500,
- **Cuando** lo cambia a S/600,
- **Entonces** recalcula porcentaje actual sin alterar gastos ni límites de periodos cerrados.

#### Escenario 3: Finalizar
- **Dado que** el presupuesto es recurrente,
- **Cuando** confirma finalizar,
- **Entonces** cesan nuevas vigencias y avisos, conservando consultas históricas.

#### Escenario 4: Eliminar con historia
- **Dado que** hay varios periodos,
- **Cuando** confirma advertencia de pérdida irreversible,
- **Entonces** se borran presupuesto y periodos, pero banco, gastos y categorías permanecen.

#### Escenario 5: Cancelar borrado
- **Dado que** aparece la advertencia,
- **Cuando** cancela,
- **Entonces** presupuesto e historia permanecen.

#### Escenario 6: Reembolso
- **Dado que** se devuelve una compra imputada,
- **Cuando** recalcula,
- **Entonces** se reduce consumo del periodo de origen sin predicción de gasto.

---

## HU-39 | Inicio e historial financiero

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-INF - Inicio e Información Financiera |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-07, HU-18, HU-19 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-22 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-41 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-41 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen cuentas y movimientos propios o estado inicial vacío.

### Descripción Ágil

Como usuario de Kipu, quiero ver mi situación y navegar por mis movimientos, para comprender mi dinero actual y su origen.

### Reglas de Negocio

1. Patrimonio = activos reales + por cobrar - pasivos; reservas no se suman otra vez.
2. Inicio conserva estructura aprobada sin widgets presupuestarios ni predicciones.
3. Historia Free completa; no confundir con analítica histórica Premium.
4. Módulos aún no utilizados muestran cero o vacío, no datos inventados.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Patrimonio
- **Dado que** hay S/1000 en banco, S/200 por cobrar y S/300 de pasivos,
- **Cuando** abre Inicio,
- **Entonces** ve patrimonio S/900 sin sumar línea ni reserva.

#### Escenario 2: Sin movimientos
- **Dado que** la cuenta es nueva,
- **Cuando** abre historial,
- **Entonces** ve estado vacío y acción para registrar.

#### Escenario 3: Historia Free
- **Dado que** existen gastos de varios meses,
- **Cuando** navega a un mes anterior,
- **Entonces** puede leer y corregir sus movimientos.

#### Escenario 4: Máscara
- **Dado que** activó privacidad,
- **Cuando** abre Inicio,
- **Entonces** los montos se ocultan manteniendo estructura.

#### Escenario 5: Reembolso
- **Dado que** se corrige un gasto,
- **Cuando** vuelve a Inicio,
- **Entonces** las métricas reflejan el ajuste y sus fechas.

---

## HU-40 | Calendario financiero

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-INF - Inicio e Información Financiera |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S7 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-09, HU-29, HU-33 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen eventos propios de tarjetas, deudas o recurrencias.

### Descripción Ágil

Como usuario de Kipu, quiero consultar fechas de movimientos y obligaciones, para anticipar vencimientos desde una sola vista.

### Reglas de Negocio

1. Diferenciar movimientos ocurridos y fechas previstas.
2. Consultar meses anteriores también en Free.
3. Cada evento abre su objeto; no cobrar automáticamente.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Mes
- **Dado que** hay cierre y vencimiento de tarjeta,
- **Cuando** abre ese mes,
- **Entonces** ve eventos distinguibles y sus fechas.

#### Escenario 2: Detalle
- **Dado que** selecciona una cuota,
- **Cuando** abre evento,
- **Entonces** llega a la deuda correcta.

#### Escenario 3: Pasado
- **Dado que** usa Free,
- **Cuando** consulta un mes anterior,
- **Entonces** ve sus operaciones sin paywall de historial.

#### Escenario 4: Varios eventos
- **Dado que** dos obligaciones vencen el mismo día,
- **Cuando** abre esa fecha,
- **Entonces** ambas aparecen sin ocultarse mutuamente.

---

## HU-41 | Estadísticas y analítica financiera

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-INF - Inicio e Información Financiera |
| **Responsable de Épica** | Integrante 2 - Desarrollo de interfaces y consultas |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S9 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-39, HU-36, HU-09, HU-26, HU-30, HU-58 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-22 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-25 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay movimientos propios; la consulta respeta el acceso Free o Premium.

### Descripción Ágil

Como usuario de Kipu, quiero analizar gastos, crédito, deudas y metas, para comparar resultados sin duplicar transferencias.

### Reglas de Negocio

1. Free: estadísticas básicas del mes actual; Premium: rangos, comparación y profundidad.
2. Excluir traslados y principal de métricas operativas.
3. Por cobrar forma parte de patrimonio; reservas no lo duplican.
4. Sin base de comparación se informa explícitamente.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Categorías
- **Dado que** hay gasto S/100 y transferencia S/200,
- **Cuando** analiza gastos,
- **Entonces** el total operativo es S/100.

#### Escenario 2: Premium
- **Dado que** elige dos periodos y filtros combinados,
- **Cuando** ejecuta comparación,
- **Entonces** se muestran fechas y diferencias absolutas/porcentuales válidas.

#### Escenario 3: Base cero
- **Dado que** el periodo anterior no tuvo gasto,
- **Cuando** compara,
- **Entonces** ve Sin base de comparación y no infinito.

#### Escenario 4: Free
- **Dado que** elige un histórico agregado,
- **Cuando** solicita reporte,
- **Entonces** se explica Premium y ofrece estadísticas actuales e historial básico.

#### Escenario 5: Metas y crédito
- **Dado que** hay reservas y crédito utilizado,
- **Cuando** consulta avance,
- **Entonces** no se suman reserva ni línea como activos nuevos.

#### Escenario 6: Periodo parcial
- **Dado que** compara mes abierto con cerrado,
- **Cuando** abre resultados,
- **Entonces** ve advertencia y fechas exactas.

---

## HU-42 | Centro de notificaciones

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-NOT - Centro de Notificaciones y Avisos |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-10, HU-43, HU-44 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-37, HU-43, HU-44 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sesión propia y centro de avisos disponible.

### Descripción Ágil

Como usuario de Kipu, quiero consultar mis alertas y recordatorios en un centro único, para abrir el contexto que requiere atención.

### Reglas de Negocio

1. Alerta describe condición ocurrida; recordatorio, evento futuro.
2. Un aviso abre su objeto exacto.
3. No se mezclan avisos de distintas cuentas Kipu.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Alerta
- **Dado que** ocurrió cruce de crédito,
- **Cuando** abre el centro,
- **Entonces** ve condición y tarjeta relacionada.

#### Escenario 2: Recordatorio
- **Dado que** se aproxima vencimiento,
- **Cuando** consulta aviso,
- **Entonces** ve fecha prevista distinguida de pago realizado.

#### Escenario 3: Navegar
- **Dado que** selecciona un aviso,
- **Cuando** lo abre,
- **Entonces** accede al detalle exacto.

#### Escenario 4: Objeto eliminado
- **Dado que** se eliminó físicamente un presupuesto,
- **Cuando** abre aviso anterior,
- **Entonces** se informa que ya no existe y no falla la navegación.

---

## HU-43 | Alertas presupuestarias y de revisión

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-NOT - Centro de Notificaciones y Avisos |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-42, HU-37, HU-51 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-10 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen presupuestos/tarjetas y bandeja funcional.

### Descripción Ágil

Como usuario de Kipu, quiero recibir alertas de consumo y candidatos pendientes, para atender excesos o confirmar registros.

### Reglas de Negocio

1. El exceso presupuestario nunca bloquea guardar gasto real.
2. Aviso de candidato exige captura autorizada.
3. No duplicar alerta ya emitida para la condición.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Presupuesto excedido
- **Dado que** el gasto supera el límite,
- **Cuando** confirma la compra,
- **Entonces** se guarda y avisa sin rechazarla por presupuesto.

#### Escenario 2: Crédito
- **Dado que** cruza un umbral de tarjeta,
- **Cuando** se evalúa condición,
- **Entonces** aparece una única alerta en el centro.

#### Escenario 3: Pendiente
- **Dado que** un candidato requiere revisión,
- **Cuando** se recibe,
- **Entonces** se avisa y abre el candidato sin afectar saldos.

#### Escenario 4: Evento repetido
- **Dado que** el candidato ya tiene aviso,
- **Cuando** llega el mismo evento,
- **Entonces** no se genera otra notificación.

---

## HU-44 | Recordatorios y deduplicación

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-NOT - Centro de Notificaciones y Avisos |
| **Responsable de Épica** | Integrante 7 - Desarrollo de eventos y avisos |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S6 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-42, HU-09, HU-29, HU-33 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-34, HU-35, HU-40 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay fechas de tarjeta, deuda o recurrencia propias.

### Descripción Ágil

Como usuario de Kipu, quiero configurar anticipación de avisos de pago, para recordar obligaciones sin mensajes repetidos.

### Reglas de Negocio

1. Anticipación configurable por tipo.
2. Dedupe por objeto, tipo y vencimiento.
3. No prometer exactitud al segundo; Android puede diferir ejecución.
4. Free conserva avisos ordinarios de objetos activos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Anticipación
- **Dado que** elige avisar dos días antes,
- **Cuando** se aproxima vencimiento,
- **Entonces** se agenda para la fecha correspondiente.

#### Escenario 2: Reintento
- **Dado que** el aviso ya se emitió,
- **Cuando** se vuelve a ejecutar la tarea,
- **Entonces** no se duplica.

#### Escenario 3: Cambio de fecha
- **Dado que** el usuario corrige vencimiento,
- **Cuando** guarda,
- **Entonces** se reemplaza la programación anterior sin doble aviso.

#### Escenario 4: Sin permiso
- **Dado que** rechazó notificaciones Android,
- **Cuando** vence la anticipación,
- **Entonces** el evento queda consultable dentro de Kipu.

#### Escenario 5: Regla pausada
- **Dado que** la recurrencia está pausada,
- **Cuando** llega una antigua fecha programada,
- **Entonces** no se emite un nuevo recordatorio de esa regla.

---

## HU-45 | Activación del listener

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-05, HU-58, HU-59 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-46, HU-49 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-46, HU-49 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Premium vigente; explicación previa y ajuste de Android accesibles.

### Descripción Ágil

Como usuario de Kipu, quiero autorizar fuentes compatibles de notificación, para capturar movimientos con mi consentimiento.

### Reglas de Negocio

1. Procesar solo paquetes/fuentes autorizados y compatibles.
2. Comprobar permiso y plan antes de leer contenido.
3. No capturar historia anterior ni prometer cobertura universal.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Fuente válida
- **Dado que** Premium y permiso están activos,
- **Cuando** llega notificación compatible autorizada,
- **Entonces** se entrega al flujo de candidato.

#### Escenario 2: Fuente excluida
- **Dado que** la app de origen no está autorizada,
- **Cuando** llega una señal,
- **Entonces** se ignora sin persistir contenido.

#### Escenario 3: Revocación
- **Dado que** el permiso fue retirado,
- **Cuando** llega otro callback,
- **Entonces** no se procesa la señal.

#### Escenario 4: Premium vencido
- **Dado que** la concesión dejó de ser válida,
- **Cuando** llega contenido,
- **Entonces** no se inicia captura y el registro manual sigue disponible.

---

## HU-46 | Extracción y candidato

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-45 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-15, HU-16 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-47, HU-51 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-34, HU-47, HU-48, HU-49, HU-50, HU-51 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Fuente compatible y autorización vigente.

### Descripción Ágil

Como usuario de Kipu, quiero obtener un movimiento propuesto desde una señal, para evitar transcribir datos que pueden identificarse.

### Reglas de Negocio

1. Extraer monto, fecha, dirección, comercio y últimos cuatro si existen.
2. Crear candidato antes de afectar ledger.
3. Campos inferidos se señalan; no adivinar tarjeta ni fecha.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Señal completa
- **Dado que** la señal identifica importe, fecha y tarjeta única,
- **Cuando** se procesa,
- **Entonces** se crea candidato prellenado con origen.

#### Escenario 2: Tarjeta ambigua
- **Dado que** los últimos cuatro coinciden con dos instrumentos,
- **Cuando** se extraen datos,
- **Entonces** se solicita selección y no se autorregistra.

#### Escenario 3: Fecha ausente
- **Dado que** el texto no indica fecha fiable,
- **Cuando** se procesa,
- **Entonces** se marca dato inferido y exige revisión.

#### Escenario 4: Texto inválido
- **Dado que** el parser no reconoce monto,
- **Cuando** se procesa,
- **Entonces** no crea movimiento confirmado y explica la incertidumbre.

---

## HU-47 | Confianza y autorregistro

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-46, HU-18, HU-23 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-51 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-50 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe candidato extraído; bandeja y ledger disponibles.

### Descripción Ágil

Como usuario de Kipu, quiero conocer la confianza de captura y revisar dudas, para automatizar solo registros suficientemente fiables.

### Reglas de Negocio

1. Confianza Alta, Media o Baja con motivos explicables.
2. Alta requiere monto, moneda, dirección, instrumento y categoría requerida resueltos, sin conflicto.
3. Crédito siempre se confirma aun con confianza alta.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Alta débito
- **Dado que** todos los campos requeridos son válidos y no hay conflicto,
- **Cuando** evalúa candidato,
- **Entonces** autorregistra una vez con origen de captura.

#### Escenario 2: Media
- **Dado que** la categoría es dudosa,
- **Cuando** evalúa candidato,
- **Entonces** lo envía a revisión sin modificar saldo.

#### Escenario 3: Baja
- **Dado que** faltan varios campos,
- **Cuando** consulta candidato,
- **Entonces** ve motivos y puede corregir o descartar.

#### Escenario 4: Crédito
- **Dado que** todos los datos de crédito son fiables,
- **Cuando** evalúa candidato,
- **Entonces** sigue pendiente de confirmar directo o cuotas.

#### Escenario 5: Cambio de plan
- **Dado que** Premium vence antes de guardar,
- **Cuando** intenta autorregistrar,
- **Entonces** se revalida y no se confirma automáticamente.

---

## HU-48 | Confirmación de crédito detectado

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-46, HU-08, HU-13 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-47 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Hay candidato asociado inequívocamente a tarjeta de crédito.

### Descripción Ágil

Como usuario de Kipu, quiero confirmar si mi compra detectada fue directa o en cuotas, para registrar correctamente la obligación.

### Reglas de Negocio

1. Confirmación obligatoria independiente de confianza.
2. Cuotas solo con instrumento de crédito.
3. Confirmar utiliza el mismo caso de uso del registro manual.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Directo
- **Dado que** el candidato de crédito es S/100,
- **Cuando** elige directo y confirma,
- **Entonces** se registra principal una vez.

#### Escenario 2: Cuotas
- **Dado que** el candidato es S/100,
- **Cuando** elige tres cuotas sin interés y confirma,
- **Entonces** se genera plan consistente con S/100.

#### Escenario 3: Sin decisión
- **Dado que** está abierta la revisión,
- **Cuando** sale sin confirmar,
- **Entonces** no se modifica deuda ni banco.

#### Escenario 4: Cambio a débito
- **Dado que** corrige el instrumento a débito,
- **Cuando** revisa formulario,
- **Entonces** desaparece cuotas y debe validar la operación corregida.

---

## HU-49 | Yape híbrido, OCR y deduplicación

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 13 |
| **Sprint de cierre** | S9 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-45, HU-46, HU-23, HU-58, HU-59 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-51, HU-16 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-34, HU-50 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Premium y fuente autorizada; para compartir hay una imagen accesible mediante permiso temporal.

### Descripción Ágil

Como usuario de Kipu, quiero capturar Yape por notificación o comprobante compartido, para registrar ingresos y pagos aunque una fuente no esté disponible.

### Reglas de Negocio

1. Notificación compatible, especialmente ingresos; Compartir complementa pagos sin notificación.
2. OCR local sin IA generativa ni subida de imagen por defecto.
3. Deduplicar entre notificación, compartido y otras fuentes compatibles.
4. No confundir dos pagos reales del mismo importe.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Ingreso notificado
- **Dado que** Yape expone señal compatible,
- **Cuando** recibe un ingreso,
- **Entonces** crea candidato y aplica confianza e integridad.

#### Escenario 2: Pago compartido
- **Dado que** no hubo notificación utilizable,
- **Cuando** comparte comprobante legible,
- **Entonces** OCR prellena un candidato local para resolución.

#### Escenario 3: Ambos órdenes
- **Dado que** hay una operación con misma referencia,
- **Cuando** recibe notificación y compartido en cualquier orden,
- **Entonces** vincula evidencias a un único movimiento.

#### Escenario 4: Dos pagos iguales
- **Dado que** son operaciones distintas de mismo importe,
- **Cuando** procesa comprobantes,
- **Entonces** no elimina una solo por similitud.

#### Escenario 5: Imagen borrosa
- **Dado que** el texto no es fiable,
- **Cuando** comparte imagen,
- **Entonces** solicita corrección sin inventar monto.

#### Escenario 6: Formato no admitido
- **Dado que** comparte archivo distinto de imagen,
- **Cuando** lo recibe Kipu,
- **Entonces** se explica el formato requerido sin procesarlo.

#### Escenario 7: Free
- **Dado que** no tiene Premium,
- **Cuando** comparte comprobante,
- **Entonces** no se aplica OCR y se ofrece registro manual.

#### Escenario 8: Vencimiento
- **Dado que** Premium vence después de capturar y antes de confirmar,
- **Cuando** abre el candidato,
- **Entonces** permite descartarlo o convertirlo explícitamente en manual sin nuevo OCR.

---

## HU-50 | Aprendizaje desde correcciones

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-16, HU-17, HU-46, HU-51 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-47, HU-49 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existen candidatos propios y preferencias por usuario.

### Descripción Ágil

Como usuario de Kipu, quiero reutilizar mis correcciones confirmadas, para mejorar las sugerencias de comercio y categoría.

### Reglas de Negocio

1. Aprender solo de correcciones confirmadas.
2. Regla explícita prevalece; no modificar catálogo general.
3. Texto fuente permanece separado de normalización.
4. No ejecutar nueva clasificación de captura en Free.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Corrección confirmada
- **Dado que** el usuario cambia comercio y categoría,
- **Cuando** confirma candidato,
- **Entonces** se guarda su asociación para futuras señales.

#### Escenario 2: Descartar
- **Dado que** modificó el formulario sin confirmar,
- **Cuando** descarta candidato,
- **Entonces** no se aprende de una decisión abandonada.

#### Escenario 3: Otro usuario
- **Dado que** otra cuenta tiene preferencia distinta,
- **Cuando** procesa su candidato,
- **Entonces** no recibe preferencias ajenas.

#### Escenario 4: Historia
- **Dado que** cambia una preferencia personal,
- **Cuando** consulta registros previos,
- **Entonces** no se reclasifican automáticamente.

---

## HU-51 | Bandeja y evidencia mínima

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-AUT - Captura Automática e Import Inbox |
| **Responsable de Épica** | Integrante 8 - Desarrollo de captura y normalización |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S8 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-46 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-47, HU-43, HU-50 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-34, HU-43, HU-50 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe candidato propio pendiente; la vista muestra origen y motivos.

### Descripción Ágil

Como usuario de Kipu, quiero editar, confirmar o descartar candidatos, para resolver registros inciertos antes de alterar mis saldos.

### Reglas de Negocio

1. Sin ledger hasta confirmar salvo autorregistro autorizado.
2. Evidencia mínima, sin logs con comprobantes o tokens.
3. Una resolución es idempotente.
4. Al vencer Premium se conserva candidato y puede convertirse explícitamente en manual.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Corregir y confirmar
- **Dado que** el candidato tiene categoría pendiente,
- **Cuando** completa campos y confirma,
- **Entonces** se crea un solo movimiento y se marca resuelto.

#### Escenario 2: Descartar
- **Dado que** el candidato no corresponde a gasto real,
- **Cuando** lo descarta,
- **Entonces** no modifica saldo.

#### Escenario 3: Doble confirmación
- **Dado que** ya se resolvió el candidato,
- **Cuando** pulsa confirmar otra vez,
- **Entonces** se devuelve el registro existente.

#### Escenario 4: Caducidad
- **Dado que** Premium venció,
- **Cuando** elige convertir candidato en manual,
- **Entonces** revisa y confirma sin OCR nuevo manteniendo dedupe.

#### Escenario 5: Privacidad
- **Dado que** se completó extracción,
- **Cuando** se limpia evidencia temporal,
- **Entonces** no quedan imágenes indefinidas ni contenido sensible en logs.

---

## HU-52 | Elegir Free o prueba Premium

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S1 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-53, HU-57, HU-58 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-53, HU-54, HU-57, HU-58 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Cuenta Kipu creada; pantalla de oferta disponible. En S1 la contratación se muestra como todavía no habilitada, sin activar un Trial ficticio.

### Descripción Ágil

Como usuario de Kipu, quiero elegir Free o conocer la prueba Premium voluntaria, para decidir sin activar cobros por accidente.

### Reglas de Negocio

1. Free permanente sin tarjeta ni anuncios.
2. Trial de siete días voluntario, elegible y vinculado a mensual/anual.
3. Informar precio posterior, renovación y cancelación antes de contratar.
4. La elección expresa intención; la activación exige compra verificada en HU-54.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Free
- **Dado que** el usuario no desea suscripción,
- **Cuando** elige continuar Free,
- **Entonces** entra sin pedir método de pago.

#### Escenario 2: Información Trial
- **Dado que** consulta la oferta,
- **Cuando** selecciona mensual o anual,
- **Entonces** ve duración, precio posterior y renovación antes de aceptar.

#### Escenario 3: No elegible
- **Dado que** ya consumió la prueba,
- **Cuando** consulta oferta elegible,
- **Entonces** no se promete otro Trial.

#### Escenario 4: Abandonar
- **Dado que** no ha confirmado compra en Play,
- **Cuando** sale de la pantalla,
- **Entonces** no se activa ni cobra una suscripción.

---

## HU-53 | Compra mensual, anual y Lifetime

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-52 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-54 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-56, HU-60 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Productos configurados en canal de pruebas y verificación HU-54 integrada en el mismo sprint.

### Descripción Ágil

Como usuario de Kipu, quiero contratar un plan mostrado por Google Play, para habilitar Premium con precio y duración claros.

### Reglas de Negocio

1. Mensual/anual recurrentes; Lifetime compra única no consumible.
2. Precios objetivo S/4.99, S/29.99 y S/49.99; UI usa precio localizado de Play.
3. No se habilita acceso por retorno de UI sin verificación.
4. Lifetime no tiene prueba propia.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Mensual
- **Dado que** el producto mensual está disponible,
- **Cuando** completa compra y verificación,
- **Entonces** se muestra Premium con vigencia y renovación.

#### Escenario 2: Anual
- **Dado que** elige oferta anual,
- **Cuando** confirma en Play,
- **Entonces** se usa precio y condiciones reales mostrados.

#### Escenario 3: Lifetime
- **Dado que** no existe renovación recurrente activa,
- **Cuando** compra y se verifica Lifetime,
- **Entonces** queda acceso sin caducidad comercial ni consumo de producto.

#### Escenario 4: Cancelar
- **Dado que** está en la hoja de compra,
- **Cuando** cancela,
- **Entonces** mantiene su acceso previo.

#### Escenario 5: Sin productos
- **Dado que** Play no devuelve oferta,
- **Cuando** abre planes,
- **Entonces** se explica indisponibilidad y no se inventa un precio cobrable.

---

## HU-54 | Verificación y reconocimiento de compras

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01, HU-52 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-53 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-55, HU-56, HU-59, HU-60 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-55, HU-56, HU-59, HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Compra reportada; backend autenticado y proveedor consultable.

### Descripción Ágil

Como usuario de Kipu, quiero obtener Premium solo cuando mi compra sea válida, para evitar pérdidas o activaciones indebidas.

### Reglas de Negocio

1. Verificar usuario, aplicación, producto, estado y vigencia.
2. PENDING no desbloquea.
3. Reconocimiento idempotente de compra inicial; Lifetime nunca se consume.
4. Token asociado a una cuenta no habilita otra.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Válida
- **Dado que** Play confirma compra del usuario,
- **Cuando** se verifica,
- **Entonces** se concede acceso y reconoce entrega una vez.

#### Escenario 2: Pendiente
- **Dado que** la compra sigue PENDING,
- **Cuando** solicita activar,
- **Entonces** se mantiene acceso previo y se informa pendiente.

#### Escenario 3: Repetida
- **Dado que** ya se reconoció el token,
- **Cuando** reintenta verificación,
- **Entonces** no duplica compra ni permiso.

#### Escenario 4: Otra cuenta
- **Dado que** el token pertenece a otro usuario Kipu,
- **Cuando** se presenta desde esta sesión,
- **Entonces** se rechaza sin reasignar propietario.

#### Escenario 5: Fallo
- **Dado que** el proveedor no responde,
- **Cuando** se solicita verificar,
- **Entonces** se informa pendiente de validación y conserva reintento.

---

## HU-55 | Reconciliación y restauración de compras

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 13 |
| **Sprint de cierre** | S5 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-54, HU-56 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-06, HU-60 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe cuenta Kipu; canal de eventos autenticado y consulta a proveedor.

### Descripción Ágil

Como usuario de Kipu, quiero recuperar y mantener mis derechos de compra en mis dispositivos, para conservar el acceso verificado aunque cambie de teléfono.

### Reglas de Negocio

1. Evento RTDN avisa: el estado se consulta al proveedor.
2. Deduplicar identidad de evento y soportar desorden.
3. Restauración se vincula a misma cuenta Kipu, no al aparato.
4. Reconciliación recupera eventos perdidos.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Evento válido
- **Dado que** llega aviso autenticado de renovación,
- **Cuando** se consulta la compra,
- **Entonces** se actualiza vigencia real.

#### Escenario 2: Duplicado
- **Dado que** el evento ya fue procesado,
- **Cuando** vuelve a llegar,
- **Entonces** no duplica acceso ni acciones.

#### Escenario 3: Desorden
- **Dado que** llega un aviso antiguo después de uno nuevo,
- **Cuando** se consulta proveedor,
- **Entonces** prevalece el estado vigente, no el orden de llegada.

#### Escenario 4: Restaurar
- **Dado que** compra vinculada al usuario sigue válida,
- **Cuando** inicia sesión en otro teléfono y restaura,
- **Entonces** recupera permiso sin comprar otra vez.

#### Escenario 5: Evento perdido
- **Dado que** no llegó RTDN,
- **Cuando** se ejecuta reconciliación,
- **Entonces** se recupera estado verificable.

#### Escenario 6: Emisor inválido
- **Dado que** un aviso no está autenticado,
- **Cuando** intenta procesarse,
- **Entonces** se rechaza sin alterar permisos.

---

## HU-56 | Estados de compra y cancelación

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 5 |
| **Sprint de cierre** | S3 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-54 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-53, HU-55, HU-60 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-55, HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Existe proyección de compra verificada para el usuario.

### Descripción Ágil

Como usuario de Kipu, quiero consultar mi vigencia aunque cancele renovación, para usar el periodo que ya tengo autorizado.

### Reglas de Negocio

1. Tipo de producto, estado de proveedor y permiso son distintos.
2. Cancelar no implica vencer inmediatamente.
3. Gracia, hold, revocación y expiración dependen del estado verificado.
4. Otra compra válida puede mantener acceso.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Cancelada vigente
- **Dado que** canceló renovación pero no terminó el periodo,
- **Cuando** abre Kipu,
- **Entonces** mantiene Premium hasta vencimiento y ve No se renovará.

#### Escenario 2: Expirada
- **Dado que** el proveedor confirma fin del periodo,
- **Cuando** revalida,
- **Entonces** pasa a Free sin borrar datos.

#### Escenario 3: Gracia
- **Dado que** el proveedor mantiene acceso durante gracia,
- **Cuando** consulta permiso,
- **Entonces** conserva Premium según vigencia autorizada.

#### Escenario 4: Revocación parcial
- **Dado que** se revoca una compra pero hay Lifetime válido,
- **Cuando** recalcula acceso,
- **Entonces** conserva el permiso procedente de Lifetime.

---

## HU-57 | Cupos, selección y downgrade

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 13 |
| **Sprint de cierre** | S2 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-01, HU-52 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-07, HU-14, HU-26, HU-30, HU-36 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-07, HU-08, HU-14, HU-26, HU-27, HU-30, HU-36, HU-58 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Política cargada; en S2 se verifican todos los grupos con fixtures de dominio y cuentas/categorías reales; cada módulo aplica la política al integrarse.

### Descripción Ágil

Como usuario de Kipu, quiero seleccionar mis objetos activos al usar Free, para conservar mi historia dentro de los límites del plan.

### Reglas de Negocio

1. Free: cuatro instrumentos, cinco raíces personalizadas, dos metas, dos deudas y dos presupuestos activos.

   **Contexto del cupo de categorías:** la aclaración de [EP-CCO](../../specs/003-ep-cco-categorias-comercios/spec.md) separa gastos e ingresos (5 + 5). Los escenarios de selección de este apartado no deben usarse para imponer un límite global de cinco sin considerar ese refinamiento.
2. Bloqueo de plan separado del estado funcional y borrado.
3. No borrar historia ni excluir saldos por downgrade.
4. Sin selección se restringe solo el grupo excedido; efectivo y grupos válidos siguen disponibles.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Excedente
- **Dado que** hay nueve categorías personalizadas,
- **Cuando** expira Premium,
- **Entonces** pide elegir cinco conservando las nueve en historia.

#### Escenario 2: Selección
- **Dado que** elige cinco categorías,
- **Cuando** guarda,
- **Entonces** esas quedan utilizables y cuatro bloqueadas.

#### Escenario 3: Instrumentos
- **Dado que** hay una cuenta, un débito y dos créditos,
- **Cuando** consulta cupos,
- **Entonces** ve cuatro; cuenta interna de crédito no vuelve a contar.

#### Escenario 4: Sin selección
- **Dado que** un grupo está excedido,
- **Cuando** registra efectivo en grupo válido,
- **Entonces** se permite sin borrar objetos excedentes.

#### Escenario 5: Recuperar Premium
- **Dado que** hay objetos archivados y otros bloqueados solo por plan,
- **Cuando** compra válida habilita Premium,
- **Entonces** se quita bloqueo de plan sin reabrir archivados.

#### Escenario 6: Concurrencia
- **Dado que** dos dispositivos ocupan el último cupo offline,
- **Cuando** sincronizan,
- **Entonces** se conservan datos y se resuelve disponibilidad sin borrar hechos.

---

## HU-58 | Acceso Free y Premium por capacidad

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S4 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-52, HU-57 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | HU-54 | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-45, HU-49, HU-59 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-41, HU-45, HU-49, HU-59, HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Política de permisos y entitlement válidos; módulos futuros usan el mismo control al integrarse.

### Descripción Ágil

Como usuario de Kipu, quiero conocer qué acciones incluye mi plan, para usar las capacidades permitidas sin perder mis datos.

### Reglas de Negocio

1. La nueva captura y categorización automática requieren Premium. Excepción aprobada: el OCR local de comprobantes Yape está disponible en Free con autorización explícita, procesamiento local y revisión antes de registrar.
2. Analítica histórica y filtros combinados requieren Premium.
3. Manual, sync, consultas básicas y personalización ordinaria siguen Free.
4. Comprobar acción en dominio, no solo ocultar botones.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Manual Free
- **Dado que** el usuario no paga,
- **Cuando** registra gasto offline,
- **Entonces** se guarda y queda pendiente de sincronizar.

#### Escenario 2: Captura Free
- **Dado que** un intent intenta solicitar OCR,
- **Cuando** se evalúa autorización,
- **Entonces** se deniega antes de extraer imagen.

#### Escenario 3: Analítica Free
- **Dado que** se intenta filtro histórico combinado,
- **Cuando** se valida consulta,
- **Entonces** se ofrece versión básica sin ejecutar el cálculo restringido.

#### Escenario 4: Premium
- **Dado que** hay concesión vigente,
- **Cuando** solicita acción incluida,
- **Entonces** se habilita respetando permisos Android.

#### Escenario 5: Personalización
- **Dado que** Free cambia icono ordinario,
- **Cuando** guarda,
- **Entonces** no aparece cobro ni bloqueo por plan.

---

## HU-59 | Concesión offline y expiración

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S4 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-54, HU-58 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-05, HU-45, HU-49, HU-60 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | HU-45, HU-49, HU-60 | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Sesión propia previamente creada y datos locales existentes.

### Descripción Ágil

Como usuario de Kipu, quiero seguir registrando sin internet y revalidar Premium al volver, para no perder mi control manual por falta de conexión.

### Reglas de Negocio

1. Concesión hasta 72 horas desde última validación, acotada por vencimiento conocido.
2. Tras caducar, núcleo manual sigue disponible; nuevas acciones Premium sensibles requieren validar.
3. No prolongar acceso cambiando reloj.
4. Lifetime no expira comercialmente, pero renueva su concesión local.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Dentro del plazo
- **Dado que** la concesión firmada sigue vigente,
- **Cuando** usa una función autorizada offline,
- **Entonces** se permite hasta el límite conocido.

#### Escenario 2: Después de 72 horas
- **Dado que** la concesión caducó sin conexión,
- **Cuando** registra gasto manual,
- **Entonces** puede guardarlo; no queda en solo lectura.

#### Escenario 3: Captura caducada
- **Dado que** la concesión venció,
- **Cuando** llega señal Premium,
- **Entonces** no se procesa nueva captura hasta validar.

#### Escenario 4: Reconectar
- **Dado que** hay cambios manuales pendientes,
- **Cuando** recupera red,
- **Entonces** sincroniza y consulta estado remoto sin duplicarlos.

#### Escenario 5: Reloj alterado
- **Dado que** se retrasa la fecha del teléfono,
- **Cuando** intenta ampliar concesión,
- **Entonces** el cambio no concede más tiempo.

#### Escenario 6: Vence antes
- **Dado que** el plan termina en 12 horas,
- **Cuando** se emite concesión,
- **Entonces** no se autoriza acceso más allá del vencimiento conocido.

---

## HU-60 | Mi plan, Lifetime y auditoría

| Campo | Valor |
| :--- | :--- |
| **Épica** | EP-PLA - Planes, Límites y Monetización Freemium |
| **Responsable de Épica** | Integrante 1 - Desarrollo Android y planes |
| **Prioridad** | Alta |
| **Puntos de Historia** | 8 |
| **Sprint de cierre** | S10 |

### Dependencias y orden

| Tipo | HU | Interpretación |
| :--- | :--- | :--- |
| **Bloqueantes** | HU-53, HU-54, HU-55, HU-56, HU-58, HU-59 | Deben estar disponibles antes de aceptar esta HU. |
| **Parciales** | Ninguna | Permiten avance con contratos/fixtures, pero la integración completa se valida con ellas. |
| **Relacionadas** | HU-06 | No bloquean; comparten datos/reglas y requieren regresión cruzada. |
| **Desbloquea** | Ninguna | HU que usan esta capacidad como dependencia bloqueante directa. |

### Precondiciones

1. Cuenta autenticada y compras verificables; gestión externa de suscripción disponible.

### Descripción Ágil

Como usuario de Kipu, quiero gestionar mi plan y pasar a Lifetime sin doble renovación, para entender vigencias y evitar cobros recurrentes no deseados.

### Reglas de Negocio

1. Mi plan muestra vigencia, renovación/no renovación, gestionar y restaurar.
2. Antes de Lifetime con recurrente activo debe verificarse no renovación.
3. Compra única no cancela sola una suscripción.
4. Auditoría sin datos financieros sensibles ni tokens en logs.
5. Validación de acceso también en backend.

### Criterios de Aceptación (Gherkin)

#### Escenario 1: Mi plan
- **Dado que** tiene suscripción mensual válida,
- **Cuando** abre configuración,
- **Entonces** ve vigencia, renovación y acceso a gestión/restauración.

#### Escenario 2: Lifetime bloqueado
- **Dado que** la mensual sigue renovando,
- **Cuando** solicita Lifetime,
- **Entonces** se pide desactivar renovación y verificarla antes.

#### Escenario 3: Cambio correcto
- **Dado que** se verificó no renovación,
- **Cuando** compra Lifetime válido,
- **Entonces** se habilita sin prometer reembolso por superposición de periodos.

#### Escenario 4: Sin verificación
- **Dado que** no se puede consultar la cancelación,
- **Cuando** intenta comprar Lifetime,
- **Entonces** se pospone explicando el motivo y se conserva plan actual.

#### Escenario 5: Auditoría
- **Dado que** cambia su entitlement,
- **Cuando** se registra evento,
- **Entonces** constan estado, fecha y resultado sin comprobantes ni tokens sensibles.

#### Escenario 6: Bypass
- **Dado que** el cliente altera un indicador local de Premium,
- **Cuando** intenta acción remota protegida,
- **Entonces** el backend aplica permiso verificado y rechaza la manipulación.
