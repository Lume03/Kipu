# KIPU V4.2 - Modelo Entidad-Relación (MER / DER)

> **MER completo integrado — 2026-10-20.** Origen: `KipuApp/Entidades/V4.2_Modelo_Entidad_Relacion.md`, versión **2.1 del 20/09/2026**. Contiene los diagramas Mermaid y cardinalidades del diseño base; no es un diagrama extraído de una base desplegada. Contrastar con los [refinamientos por épica](README.md). En particular, la regla original de metas mediante `GOALS_VIRTUAL` no autoriza reinterpretar las reservas actuales: consultar [EP-MET](../../specs/006-ep-met-metas-reservas/data-model.md) y la [constitución](../../.specify/memory/constitution.md). Los modelos de `Entidades antiguas/` y Bizagi siguen excluidos.

> **Diagramas de Entidad-Relación, matriz de cardinalidades, integridad referencial y restricciones relacionales para Android (Room/SQLite) y Backend (Supabase/PostgreSQL).**  
> **Versión:** 2.1 Canónica Alineada — Fecha: 20/09/2026.  
> **Referencias:** [Especificación de producto](../producto/especificacion.md), [Product Backlog](../producto/backlog.md), [Arquitectura y modelado](modelo-de-datos.md), [Catálogo de entidades](entidades.md).

---

## 1. Convenciones de Notación Relacional

Los diagramas utilizan la notación estándar de **Crow's Foot** (Pata de Gallo) soportada en Mermaid `erDiagram`:

| Símbolo | Significado de Cardinalidad | Interpretación en Dominio Kipu |
| :---: | :--- | :--- |
| <code>&#124;&#124;--&#124;&#124;</code> | Uno a Uno estricto (1:1) | Ejemplo: `auth.users` con `profiles`. |
| <code>&#124;&#124;--&#124;{</code> | Uno a Muchos obligatorio (1:N) | Cada elemento hijo debe pertenecer exactamente a un padre. |
| <code>&#124;&#124;--o{</code> | Uno a Muchos opcional (1:0..N) | El padre puede tener cero, uno o muchos hijos. |
| <code>}&#124;--&#124;{</code> | Muchos a Muchos (N:M) | Se resuelve mediante tabla intermedia asociativa (ej. `credit_payment_allocations`). |
| <code>o&#124;--&#124;{</code> | Cero o Uno a Muchos (0..1:N) | Referencias globales o anulables (ej. categorías globales del sistema con `user_id IS NULL`). |

---

## 2. Diagrama Entidad-Relación Global (Macro-DER)

Visión panorámica de alto nivel que conecta los 6 subsistemas principales de Kipu:

