# KIPU V4.2 - Catálogo Canónico de Entidades del Sistema

> **Documento completo integrado — 2026-10-20.** Origen: `KipuApp/Entidades/V4.2_Entidades.md`. Se conserva el diccionario de la versión **2.1 del 20/09/2026**, sin presentarlo como esquema remoto actual. Consultar los [refinamientos por dominio](README.md) y las [diferencias pendientes](../control-documental.md). Las menciones originales a OCR/SMS, estados, cupos o reservas no sustituyen los requisitos y contratos aprobados posteriores.

> **Diccionario exhaustivo de entidades, tablas y atributos para Android nativo (Room/SQLite) y Backend (Supabase/PostgreSQL).**  
> **Versión:** 2.1 Canónica Alineada — Fecha: 20/09/2026.  
> **Referencias:** [Especificación de producto](../producto/especificacion.md), [Product Backlog](../producto/backlog.md), [Arquitectura y modelado](modelo-de-datos.md), [MER](modelo-entidad-relacion.md).

---

## 1. Resumen Estructural de Entidades

El sistema distribuye sus entidades en tres capas de persistencia y visibilidad:
1. **Esquema `public` de Supabase (30 tablas):** Entidades de negocio accesibles por el cliente autenticado bajo estrictas políticas de Row Level Security (RLS `auth.uid() = user_id`).
2. **Esquema `internal` de Supabase (4 tablas):** Libro mayor de doble partida, eventos de facturación y logs de sincronización con `REVOKE ALL` para clientes móviles (solo accesibles vía funciones RPC con `SECURITY DEFINER`).
3. **Esquema Local Android Room (10 tablas exclusivas):** Privacidad estricta, búferes efímeros de OCR/SMS, colas offline y proyecciones de rendimiento UI.

---

## 2. Módulo 1: Identidad, Dispositivos y Configuración

### 2.1 `profiles`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Perfil principal del usuario, preferencias globales de presentación, moneda base, día de corte financiero y contenedor JSONB de opciones estéticas/privacidad (ej. máscara de montos sensibles `MoneyText`).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `user_id` | `UUID` | `TEXT` | No | | PK, FK `auth.users(id)` ON DELETE CASCADE | Identificador de seguridad del usuario. |
| `display_name` | `TEXT` | `TEXT` | Sí | | | Nombre visible o alias del usuario. |
| `currency_code` | `CHAR(3)` | `TEXT` | No | `'PEN'` | FK `currencies(code)` | Moneda base por defecto (PEN para Perú). |
| `time_zone` | `TEXT` | `TEXT` | No | `'America/Lima'` | | Zona horaria IANA para cómputo de fechas civiles. |
| `month_start` | `SMALLINT` | `INTEGER` | No | `1` | CHECK `(month_start >= 1 AND month_start <= 28)` | Día de inicio del mes financiero. |
| `hide_balances` | `BOOLEAN` | `INTEGER` | No | `false` | | Flag para ocultamiento con máscara `MoneyText`. |
| `theme_mode` | `TEXT` | `TEXT` | No | `'SYSTEM'` | CHECK `theme_mode IN ('LIGHT','DARK','SYSTEM')` | Modo del tema visual de la UI. |
| `biometric_enabled`| `BOOLEAN` | `INTEGER` | No | `false` | | Flag de autenticación biométrica requerida. |
| `preferences` | `JSONB` | `TEXT` | No | `'{}'` | | Diccionario extensible de preferencias del cliente. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de última modificación. |

* **Políticas RLS:** `profiles_own` (ALL para `auth.uid() = user_id`).

---

### 2.2 `user_devices`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Registro de instalaciones móviles autorizadas. Controla el límite de dispositivos del plan (Free: máx. 1; Pro: máx. 5).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador único de la instalación. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario del dispositivo. |
| `device_fingerprint` | `TEXT` | `TEXT` | No | | | Hash criptográfico del dispositivo. |
| `device_name` | `TEXT` | `TEXT` | No | | | Nombre comercial (ej. "Samsung Galaxy S23"). |
| `platform` | `TEXT` | `TEXT` | No | `'ANDROID'` | CHECK `platform IN ('ANDROID','IOS','WEB')` | Plataforma cliente. |
| `app_version` | `TEXT` | `TEXT` | Sí | | | Versión semántica de la aplicación instalada. |
| `last_sync_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Último contacto confirmado de sincronización. |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Estado operativo del dispositivo. |
| `revoked_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Timestamp en que se revocó el acceso. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de registro inicial. |

---

### 2.3 `account_deletion_requests`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Mutable.
* **Propósito:** Registro regulatorio para el derecho al olvido y eliminación programada (periodo de gracia de 30 días).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la solicitud. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Usuario solicitante. |
| `status` | `TEXT` | `TEXT` | No | `'REQUESTED'` | CHECK `status IN ('REQUESTED','PROCESSING','COMPLETED','FAILED','CANCELED')` | Estado del proceso de borrado. |
| `requested_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Momento de la petición. |
| `scheduled_for` | `TIMESTAMPTZ` | `INTEGER` | No | `now() + 30 days` | | Fecha proyectada de purga definitiva. |
| `completed_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Momento de ejecución final. |
| `error_details` | `TEXT` | `TEXT` | Sí | | | Mensaje técnico en caso de fallo. |

---

## 3. Módulo 2: Catálogos Maestros y Clasificación

### 3.1 `currencies`
* **Esquema:** `public` | **Réplica Room:** Sí (Cache) | **Sincronización:** Solo lectura | **Tipo:** Catálogo.
* **Propósito:** Catálogo estandarizado ISO 4217 de monedas permitidas.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `code` | `CHAR(3)` | `TEXT` | No | | PK | Código ISO (ej. 'PEN', 'USD', 'EUR'). |
| `symbol` | `TEXT` | `TEXT` | No | | | Símbolo visual (ej. 'S/', '$', '€'). |
| `name` | `TEXT` | `TEXT` | No | | | Nombre formal de la divisa. |
| `scale` | `SMALLINT` | `INTEGER` | No | `2` | CHECK `scale >= 0 AND scale <= 4` | Cantidad de decimales de la moneda. |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Disponibilidad para nuevas cuentas. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Fecha de alta en el catálogo. |

