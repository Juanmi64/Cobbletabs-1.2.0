# Changelog

Todos los cambios notables de CobbleTabs se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el
versionado es SemVer (`MAJOR.MINOR.PATCH`).

## [1.2.3] - 2026-09-25

### Añadido

- **Chip de lado en cada fila del editor**: la lista muestra un chip con el lado de cada
  pestaña (`auto`, `izq`, `der`, `arriba`, `abajo`); un clic sobre él **cambia el lado al
  instante** (ciclo automático → izquierda → derecha → arriba → abajo), sin abrir el diálogo
  (solo en pestañas normales; las admin no lo muestran)
- **Barra de scroll** en la lista del editor: ya no hace falta contar con la rueda a ciegas,
  hay un indicador visual de la posición y las filas ganan algo de aire (9 visibles)
- El **chip de lado del diálogo** de edición guarda y aplica al momento; en el panel, el botón
  de **esquina de la fila admin** pasa a ser un **chip cíclico** (clic = siguiente esquina,
  guarda al instante) con tooltip y atenuado cuando la fila admin está apagada
- Tooltips en los chips de **negrita** y **activada**, resaltado de hover en **Guardar /
  Cancelar** y separadores visuales en el panel y el diálogo

### Cambiado

- **Editor rediseñado**: panel más ancho (264 px) con cabecera separada, filas más legibles
  (icono, nombre a color, chip de lado, comando y ✕), pie en dos filas de botones y diálogo
  reorganizado con etiquetas sobre cada campo, vista previa del icono más grande y selectores
  compactos en vez de los menús desplegables de 1.2.2 (el de lado y el de esquina)
- **Las pestañas admin ya no muestran el selector "Lado del inventario"**: su posición la
  decide la esquina de la fila admin, no el campo "side" (el campo se limpia al guardar una
  admin y la fila de chips sube en su lugar). Antes el selector se mostraba también en las
  admin aunque no tuviera ningún efecto

## [1.2.2] - 2026-09-25

### Añadido

- **Lado del inventario por pestaña**: cada pestaña puede fijar su lado con el nuevo campo
  **`"side"`** (`left`, `right`, `top` o `bottom`; también en español: `izquierda`, `derecha`,
  `arriba`, `abajo`…). Las de lado **arriba/abajo** se dibujan colgando del borde superior o
  inferior de la GUI, así que las pestañas ya pueden colocarse en las **4 esquinas del inventario**.
  Vacío (`""`, el valor por defecto) = **reparto automático clásico**: la mitad a la izquierda y el
  resto a la derecha, como siempre. En el **editor ingame**, el diálogo de edición añade un selector
  **"Lado del inventario"** con menú desplegable (Automático / Izquierda / Derecha / Arriba / Abajo)
- **Los efectos de estado ya no se dibujan encima de las pestañas**: cuando hay pestañas en el lado
  derecho del inventario, la columna de efectos de vanilla se **desplaza hacia fuera** para dejarles
  sitio; si hay pestañas en los lados **arriba/abajo**, los efectos se **ocultan** (vanilla solo mira
  el hueco a la derecha y los pintaría sobre la fila). Implementado con el primer mixin del mod
  (`EffectRenderingInventoryScreen`), solo activo en el inventario del jugador y solo con las
  pestañas visibles

### Cambiado

- El diálogo de edición del editor es algo más alto (168 px) para hacer sitio al selector de lado;
  los toggles de negrita/activada y los botones Guardar/Cancelar bajan 16 px

## [1.2.1] - 2026-09-24

### Añadido

- **Borrado rápido de pestañas**: cada fila de la lista del editor tiene ahora un botón **✕** al
  final para borrar la pestaña sin abrir el diálogo (los huecos extra y las admin integradas se
  desactivan en vez de borrarse, igual que con la ✕ del diálogo). Al confirmar un borrado desde el
  diálogo, este se cierra y vuelve a la lista en vez de seguir mostrando la pestaña ya borrada
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

- **Pestañas por defecto renovadas**: la config nueva trae solo **2 pestañas activas**, **Menu**
  (`/menu`, brújula, cian) y **RTP** (`/rtp`, perla de ender, verde). Las clásicas (PC, Wiki,
  Daycare, Daily, STS y WT) siguen en la config pero **desactivadas**; en configs ya existentes no
  se toca nada
- **Fila admin en dos filas**: al superar **5 pestañas admin** (configurable con el nuevo campo
  **`admin.maxPerRow`**, de 1 a 8), el resto ya no sigue apilándose hacia el centro sino que forma
  una **segunda fila hacia el interior de la pantalla** (encima de la primera en las esquinas
  inferiores, debajo en las superiores); el toggle admin se aparta solo para no solaparse
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
