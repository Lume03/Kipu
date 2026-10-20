## Clasificación
- **Tipo:** posible defecto funcional de presentación de un campo requerido por el contrato.
- **Estado:** ausencia observada en la captura provista; ruta/build no se han reproducido independientemente en esta revisión. El código fuente inspeccionado sí contiene una fila de comercio condicional en los detalles opcionales, así que causa y correspondencia del APK requieren verificación.
- **Prioridad sugerida:** P2. Sin el selector, no se puede asociar el comercio canónico desde ese formulario.
- **Alcance:** registro rápido de gasto, HU-15 / FR-021 de EP-CCO y HU-18 de EP-MOV.

## Entorno / evidencia
Captura aportada por el Owner desde Android; se ve Gasto seleccionado y «Más detalles (opcional)» expandido. El recorte excluye el saldo/cuenta del encabezado y conserva categoría, fecha y nota. No se adjunta una imagen alterada del control ausente.

## Pasos observados
1. Abrir **Movimientos → Registrar movimiento**.
2. Elegir **Gasto**.
3. Expandir **Más detalles (opcional)**.
4. Revisar los controles disponibles junto a fecha y nota.

## Esperado y fundamento
FR-021 de `specs/003-ep-cco-categorias-comercios/spec.md` exige selector independiente de categoría y comercio en crear/editar movimiento. Comercio debe poder seleccionarse del catálogo o conservarse como texto provisional; no debe convertirse en obligatorio.

## Resultado observado
En la captura, con los detalles expandidos, aparecen categoría, fecha/hora y nota; no aparece una fila de comercio. El fuente consultado (`QuickMovementScreen.kt`) tiene una fila condicional dentro de esa sección. Por tanto, verificar si la instalación oculta la fila por estado/tipo, si el artefacto difiere o si hay otra ruta; no se atribuye aún una causa al checkout.

## Impacto y solución propuesta
La persona puede registrar el movimiento sin asociar un comercio del catálogo, pese a que el contrato contempla esa relación. Hacer visible y alcanzable la fila **Comercio o pagador** para gastos cuando se expande la sección, preservar su independencia de categoría y explicar que es opcional.

## Criterios de aceptación
- En el APK identificado, Gasto → detalles expandidos muestra un control de comercio accesible.
- El selector busca/selecciona un comercio y permite texto provisional según FR-015/021 sin asignación automática.
- Cambiar comercio no cambia categoría y viceversa.
- Se conserva optionalidad y el registro no falla por dejarlo vacío.
- QA verifica alta y edición con fixture; registrar versión/hash y descartar diferencia de build.

## Relación con issues existentes
#46 trató reducir carga visual sin ocultar datos requeridos; se cita como antecedente de diseño, no como resolución de esta omisión observada. No duplicar #46: este reporte contrasta la presencia del control con FR-021.