---

### 3.2 `categories`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Jerarquía mutable.
* **Propósito:** Taxonomía de gastos e ingresos. Árbol de **máximo 2 niveles** (raíz y subcategoría) mediante `parent_id`. Cumple estrictamente con **RF-K10: Cero etiquetas/tags**.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la categoría. |
| `user_id` | `UUID` | `TEXT` | Sí | | FK `auth.users(id)` ON DELETE CASCADE | NULL para categorías globales del sistema. |
| `name` | `TEXT` | `TEXT` | No | | | Nombre visible de la categoría. |
| `icon_key` | `TEXT` | `TEXT` | Sí | | | Identificador del icono en la app. |
| `color_argb` | `INTEGER` | `INTEGER` | Sí | | | Valor numérico ARGB (legacy). |
| `color_token` | `TEXT` | `TEXT` | Sí | | | Token semántico de color de Stitch/Material. |
| `parent_id` | `UUID` | `TEXT` | Sí | | FK `categories(id)` ON DELETE RESTRICT | NULL = Categoría raíz; Not Null = Subcategoría. |
| `is_system` | `BOOLEAN` | `INTEGER` | No | `false` | | Flag de plantilla predeterminada inmutable. |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Estado de activación. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Revisión de sincronización optimista. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de última edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Tombstone de eliminación lógica. |

---

### 3.3 `merchant_services`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Catálogo de comercios habituales y servicios recurrentes (Netflix, Spotify, Luz, Agua). Es **totalmente independiente de la categoría** para permitir reclasificaciones sin perder el comercio.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del comercio/servicio. |
| `user_id` | `UUID` | `TEXT` | Sí | | FK `auth.users(id)` ON DELETE CASCADE | Propietario (NULL si es global). |
| `name` | `TEXT` | `TEXT` | No | | | Nombre canónico visible. |
| `normalized_name` | `TEXT` | `TEXT` | No | | | Texto en mayúsculas y sin acentos para match. |
| `is_system` | `BOOLEAN` | `INTEGER` | No | `false` | | Predefinido por la plataforma. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Revisión de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de actualización. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 3.4 `merchant_rules`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Motor de auto-clasificación por patrones de texto (Regex o substrings) observados en SMS, comprobantes y notificaciones.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la regla. |
| `user_id` | `UUID` | `TEXT` | Sí | | FK `auth.users(id)` ON DELETE CASCADE | Propietario de la regla. |
| `pattern` | `TEXT` | `TEXT` | No | | | Expresión o palabra clave observada. |
| `merchant_service_id` | `UUID` | `TEXT` | Sí | | FK `merchant_services(id)` | Comercio asociado automáticamente. |
| `category_id` | `UUID` | `TEXT` | Sí | | FK `categories(id)` | Categoría asignada automáticamente. |
| `priority` | `INTEGER` | `INTEGER` | No | `100` | | Orden de evaluación (mayor = antes). |
| `is_system` | `BOOLEAN` | `INTEGER` | No | `false` | | Regla global del sistema. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de versión. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Creación de la regla. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Modificación de la regla. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 3.5 `credit_products`
* **Esquema:** `public` | **Réplica Room:** Sí (Cache) | **Sincronización:** Solo lectura | **Tipo:** Catálogo referencial.
* **Propósito:** Catálogo de productos crediticios de instituciones financieras peruanas (BCP, BBVA, Interbank, Scotiabank, Falabella) con tasas referenciales de mercado (TEA).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del producto financiero. |
| `institution_code` | `TEXT` | `TEXT` | No | | | Código de la entidad (ej. 'BCP', 'INTERBANK'). |
| `product_name` | `TEXT` | `TEXT` | No | | | Nombre comercial (ej. 'Visa Signature Latam'). |
| `card_network` | `TEXT` | `TEXT` | Sí | | CHECK `card_network IN ('VISA','MASTERCARD','AMEX','DINERS','OTHER')` | Red de pago. |
| `reference_tea_bps` | `INTEGER` | `INTEGER` | Sí | | CHECK `reference_tea_bps >= 0` | Tasa Efectiva Anual referencial en basis points. |
| `effective_from` | `DATE` | `TEXT` | No | `CURRENT_DATE` | | Fecha de inicio de vigencia de la tasa. |
| `effective_to` | `DATE` | `TEXT` | Sí | | | Fecha de caducidad de la tasa referencial. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |

---

## 4. Módulo 3: Núcleo Contable, Cuentas e Instrumentos

