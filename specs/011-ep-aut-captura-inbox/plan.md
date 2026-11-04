# Plan HU-49 - S9

Autoridad: spec.md, D1/D2 del Owner, 2026-11-03; checkout main por mandato del coordinador.

Crear evidencia Room local por owner/fuente/identidad con índice único owner + referencia normalizada + fuente, nunca importe. La raíz apunta al candidato original capture/receipt; la confirmación financiera sigue en los casos existentes. Serializar evidencia e inserción en una misma transacción Room. La segunda fuente conserva la primera extracción y completa campos vacíos sin sobrescribir ediciones. Las raíces confirmadas siguen siendo destinos de evidencia.

La similitud sin referencia solo propone unión explícita; revalidar al aceptar. Mantener separado persiste la elección. Descartar el borrador redundante nunca escribe ledger. Mostrar controles en ambas revisiones conservando el contenido Compose sin estado. Mantener OCR Free con consentimiento y gates Premium de captura.

Room v37 agrega y consolida capture_evidence y fields_edited en receipt_candidates (migración histórica 35→36 y migración 36→37 con HybridYapeMigrationTest y esquema 37.json). Pruebas Room locales con Robolectric verifican correlación, aislamiento, rollback e índices. Validación en dispositivos reales comprobó migración 33→37 en hardware (A16 y S24), OCR sintético local en A16 (importe 46.70 prellenado, candidato DISCARDED) y concesión de URI Android en S24 (ReceiptShareUriGrantTest); compartir desde app Yape real, notificación viva de Yape y ocho muestras privadas se mantienen como NO VERIFICADO.