```mermaid
erDiagram
    AUTH_USERS ||--|| PROFILES : "configura"
    AUTH_USERS ||--o{ USER_DEVICES : "autoriza"
    AUTH_USERS ||--o{ ACCOUNTS : "posee"
    AUTH_USERS ||--o{ CATEGORIES : "define"
    AUTH_USERS ||--o{ MERCHANT_SERVICES : "reconoce"
    AUTH_USERS ||--o{ TRANSACTIONS : "registra"
    AUTH_USERS ||--o{ DEBTS : "contrae_o_presta"
    AUTH_USERS ||--o{ GOALS : "proyecta"
    AUTH_USERS ||--o{ BUDGETS : "limita"
    AUTH_USERS ||--o{ RECURRENCES : "programa"
    AUTH_USERS ||--o{ BILLING_PURCHASES : "adquiere"
    AUTH_USERS ||--o{ IMPORT_CANDIDATES : "ingesta"

    ACCOUNTS ||--o{ CARDS : "soporta"
    ACCOUNTS ||--o{ TRANSACTIONS : "imputa"
    ACCOUNTS ||--o{ LEDGER_ENTRIES : "consolida"

    CATEGORIES ||--o{ CATEGORIES : "subclasifica"
    CATEGORIES ||--o{ TRANSACTIONS : "clasifica"
    CATEGORIES ||--o{ BUDGETS : "acota"

    MERCHANT_SERVICES ||--o{ TRANSACTIONS : "identifica"
    MERCHANT_SERVICES ||--o{ MERCHANT_RULES : "asocia"

    TRANSACTIONS ||--o{ TRANSACTION_LINKS : "origina"
    TRANSACTIONS ||--o{ TRANSACTION_LINKS : "destina"
    TRANSACTIONS ||--o{ LEDGER_ENTRIES : "desglosa"
    TRANSACTIONS ||--o{ CREDIT_INSTALLMENTS : "genera_cuotas"
    TRANSACTIONS ||--o{ DEBT_EVENTS : "liquida"
    TRANSACTIONS ||--o{ GOAL_EVENTS : "fondea"

    CREDIT_INSTALLMENTS ||--o{ CREDIT_PAYMENT_ALLOCATIONS : "amortiza"
    TRANSACTIONS ||--o{ CREDIT_PAYMENT_ALLOCATIONS : "abona"

    DEBTS ||--o{ DEBT_INSTALLMENTS : "programa"
    DEBTS ||--o{ DEBT_EVENTS : "altera"

    GOALS ||--o{ GOAL_EVENTS : "acumula"

    BUDGETS ||--o{ BUDGET_PERIODS : "vigencias"

    RECURRENCES ||--o{ RECURRENCE_OCCURRENCES : "proyecta"

    BILLING_PRODUCTS ||--o{ BILLING_PURCHASES : "tarifica"
    PLAN_SELECTIONS ||--o{ PLAN_SELECTION_ITEMS : "asigna_cuota"
```

---

## 3. DER Núcleo Contable, Cuentas, Tarjetas y Movimientos

Detalle del subsistema transaccional y libro mayor de doble partida:

```mermaid
erDiagram
    ACCOUNTS {
        uuid id PK
        uuid user_id FK
        string name
        string account_type
        string currency_code FK
        boolean is_archived
        bigint revision
    }

    CARDS {
        uuid id PK
        uuid user_id FK
        uuid account_id FK
        string network
        string alias
        string last4
        boolean is_credit
        bigint credit_limit_minor
        smallint closing_day
        smallint due_day
        integer personal_tea_bps
        uuid product_id FK
        string style_preset_id
    }

    CREDIT_PRODUCTS {
        uuid id PK
        string institution_code
        string product_name
        string card_network
        integer reference_tea_bps
        date effective_from
    }

    CATEGORIES {
        uuid id PK
        uuid user_id FK
        string name
        string icon_key
        string color_token
        uuid parent_id FK
        boolean is_system
    }

    MERCHANT_SERVICES {
        uuid id PK
        uuid user_id FK
        string name
        string normalized_name
        boolean is_system
    }

    MERCHANT_RULES {
        uuid id PK
        uuid user_id FK
        string pattern
        uuid merchant_service_id FK
        uuid category_id FK
        integer priority
    }

    TRANSACTIONS {
        uuid id PK
        uuid user_id FK
        uuid account_id FK
        uuid card_id FK
        uuid category_id FK
        uuid merchant_service_id FK
        string transaction_type
        string operation_kind
        bigint amount_minor
        string currency_code FK
        uuid transfer_group_id
        timestamptz occurred_at
        smallint installment_count
        string status
        bigint revision
    }

    TRANSACTION_LINKS {
        uuid id PK
        uuid user_id FK
        uuid source_transaction_id FK
        uuid target_transaction_id FK
        string link_type
        jsonb metadata
        timestamptz created_at
    }

    LEDGER_ENTRIES {
        uuid id PK
        uuid transaction_id FK
        uuid user_id FK
        uuid account_id FK
        bigint signed_amount_minor
        string currency_code FK
        string entry_role
        timestamptz occurred_at
    }

    CREDIT_INSTALLMENTS {
        uuid id PK
        uuid user_id FK
        uuid transaction_id FK
        smallint installment_number
        date due_date
        bigint principal_minor
        bigint interest_minor
        string status
    }

    CREDIT_PAYMENT_ALLOCATIONS {
        uuid id PK
        uuid user_id FK
        uuid payment_transaction_id FK
        uuid installment_id FK
        bigint allocated_minor
        timestamptz created_at
    }

    ACCOUNTS ||--o{ CARDS : "vincula"
    CREDIT_PRODUCTS ||--o{ CARDS : "referencia_tasas"
    CATEGORIES ||--o{ CATEGORIES : "jerarquia_2_niveles"
    CATEGORIES ||--o{ MERCHANT_RULES : "clasifica_regla"
    MERCHANT_SERVICES ||--o{ MERCHANT_RULES : "identifica_regla"

    ACCOUNTS ||--o{ TRANSACTIONS : "imputa_origen"
    CARDS ||--o{ TRANSACTIONS : "instrumento"
    CATEGORIES ||--o{ TRANSACTIONS : "categoria"
    MERCHANT_SERVICES ||--o{ TRANSACTIONS : "comercio"

    TRANSACTIONS ||--o{ TRANSACTION_LINKS : "source"
    TRANSACTIONS ||--o{ TRANSACTION_LINKS : "target"

    TRANSACTIONS ||--o{ LEDGER_ENTRIES : "asientos_doble_partida"
    ACCOUNTS ||--o{ LEDGER_ENTRIES : "asiento_cuenta"

    TRANSACTIONS ||--o{ CREDIT_INSTALLMENTS : "cronograma_compra"
    TRANSACTIONS ||--o{ CREDIT_PAYMENT_ALLOCATIONS : "pago_origen"
    CREDIT_INSTALLMENTS ||--o{ CREDIT_PAYMENT_ALLOCATIONS : "cuota_destino"
```

