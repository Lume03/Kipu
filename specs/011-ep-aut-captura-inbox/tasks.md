# Tareas HU-49 - S9

- [x] T049-1 Política de correlación y pruebas de referencias, ambos órdenes, ventana, ambigüedad y reembolsos.
- [x] T049-2 Evidencia Room 37 (capture_evidence con PK compuesta `owner_id, source, event_id` e índice no único `owner_id, reference, source` reconciliado con decisión del Owner, fields_edited, migraciones 35→36 y 36→37 con HybridYapeMigrationTest y validación de esquema 37.json).
- [x] T049-3 Integración simétrica notificaciones/OCR, raíz confirmada y ediciones preservadas.
- [x] T049-4 Unión o separación explícita en ambas revisiones.
- [x] T049-5 Tests y builds debug/androidTest; commits locales sin push.

> Nota validación hardware Sprint 9 (2026-11-03): Migración 33→37 exitosa en A16 y S24. OCR sintético local verificado en A16 (Photo Picker, candidato DISCARDED sin alterar ledger/saldos); concesión de URI Android aislada verificada en S24 (ReceiptShareUriGrantTest). Casos que permanecen NO VERIFICADO: flujo completo desde app Yape real, notificación viva en ambos órdenes y 8 muestras privadas ausentes.

> Decisión Owner (2026-11-03 [2026-11-04 UTC]): El Product Owner aprobó formalmente **«Conservar eventos incompatibles para revision»** ([decisiones del Owner](../sprint-9-owner-decisions.md)), reconciliando FR-012 y la entidad `capture_evidence` con el esquema Room v37 implementado (PK compuesta e índice no único, sin fusión automática de incompatibles ni duplicación contable en ledger).