### 4.1 `accounts`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable (sin saldo almacenado).
* **Propósito:** Contenedores de dinero del usuario (efectivo, bancos, billeteras Yape/Plin, cuenta virtual de metas `GOALS_VIRTUAL` y pasivos `CREDIT_LIABILITY`).  
* **Regla Contable:** **Nunca almacena un saldo mutable**. El saldo se calcula en tiempo de consulta mediante `v_account_balances` desde `internal.ledger_entries`.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la cuenta. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario de la cuenta. |
| `name` | `TEXT` | `TEXT` | No | | | Nombre asignado (ej. 'Cuenta Sueldo BCP'). |
| `account_type` | `TEXT` | `TEXT` | No | | CHECK `account_type IN ('CASH','BANK','WALLET','SAVINGS','GOALS_VIRTUAL','CREDIT_LIABILITY')` | Naturaleza contable de la cuenta. |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda de la cuenta. |
| `institution_code` | `TEXT` | `TEXT` | Sí | | | Código de banco emisor si aplica. |
| `color_argb` | `INTEGER` | `INTEGER` | Sí | | | Color visual. |
| `icon_key` | `TEXT` | `TEXT` | Sí | | | Clave del icono. |
| `is_archived` | `BOOLEAN` | `INTEGER` | No | `false` | | Flag de archivado. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Revisión de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de modificación. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 4.2 `cards`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Tarjetas de débito y líneas de crédito (pasivos). Registra metadatos no sensibles (`last4`, `network`, `style_preset_id`) y parámetros financieros (`credit_limit_minor`, `closing_day`, `due_day`, `personal_tea_bps`).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador único de la tarjeta. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `account_id` | `UUID` | `TEXT` | No | | FK `accounts(id)` ON DELETE RESTRICT | Cuenta base (Activo para débito; pasivo para crédito). |
| `network` | `TEXT` | `TEXT` | Sí | | CHECK `network IN ('VISA','MASTERCARD','AMEX','DINERS','OTHER')` | Franquicia de la tarjeta. |
| `alias` | `TEXT` | `TEXT` | No | | | Nombre distintivo (ej. 'CMR Falabella'). |
| `last4` | `CHAR(4)` | `TEXT` | Sí | | CHECK `last4 IS NULL OR length(last4) = 4` | Últimos 4 dígitos (nunca PAN completo). |
| `is_credit` | `BOOLEAN` | `INTEGER` | No | `false` | | Diferenciador débito vs crédito. |
| `credit_limit_minor` | `BIGINT` | `INTEGER` | Sí | | CHECK `credit_limit_minor >= 0` | Límite de crédito en céntimos (si es crédito). |
| `closing_day` | `SMALLINT` | `INTEGER` | Sí | | CHECK `closing_day >= 1 AND closing_day <= 28` | Día de cierre mensual de facturación. |
| `due_day` | `SMALLINT` | `INTEGER` | Sí | | CHECK `due_day >= 1 AND due_day <= 28` | Día de vencimiento de pago mensual. |
| `personal_tea_bps` | `INTEGER` | `INTEGER` | Sí | | CHECK `personal_tea_bps >= 0` | TEA real del usuario en basis points. |
| `product_id` | `UUID` | `TEXT` | Sí | | FK `credit_products(id)` | Producto del catálogo referencial offline. |
| `style_preset_id` | `TEXT` | `TEXT` | Sí | | | Identificador del estilo visual de la tarjeta. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Versión de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de actualización. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 4.3 `transactions`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable con auditoría.
* **Propósito:** El **Ledger de Movimientos**. Es el libro contable principal de la app. Registra la transacción financiera vinculando cuenta origen, categoría, comercio/servicio opcional, monto en céntimos (`amount_minor`), fecha y estado.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador único de la transacción. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario del movimiento. |
| `account_id` | `UUID` | `TEXT` | No | | FK `accounts(id)` ON DELETE RESTRICT | Cuenta contable primaria. |
| `card_id` | `UUID` | `TEXT` | Sí | | FK `cards(id)` ON DELETE SET NULL | Tarjeta empleada en la operación. |
| `category_id` | `UUID` | `TEXT` | Sí | | FK `categories(id)` ON DELETE SET NULL | Categoría de gasto o ingreso. |
| `merchant_service_id`| `UUID` | `TEXT` | Sí | | FK `merchant_services(id)` ON DELETE SET NULL | Comercio o servicio identificado. |
| `transaction_type` | `TEXT` | `TEXT` | No | | CHECK `transaction_type IN ('EXPENSE','INCOME','TRANSFER')` | Tipo fundamental de movimiento. |
| `operation_kind` | `TEXT` | `TEXT` | No | `'STANDARD'` | CHECK `operation_kind IN ('STANDARD','CARD_PURCHASE','CARD_PAYMENT','DEBT_DISBURSEMENT','DEBT_PAYMENT','GOAL_FUNDING','REFUND','ADJUSTMENT')` | Subtipo de operación de dominio. |
| `amount_minor` | `BIGINT` | `INTEGER` | No | | CHECK `amount_minor > 0` | Monto absoluto en céntimos (> 0). |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda de la transacción. |
| `transfer_group_id` | `UUID` | `TEXT` | Sí | | | Identificador de enlace de transferencia. |
| `occurred_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Instante real del hecho económico. |
| `installment_count` | `SMALLINT` | `INTEGER` | No | `1` | CHECK `installment_count >= 1` | Número de cuotas pactadas. |
| `interest_mode` | `TEXT` | `TEXT` | No | `'NONE'` | CHECK `interest_mode IN ('NONE','INCLUDED','REFERENTIAL')` | Modalidad de interés aplicado. |
| `merchant_raw_text` | `TEXT` | `TEXT` | Sí | | | Texto original recibido en la captura. |
| `notes` | `TEXT` | `TEXT` | Sí | | | Comentario del usuario. |
| `refund_of_transaction_id`| `UUID`| `TEXT` | Sí | | FK `transactions(id)` | Transacción original si es reembolso. |
| `status` | `TEXT` | `TEXT` | No | `'CONFIRMED'` | CHECK `status IN ('CONFIRMED','REVISED','VOIDED')` | Estado del movimiento. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Versión para resolución de conflictos. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de inserción. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de última actualización. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Tombstone de anulación. |

---

### 4.4 `transaction_links`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Relacional inmutable.
* **Propósito:** Enlaces de Transacciones. Se encarga de emparejar transacciones relacionadas (transferencias entre cuentas, pagos de tarjetas de crédito **RN-06**, amortizaciones de deudas, reembolsos y anulaciones **RN-12**, **DTV-09**). Garantiza que la operación se mueva en el ledger como un traslado de fondos y no duplique el gasto ni altere la analítica.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del enlace. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario de la relación. |
| `source_transaction_id`| `UUID` | `TEXT` | No | | FK `transactions(id)` ON DELETE CASCADE | Transacción origen (salida de fondos / pago). |
| `target_transaction_id`| `UUID` | `TEXT` | No | | FK `transactions(id)` ON DELETE CASCADE | Transacción destino (abono / recepción / reembolso). |
| `link_type` | `TEXT` | `TEXT` | No | | CHECK `link_type IN ('TRANSFER','CARD_PAYMENT','DEBT_AMORTIZATION','REFUND','REVERSAL')` | Propósito del emparejamiento. |
| `metadata` | `JSONB` | `TEXT` | No | `'{}'` | | Datos contextuales adicionales. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación del enlace. |

* **Restricciones de Integridad:** CHECK `source_transaction_id <> target_transaction_id`. UNIQUE `(source_transaction_id, target_transaction_id, link_type)`.

---

### 4.5 `transaction_revisions`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Append-only.
* **Propósito:** Registro histórico inmutable de auditoría para cada modificación realizada sobre una transacción.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la revisión. |
| `transaction_id` | `UUID` | `TEXT` | No | | FK `transactions(id)` ON DELETE CASCADE | Transacción auditada. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Autor del cambio. |
| `revision_number` | `BIGINT` | `INTEGER` | No | | | Número secuencial de revisión (1, 2, 3...). |
| `previous_payload` | `JSONB` | `TEXT` | No | | | Estado anterior completo. |
| `new_payload` | `JSONB` | `TEXT` | No | | | Estado nuevo resultante. |
| `change_reason` | `TEXT` | `TEXT` | Sí | | | Motivo del ajuste. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de la modificación. |

---

### 4.6 `internal.ledger_entries`
* **Esquema:** `internal` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Append-only estricto.
* **Propósito:** Asientos contables de partida doble. Constituye la **única autoridad de saldos** en Kipu.  
* **Seguridad:** Los clientes móviles tienen `REVOKE ALL` sobre esta tabla; solo se alimenta vía RPCs `SECURITY DEFINER`.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del asiento contable. |
| `transaction_id` | `UUID` | `TEXT` | No | | FK `transactions(id)` ON DELETE CASCADE | Transacción origen del asiento. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `account_id` | `UUID` | `TEXT` | No | | FK `accounts(id)` ON DELETE RESTRICT | Cuenta contable afectada. |
| `signed_amount_minor`| `BIGINT` | `INTEGER` | No | | CHECK `signed_amount_minor <> 0` | Importe con signo (+ ingreso, - egreso). |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda del asiento. |
| `entry_role` | `TEXT` | `TEXT` | No | | CHECK `entry_role IN ('PRIMARY','SOURCE','DESTINATION','LIABILITY','REVERSAL')` | Rol del asiento en la partida. |
| `occurred_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Fecha efectiva contable. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de generación. |