---

## 4. DER Planificación Financiera (Deudas, Metas, Presupuestos y Recurrencias)

Detalle de metas, obligaciones y reglas de presupuesto enlazadas con transacciones:

```mermaid
erDiagram
    DEBTS {
        uuid id PK
        uuid user_id FK
        string obligation_type
        string counterparty_name
        bigint total_minor
        string currency_code FK
        date due_date
        string status
    }

    DEBT_INSTALLMENTS {
        uuid id PK
        uuid user_id FK
        uuid debt_id FK
        smallint installment_number
        date due_date
        bigint amount_minor
        string status
    }

    DEBT_EVENTS {
        uuid id PK
        uuid user_id FK
        uuid debt_id FK
        uuid transaction_id FK
        uuid installment_id FK
        string event_type
        bigint amount_minor
        timestamptz occurred_at
    }

    GOALS {
        uuid id PK
        uuid user_id FK
        string name
        bigint target_minor
        string currency_code FK
        date target_date
        string status
    }

    GOAL_EVENTS {
        uuid id PK
        uuid user_id FK
        uuid goal_id FK
        uuid transaction_id FK
        string event_type
        bigint amount_minor
        timestamptz occurred_at
    }

    BUDGETS {
        uuid id PK
        uuid user_id FK
        uuid category_id FK
        string name
        bigint limit_minor
        string currency_code FK
        string period_type
        boolean is_active
    }

    BUDGET_PERIODS {
        uuid id PK
        uuid user_id FK
        uuid budget_id FK
        date starts_on
        date ends_on
        bigint limit_minor
    }

    RECURRENCES {
        uuid id PK
        uuid user_id FK
        jsonb template_transaction
        string frequency
        integer interval_count
        date start_date
        date next_due_date
        boolean is_active
    }

    RECURRENCE_OCCURRENCES {
        uuid id PK
        uuid user_id FK
        uuid recurrence_id FK
        date due_date
        bigint expected_amount_minor
        uuid matched_transaction_id FK
        string status
    }

    TRANSACTIONS {
        uuid id PK
        uuid user_id FK
    }

    DEBTS ||--o{ DEBT_INSTALLMENTS : "cronograma_cuotas"
    DEBTS ||--o{ DEBT_EVENTS : "historial_amortizaciones"
    DEBT_INSTALLMENTS ||--o{ DEBT_EVENTS : "pago_cuota"
    TRANSACTIONS ||--o{ DEBT_EVENTS : "movimiento_financiero"

    GOALS ||--o{ GOAL_EVENTS : "aportes_y_retiros"
    TRANSACTIONS ||--o{ GOAL_EVENTS : "transferencia_metas"

    BUDGETS ||--o{ BUDGET_PERIODS : "historico_ciclos"

    RECURRENCES ||--o{ RECURRENCE_OCCURRENCES : "genera_vencimientos"
    TRANSACTIONS ||--o{ RECURRENCE_OCCURRENCES : "empareja_real"
```

