## Seguimiento de evidencia — identidad del artefacto actual (2026-10-20)

La continuación de QA registró mediante lectura ADB que el SHA-256 del `base.apk` instalado coincide con el candidato local QA Premium debug APK:

```text
package: com.kipu.app
versionName: 0.1.0
versionCode: 1
APK SHA-256: e218941857b805335e932ac78b6412ef31e316607a0775ca2824db4eda8c2864
```

El Coordinador recalculó independientemente el hash del candidato local y leyó su metadata. Su worktree `012-ep-pla-qa-premium` está en `d5943e8748490765caec95638802bef160e50d30`, con base/merge-base `dd6758a01a236aa61053ebf8ab24f25e0c835ba5`. El delta inspeccionado corresponde a configuración/helper de provisioning QA, scripts y documentación; no modifica las pantallas, deudas ni movimientos de la base. El acceso QA es temporal y local, no una compra, entitlement remoto ni modificación de cuotas del servidor.

**Alcance exacto:** la observación de QA vincula los bytes de la instalación leída en esa continuación al artefacto local disponible. No es una build attestation reproducible, no acredita un release Sprint 6 y no demuestra por sí sola que cada síntoma histórico se produjera con esos mismos bytes. Las hipótesis de causa del issue siguen pendientes; no se ha aplicado ninguna corrección ni hay nueva frecuencia de reproducción que sustituya la originalmente informada.

Fuente documental local: `docs/qa/auditoria-sprint-6-2026-10-19/informe.md`, §1 y continuación P1, complementada por `continuacion-tecnica-2026-10-20.md`. Los documentos de auditoría siguen locales/sin commit; no se fabrica un enlace remoto a ellos. Este seguimiento no cierra el issue ni aprueba el release.
