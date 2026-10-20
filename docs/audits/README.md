# Auditorías

Los informes describen el alcance y la fecha de su ejecución. Un hallazgo no se considera vigente, resuelto o cerrado solo por estar en esta carpeta.

- [Auditoría funcional y UX](funcional/2026-09-28/informe.md).
- [Auditoría del tema oscuro — issue 39](tema-oscuro/issue-39/informe.md) y [plan](tema-oscuro/issue-39/plan.md).
- [Evidencias funcionales por recorrido](funcional/2026-09-28/evidencias/README.md): capturas, jerarquías, diagnósticos y snapshots separados.
- [Evidencias del tema oscuro](tema-oscuro/issue-39/evidencias/README.md): asociadas a su propia auditoría, no mezcladas con la funcional.
- [QA](../qa/README.md): auditorías por ejecución, seguimiento de issues y reglas de privacidad.
- [Auditorías específicas de UI/UX](../ux-ui/audits/): se conservan sus rutas.

No se copian `KipuApp/Auditorias` ni `KipuApp/auditoria_prs` en bloque. Hay informes de igual nombre pero distinto contenido y evidencia ya duplicada. Esos originales quedan allí; esta integración no certifica su cierre ni reemplaza los informes del repositorio.

## Organización

Cada auditoría tiene una carpeta por tema y, cuando corresponde, fecha: informe, plan y evidencias quedan juntos. La raíz no acumula capturas sueltas. Se preservan los archivos originales y se actualizan sus referencias; mover evidencia no equivale a auditarla de nuevo.

Los volcados XML se limitan a los cuatro citados por el informe funcional; se retiraron 169 redundantes/no referenciados. Las capturas PNG funcionales sin referencias también se retiraron. Los snapshots de Room se conservan únicamente en `_local-only/`, fuera de Git y del paquete distribuido.