---

## 5. DER Facturación, Planes, Dispositivos e Identidad

Detalle de suscripciones de Google Play Store, control de cuotas y dispositivos autorizados:

```mermaid
erDiagram
    PROFILES {
        uuid user_id PK
        string display_name
        string currency_code
        smallint month_start
        boolean hide_balances
        jsonb preferences
    }

    USER_DEVICES {
        uuid id PK
        uuid user_id FK
        string device_fingerprint
        string device_name
        string platform
        boolean is_active
        timestamptz last_sync_at
    }

    BILLING_PRODUCTS {
        string id PK
        string store_product_id
        string base_plan_id
        string name
        string plan_type
        jsonb features
        boolean is_active
    }

    BILLING_PURCHASES {
        uuid id PK
        uuid user_id FK
        string order_id
        string purchase_token_hash
        string product_id FK
        string purchase_state
        string entitlement_state
        timestamptz starts_at
        timestamptz expires_at
    }

    BILLING_EVENTS {
        uuid id PK
        uuid user_id FK
        string purchase_token_hash
        string event_type
        jsonb raw_payload
        timestamptz received_at
    }

    PLAN_SELECTIONS {
        uuid id PK
        uuid user_id FK
        string feature_key
        bigint revision
    }

    PLAN_SELECTION_ITEMS {
        uuid id PK
        uuid plan_selection_id FK
        uuid resource_id
        string resource_type
        timestamptz created_at
    }

    PROFILES ||--o{ USER_DEVICES : "dispositivos_activos"
    PROFILES ||--o{ BILLING_PURCHASES : "compras_usuario"
    BILLING_PRODUCTS ||--o{ BILLING_PURCHASES : "tarifica"
    BILLING_PURCHASES ||--o{ BILLING_EVENTS : "auditoria_rtdn"
    PROFILES ||--o{ PLAN_SELECTIONS : "recursos_en_cuota"
    PLAN_SELECTIONS ||--o{ PLAN_SELECTION_ITEMS : "items_asignados"
```

---

## 6. DER Captura Asistida e Ingesta Offline (Room + Supabase)

Detalle del flujo desde la captura OCR/SMS local en Android hasta la bandeja `import_candidates` y su confirmación en `transactions`:

```mermaid
erDiagram
    CAPTURE_SOURCE_EVENTS {
        string id PK
        string source_type
        string raw_fingerprint
        string source_package
        integer observed_at
    }

    CAPTURE_CANDIDATES {
        string id PK
        string source_event_id FK
        integer extracted_amount_minor
        string extracted_merchant
        integer confidence_bps
        string status
    }

    CAPTURE_CANDIDATE_FIELDS {
        string id PK
        string candidate_id FK
        string field_name
        string raw_value
        integer confidence_bps
    }

    IMPORT_CANDIDATES {
        uuid id PK
        uuid user_id FK
        string raw_hash
        string source_app
        string raw_text
        jsonb extracted_data
        numeric confidence_score
        string status
        uuid confirmed_transaction_id FK
    }

    TRANSACTIONS {
        uuid id PK
        uuid user_id FK
        bigint amount_minor
        string status
    }

    CAPTURE_SOURCE_EVENTS ||--o{ CAPTURE_CANDIDATES : "parsea_local"
    CAPTURE_CANDIDATES ||--o{ CAPTURE_CANDIDATE_FIELDS : "evidencia_campos"
    CAPTURE_CANDIDATES }o..o| IMPORT_CANDIDATES : "promociona_a_bandeja"
    IMPORT_CANDIDATES ||--o| TRANSACTIONS : "confirma_a_ledger"
```

