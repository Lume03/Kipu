# Security Requirements Checklist: EP-REC — Recurrencias y obligaciones

**Purpose**: Revisar completitud y precisión de los requisitos de seguridad/privacidad del slice EP-REC.
**Created**: 2026-10-18
**Feature**: [spec.md](../spec.md)

**Note**: Checklist de calidad de requisitos, no ejecución de pruebas de seguridad.
**Review Ownership**: Solo el reviewer marca `[x]` cuando confirma que el criterio de calidad está satisfecho.
**Marker Semantics**: `[x]` valida requisitos de seguridad; no significa que el código haya sido auditado o aprobado.

## Identity and Ownership

- [ ] CHK001 ¿La especificación identifica de manera inequívoca cómo se autentica la persona y cómo se deriva el owner remoto sin confiar en `user_id` del cliente? [Completeness, Spec §FR-011, Contract §1/§6]
- [ ] CHK002 ¿Las reglas de propiedad de todas las referencias de plantilla (cuenta, categoría, comercio) y sus errores quedan definidas para impedir acceso cruzado? [Coverage, Data Model §template_transaction, Contract §6]
- [ ] CHK003 ¿El alcance de RLS, roles y permisos de tabla/RPC está especificado para lecturas, escrituras, recibos y feed de sync? [Gap, Contract §6]

## Integrity and Replay Safety

- [ ] CHK004 ¿Los requisitos fijan de forma verificable revisión esperada, idempotencia, reutilización de operation ID y rechazo de hashes/payloads inconsistentes? [Completeness, Spec §FR-010, Contract §3]
- [ ] CHK005 ¿La política de serialización/canonicalización define bytes, UTF-8, orden de claves/arreglos, strings, nulos, normalización decimal exacta sobre JSONB, integridad/rangos por path numérico y vectores/paridad Kotlin-SQL; y exige `reminder_lead_days` normalizado, no nulo, incluido en el hash, rechazando omitido/`null`? [Measurability/integrity, Contract §2/§3, TECH-REC-HASH-01]
- [ ] CHK006 ¿Se especifica que cambios de regla, recibos y sync son atómicos y que fallas no dejan estado parcial ni un movimiento financiero? [Coverage, Plan §Architecture Overview, Contract §1]
- [ ] CHK007 ¿Las migraciones preservan filas/historia, separan `status` de `deleted_at` y conservan `is_active` como proyección read-only (`ACTIVE=true`; `PAUSED`/`CANCELLED=false`) hasta inventario autorizado, retirándola solo en migración posterior revisada? [Recovery/compatibility, Data Model §Source and compatibility/State transitions]

## Privacy and Operational Boundaries

- [ ] CHK008 ¿Los errores para referencias ajenas no revelan existencia ni datos de otra persona? [Privacy, Contract §5]
- [ ] CHK009 ¿Los requisitos de logs/evidencia excluyen tokens, credenciales, seriales del dispositivo y valores personales no necesarios? [Gap, Quickstart §Prerequisites/Evidence]
- [ ] CHK010 ¿El límite con Supabase hosted exige inventario/autorización previos y prohíbe que validación local implique despliegue? [Governance, Plan §Gate, Contract §6]
- [ ] CHK011 ¿La rutina de proyección de ocurrencias queda explícitamente fuera de RPCs, grants, triggers y rutas de cliente de este slice? [Boundary, Spec §Scope, Research §2]
- [ ] CHK012 ¿Los RPCs validan/hash únicamente el objeto `jsonb` post-parseo, sin afirmar que detectan miembros duplicados del texto, y conservan la validación de allowlist/owner antes de hash, recibo o mutación? [Integrity, Spec §TECH-REC-JSON-01, Contract §2/§3]

## Notes

- Esta checklist no sustituye la revisión independiente de `security` sobre el SQL/RLS implementado.
- Dejar todos los ítems pendientes hasta que un reviewer de requisitos los evalúe; no marcar verde por el solo hecho de que exista un diseño.
