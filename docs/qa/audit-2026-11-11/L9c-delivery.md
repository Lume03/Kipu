# L9c ? Deuda de tarjeta con inter?s

Fecha: 2026-11-12. Rama: `Lume03/audit-fixes`. Resultado: implementaci?n y verificaciones solicitadas completas, sin commits ni push; sin cambios en Supabase remoto.

## Decisiones y comportamiento

- La deuda can?nica suma principal + inter?s contractual ?nicamente para cuotas `WITH_INTEREST` de compras confirmadas/revisadas. En `INTEREST_FREE`, los valores hist?ricos de inter?s estimado no generan deuda. Los simuladores no escriben cuotas ni crean deuda.
- Pago FIFO por `due_date`, fecha/hora de compra, n?mero de cuota y UUID. Dentro de cada cuota, principal primero y despu?s inter?s; se salda el total de esa cuota antes de pasar a la siguiente. `allocated_minor` representa el abono acumulado sobre ambos componentes, sin introducir columnas por componente.
- El inter?s s? consume l?nea de cr?dito: deuda actual, validaci?n del total financiado de nuevas compras y resumen usado/disponible incluyen inter?s. Se conserva la pol?tica de devoluci?n FIFO sobre el saldo restante de la compra.
- `CARD_HAS_OUTSTANDING_DEBT` conserva su guardia vigente y ahora tambi?n bloquea cuando queda solo inter?s, al consultar el saldo can?nico.
- El feed de transacciones expone `status` y `outstanding_minor` por cuota. El resumen `v_credit_summary` calcula deuda can?nica y conserva `security_invoker=true`, sin conceder acceso nuevo al esquema privado.

## Servidor

Migraci?n nueva: `supabase/migrations/20261112130000_card_debt_includes_interest.sql`.

| Funci?n/vista reemplazada | Migraci?n de origen de la definici?n |
| --- | --- |
| `private.credit_installment_balances` | `20261101140000_credit_card_principal_quota_archive.sql` |
| `public.allocate_credit_payment_v1_pre_liability` | `20261112120000_money_and_date_rules.sql` |
| `public.pay_credit_card_v1` | `20260925022130_s3_credit_canonical_purchase_payment.sql` |
| `public.register_transaction_v1_pre_liability_s3` | `20261101140000_credit_card_principal_quota_archive.sql` |
| `internal.credit_transaction_sync_projection_v1` | `20261031163043_credit_purchase_terms_and_cycles.sql` |
| `public.allocate_credit_payment` (compatibilidad legacy) | `20261102013000_restore_baseline_dependencies.sql` |
| `public.v_credit_summary` | `20260920162500_align_domain_schema_names.sql`; `security_invoker` de `20260930025207_sprint_3_db_hardening.sql` |

Las firmas, privilegios existentes y controles de los comandos se preservan. La guardia de archivo de `20261112120000` y `private.refresh_credit_installment_statuses` de `20261031163043` ya dependen de los saldos can?nicos y no requieren reemplazo. La migraci?n recalcula estados hist?ricos, incluidas cuotas previamente `PAID` solo por principal.

## App / Room

Versi?n **40 ? 41**, `MIGRATION_40_41`, registrada en `CoreModule`; schema exportado por KSP en `app/schemas/com.kipu.app.core.database.KipuDatabase/41.json`.

La consulta `CREDIT_INSTALLMENT_BALANCES_SQL` aplica la misma condici?n por financiaci?n. La migraci?n elimina los triggers con SQL capturado, los recrea mediante el helper vigente y recalcula estados para todos los propietarios. El trigger de cuotas tambi?n observa cambios de `financing`.

La sugerencia ?Pagar total? recibe `debt` de `observeCardDebt`, que consume `observeOutstandingBalanceForCard`; la asignaci?n local obtiene `getOutstandingInstallmentsForCard`. Ambas rutas quedan corregidas mediante la consulta compartida; el asignador mantiene el mismo orden FIFO que el servidor.

## Verificaci?n

- `npx --no-install supabase db reset`: completado correctamente contra la instancia **local**, incluida la nueva migraci?n.
- `npx --no-install supabase test db`: **PASS**, 82 archivos, **1599 pruebas**. El archivo nuevo `card_debt_includes_interest.test.sql` contiene 16 aserciones: deuda/cupo con inter?s, resumen como `authenticated`, pago de principal que deja inter?s, bloqueo de archivo, pago restante y pago total ?nico, cuota `PAID`, feed y comportamiento sin inter?s.
- `gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**. Tambi?n se ejecutaron esas tareas junto a `:app:testDebugUnitTest --tests com.kipu.app.feature.accounts.domain.CreditPaymentAllocatorTest`: **BUILD SUCCESSFUL**, 6 pruebas unitarias sin fallos.
- `CardInterestMigrationTest`: a?adido y **compilado**, sin ejecuci?n instrumentada ni ADB. Valida esquema 40?41, reemplazo de SQL legacy, reapertura de inter?s pendiente, consulta local con/sin inter?s, exclusi?n de borrador, pagos parcial/total y anulaci?n del pago.
- `git diff --check`: sin errores.

## Riesgos y l?mites

- Las compras hist?ricas `WITH_INTEREST` pueden aumentar la deuda mostrada y reabrir cuotas que antes figuraban pagadas solo por principal; es el efecto esperado de la decisi?n aprobada.
- La migraci?n instrumentada no se ejecut? en hardware, por instrucci?n; su validaci?n de ejecuci?n queda pendiente de un entorno con dispositivo.
- Los asientos contables hist?ricos de pasivo conservan su representaci?n anterior; el saldo de deuda/cupo proviene ahora de cuotas y asignaciones, no de sumar ese ledger. Este lote no introduce reconocimiento contable separado del gasto de inter?s ni reescribe evidencia hist?rica.
- La vista repite la expresi?n can?nica para conservar los permisos RLS sin exponer funciones privadas; las pruebas comparan ambos resultados, pero futuros cambios deben mantenerlas alineadas.
- Nada fue aplicado a remoto; la entrega no acredita despliegue remoto. No se modificaron `specs/` ni otros documentos.
