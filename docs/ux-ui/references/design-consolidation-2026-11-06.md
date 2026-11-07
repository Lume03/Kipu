# Verificación de consolidación visual — 2026-11-06

Este informe registra verificaciones y pendientes; la única autoridad visual es [docs/DESIGN.md](../../DESIGN.md). No constituye un diseño alternativo ni modifica requisitos funcionales.

Se reemplazó DESIGN con valores extraídos del tema Android y se resolvió DOC-03 en docs/ por decisión expresa del propietario del 2026-11-06. Se corrigieron índices y se añadieron únicamente notas de vigencia a informes y auditorías históricos; se conservó su evidencia. No se editaron código, imágenes, specs, constitución ni instrucciones de agentes, no hubo stage/commit/cambio de rama, compilación ni interacción con teléfonos.

## Validación reproducible

**276 valores del frontmatter verificados, cero diferencias:** 220 colores efectivos (claro y oscuro), 53 propiedades de 11 roles tipográficos y 3 radios. YAML válido y sin claves duplicadas; solo grupos admitidos por el formato. No hay tokens de espaciado global en el tema: las medidas locales documentadas no se inventan como tokens. Los colores ARGB con transparencia se convierten a CSS RRGGBBAA.

Ejecutar el siguiente bloque Python desde la raíz del repositorio, con PyYAML instalado (disponible en la sesión). Lee el frontmatter y vuelve a resolver las expresiones del código, incluidos getters, ramas por tema y sustituciones del proveedor; falla ante cualquier diferencia. Para pasarlo por stdin en PowerShell usar `$OutputEncoding = [System.Text.UTF8Encoding]::new($false)`.

```python
from pathlib import Path
import re, yaml
root = Path.cwd()
t = root / 'app/src/main/java/com/kipu/app/ui/theme'
def read(name): return (t / name).read_text(encoding='utf8')
constants = dict(re.findall(r'val (\w+) = Color\(0x([0-9A-F]{8})\)', read('Color.kt')))
def evaluate(expr, mode, scheme):
    expr = expr.strip()
    if expr.startswith('if (isDark)'):
        dark, light = expr[len('if (isDark)'):].split(' else ')
        return evaluate(dark if mode == 'dark' else light, mode, scheme)
    if expr.startswith('scheme.'): return scheme[expr[7:]]
    argb = constants.get(expr)
    if argb is None:
        match = re.fullmatch(r'Color\(0x([0-9A-F]{8})\)', expr)
        assert match, expr
        argb = match[1]
    return '#' + (argb[2:] if argb[:2] == 'FF' else argb[2:] + argb[:2])
expected = {}
for mode, title in [('light', 'Light'), ('dark', 'Dark')]:
    text = re.search(r'private val '+title+r'ColorScheme = \w+ColorScheme\((.*?)\n\)', read('Theme.kt'), re.S)[1]
    scheme = {k: evaluate(e, mode, {}) for k,e in re.findall(r'^    (\w+) = (.*),$', text, re.M)}
    expected.update({mode+'-'+k:v for k,v in scheme.items()})
    text = re.search(r'private val '+title+r'CalmEmeraldColors = CalmEmeraldColors\((.*?)\n\)', read('CalmEmeraldTokens.kt'), re.S)[1]
    emerald = {k:evaluate(e, mode, scheme) for k,e in re.findall(r'^    (\w+) = (.*),$', text, re.M) if k != 'isDark'}
    for k,v in re.findall(r'^            (\w+) = scheme\.(\w+),$', read('CalmEmeraldTokens.kt'), re.M): emerald[k] = scheme[v]
    default = re.search(r'val nearLimitOrange: Color = (Color\(0x[0-9A-F]+\))', read('CalmEmeraldTokens.kt'))[1]
    emerald.setdefault('nearLimitOrange', evaluate(default, mode, scheme))
    for name in ['incomeText', 'expenseText']:
        expr = re.search(r'val '+name+r': Color get\(\) = if \(isDark\) (\w+) else (Color\(0x[0-9A-F]+\))', read('CalmEmeraldTokens.kt'))
        emerald[name] = emerald[expr[1]] if mode == 'dark' else evaluate(expr[2], mode, scheme)
    expected.update({mode+'-emerald-'+k:v for k,v in emerald.items()})
    text = re.search(r'KipuUiColors\(\n            isDark = isDark,(.*?)\n        \)', read('KipuUiColors.kt'), re.S)[1]
    expected.update({mode+'-ui-'+k:evaluate(e, mode, scheme) for k,e in re.findall(r'^            (\w+) = (.*),$', text, re.M)})
for mode, title in [('light', 'Light'), ('dark', 'Dark')]:
    text = re.search(r'private val '+title+r'Tones = listOf\((.*?)\n\)', read('PastelTones.kt'), re.S)[1]
    rows = re.findall(r'PastelTone\(Color\(0xFF([0-9A-F]{6})\), Color\(0xFF([0-9A-F]{6})\), Color\(0xFF([0-9A-F]{6})\)\)', text)
    assert len(rows) == 7
    for index, values in enumerate(rows):
        for prop, value in zip(['fill', 'tile', 'ink'], values): expected[f'{mode}-pastel-{index}-{prop}'] = '#'+value
design = (root/'docs/DESIGN.md').read_text(encoding='utf8')
# SafeLoader estricto: rechaza claves duplicadas además de errores YAML.
class StrictLoader(yaml.SafeLoader): pass
def mapping(loader, node, deep=False):
    result = {}
    for key, value in node.value:
        key = loader.construct_object(key, deep=deep)
        assert key not in result, ('clave duplicada', key)
        result[key] = loader.construct_object(value, deep=deep)
    return result
StrictLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, mapping)
data = yaml.load(design.split('---',2)[1], Loader=StrictLoader)
assert data['colors'] == expected, [(k, v, data['colors'].get(k)) for k,v in expected.items() if data['colors'].get(k) != v]
expected_type = {}
for role, text in re.findall(r'    (\w+) = TextStyle\((.*?)\n    \)', read('Type.kt'), re.S):
    item = {'fontFamily':'Inter'}
    for key in ['fontSize', 'lineHeight', 'letterSpacing']:
        m = re.search(key+r' = (?:\(([-\d.]+)\)|([-\d.]+)).sp', text)
        if m: item[key] = (m[1] or m[2])+'sp'
    weight = re.search(r'fontWeight = FontWeight.(\w+)', text)[1]
    item['fontWeight'] = {'Normal':400,'Medium':500,'SemiBold':600}[weight]
    assert 'fontFamily = Inter' in text
    expected_type[role] = item
assert data['typography'] == expected_type
shapes = dict(re.findall(r'    (small|medium|large) = RoundedCornerShape\((\d+).dp\)', read('Theme.kt')))
assert data['rounded'] == {k:v+'dp' for k,v in shapes.items()}
assert set(data) == {'name','description','colors','typography','rounded'}
count = len(expected) + sum(map(len, expected_type.values())) + len(shapes)
print(f'{count} valores verificados: {len(expected)} colores, {sum(map(len, expected_type.values()))} propiedades tipográficas, {len(shapes)} radios; cero diferencias')
def lum(h):
    rgb = [int(h[i:i+2],16)/255 for i in (1,3,5)]
    linear = [c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4 for c in rgb]
    return sum(a*b for a,b in zip(linear,[0.2126,0.7152,0.0722]))
def contrast(a,b):
    x,y=sorted([lum(a),lum(b)])
    return (y+0.05)/(x+0.05)
print('Blanco/acento:', round(contrast('#FFFFFF','#14B8A6'),4))
print('Blanco/coral:', round(contrast('#FFFFFF','#E85D5D'),4))
```

