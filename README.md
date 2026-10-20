# Kipu

Aplicación Android nativa de finanzas personales, con enfoque local-first.

## Documentación

- [Índice principal](docs/README.md): producto, arquitectura, planificación, referencias, UI/UX y QA.
- [Control documental](docs/control-documental.md): selección desde KipuApp, vigencia y diferencias pendientes.
- [Especificaciones por épica](specs/): requisitos refinados, planes, tareas y contratos de Spec Kit.

La documentación principal se mantiene en `docs/`; los originales no seleccionados permanecen en `KipuApp`. No se requiere consultar Obsidian Mind para acceder a las definiciones integradas.

## Integraciones de Spec Kit

Este proyecto tiene instaladas las integraciones `opencode` y `agy` de Spec Kit.
La integración predeterminada debe seleccionarse desde la raíz del proyecto según
el agente que se vaya a utilizar.

### Usar OpenCode

```bash
specify integration use opencode
```

### Usar AGY

```bash
specify integration use agy
```

El comando `specify integration use <integracion>` cambia la integración
predeterminada sin desinstalar las demás integraciones disponibles.

Para comprobar cuál está activa y consultar el estado de las integraciones:

```bash
specify integration status
```

También se pueden listar las integraciones disponibles e instaladas:

```bash
specify integration list
```

No se recomienda usar la opción `--force` para un cambio normal, ya que puede
sobrescribir archivos compartidos que hayan sido personalizados.