---

## 7. Matriz Completa de Relaciones y Cardinalidades

| Entidad Origen | Cardinalidad | Entidad Destino | Clave Foránea (FK) | Regla ON DELETE | Semántica de Negocio |
| :--- | :---: | :--- | :--- | :--- | :--- |
| `auth.users` | `1 : 1` | `public.profiles` | `user_id` | CASCADE | Perfil único por usuario autenticado. |
| `auth.users` | `1 : N` | `public.user_devices` | `user_id` | CASCADE | Instalaciones autorizadas del usuario. |
| `auth.users` | `1 : N` | `public.accounts` | `user_id` | CASCADE | Cuentas patrimoniales del usuario. |
| `auth.users` | `1 : N` | `public.categories` | `user_id` | CASCADE | Categorías personalizadas (NULL = globales). |
| `auth.users` | `1 : N` | `public.merchant_services` | `user_id` | CASCADE | Comercios propios del usuario (NULL = globales). |
| `auth.users` | `1 : N` | `public.transactions` | `user_id` | CASCADE | Movimientos contables del usuario. |
| `public.accounts` | `1 : N` | `public.cards` | `account_id` | RESTRICT | Una cuenta bancaria o de pasivo respalda tarjetas. |
| `public.credit_products` | `0..1 : N` | `public.cards` | `product_id` | SET NULL | Catálogo referencial de tasas para simulación. |
| `public.categories` | `0..1 : N` | `public.categories` | `parent_id` | RESTRICT | Jerarquía de categorías (máx. 2 niveles). |
| `public.merchant_services`| `1 : N` | `public.merchant_rules` | `merchant_service_id` | CASCADE | Reglas de texto que asignan a un comercio. |
| `public.categories` | `0..1 : N` | `public.merchant_rules` | `category_id` | SET NULL | Categoría sugerida automáticamente por la regla. |
| `public.accounts` | `1 : N` | `public.transactions` | `account_id` | RESTRICT | Cuenta afectada por la transacción. |
| `public.cards` | `0..1 : N` | `public.transactions` | `card_id` | SET NULL | Tarjeta empleada en la operación (opcional). |
| `public.categories` | `0..1 : N` | `public.transactions` | `category_id` | SET NULL | Clasificación del gasto/ingreso (opcional). |
| `public.merchant_services`| `0..1 : N` | `public.transactions` | `merchant_service_id` | SET NULL | Comercio o servicio identificado (opcional). |
| `public.transactions` | `1 : N` | `public.transaction_links` | `source_transaction_id`| CASCADE | Transacción origen del emparejamiento. |
| `public.transactions` | `1 : N` | `public.transaction_links` | `target_transaction_id`| CASCADE | Transacción destino del emparejamiento. |
| `public.transactions` | `1 : N` | `internal.ledger_entries` | `transaction_id` | CASCADE | Asientos contables inmutables de partida doble. |
| `public.accounts` | `1 : N` | `internal.ledger_entries` | `account_id` | RESTRICT | Cuenta afectada en el balance contable. |
| `public.transactions` | `1 : N` | `public.credit_installments` | `transaction_id` | CASCADE | Cronograma de cuotas de una compra a crédito. |
| `public.transactions` | `1 : N` | `public.credit_payment_allocations` | `payment_transaction_id`| CASCADE | Transacción de pago que amortiza cuotas. |
| `public.credit_installments`| `1 : N`| `public.credit_payment_allocations` | `installment_id` | CASCADE | Cuota crediticia amortizada. |
| `auth.users` | `1 : N` | `public.debts` | `user_id` | CASCADE | Deudas y préstamos del usuario. |
| `public.debts` | `1 : N` | `public.debt_installments` | `debt_id` | CASCADE | Cronograma de cuotas de la deuda/préstamo. |
| `public.debts` | `1 : N` | `public.debt_events` | `debt_id` | CASCADE | Hechos de amortización o ajuste de deuda. |
| `public.transactions` | `0..1 : N` | `public.debt_events` | `transaction_id` | SET NULL | Transacción bancaria vinculada al pago de deuda. |
| `auth.users` | `1 : N` | `public.goals` | `user_id` | CASCADE | Metas de ahorro del usuario. |
| `public.goals` | `1 : N` | `public.goal_events` | `goal_id` | CASCADE | Aportes o retiros de la meta. |
| `public.transactions` | `0..1 : N` | `public.goal_events` | `transaction_id` | SET NULL | Transferencia bancaria asociada al ahorro. |
| `auth.users` | `1 : N` | `public.budgets` | `user_id` | CASCADE | Presupuestos definidos por el usuario. |
| `public.categories` | `0..1 : N` | `public.budgets` | `category_id` | RESTRICT | Categoría presupuestada (NULL = Total General). |
| `public.budgets` | `1 : N` | `public.budget_periods` | `budget_id` | CASCADE | Historial inmutable de vigencias presupuestarias. |
| `auth.users` | `1 : N` | `public.recurrences` | `user_id` | CASCADE | Reglas de gastos/ingresos recurrentes. |
| `public.recurrences` | `1 : N` | `public.recurrence_occurrences`| `recurrence_id` | CASCADE | Instancias proyectadas en el calendario. |
| `public.transactions` | `0..1 : 1` | `public.recurrence_occurrences`| `matched_transaction_id`| SET NULL | Transacción real que satisfizo la ocurrencia. |
| `public.billing_products`| `1 : N` | `public.billing_purchases` | `product_id` | RESTRICT | Producto de suscripción contratado. |
| `public.plan_selections` | `1 : N` | `public.plan_selection_items` | `plan_selection_id` | CASCADE | Elementos que ocupan cuotas del plan Free. |
| `public.transactions` | `0..1 : 1` | `public.import_candidates` | `confirmed_transaction_id`| SET NULL | Transacción creada tras confirmación en bandeja. |