El cálculo sRGB de luminancia relativa usa `(Lmayor + 0.05)/(Lmenor + 0.05)`. Blanco/acento da 2.4892:1 y blanco/coral 3.4119:1 (las cifras completas impresas por el bloque prevalecen sobre este redondeo). La restricción del acento claro es una decisión explícita del propietario, no una supuesta corrección ya desplegada.

## Impeccable y apariencia

Se ejecutó exactamente `C:\Users\Alume\.agents\skills\impeccable\scripts\impeccable.cmd context --target app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardScreen.kt`, con salida 0. `RESOLVED_CONTEXT.designPath` es `docs\DESIGN.md`, confirmando que selecciona el documento nuevo. Emitió `DOCUMENT_NOT_INLINED` por el tamaño del documento: no cargó el cuerpo en su salida; el archivo se leyó/generó directamente en esta sesión. Emitió `NO_PRODUCT_MD` esperado y `PRODUCT_INIT_REQUIRED` porque informa `platform: null` y `hasVisualImplementation: false` pese al target Kotlin existente: limitación del descubrimiento del contexto, no error YAML ni autorización para crear PRODUCT. No se ejecutaron init, hooks, live ni detector web; no se creó DESIGN raíz ni sidecar.

Se inspeccionaron las capturas locales A16 `a16/01-inicio-actual.png` y S24 `s24/06-dashboard-empty.png`, dentro de `docs/qa/ux-review-2026-11-05/capturas/_local-only/`. Muestran el hero teal sólido, tarjetas claras y navegación propia; no se muestrearon píxeles como fuente de tokens ni se certificó la build desplegada. No se ejecutó la app ni se validó visualmente el tema oscuro: sus valores se verificaron estáticamente en código. Se corrigió un ancla preexistente del índice R6 del catálogo Stitch para que apunte al encabezado que incluye «Perfeccionada».

## Diferencias e inconsistencias del código, sin corregir

- `CalmEmeraldTokens.kt:66` declara `onPrimaryDeep` oscuro `#042F2E`, pero el proveedor lo sustituye por `scheme.onPrimary` (`#0B1220`); `primaryText` original `#EFF1F3` queda en `#F8FAFC`. DESIGN registra los efectivos.
- `navSelectedPill/navSelectedContent` existen pero la navegación propia usa `incomeBg/primaryDeep`; no se presenta el par nominal como si fuera el consumidor real.
- `heroGradientEnd` existe pero `DashboardHero` usa `heroGradientStart` sólido; `heroSubcardBg` tampoco se consume aquí, pues las subtarjetas usan negro al 18 % fijo en ambos temas. `HeroLabel #BDEFE6`, `HeroDot #7DE0CF`, `HeroSubtitle #CFEFE9` son colores locales fijos; los subtítulos y el monto no usan todos el mismo foreground.
- El `surfaceVariant` oscuro efectivo es `#0F172A`; `KipuDarkSurfaceVariant #1A263B` alimenta `surfaceContainer`. `borderSubtle` y `outlineVariant` también difieren en ambos temas.
- `KipuOnError` es blanco fijo; el esquema oscuro usa `onError #450A0A`. Consumir el par del esquema para errores.
- `BrandLogo` puede usar blanco sobre `primaryDeep` en fallback oscuro, par incompatible con la regla de foreground oscuro del acento. Queda registrado, sin cambiar código. Los presets genéricos mantienen `sourceLabel = Kipu Andean Modernist`, otra procedencia heredada que no se editó.
- Los tokens decorativos claros de ingreso/gasto y los de texto tienen valores diferentes. El par blanco/coral del hallazgo S24-UX-006 sigue siendo una limitación; las propuestas del informe UX no se convierten en implementación.
- `KipuTypography` configura 11 roles; los demás heredan Material. No se garantiza Inter en toda la app. No hay escala global de espaciado ni elevación dentro del tema.

## Consumo del acento y defaults Material

Búsqueda de `KipuSecondary`, `14B8A6` y `.secondary` con límite de palabra en todo `app/src/main/java/`: solo asignaciones del tema y tinte fijo de `RateCatalogScreen.kt:862`, sin otro consumo explícito de secondary en pantallas. Los numerosos `secondaryContainer/onSecondaryContainer` son otros roles y no prueban uso directo del acento. Se inspeccionó la dependencia local Material 3 Android 1.4.0 (sources.jar en caché Gradle); sin red ni compilación.

