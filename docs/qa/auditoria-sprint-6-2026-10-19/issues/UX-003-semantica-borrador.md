## Clasificación
- **Tipo:** contrato de producto/usabilidad pendiente; no bug funcional demostrado.
- **Estado/frecuencia:** QA observó la secuencia una vez (1/1) en la misma sesión.
- **Prioridad:** P3 / baja. No se guardó el borrador ni se observó efecto financiero.
- **Alcance:** formulario de gasto Android 0.1.0, fixture propio QA.

## Secuencia observada por QA
Se introdujeron importe PEN 1.00, categoría y nota QA; se cerró con X, se navegó a otra pantalla y se abrió de nuevo el formulario. Los campos volvieron a aparecer. La captura adjunta solo muestra el formulario reabierto; la secuencia procede del registro QA. La especificación consultada no define si X descarta o conserva.

## Riesgo y decisión necesaria
Si se espera descarte, los valores retenidos pueden sorprender; si se conserva un borrador, debería comunicarse. No se atribuye una causa técnica ni se afirma que se guardara un movimiento.

## Propuesta
Product/UX debe definir la semántica. Comunicar «Borrador guardado» si se conserva; si se descarta, limpiar o pedir confirmación cuando existan cambios no guardados.

## Criterios de aceptación
- X tiene resultado predecible y documentado.
- La reapertura coincide con el contrato y comunica la retención cuando aplique.
- Cerrar no crea un movimiento.
- Una prueba QA registra abrir → editar → cerrar → navegar → reabrir y verifica el contrato, sin efectos financieros.

No se presenta como bug confirmado hasta que exista un criterio esperado.