---

## 8. Invariantes y Reglas de Integridad Relacional

1. **Aislamiento Multi-Tenant Estricto (`user_id`):**
   * Toda tabla que guarde información del usuario incluye `user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE`.
   * En consultas y uniones (`JOIN`), el motor SQL y las políticas RLS exigen que ambas tablas coincidan en `user_id`. Jamás se permite que una transacción de usuario $A$ enlace una cuenta o tarjeta de usuario $B$.

2. **Inmutabilidad Contable del Libro Mayor (`internal.ledger_entries`):**
   * Un asiento contable insertado **nunca se actualiza ni se elimina físicamente**.
   * Las correcciones o anulaciones se realizan insertando nuevos asientos contables compensatorios de reversión (`entry_role = 'REVERSAL'`).

3. **No Duplicación de Saldos en Metas (`GOALS_VIRTUAL`):**
   * Los fondos destinados a metas de ahorro residen en la cuenta virtual unificada `GOALS_VIRTUAL`.
   * Todo aporte a una meta se procesa contablemente como una transferencia (`TRANSFER`) desde una cuenta de activo real hacia `GOALS_VIRTUAL`, evitando duplicar saldos en la vista consolidada de patrimonio.

4. **Emparejamiento Bidireccional Limpio (`transaction_links`):**
   * En transferencias entre cuentas propias o pagos de tarjeta (**RN-06**), se generan dos transacciones vinculadas mediante un registro en `transaction_links` con `link_type = 'TRANSFER'` o `'CARD_PAYMENT'`.
   * En reembolsos (**RN-12**, **DTV-09**), se enlaza la transacción de egreso original con la de reintegro bajo `link_type = 'REFUND'`, neutralizando el gasto en las proyecciones analíticas.

5. **Restricción Estricta de Jerarquía de Categorías (RF-K10):**
   * `categories.parent_id` solo puede apuntar a una categoría raíz (`parent_id IS NULL`).
   * No se permiten sub-subcategorías (profundidad máxima = 2).
   * No existen tablas intermedias de etiquetas o tags.