Defaults con efecto directo confirmado: `AlertDialogDefaults.iconContentColor` toma `DialogTokens.IconColor = Secondary`; `NavigationBarItemDefaults` toma `NavigationBarTokens.ItemActiveLabelTextColor = Secondary` y `NavigationRailItemDefaults` toma `NavigationRailColorTokens.ItemActiveLabelText = Secondary`. Por tanto pueden producir texto claro en acento sin literal de pantalla; la navegación propia no usa esos defaults. Otros tokens que mencionan Secondary se inventarían como usos si no se revisara si su ruta se consume: el inventario siguiente distingue declaraciones de token de consumidores ejecutados.

Declaraciones Secondary localizadas en los archivos de tokens de la dependencia (no constituyen prueba de que cada estado se use en la app):

- `AssistChipTokens.kt:44`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `DialogTokens.kt:36`: `val IconColor = ColorSchemeKeyTokens.Secondary`.
- `ElevatedCardTokens.kt:32`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `FilledCardTokens.kt:32`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `FilledTonalIconButtonTokens.kt:31`: `val SelectedContainerColor = ColorSchemeKeyTokens.Secondary`.
- `FilterChipTokens.kt:54`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `InputChipTokens.kt:34`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `ListTokens.kt:26`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `MenuTokens.kt:25`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `NavigationBarTokens.kt:31`: `val ItemActiveLabelTextColor = ColorSchemeKeyTokens.Secondary`.
- `NavigationDrawerTokens.kt:40`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `NavigationRailColorTokens.kt:26`: `val ItemActiveLabelText = ColorSchemeKeyTokens.Secondary`.
- `SearchBarTokens.kt:30`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `SheetBottomTokens.kt:32`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `SuggestionChipTokens.kt:44`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `SwitchTokens.kt:36`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `TimeInputTokens.kt:27`: `val FocusIndicatorColor = ColorSchemeKeyTokens.Secondary`.
- `TonalButtonTokens.kt:42`: `val SelectedContainerColor = ColorSchemeKeyTokens.Secondary`.

## Menciones pendientes fuera de docs/

Cada línea siguiente es una cita localizada, no una ratificación. EP-PLA y su paleta anterior requieren refinamiento Spec Kit; EP-INF aún registra el gate de conflicto; las referencias compatibles a DESIGN se incluyen para que el propietario pueda revisar toda la cadena. La constitución y AGENTS.md no declaran una paleta visual competidora (búsqueda de los términos indicados). No se editaron estos archivos.