---

## 5. Módulo 4: Créditos y Amortizaciones

### 5.1 `credit_installments`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Cronograma de cuotas pactadas para compras con tarjeta de crédito (`operation_kind = 'CARD_PURCHASE'`). Reparto determinista de centavos.

| Campo                | Tipo Postgres | Tipo Room | Nulo | Default             | Restricciones / Claves                                  | Descripción                          |
| :------------------- | :------------ | :-------- | :--: | :------------------ | :------------------------------------------------------ | :----------------------------------- |
| `id`                 | `UUID`        | `TEXT`    |  No  | `gen_random_uuid()` | PK                                                      | Identificador de la cuota.           |
| `user_id`            | `UUID`        | `TEXT`    |  No  |                     | FK `auth.users(id)` ON DELETE CASCADE                   | Propietario.                         |
| `transaction_id`     | `UUID`        | `TEXT`    |  No  |                     | FK `transactions(id)` ON DELETE CASCADE                 | Transacción de compra a crédito.     |
| `installment_number` | `SMALLINT`    | `INTEGER` |  No  |                     | CHECK `installment_number >= 1`                         | Número correlativo de cuota (1..N).  |
| `due_date`           | `DATE`        | `TEXT`    |  No  |                     |                                                         | Fecha de vencimiento pactada.        |
| `principal_minor`    | `BIGINT`      | `INTEGER` |  No  |                     | CHECK `principal_minor >= 0`                            | Amortización de capital en céntimos. |
| `interest_minor`     | `BIGINT`      | `INTEGER` |  No  | `0`                 | CHECK `interest_minor >= 0`                             | Interés asignado a la cuota.         |
| `status`             | `TEXT`        | `TEXT`    |  No  | `'PENDING'`         | CHECK `status IN ('PENDING','PARTIAL','PAID','VOIDED')` | Estado de la cuota.                  |
| `revision`           | `BIGINT`      | `INTEGER` |  No  | `1`                 |                                                         | Versión de sincronización.           |
| `created_at`         | `TIMESTAMPTZ` | `INTEGER` |  No  | `now()`             |                                                         | Creación del registro.               |
| `updated_at`         | `TIMESTAMPTZ` | `INTEGER` |  No  | `now()`             |                                                         | Modificación del registro.           |
| `deleted_at`         | `TIMESTAMPTZ` | `INTEGER` |  Sí  |                     |                                                         | Eliminación lógica.                  |

---

### 5.2 `credit_payment_allocations`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Append-only.
* **Propósito:** Asignación N:M inmutable que vincula una transacción de pago de tarjeta (`CARD_PAYMENT`) con las cuotas amortizadas.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la asignación. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `payment_transaction_id`| `UUID`| `TEXT` | No | | FK `transactions(id)` ON DELETE CASCADE | Transacción de pago ejecutada. |
| `installment_id` | `UUID` | `TEXT` | No | | FK `credit_installments(id)` ON DELETE CASCADE| Cuota amortizada. |
| `allocated_minor` | `BIGINT` | `INTEGER` | No | | CHECK `allocated_minor > 0` | Importe aplicado en céntimos. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de la asignación. |

---

