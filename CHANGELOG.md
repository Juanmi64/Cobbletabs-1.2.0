# Changelog

Todos los cambios notables de CobbleTabs se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el
versionado es SemVer (`MAJOR.MINOR.PATCH`).

## [No versionado]

### Añadido

- **Editor disponible en el inventario creativo**: el botón de libro y pluma para abrir el editor
  de pestañas aparece ahora también en el inventario creativo (antes solo en survival), en la misma
  posición a la izquierda del toggle y por encima de la tira de pestañas vanilla
- **Colores RGB personalizados en las pestañas**: el diálogo de edición añade un **campo de color
  libre** donde escribir un hex `#RRGGBB` (o un nombre de color), con **vista previa en vivo** junto
  a la paleta rápida. Nuevo swatch **"sin color"** (raya gris) para volver al color clásico de cada
  pestaña
- **Colores para los presets**: cada preset puede tener un **color propio** (paleta en el panel de
  detalles). Se guarda en el JSON del preset (`"color"`) y viaja con él al **exportar/importar**; el
  nombre del preset se pinta de ese color en la lista y en la cabecera. En los integrados es solo
  de sesión

### Cambiado

- **Pantalla de presets rediseñada** a dos columnas: la izquierda lista los presets (integrados en
  cian, guardados en blanco, contador de pestañas, marcador del color al seleccionar) y la derecha
  muestra el **panel de detalles** del preset seleccionado: nombre a color, tipo, contenido completo
  (las admin marcadas) y las acciones como botones: **Cargar**, **Exportar** (JSON al portapapeles),
  **Sobrescribir** y **Eliminar** (deshabilitados en integrados). Selección con clic o **↑/↓**;
  el botón **Importar** del portapapeles sigue igual. Más claro e intuitivo que los iconos al hover
- **Botones del inventario en creativo**: el toggle de pestañas y el botón del editor se dibujan
  ahora **más arriba** en el inventario creativo (fuera de la tira de pestañas vanilla) y el botón
  del editor está **a la izquierda del toggle**, para que nunca queden tapados ni dejen de ser
  clicables

### Añadido (1.2.x)

- **Gestión completa de presets**: botón **Importar** para guardar como preset el JSON del
  portapapeles (con confirmación y nombre automático; con **Ctrl+V** también se pega un JSON o un
  nombre en el campo). Los presets integrados no se pueden sobrescribir

- **Control de la fila admin desde el editor ingame**: el panel de edición tiene dos botones:
  **"Fila admin: Sí/No"** activa o desactiva las pestañas admin sin tocar el JSON (al activarla se
  encienden también las admin integradas Survival/Creative/Spectator) y **"Esquina"** despliega un
  menú con las **4 esquinas de la pantalla** (resalta la activa) para aplicar `admin.corner`,
  guardando y aplicando el cambio al instante

## [1.2.0] - 2026-09-22

### Añadido

- **Atajo de teclado para el editor**: **Shift+F8** abre el editor de pestañas desde el juego;
  además hay una tecla **"Abrir editor de pestañas"** asignable en Controles (sin asignar por defecto)
- **Carpeta propia en config**: la config y los presets viven ahora en `config/cobbletabs/`
  (`cobbletabs.json` + `presets/`), con **migración automática** desde las rutas antiguas

### Cambiado

- **Cobblemon pasa a ser dependencia opcional** (`suggests`): el mod arranca y funciona sin él;
  solo los iconos de Cobblemon de las pestañas por defecto se muestran como papel si no está instalado

### Añadido

- **Editor de pestañas dentro del juego**: botón de libro y pluma sobre el inventario que abre
  una pantalla para gestionar las pestañas sin tocar el JSON:
  - Editar **comando**, **icono** (con previsualización del item), **texto** y **color**
    (paleta rápida de 10 colores)
  - Toggles de **negrita** y **activada/desactivada** por pestaña
  - **Añadir** pestañas nuevas e **insertar/reordenar arrastrando** el icono de cada fila
  - Lista conmutables entre pestañas normales y **admin**
  - Botón **Por defecto** para restaurar todas las pestañas (con confirmación)
  - Todo se guarda al instante en `config/cobbletabs/cobbletabs.json` y se aplica sin salir del inventario
- **Presets**: pantalla para crear, generar y cargar conjuntos de pestañas con nombre
  (accesible desde el editor, botón "Presets"):
  - **Cargar** un preset con un clic (con confirmación; reemplaza pestañas normales y admin)
  - **Crear** un preset desde la config actual: se guarda en `config/cobbletabs/presets/<nombre>.json`
  - **Borrar** presets guardados (los integrados no se pueden borrar)
  - Tooltip con el contenido de cada preset (comando → nombre e icono de cada pestaña)
  - **Plantillas integradas**: `default` (las clásicas del mod) y `pokegalaxia`
    (Menu, Wiki, GTS, AH, Warps, STS, WT y Balance)
  - Si el preset trae pestañas admin, la fila admin se activa sola
- Nuevo campo de config `showEditButton` (por defecto `true`) para ocultar el botón del editor
- Colores por nombre añadidos a `parseColor`: aqua/cian/turquesa, orange/naranja,
  purple/morado/lila/magenta, blue/azul

### Cambiado

- En el inventario, el orden de prioridad de clics es ahora: botón editor → toggle de pestañas →
  toggle admin → pestañas admin → pestañas laterales

## [1.1.0] - 2026-09-16

### Añadido

- **Pestañas de administración**: fila aparte en cualquier esquina de la pantalla
  (`admin.corner`: `bottom_right`, `bottom_left`, `top_right`, `top_left`, también en español),
  con pestañas propias para comandos de staff (por defecto Survival/Creative/Spectator,
  desactivadas) y ampliables sin límite
- **Botón toggle propio para la fila admin**, con estado persistente independiente
  (`admin.visible`) y desaparecible (`admin.showToggleButton`)
- **4 huecos extra** de pestaña (2 por lado), desactivados por defecto
- **Nombres a color y en negrita** configurables por pestaña (`color` con nombres en
  inglés/español o hex `#RRGGBB`, `bold`)
- Migración automática de configs antiguas: extras y pestañas admin se añaden solas (también con F8)
- Logotipo (marca de agua) con tamaño y opacidad configurables

### Cambiado

- Reparto de pestañas laterales automático según cuántas quepan por lado (`maxPerSide`)

## [1.0.0] - 2026-09-16

### Añadido

- Primera versión: pestañas laterales en el inventario (hasta 5 por lado) que ejecutan un comando
  con un clic (`/pc`, `/wiki`, `/daycare`, `/daily`, `/sts`, `/wt`)
- Archivo de configuración JSON (`config/cobbletabs.json`) con recarga en caliente (tecla F8)
- Botón para ocultar/mostrar las pestañas, con estado persistente
- Logo discreto en el inventario (solo ahí; sin pestañas en cofres ni enderchests)
- Traducciones en inglés y español