- `specs/002-ep-cta-cuentas-tarjetas/contracts/ui-contract.md:5`: `docs/DESIGN.md` is the sole local source for color, typography, spacing, shapes and accessibility tokens. Screen 4/5 assets are not present with stable Stitch IDs, so implementation may claim token compliance but not pixel-perfect prototype fidelity until those assets are approved.
- `specs/002-ep-cta-cuentas-tarjetas/plan.md:63`: **Constraints**: Local-first; dinero en `Long` minor units; `abs(amountMinor) <= 99_999_999_999_999`; aritmetica exacta con deteccion de overflow; PEN/USD sin conversion; PAN/CVV ausentes de modelos y logs; RLS y propiedad estructural; migraciones no destructivas; conflicto financiero nunca last-write-wins; Stitch `Kipu Andean Modernist`; accesibilidad 48dp/200%; sin Open Banking ni pagos reales
- `specs/002-ep-cta-cuentas-tarjetas/plan.md:198`: `docs/DESIGN.md` es la fuente visual. No existen IDs/artefactos Stitch autoritativos de Pantallas 4/5 en el repositorio, por lo que el plan aplica tokens y composicion funcional sin afirmar fidelidad pixel-perfect a un artefacto ausente.
- `specs/002-ep-cta-cuentas-tarjetas/plan.md:309`: 4. Actualizar `docs/DESIGN.md` antes de usar colores propios de Banco de la Nacion o afirmar fidelidad a Pantallas 4/5.
- `specs/002-ep-cta-cuentas-tarjetas/research.md:188`: - Copiar colores de fuentes externas sin aprobar: contradice la fuente de verdad visual.
- `specs/002-ep-cta-cuentas-tarjetas/tasks.md:21`: - [X] T003 [P] Setup design system tokens and typography helpers for Andean Modernist in `app/src/main/java/com/kipu/app/ui/theme/Type.kt`
- `specs/004-ep-mov-movimientos-ledger/contracts/ui-contract.md:5`: 1. **Tokens y Sistema Visual**: [`docs/DESIGN.md`](../../../docs/DESIGN.md) (`Kipu Andean Modernist`).
- `specs/004-ep-mov-movimientos-ledger/plan.md:104`: - Se implementa con fidelidad visual estricta a `docs/DESIGN.md` y `Stich Prompts.md` (Pantalla 10 para Historial y Pantalla 11 para Modal de Registro en Bottom Sheet). Reutiliza tema, componentes, tokens de color (Primary #0F766E, Income #16A34A, Expense #E85D5D) y números tabulares `tnum`.
- `specs/004-ep-mov-movimientos-ledger/research.md:49`: No se encontró `DESIGN.md` bajo el proyecto ni sus carpetas hermanas. Se reutilizan tema y componentes actuales y se solicita la ubicación de la fuente visual definitiva.
- `specs/004-ep-mov-movimientos-ledger/spec.md:440`: - Las fuentes visuales y contratos de diseño normativos son `docs/DESIGN.md` y `Stich Prompts.md` (Pantalla 10 para Historial y Pantalla 11 para Registro en Bottom Sheet), aplicando tokens de color, números tabulares `tnum` y componentes establecidos.
- `specs/004-ep-mov-movimientos-ledger/tasks.md:79`: - [x] T024 [US1] Implementar la Pantalla 11 (Modal de Registro en Bottom Sheet) con tabs Gasto/Ingreso/Transferencia, referencias tipadas por categoría, selectores, DatePicker, teclado numérico, números tabulares y feedback de guardado local/pendiente según Stich Prompts.md y docs/DESIGN.md en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
- `specs/004-ep-mov-movimientos-ledger/tasks.md:80`: - [x] T025 [P] [US1] Implementar la Pantalla 10 (Historial/Ledger) con TopBar, buscador, chips de filtro rápido, lista cronológica, badges de sincronización, colores por tokens y números tabulares según Stich Prompts.md y docs/DESIGN.md en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- `specs/004-ep-mov-movimientos-ledger/team-questions.md:24`: | Diseño | Al no existir `DESIGN.md`, se reutilizan el tema y componentes actuales hasta que aparezca una fuente visual oficial. |
- `specs/004-ep-mov-movimientos-ledger/team-questions.md:53`: 15. ¿Dónde está `DESIGN.md` o cuál es la fuente visual oficial que lo reemplaza?
- `specs/009-ep-inf-informacion-financiera/checklists/quality.md:26`: - [ ] CHK010 Product/design resuelve conflicto Andean Modernist vs Calm Emerald por pantalla o unificación.
- `specs/009-ep-inf-informacion-financiera/checklists/requirements.md:14`: - [ ] CHK006 GATE-DESIGN-01 requiere autoridad visual aprobada antes de implementar (no resuelto por el autor).
- `specs/009-ep-inf-informacion-financiera/plan.md:29`: - Gates abiertos: GATE-DESIGN-01, GATE-PRODUCT-02, GATE-ARCH-03, GATE-ANALYTICS-04, GATE-ACCESS-05; bloquean implementación hasta decisión/contratos.
- `specs/009-ep-inf-informacion-financiera/plan.md:39`: | Divergencia visual Andean Modernist / Calm Emerald | Resolver GATE-DESIGN-01 con autoridad producto/diseño; no escoger arbitrariamente. |
- `specs/009-ep-inf-informacion-financiera/research.md:9`: - `docs/control-documental.md` DOC-03 confirma conflicto de autoridad visual: DESIGN.md declara Andean Modernist y el índice mockups Calm Emerald. No es seguro elegir una. Hay nueve imágenes no disponibles en checkout.
- `specs/009-ep-inf-informacion-financiera/research.md:29`: - GATE-DESIGN-01: qué sistema visual gobierna Inicio/calendario/estadísticas y estado de tarjetas faltante.
- `specs/009-ep-inf-informacion-financiera/research.md:41`: | GATE-DESIGN-01 | Inicio y calendario usan el tema y los componentes ya implementados en la app (`KipuTheme`). La unificación DESIGN.md / Calm Emerald (DOC-03) sigue abierta y no se resuelve aquí. | Producto |
- `specs/009-ep-inf-informacion-financiera/spec.md:89`: - **GATE-DESIGN-01 (bloquea cierre de diseño visual e implementación)**: `docs/control-documental.md` registra autoridad visual en conflicto: `docs/DESIGN.md` (Andean Modernist) frente a `docs/ux-ui/mockups/README.md` (Calm Emerald), con proyectos Stitch distintos. Producto/diseño debe determinar autoridad por pantalla o aprobar unificación; no se elige por fecha. Las nueve imágenes de estados de tarjetas referenciadas no están disponibles en checkout y no son fuente para este alcance.
- `specs/009-ep-inf-informacion-financiera/tasks.md:10`: - [X] T001 [P] Resolver GATE-DESIGN-01 con autoridad visual y registrar decisión referenciada desde `specs/009-ep-inf-informacion-financiera/spec.md`.
- `specs/010-ep-not-notificaciones-avisos/research.md:43`: **Decision**: Use `docs/DESIGN.md` semantic Kipu theme colors and the already bundled Inter typeface. Build filter chips and explicit archive actions with at least 48dp targets. Use accessible read/category labels, a semantic unread count, and a text-plus-dot distinction. Apply Compose enter/exit/badge motion only when reduced motion is not enabled.
- `specs/012-ep-pla-planes-monetizacion/plan.md:9`: **Propagated**: 2026-09-15 — Updated from spec.md refinement (adopción de tokens del design system Stitch "Kipu Andean Modernist" como única fuente de verdad visual).
- `specs/012-ep-pla-planes-monetizacion/plan.md:11`: **Propagated**: 2026-09-15 — Updated from spec.md refinement (cleanup sin cambios funcionales: eliminadas las referencias residuales al sistema de diseño previo; el plan ya apunta a `docs/DESIGN.md`).
- `specs/012-ep-pla-planes-monetizacion/plan.md:95`: | Design system | `Kipu Andean Modernist` - `assets/a21e2e45f51e490fa03b92fb8cb83c55` |
- `specs/012-ep-pla-planes-monetizacion/plan.md:98`: | Autoridad local | `docs/DESIGN.md` y `spec.md` |
- `specs/012-ep-pla-planes-monetizacion/plan.md:110`: | Colores `#005c55`/`#f7f9fb` | Mantener como tokens normativos; referencia completa en `docs/DESIGN.md`. |
- `specs/012-ep-pla-planes-monetizacion/plan.md:292`: - **Stitch Prompt 1**: `Stich Prompts.md` no está presente en el repositorio. La pantalla y design system ya extraídos mediante MCP constituyen la referencia disponible; si el archivo se incorpora, debe archivarse para trazabilidad sin reemplazar `spec.md` ni el archivo de tokens `docs/DESIGN.md`.
- `specs/012-ep-pla-planes-monetizacion/plan.md:302`: - La Pantalla 1B coincide con los tokens, disposición, componentes y copy aprobados de Stitch (`docs/DESIGN.md`), es accesible, carece de Top/Bottom Bar y navega únicamente después del commit local del CTA Premium o Free.
- `specs/012-ep-pla-planes-monetizacion/research.md:19`: **Decision**: Usar como referencia visual y estructural autoritativa la pantalla Stitch `projects/5775615138851387862/screens/b8b4bfdcf384409887e54a975c549797`, dentro de `Kipu V4 Finale`, conciliada con los invariantes funcionales de `docs/DESIGN.md` y `spec.md`.
- `specs/012-ep-pla-planes-monetizacion/research.md:21`: **Rationale**: El MCP confirmó la pantalla “Pantalla 1B: Selección de Plan”, canvas móvil 780x2412 y design system `Kipu Andean Modernist`. La composición aprobada incluye Free informativo, Trial estático, tres opciones Premium, precios, badges, dos CTA y footer fiscal. El comportamiento continúa limitado a persistir una preferencia sin cobro ni entitlement.
- `specs/012-ep-pla-planes-monetizacion/research.md:162`: - `Stich Prompts.md`: no existe localmente; el artefacto Stitch extraído y `docs/DESIGN.md` son la evidencia disponible, sin afirmaciones adicionales.
- `specs/012-ep-pla-planes-monetizacion/spec.md:20`: **Refined**: 2026-09-15 — Adopción del design system Stitch "Kipu Andean Modernist" como única fuente de verdad visual de la Pantalla 1B; actualización de tokens de UI y lista de artefactos derivados; sin cambios funcionales (FR/RN/SC y user stories intactos).
- `specs/012-ep-pla-planes-monetizacion/spec.md:22`: **Refined**: 2026-09-15 — Eliminación de las referencias residuales al sistema de diseño previo a Stitch; todas las referencias de diseño apuntan a `docs/DESIGN.md` (cleanup post-adopción del design system Stitch; sin cambios funcionales).
- `specs/012-ep-pla-planes-monetizacion/spec.md:224`: - ~~La maquetación tomaba como referencia los tokens del documento de diseño previo: Primary `#0F766E`, Ink `#0F172A` y radios de `16dp`/`12dp`.~~ *(Reemplazado por tokens del design system Stitch; la única fuente de verdad visual es `docs/DESIGN.md`.)*
- `specs/012-ep-pla-planes-monetizacion/spec.md:225`: - La maquetación adopta el design system Stitch `Kipu Andean Modernist` (Pantalla 1B `b8b4bfdcf384409887e54a975c549797`, proyecto `projects/5775615138851387862`) como única fuente de verdad visual y usa su disposición y componentes como referencia de fidelidad. Tokens registrados en `docs/DESIGN.md`: background `#F7F9FB`; surface de tarjetas `#FFFFFF`; onSurface `#191C1E`; onSurfaceVariant `#3E4947`; outline `#6E7977`; outlineVariant `#BDC9C6`; primary (CTA) `#0F766E` con pressed `#005C55`; ring de selección (surface_tint) `#006A63`; secondary `#216963`; secondaryContainer `#A8ECE5`; onSecondaryContainer `#266D68`; error `#BA1A1A`; dark background `#0B1220`. Radios de `16dp` (tarjetas) y `12dp` (controles/CTA). Tipografía Inter obligatoria en toda la pantalla; montos con números tabulares (`tnum`). Grilla base 8dp, márgenes de página 16dp, separación de secciones 24dp, targets mínimos 48dp.
- `specs/012-ep-pla-planes-monetizacion/spec.md:713`: - ~~El documento de diseño de marca previo~~ *(Sustituido por `docs/DESIGN.md` — design system Stitch `Kipu Andean Modernist` como autoridad.)*
- `specs/012-ep-pla-planes-monetizacion/spec.md:714`: - `DESIGN.md`
- `specs/012-ep-pla-planes-monetizacion/tasks.md:14`: **Propagated**: 2026-09-15 — Updated from spec.md refinement (cleanup sin cambios funcionales: eliminadas las referencias residuales al sistema de diseño previo; ninguna tarea afectada, T042/T056 ya apuntan a `docs/DESIGN.md`).
- `specs/012-ep-pla-planes-monetizacion/tasks.md:118`: - [X] T042 [P] [US1] Replace template purple/dynamic styling with the Stitch design system tokens registered in `docs/DESIGN.md` (paleta tonal: primary `#0F766E`, pressed `#005C55`, background `#F7F9FB`, surface `#FFFFFF`, onSurface `#191C1E`, radios 16dp/12dp, Inter) in `app/src/main/java/com/kipu/app/ui/theme/Color.kt`, `app/src/main/java/com/kipu/app/ui/theme/Theme.kt`, and `app/src/main/java/com/kipu/app/ui/theme/Type.kt`
- `specs/012-ep-pla-planes-monetizacion/tasks.md:122`: - [X] T056 [P] [US1] Adoptar los tokens del design system Stitch (paleta tonal `#0F766E`/`#005C55`/`#006A63`/`#F7F9FB`/`#FFFFFF`/`#191C1E`/`#A8ECE5`/`#216963`, radios 16dp/12dp) e Inter empaquetada en `res/font` en el tema global, re-estilar la Pantalla 1B según el layout Stitch conservando el copy aprobado y los invariantes (5 límites Free, CTA único, sin barras/slogans/filler, orden `MONTHLY`->`ANNUAL`->`LIFETIME`, `tnum`, 48dp), retirar el DESIGN previo a Stitch y crear la guía de tokens Stitch, actualizar `research.md` y verificar compile/tests/lint y ausencia del contenido visual reemplazado en `app/src/main/java/com/kipu/app/feature/plans/presentation/PlanSelectionScreen.kt` y `docs/` (depends on T042, T045). Nota de rutas 2026-10-20: la guía creada entonces como `docs/stitch-design-system.md` se renombró a `docs/DESIGN.md`; no es el documento previo eliminado ni autoriza reejecutar su eliminación.
- `specs/012-ep-pla-planes-monetizacion/tasks.md:131`: - [X] T058 [P] [US1] Replace the superseded eligibility/prohibition copy with the exact approved Pantalla 1B strings and document its component/icon mapping and product-decision rationale in `app/src/main/res/values/strings.xml`, `docs/DESIGN.md`, `specs/012-ep-pla-planes-monetizacion/research.md`, and `specs/012-ep-pla-planes-monetizacion/quickstart.md`

## Inventario de colores fijos fuera del tema

Búsqueda estática exhaustiva de literales `Color(0xFF…)` (ARGB opaco) en Kotlin bajo `app/src/main/java/`, excluyendo `ui/theme/`; no incluye valores leídos de BD, assets ni expresiones dinámicas. Cada entrada indica archivo, línea y RGB. Los presets hex en cadenas están documentados aparte en DESIGN; colores de marcas y helpers se incluyen aquí como usos especializados, no necesariamente defectos. Este inventario no autoriza trasladarlos a tokens generales.

- `app/src/main/java/com/kipu/app/feature/accounts/presentation/components/AccountVisuals.kt`: `34:#0F766E`, `35:#208358`, `36:#0F766E`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardArt.kt`: `43:#1C2430`, `82:#D2D2D2`, `106:#3B2A00`, `107:#20242B`, `108:#D9D6D1`, `112:#3B2A00`, `113:#20242B`, `130:#0B3A8F`, `135:#2A1F00`, `136:#101418`, `138:#101418`, `139:#06381B`, `140:#12306E`, `141:#06381B`, `142:#2A1F00`, `145:#06381B`, `146:#12301F`, `147:#2A1F00`, `148:#101418`, `150:#191C1E`, `172:#082228`, `173:#104984`, `174:#104984`, `175:#123D65`, `183:#D4D2D0`, `184:#252321`, `185:#D4D2D0`, `188:#06260A`, `189:#06260A`, `190:#0F1A1A`, `197:#00386B`, `199:#001E3D`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CreditCardSummaryCard.kt`: `183:#1E293B`, `183:#0F172A`, `190:#FBBF24`, `248:#1E293B`, `248:#0F172A`, `255:#FBBF24`, `432:#1E293B`, `432:#E2E8F0`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardSections.kt`: `99:#BDEFE6`, `100:#7DE0CF`, `101:#CFEFE9`, `429:#E9E1F3`, `429:#E1EEF3`, `430:#E8DAF5`, `430:#CFF5F3`, `431:#5B1F8F`, `431:#0B6B68`, `584:#0F172A`, `585:#334155`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/dashboard/DashboardThemePreview.kt`: `44:#7F1D1D`, `44:#FEE2E2`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/detail/AccountDetailScreen.kt`: `1035:#0B1F33`, `1035:#081826`, `1040:#D4AF37`, `1176:#FBBF24`, `1230:#E5C07B`, `1230:#D19A66`, `1230:#E5C07B`, `1237:#7A5813`, `1243:#7A5813`, `1349:#111111`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/AccountAddFormComponents.kt`: `82:#475569`, `291:#DCE8F7`, `291:#EBDDF5`, `292:#1E4FA8`, `292:#6B2FA0`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CalmEmeraldInstrumentComponents.kt`: `1260:#DCE8F7`, `1261:#1E4FA8`, `1268:#EBDDF5`, `1269:#6B2FA0`, `1276:#DDF3E6`, `1277:#1B6B3A`, `1284:#0F172A`, `1285:#FBBF24`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/CreditCardTermsDialog.kt`: `150:#0F243A`, `169:#5EEAD4`, `221:#5EEAD4`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/InstrumentFormModels.kt`: `40:#FFC600`, `41:#002A8F`, `51:#004481`, `62:#009940`, `63:#191C1E`, `73:#ED1C24`, `74:#FFFFFF`, `84:#FFC107`, `85:#1F1B13`, `95:#005A8C`, `106:#607D8B`, `117:#00843D`, `128:#004B93`, `139:#621275`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/MovementClassificationEditor.kt`: `72:#3F51B5`, `76:#3F51B5`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/RateCatalogScreen.kt`: `752:#0D1E2C`, `771:#FBBF24`, `777:#1E293B`, `784:#CBD5E1`, `794:#162D3F`, `805:#10B981`, `811:#CBD5E1`, `820:#334155`, `821:#1E293B`, `832:#94A3B8`, `845:#94A3B8`, `862:#14B8A6`, `868:#CBD5E1`, `876:#10B981`, `876:#F59E0B`.
- `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/UnifiedInstrumentFormScreen.kt`: `119:#475569`, `431:#005C55`, `1051:#FEF3C7`, `1051:#5A3200`, `1052:#FFE8D6`, `1052:#5C2500`, `1053:#FDECEC`, `1053:#8B1F1F`.
- `app/src/main/java/com/kipu/app/feature/auth/presentation/AccountCreatedScreen.kt`: `169:#0B5F58`, `170:#0B5F58`.
- `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthCelebrationMotion.kt`: `324:#FDE68A`, `324:#99F6E4`, `331:#0F766E`, `383:#052E2A`, `383:#0B463E`, `383:#0F766E`, `385:#0A5A54`, `385:#0F766E`, `385:#2C9A8E`.
- `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormComponents.kt`: `254:#0B5F58`.
- `app/src/main/java/com/kipu/app/feature/auth/presentation/AuthFormScreen.kt`: `245:#0A5A54`.
- `app/src/main/java/com/kipu/app/feature/auth/presentation/WelcomeBackCurtain.kt`: `181:#042824`, `181:#042824`, `203:#0F766E`.
- `app/src/main/java/com/kipu/app/feature/capture/presentation/CaptureComponents.kt`: `217:#B91C1C`.
- `app/src/main/java/com/kipu/app/feature/categories/presentation/categories/CategoryPresentationEditor.kt`: `71:#3F51B5`.
- `app/src/main/java/com/kipu/app/feature/categories/presentation/CategoryDesignTokens.kt`: `182:#0F766E`.
- `app/src/main/java/com/kipu/app/feature/categories/presentation/components/MerchantPicker.kt`: `255:#652D90`, `256:#006241`, `257:#C8102E`, `258:#E5AB00`, `259:#E2231A`, `260:#E50914`, `261:#1DB954`, `262:#000000`, `576:#1C1C1E`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/AccountSelectorBottomSheet.kt`: `84:#86EFAC`, `84:#166534`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/components/MovementClassificationRows.kt`: `41:#451A03`, `41:#FDBA74`, `43:#FEF3C7`, `169:#2E1065`, `169:#F3E8FF`, `170:#C4B5FD`, `170:#7E22CE`, `307:#7C2D12`, `307:#FDBA74`, `307:#FFEDD5`, `307:#9A3412`, `309:#4C1D95`, `309:#C4B5FD`, `309:#EDE9FE`, `309:#5B21B6`, `311:#0C4A6E`, `311:#7DD3FC`, `311:#E0F2FE`, `311:#075985`, `313:#881337`, `313:#FDA4AF`, `313:#FFE4E6`, `313:#9F1239`, `315:#78350F`, `315:#FCD34D`, `315:#FEF3C7`, `315:#92400E`, `317:#064E3B`, `317:#6EE7B7`, `317:#D1FAE5`, `317:#065F46`, `319:#831843`, `319:#F472B6`, `319:#FCE7F3`, `319:#9D174D`, `321:#1E293B`, `321:#94A3B8`, `321:#F1F5F9`, `321:#475569`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorSheet.kt`: `744:#B91C1C`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementFiltersSheet.kt`: `364:#E6F4EA`, `365:#A7F3D0`, `366:#0F766E`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/PastelCategoryPickerFlow.kt`: `177:#5EEAD4`, `177:#074842`, `178:#123B38`, `178:#F0FDF4`, `184:#475569`, `185:#94A3B8`, `185:#64748B`, `197:#DCFCE7`, `197:#166534`, `197:#166534`, `198:#FFEDD5`, `198:#9A3412`, `198:#9A3412`, `199:#EDE9FE`, `199:#5B21B6`, `199:#5B21B6`, `200:#E0F2FE`, `200:#075985`, `200:#075985`, `201:#FFE4E6`, `201:#9F1239`, `201:#9F1239`, `202:#FEF3C7`, `202:#92400E`, `202:#92400E`, `203:#D1FAE5`, `203:#065F46`, `203:#065F46`, `204:#F3E8FF`, `204:#6B21A8`, `204:#6B21A8`, `205:#E0E7FF`, `205:#3730A3`, `205:#3730A3`, `206:#DBEAFE`, `206:#1D4ED8`, `206:#1D4ED8`, `207:#CCFBF1`, `207:#0F766E`, `207:#0F766E`, `208:#F1F5F9`, `208:#334155`, `208:#334155`, `209:#FEE2E2`, `209:#B91C1C`, `209:#B91C1C`, `219:#14532D`, `219:#86EFAC`, `220:#7C2D12`, `220:#FDBA74`, `221:#4C1D95`, `221:#C4B5FD`, `222:#0C4A6E`, `222:#7DD3FC`, `223:#881337`, `223:#FDA4AF`, `224:#78350F`, `224:#FCD34D`, `225:#064E3B`, `225:#6EE7B7`, `226:#581C87`, `226:#D8B4FE`, `227:#312E81`, `227:#A5B4FC`, `228:#1E3A8A`, `228:#93C5FD`, `229:#134E4A`, `229:#5EEAD4`, `230:#7F1D1D`, `230:#FCA5A5`, `231:#334155`, `231:#CBD5E1`, `422:#0D6E64`.
- `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`: `724:#134E4A`, `724:#E6F7F3`, `725:#5EEAD4`, `725:#075E52`, `811:#1E3A8A`, `811:#EFF6FF`, `984:#082F49`, `984:#7DD3FC`, `986:#E0F2FE`, `986:#0284C7`, `1070:#1E293B`, `1070:#E2E8F0`.
- `app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptImportNoticeSheet.kt`: `114:#0A301D`, `114:#DCFCE7`, `115:#0B3B36`, `115:#E1F4F0`, `116:#3A2A0B`, `116:#FEF3C7`, `117:#1F2A3D`, `117:#EEF2F6`, `120:#86EFAC`, `120:#166534`, `121:#5EEAD4`, `121:#0F766E`, `122:#FCD34D`, `122:#8A4B00`, `123:#CBD5E1`, `123:#475569`.
- `app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptInboxComponents.kt`: `69:#0F766E`, `70:#0A5650`, `71:#6E2A8C`, `72:#F0E4F7`, `88:#0B5F58`, `96:#0B5F58`, `145:#042824`, `145:#042824`, `153:#2A1235`, `154:#5B4A66`, `157:#042824`, `157:#042824`.
- `app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptReviewScreen.kt`: `200:#0F766E`, `200:#12998C`.
- `app/src/main/java/com/kipu/app/feature/receipts/presentation/ReceiptShareGuide.kt`: `100:#6E2A8C`, `101:#2A1235`, `102:#5B4A66`, `103:#F0E4F7`, `104:#0F766E`, `105:#15803D`, `181:#E3F4F0`, `181:#F2F9F8`, `181:#EAF0F8`, `203:#0F172A`, `248:#BFE6DF`, `248:#9CC9E6`, `250:#0B5F58`, `338:#0F172A`, `338:#0F172A`, `339:#111827`, `340:#FBF8FD`, `345:#111827`, `380:#EADCF2`, `411:#CBD5E1`, `412:#334155`, `426:#EEF2F6`, `427:#475569`, `430:#0B5F58`, `430:#334155`, `450:#F8FAFC`, `451:#0F172A`, `452:#E1F4F0`, `454:#0B5F58`, `455:#0B5F58`, `460:#FDF0B8`, `462:#64748B`, `465:#0F172A`, `468:#EEF2F6`, `520:#0F172A`.
- `app/src/main/java/com/kipu/app/feature/setup/SetupComponents.kt`: `86:#0A5A54`, `87:#0F766E`, `88:#2C9A8E`, `97:#062D27`, `97:#0B463E`, `97:#0F6B62`, `216:#0F766E`, `321:#FFEDD5`, `321:#C2410C`, `322:#E0F2FE`, `322:#0369A1`, `323:#EDE9FE`, `323:#6D28D9`, `324:#FCE7F3`, `324:#BE185D`, `325:#DCFCE7`, `325:#15803D`, `326:#FEF3C7`, `326:#B45309`, `327:#FAE8FF`, `327:#A21CAF`, `328:#CCFBF1`, `328:#0F766E`, `329:#E0E7FF`, `329:#4338CA`, `330:#DBEAFE`, `330:#1D4ED8`, `331:#ECFCCB`, `331:#4D7C0F`, `332:#E2E8F0`, `332:#475569`.
- `app/src/main/java/com/kipu/app/feature/setup/SetupOtpScreen.kt`: `227:#0F766E`, `229:#0F766E`.
- `app/src/main/java/com/kipu/app/feature/setup/SetupScreens.kt`: `100:#208358`, `476:#208358`, `499:#8A4B00`.
- `app/src/main/java/com/kipu/app/feature/statements/presentation/ImportMovementsScreen.kt`: `172:#6E2A8C`.
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementDesign.kt`: `99:#FCD34D`, `99:#8A4B00`, `115:#002A8D`, `117:#FF7800`, `136:#0A301D`, `136:#DCFCE7`, `136:#86EFAC`, `136:#166534`, `136:#14532D`, `136:#A7E3BC`, `137:#3A2A0B`, `137:#FEF3C7`, `137:#FCD34D`, `137:#8A4B00`, `137:#6B4A12`, `137:#F3D58A`, `138:#3B1216`, `138:#FDECEC`, `138:#FCA5A5`, `138:#B42F2F`, `138:#7F1D1D`, `138:#F0B4B4`, `139:#2A1A44`, `139:#F0E4F7`, `139:#D8B4FE`, `139:#6E2A8C`, `139:#4A2A70`, `139:#E0CBEE`, `140:#11284A`, `140:#E6EEFA`, `140:#93C5FD`, `140:#1E4E9E`, `140:#1E3A8A`, `140:#C5D7F2`.
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementReviewScreen.kt`: `260:#0F766E`, `260:#64BAB0`, `557:#0F766E`, `557:#073F3A`, `559:#5EEAD4`, `571:#0F766E`, `571:#073F3A`, `592:#0B5F58`.
- `app/src/main/java/com/kipu/app/feature/statements/presentation/StatementScanOverlay.kt`: `119:#0F766E`, `119:#0B5F58`, `119:#073F3A`, `149:#02201D`, `149:#02201D`, `150:#F4F7FA`, `154:#C9D3DD`, `155:#DCE3EA`, `157:#64748B`, `163:#E1F4F0`, `165:#0F766E`, `169:#334155`, `170:#0F172A`, `172:#E6ECF1`, `174:#D7DFE7`, `189:#5EEAD4`, `189:#5EEAD4`, `189:#5EEAD4`, `205:#5EEAD4`, `220:#A7F3D0`, `221:#A7F3D0`, `225:#0B5F58`.
- `app/src/main/java/com/kipu/app/navigation/ReceiptsNavigation.kt`: `608:#EFF3F7`.
- `app/src/main/java/com/kipu/app/ui/component/BrandLogo.kt`: `51:#002A8F`, `52:#004481`, `53:#00843D`, `54:#EC111A`, `55:#C8102E`, `56:#0B3D91`, `57:#007A33`, `58:#0060A9`, `59:#5B2D86`, `60:#742284`, `61:#00A19B`.

Total: **457 ocurrencias** de literales opacos fuera del tema.

## Archivos cambiados y controles finales

- `docs/DESIGN.md`.
- `docs/README.md`.
- `docs/control-documental.md`.
- `docs/qa/auditoria-sprint-6-2026-10-19/checkpoint-coordinacion.md`.
- `docs/qa/auditoria-sprint-6-2026-10-19/issues-publicacion.md`.
- `docs/qa/auditoria-sprint-6-2026-10-19/issues/UI-001-investigacion-tokens.md`.
- `docs/qa/auditoria-sprint-6-2026-10-19/revision-ui.md`.
- `docs/qa/pr-reviews/2026/ISSUE-62-token-authority-6441851/review.md`.
- `docs/qa/ux-review-2026-11-05/a16-uso-diario.md`.
- `docs/qa/ux-review-2026-11-05/informe-ux.md`.
- `docs/qa/ux-review-2026-11-05/s24-primer-uso.md`.
- `docs/ux-ui/MOCKUP_MIGRATION_MAP.md`.
- `docs/ux-ui/README.md`.
- `docs/ux-ui/audits/AUTH_ONBOARDING_IMPLEMENTATION.md`.
- `docs/ux-ui/audits/KIPU_UI_UX_AUDIT.md`.
- `docs/ux-ui/implementation/APPROVED_MOCKUPS_EXECUTION.md`.
- `docs/ux-ui/implementation/KIPU_CALM_EMERALD_REDESIGN.md`.
- `docs/ux-ui/mockups/README.md`.
- `docs/ux-ui/references/issue-62-token-authority-investigation.md`.
- `docs/ux-ui/references/stitch-redesign-catalog.md`.
- `docs/ux-ui/references/design-consolidation-2026-11-06.md`.

Se verifican enlaces relativos de archivos tocados, incluidos destinos y anclas Markdown; se excluyen URLs externas y rutas en bloques de código. Se revisa de nuevo autoridad visual: las declaraciones antiguas localizadas en docs/ quedan bajo una nota histórica explícita, salvo los índices activos que remiten a DESIGN. `git diff --check` y restricción del árbol de trabajo se comprueban al finalizar; no se ejecutan pruebas de app para este cambio documental.

Resultado final de controles: **861 enlaces relativos y 11 anclas válidos**, cero destinos rotos; **10 informes históricos conservados textualmente salvo la nota añadida**; 11 colores de marca y 66 presets bancarios de la tabla cotejados con su fuente, sin diferencias (adicionales a los 276 valores del frontmatter). La búsqueda final no encontró autoridad visual competidora activa sin nota de vigencia. Rama `main`, índice sin cambios y únicamente 21 Markdown bajo docs/ en el árbol de trabajo; `git diff --check` sin errores.