## 6. Módulo 5: Deudas y Préstamos

### 6.1 `debts`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Módulo de Deudas. Registra deudas del usuario (`PAYABLE`) o préstamos otorgados a terceros (`RECEIVABLE`). Almacena capital inicial (`total_minor`), saldo pendiente y estado. Si es deuda propia, registra condicionalmente si generó dinero recibido incrementando el activo correspondiente sin generar un ingreso operativo falso.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la deuda/préstamo. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `obligation_type` | `TEXT` | `TEXT` | No | | CHECK `obligation_type IN ('PAYABLE','RECEIVABLE')` | Tipo: Deuda propia o préstamo otorgado. |
| `counterparty_name` | `TEXT` | `TEXT` | No | | | Nombre del acreedor o deudor. |
| `total_minor` | `BIGINT` | `INTEGER` | No | | CHECK `total_minor > 0` | Principal pactado en céntimos. |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda de la deuda. |
| `due_date` | `DATE` | `TEXT` | Sí | | | Fecha límite general de pago. |
| `notes` | `TEXT` | `TEXT` | Sí | | | Notas adicionales. |
| `status` | `TEXT` | `TEXT` | No | `'ACTIVE'` | CHECK `status IN ('ACTIVE','SETTLED','CANCELLED')` | Estado de la deuda. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de versión. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de registro. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 6.2 `debt_installments`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Cronograma pactado de cuotas para la amortización de una deuda o cobro de un préstamo.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la cuota de deuda. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `debt_id` | `UUID` | `TEXT` | No | | FK `debts(id)` ON DELETE CASCADE | Deuda a la que pertenece. |
| `installment_number` | `SMALLINT` | `INTEGER` | No | | CHECK `installment_number >= 1` | Número correlativo de cuota. |
| `due_date` | `DATE` | `TEXT` | No | | | Vencimiento de la cuota. |
| `amount_minor` | `BIGINT` | `INTEGER` | No | | CHECK `amount_minor > 0` | Importe de la cuota en céntimos. |
| `status` | `TEXT` | `TEXT` | No | `'PENDING'` | CHECK `status IN ('PENDING','PAID','CANCELLED')` | Estado de la cuota. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Versión de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 6.3 `debt_events`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Append-only.
* **Propósito:** Hechos financieros que alteran el saldo de la deuda (desembolsos, amortizaciones, ajustes, condonaciones) sin contaminar los gastos operativos.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del evento de deuda. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `debt_id` | `UUID` | `TEXT` | No | | FK `debts(id)` ON DELETE CASCADE | Deuda afectada. |
| `transaction_id` | `UUID` | `TEXT` | Sí | | FK `transactions(id)` | Transacción bancaria si hubo flujo real. |
| `installment_id` | `UUID` | `TEXT` | Sí | | FK `debt_installments(id)` | Cuota amortizada si aplica. |
| `event_type` | `TEXT` | `TEXT` | No | | CHECK `event_type IN ('DISBURSEMENT','PAYMENT','ADJUSTMENT','FORGIVENESS')` | Naturaleza del evento. |
| `amount_minor` | `BIGINT` | `INTEGER` | No | | CHECK `amount_minor > 0` | Importe en céntimos. |
| `occurred_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Instante del evento. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Registro del evento. |

---

## 7. Módulo 6: Metas de Ahorro

### 7.1 `goals`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Metas de Ahorro. Almacena los objetivos financieros del usuario (`target_minor`, `target_date`). Se asocia a la única Cuenta Metas virtual unificada (`GOALS_VIRTUAL`) encargada de acumular y centralizar aportes de ahorro de forma lógica, evitando duplicar activos reales sobre cuentas bancarias físicas.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la meta. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `name` | `TEXT` | `TEXT` | No | | | Nombre del objetivo (ej. 'Fondo de Emergencia'). |
| `target_minor` | `BIGINT` | `INTEGER` | No | | CHECK `target_minor > 0` | Monto objetivo en céntimos. |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda de la meta. |
| `target_date` | `DATE` | `TEXT` | Sí | | | Fecha estimada de cumplimiento. |
| `icon_key` | `TEXT` | `TEXT` | Sí | | | Icono de la meta. |
| `color_argb` | `INTEGER` | `INTEGER` | Sí | | | Color visual. |
| `status` | `TEXT` | `TEXT` | No | `'ACTIVE'` | CHECK `status IN ('ACTIVE','COMPLETED','ARCHIVED')` | Estado de la meta. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 7.2 `goal_events`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Append-only.
* **Propósito:** Historial de aportes y liberaciones hacia la meta. Los movimientos de ahorro o aportes hacia la Cuenta Metas virtual se unifican contablemente bajo el tipo Transferencia.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del evento de meta. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `goal_id` | `UUID` | `TEXT` | No | | FK `goals(id)` ON DELETE CASCADE | Meta afectada. |
| `transaction_id` | `UUID` | `TEXT` | Sí | | FK `transactions(id)` | Transacción bancaria de transferencia. |
| `event_type` | `TEXT` | `TEXT` | No | | CHECK `event_type IN ('ALLOCATION','RELEASE','DIRECT_DEPOSIT','ADJUSTMENT')` | Tipo de movimiento. |
| `amount_minor` | `BIGINT` | `INTEGER` | No | | CHECK `amount_minor <> 0` | Monto aportado (+) o liberado (-). |
| `occurred_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Fecha del movimiento. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Registro del evento. |

---

## 8. Módulo 7: Recurrencias y Calendario

### 8.1 `recurrences`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Reglas de proyección periódica para gastos e ingresos fijos (sueldos, suscripciones, servicios públicos).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la regla recurrente. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `template_transaction`| `JSONB` | `TEXT` | No | | | Plantilla del movimiento proyectado. |
| `frequency` | `TEXT` | `TEXT` | No | | CHECK `frequency IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY','CUSTOM')` | Periodicidad. |
| `interval_count` | `INTEGER` | `INTEGER` | No | `1` | CHECK `interval_count >= 1` | Multiplicador de periodicidad. |
| `start_date` | `DATE` | `TEXT` | No | | | Fecha de inicio de la recurrencia. |
| `end_date` | `DATE` | `TEXT` | Sí | | | Fecha de culminación opcional. |
| `next_due_date` | `DATE` | `TEXT` | No | | | Próximo vencimiento calculado. |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Estado activo de la regla. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de versión. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 8.2 `recurrence_occurrences`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Instancias individuales proyectadas de una regla recurrente para el calendario financiero y su emparejamiento con transacciones reales.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la ocurrencia. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `recurrence_id` | `UUID` | `TEXT` | No | | FK `recurrences(id)` ON DELETE CASCADE | Regla generadora. |
| `due_date` | `DATE` | `TEXT` | No | | | Fecha proyectada. |
| `expected_amount_minor`| `BIGINT`| `INTEGER` | Sí | | CHECK `expected_amount_minor > 0` | Importe esperado en céntimos. |
| `matched_transaction_id`| `UUID` | `TEXT` | Sí | | FK `transactions(id)` | Transacción confirmada que cumplió la cita. |
| `status` | `TEXT` | `TEXT` | No | `'EXPECTED'` | CHECK `status IN ('EXPECTED','CONFIRMED','SKIPPED')` | Estado de la ocurrencia. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de sincronización. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Creación del registro. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Modificación del registro. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

## 9. Módulo 8: Presupuestos y Control de Gasto

### 9.1 `budgets`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Configuración de Presupuestos. Almacena las reglas y políticas de límites de gastos impuestas por el usuario (total o por categoría/subcategoría). En el plan Free, se restringe a un máximo de **2 presupuestos activos**.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del presupuesto. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `category_id` | `UUID` | `TEXT` | Sí | | FK `categories(id)` ON DELETE RESTRICT | NULL = Presupuesto Total General. |
| `name` | `TEXT` | `TEXT` | No | | | Nombre del presupuesto (ej. 'Supermercado'). |
| `limit_minor` | `BIGINT` | `INTEGER` | No | | CHECK `limit_minor > 0` | Límite máximo en céntimos. |
| `currency_code` | `CHAR(3)` | `TEXT` | No | | FK `currencies(code)` | Moneda del presupuesto. |
| `period_type` | `TEXT` | `TEXT` | No | `'MONTHLY'` | CHECK `period_type IN ('MONTHLY','WEEKLY','CUSTOM')` | Periodicidad del ciclo. |
| `alert_thresholds_bps` | `SMALLINT[]`| `TEXT` | No | `'{5000,8000,10000}'` | | Umbrales de alerta (50%, 80%, 100% en bps). |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Estado activo. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de versión. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de edición. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica. |

---

### 9.2 `budget_periods`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Inmutable por ciclo.
* **Propósito:** Historial de Períodos de Presupuesto. Registra las vigencias y consumos acumulados reales de cada ciclo de presupuesto, congelando los límites para no distorsionar meses históricos.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del periodo. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `budget_id` | `UUID` | `TEXT` | No | | FK `budgets(id)` ON DELETE CASCADE | Presupuesto matriz. |
| `starts_on` | `DATE` | `TEXT` | No | | | Fecha física de inicio de la vigencia. |
| `ends_on` | `DATE` | `TEXT` | No | | | Fecha física de fin de la vigencia. |
| `limit_minor` | `BIGINT` | `INTEGER` | No | | CHECK `limit_minor > 0` | Snapshot del límite asignado al periodo. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de creación del periodo. |

---

## 10. Módulo 9: Notificaciones y Captura Asistida

### 10.1 `app_notifications`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Centro de alertas del usuario (alertas de presupuesto, vencimientos de tarjetas y deudas, hitos de metas).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la notificación. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Destinatario. |
| `title` | `TEXT` | `TEXT` | No | | | Título del mensaje. |
| `body` | `TEXT` | `TEXT` | No | | | Contenido de la alerta. |
| `notification_type` | `TEXT` | `TEXT` | No | | CHECK `notification_type IN ('BUDGET_ALERT','BILLING_DUE','GOAL_REACHED','SYSTEM')` | Tipo de evento notificado. |
| `reference_entity_type`| `TEXT`| `TEXT` | Sí | | | Tipo de entidad asociada ('budget','card','goal','debt'). |
| `reference_entity_id` | `UUID` | `TEXT` | Sí | | | ID para navegación profunda en UI. |
| `is_read` | `BOOLEAN` | `INTEGER` | No | `false` | | Estado de lectura. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Momento de emisión. |
| `deleted_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Eliminación lógica por el usuario. |

---

### 10.2 `import_candidates`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Bandeja de Entrada de Automatización. Almacena temporalmente los datos preliminares detectados por el motor de captura offline (notificaciones en segundo plano, SMS, Sharesheet de Android con OCR de Yape local vía Google ML Kit) con su `raw_hash`, `source_app`, texto original y nivel de confianza. Permanece aquí hasta que el usuario confirma el movimiento para ingresarlo al ledger real, o lo descarta.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del candidato. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `raw_hash` | `TEXT` | `TEXT` | No | | | Huella digital / fingerprint del texto crudo. |
| `source_app` | `TEXT` | `TEXT` | No | | | Origen ('PE_YAPE', 'PE_PLIN', 'SMS_BCP', etc.). |
| `raw_text` | `TEXT` | `TEXT` | No | | | Texto completo capturado. |
| `extracted_data` | `JSONB` | `TEXT` | No | `'{}'` | | JSON con campos extraídos (monto, comercio, fecha). |
| `confidence_score` | `NUMERIC(4,3)`| `REAL` | No | `1.000` | CHECK `confidence_score >= 0.000 AND confidence_score <= 1.000` | Certidumbre de la inferencia (0.000 a 1.000). |
| `status` | `TEXT` | `TEXT` | No | `'PENDING'` | CHECK `status IN ('PENDING','CONFIRMED','DISCARDED')` | Estado en la bandeja de entrada. |
| `confirmed_transaction_id`| `UUID`| `TEXT` | Sí | | FK `transactions(id)` ON DELETE SET NULL | Transacción real generada tras confirmación. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de captura. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de confirmación o descarte. |

* **Restricciones:** UNIQUE `(user_id, raw_hash)`.

---

## 11. Módulo 10: Facturación, Planes y Monetización

### 11.1 `billing_products`
* **Esquema:** `public` | **Réplica Room:** Sí (Cache) | **Sincronización:** Solo lectura | **Tipo:** Catálogo administrativo.
* **Propósito:** Definición de productos de suscripción y planes ofertados en Google Play Store.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `TEXT` | `TEXT` | No | | PK | Identificador del producto (ej. 'kipu_pro_monthly'). |
| `store_product_id` | `TEXT` | `TEXT` | No | | | Product ID en Google Play Console. |
| `base_plan_id` | `TEXT` | `TEXT` | Sí | | | Base plan ID para suscripciones periódicas. |
| `name` | `TEXT` | `TEXT` | No | | | Nombre comercial (ej. 'Kipu Pro Mensual'). |
| `plan_type` | `TEXT` | `TEXT` | No | | CHECK `plan_type IN ('FREE','PRO_MONTHLY','PRO_ANNUAL')` | Tipo de plan comercial. |
| `features` | `JSONB` | `TEXT` | No | `'{}'` | | Diccionario de cuotas y capacidades. |
| `is_active` | `BOOLEAN` | `INTEGER` | No | `true` | | Habilitado para compra. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |

---

### 11.2 `billing_purchases`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Servidor $\rightarrow$ Cliente | **Tipo:** Solo lectura cliente.
* **Propósito:** Compras y suscripciones verificadas en Google Play. El cliente móvil **nunca escribe directamente**; se actualiza mediante Edge Functions con RTDN o validación de token.

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la compra. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `order_id` | `TEXT` | `TEXT` | Sí | | | Order ID de Google Play (GPA.XXXX). |
| `purchase_token_hash`| `TEXT` | `TEXT` | No | | UNIQUE | Hash SHA-256 del token de compra. |
| `product_id` | `TEXT` | `TEXT` | No | | FK `billing_products(id)` | Producto adquirido. |
| `purchase_state` | `TEXT` | `TEXT` | No | | CHECK `purchase_state IN ('PURCHASED','PENDING','CANCELLED')` | Estado de la compra. |
| `entitlement_state` | `TEXT` | `TEXT` | No | | CHECK `entitlement_state IN ('ACTIVE','GRACE_PERIOD','ON_HOLD','PAUSED','EXPIRED')` | Estado del derecho Pro. |
| `starts_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Inicio de la vigencia. |
| `expires_at` | `TIMESTAMPTZ` | `INTEGER` | Sí | | | Expiración (NULL para vitalicio). |
| `verified_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Última confirmación con la API de Google Play. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de alta. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de actualización. |

---

### 11.3 `internal.billing_events`
* **Esquema:** `internal` | **Réplica Room:** No | **Sincronización:** No | **Tipo:** Append-only estricto.
* **Propósito:** Historial inmutable de auditoría para eventos de facturación y notificaciones RTDN de Google Play.

| Campo | Tipo Postgres | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | PK | Identificador del evento. |
| `user_id` | `UUID` | Sí | | FK `auth.users(id)` ON DELETE CASCADE | Usuario relacionado. |
| `purchase_token_hash`| `TEXT` | No | | | Hash de correlación. |
| `event_type` | `TEXT` | No | | CHECK `event_type IN ('PURCHASE','RENEWAL','CANCELLATION','RTDN_UPDATE','VERIFICATION')` | Tipo de evento. |
| `raw_payload` | `JSONB` | No | | | Payload íntegro recibido del webhook de Google. |
| `received_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp de recepción. |

---

### 11.4 `plan_selections`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Mutable.
* **Propósito:** Registro de las selecciones activas de recursos del usuario sujetos a cuotas del plan (presupuestos, deudas, dispositivos).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador de la selección. |
| `user_id` | `UUID` | `TEXT` | No | | FK `auth.users(id)` ON DELETE CASCADE | Propietario. |
| `feature_key` | `TEXT` | `TEXT` | No | | CHECK `feature_key IN ('BUDGETS','OBLIGATIONS','DEVICES')` | Recurso limitado. |
| `revision` | `BIGINT` | `INTEGER` | No | `1` | | Control de versión. |
| `updated_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de actualización. |

* **Restricciones:** UNIQUE `(user_id, feature_key)`.

---

### 11.5 `plan_selection_items`
* **Esquema:** `public` | **Réplica Room:** Sí | **Sincronización:** Bidireccional | **Tipo:** Relacional.
* **Propósito:** Elementos individuales seleccionados para ocupar las cuotas del plan Free (ej. los 2 presupuestos activos o las 2 deudas activas).

| Campo | Tipo Postgres | Tipo Room | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | `TEXT` | No | `gen_random_uuid()` | PK | Identificador del ítem. |
| `plan_selection_id` | `UUID` | `TEXT` | No | | FK `plan_selections(id)` ON DELETE CASCADE | Selección matriz. |
| `resource_id` | `UUID` | `TEXT` | No | | | ID de la entidad seleccionada (presupuesto, deuda, etc.). |
| `resource_type` | `TEXT` | `TEXT` | No | | | Tipo de recurso seleccionado. |
| `created_at` | `TIMESTAMPTZ` | `INTEGER` | No | `now()` | | Timestamp de inclusión. |

* **Restricciones:** UNIQUE `(plan_selection_id, resource_id)`.

---

## 12. Módulo 11: Sincronización e Idempotencia

### 12.1 `internal.command_receipts`
* **Esquema:** `internal` | **Réplica Room:** No | **Sincronización:** No | **Tipo:** Append-only.
* **Propósito:** Garantía de idempotencia estricta para mutaciones remotas enviadas por los dispositivos móviles.

| Campo | Tipo Postgres | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | PK | Identificador del recibo. |
| `user_id` | `UUID` | No | | FK `auth.users(id)` ON DELETE CASCADE | Usuario emisor. |
| `idempotency_key` | `TEXT` | No | | | Clave de idempotencia del cliente. |
| `command_type` | `TEXT` | No | | | Tipo de comando despachado. |
| `request_hash` | `TEXT` | No | | | Hash MD5/SHA-256 del payload recibido. |
| `response_payload` | `JSONB` | Sí | | | Respuesta previamente calculada para reenvío idéntico. |
| `status` | `TEXT` | No | | CHECK `status IN ('APPLIED','REJECTED')` | Estado de aplicación. |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp de recepción. |

* **Restricciones:** UNIQUE `(user_id, idempotency_key)`.

---

### 12.2 `internal.sync_changes`
* **Esquema:** `internal` | **Réplica Room:** No | **Sincronización:** Servidor $\rightarrow$ Cliente (Feed delta) | **Tipo:** Append-only.
* **Propósito:** Registro delta monotónico global de cambios y eliminaciones (tombstones). Alimenta `sync_pull`.

| Campo | Tipo Postgres | Nulo | Default | Restricciones / Claves | Descripción |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `sequence` | `BIGSERIAL` | No | | PK | Cursor secuencial incremental global. |
| `user_id` | `UUID` | No | | FK `auth.users(id)` ON DELETE CASCADE | Usuario afectado. |
| `entity_type` | `TEXT` | No | | | Nombre de la entidad modificada. |
| `entity_id` | `UUID` | No | | | ID del registro modificado o borrado. |
| `revision` | `BIGINT` | No | `1` | | Revisión resultante. |
| `operation` | `TEXT` | No | | CHECK `operation IN ('UPSERT','DELETE')` | Tipo de mutación. |
| `payload` | `JSONB` | Sí | | | Snapshot mínimo de datos sincronizables. |
| `occurred_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp del cambio. |

* **Índices:** `(user_id, sequence)`.

---

## 13. Módulo 12: Tablas Exclusivas de Android Room (SQLite Local)

> [!IMPORTANT]
> **Aislamiento Estricto por Privacidad y Rendimiento:**  
> Las siguientes 10 entidades residen **únicamente** en la base de datos Room (`KipuDatabase`) del dispositivo Android. **Nunca** se sincronizan a la nube para salvaguardar datos confidenciales (texto crudo de SMS, notificaciones del sistema operativo y capturas OCR).

| Tabla Room | Campos | Propósito Técnico |
| :--- | :--- | :--- |
| **`sync_outbox`** | `operation_id TEXT PK`, `user_id TEXT`, `aggregate_type TEXT`, `contract_version INTEGER`, `status TEXT`, `attempt_count INTEGER`, `next_attempt_at INTEGER`, `created_at INTEGER` | Cola local duradera de transacciones y comandos pendientes de sincronizar con Supabase con retroceso (backoff) y control de lease. |
| **`sync_checkpoint`** | `user_id TEXT PK`, `last_sequence INTEGER`, `updated_at INTEGER` | Cursor delta más reciente confirmado por el cliente para consultas incrementales a `internal.sync_changes`. |
| **`sync_conflicts`** | `id TEXT PK`, `entity_type TEXT`, `entity_id TEXT`, `local_revision INTEGER`, `remote_revision INTEGER`, `conflict_details TEXT`, `resolved INTEGER` | Registro de discrepancias de sincronización que requirieron resolución asistida o reglas especiales. |
| **`feature_access_cache`** | `user_id TEXT PK`, `policy_version INTEGER`, `effective_tier TEXT`, `entitlement_expires_at INTEGER`, `verified_at INTEGER`, `source TEXT` | Caché local de permisos y cuotas del plan Free/Pro con validez máxima de **hasta 72 horas offline**. |
| **`account_balance_projection`**| `account_id TEXT PK`, `balance_minor INTEGER`, `calculated_revision INTEGER`, `updated_at INTEGER` | Proyección de saldo local para renderizado instantáneo en la UI de Android sin bloquear el hilo principal. |
| **`automation_source_settings`**| `id TEXT PK`, `source_type TEXT`, `is_enabled INTEGER`, `consent_granted_at INTEGER` | Registro local del consentimiento explícito del usuario para lectura de notificaciones o SMS. |
| **`reminder_schedules`** | `id TEXT PK`, `entity_type TEXT`, `entity_id TEXT`, `scheduled_at INTEGER`, `reminder_type TEXT`, `status TEXT` | Alarmas y recordatorios programados en el dispositivo mediante Android `AlarmManager` y `WorkManager`. |
| **`capture_source_events`** | `id TEXT PK`, `source_type TEXT`, `raw_fingerprint TEXT`, `source_package TEXT`, `observed_at INTEGER` | Huella digital de mensajes y eventos de notificación observados para evitar reprocesamiento redundante. |
| **`capture_candidates`** | `id TEXT PK`, `source_event_id TEXT`, `extracted_amount_minor INTEGER`, `extracted_merchant TEXT`, `confidence_bps INTEGER`, `status TEXT` | Borradores transaccionales locales extraídos por OCR o regex antes de ser promovidos a `import_candidates`. |
| **`capture_candidate_fields`**| `id TEXT PK`, `candidate_id TEXT`, `field_name TEXT`, `raw_value TEXT`, `confidence_bps INTEGER` | Desglose por campo individual (fecha, monto, comercio) con su respectiva certidumbre estadística. |
