# CobbleTabs

![CobbleTabs](src/main/resources/assets/cobbletabs/textures/gui/logo.png)

**Creador:** [Juanmi64](https://github.com/Juanmi64)

**Mod creado para Pokegalaxia**, pero se pueden usar en otros servers.

Mod **cliente** para **Minecraft 1.21.1 (Fabric)** que añade pestañas en los laterales del inventario para ejecutar comandos con un solo clic. Pensado para servidores con **Cobblemon** y plugins de comandos personalizados (Cobblemon es opcional: funciona en cualquier server).

## ✨ Características

- **Pestañas laterales** en la GUI del inventario: hasta **5 por lado** (reparto automático según el número de pestañas)
- **Editor de pestañas dentro del juego** (botón de libro y pluma sobre el inventario): añade, edita, reordena y borra pestañas sin tocar el JSON — comando, icono, texto, color (incluido **RGB personalizado**) y negrita
- **Presets**: guarda tu configuración actual como preset con nombre, genera presets nuevos, cárgalos con un clic, sobrescríbelos, elimínalos y **compártelos** copiando/pegando su JSON al portapapeles (incluye plantillas integradas "default" y "pokegalaxia"). Con **panel de detalles**: contenido del preset, acciones y **color propio por preset**
- **4 huecos extra** de pestaña (2 por lado), desactivados por defecto: actívalos desde la config para añadir más comandos de acceso rápido sin tocar código
- **Pestañas de administración opcionales** (gamemode y comandos de staff): fila en una **esquina de la pantalla** (configurable: las 4 esquinas posibles), fuera del inventario, desactivadas por defecto y ampliables con tantas pestañas como necesites
- **Botón propio para ocultar/mostrar la fila admin**, con estado persistente e independiente del toggle de las pestañas laterales
- Un clic (izquierdo o derecho) ejecuta el comando de la pestaña al instante, sin escribir en el chat
- **Nombres en negrita y a color** (paleta rápida o **RGB personalizado**), configurables por pestaña
- **Logo discreto** (marca de agua) en la esquina superior derecha del inventario del jugador
- **Botón para ocultar/mostrar** todas las pestañas, con estado persistente
- **Archivo de configuración** en JSON: añade, reordena, renombra o desactiva pestañas sin tocar el código
- **Tecla F8** para recargar la configuración sin reiniciar el juego · **Shift+F8** abre el editor (y hay una tecla asignable en Controles)
- No se muestran pestañas en cofres ni enderchests, y el logo solo aparece en tu inventario

## 📦 Instalación

1. Instala el [Fabric Loader](https://fabricmc.net/use/installer/) para Minecraft **1.21.1**
2. Descarga y añade a la carpeta `mods`:
   - [Fabric API](https://modrinth.com/mod/fabric-api) para 1.21.1
   - `cobbletabs-1.2.0.jar` (este mod)
   - [Cobblemon](https://modrinth.com/mod/cobblemon) **(opcional)**: solo hace falta para los iconos de Cobblemon de las pestañas por defecto; sin él, esos iconos se muestran como papel y el mod funciona igual
3. Inicia el juego. La primera vez se generará `config/cobbletabs/cobbletabs.json`

> Requisitos: Java 21, Fabric Loader ≥ 0.16.0 y Fabric API (obligatorios). Cobblemon es opcional (el mod lo declara como sugerencia, no como dependencia).

## 🖱️ Uso

| Acción | Resultado |
|--------|-----------|
| Clic en una pestaña | Ejecuta su comando (ej. `/pc`) |
| Clic en el botón con icono de barrera | Oculta las pestañas |
| Clic en el botón con icono de ojo de ender | Muestra las pestañas |
| **F8** | Recarga la config al instante |
| **Shift+F8** | Abre el **editor de pestañas** desde el juego |
| Tecla asignable **"Abrir editor de pestañas"** (Controles → CobbleTabs) | Abre el editor con la tecla que elijas |
| Clic en el botón de libro y pluma (a la izquierda del toggle, en el inventario survival o creativo) | Abre el **editor de pestañas** dentro del juego |
| Pasar el ratón por una pestaña | Muestra el nombre de la pestaña |

La tecla F8 se puede cambiar en **Opciones → Controles → CobbleTabs → Recargar configuración**, y funciona tanto en el mundo como con el inventario abierto. La tecla **"Abrir editor de pestañas"** está sin asignar por defecto (Shift+F8 ya abre el editor).

> En el **inventario creativo**, el toggle de pestañas y el botón del editor suben automáticamente por encima de la tira de pestañas vanilla para que sigan visibles y clicables.

### Pestañas por defecto

| Pestaña | Comando | Icono | Color |
|---------|---------|-------|-------|
| PC | `/pc` | `cobblemon:pc` | Gris |
| Wiki | `/wiki` | `cobblemon:pokedex_red` | Rojo claro |
| Daycare | `/daycare` | `minecraft:book` | Rosa |
| Daily | `/daily` | `minecraft:clock` | Amarillo |
| STS | `/sts` | `cobblemon:verdant_ball` | Blanco |
| WT | `/wt` | `cobblemon:premier_ball` | Verde |
| Extra 1-4 | (por configurar) | `minecraft:nether_star` | Aqua / Naranja / Morado / Azul |

Las 4 pestañas **extra** vienen **desactivadas**: son huecos listos para que les asignes comando,
nombre e icono desde la config cuando el server añada más comandos.

> Los comandos deben existir en el servidor donde juegas (por ejemplo, vía plugins). Si el servidor no los tiene, verás el error estándar de comando desconocido.

## 📝 Editor dentro del juego

En tu inventario (survival **y creativo**) hay un botoncito de **libro y pluma** (a la izquierda del botón de ocultar pestañas) que abre el editor de CobbleTabs. Desde ahí, sin salir del juego ni editar JSON:

- **Añadir** una pestaña nueva y editar las existentes: **comando**, **icono** (id de item, con previsualización), **texto**, **color** — paleta rápida o **escribe tu RGB hex `#RRGGBB` (o un nombre) en el campo de color**, con vista previa en vivo — y **negrita**
- **Activar/desactivar** una pestaña sin borrarla (toggle Sí/No en el diálogo)
- **Reordenar arrastrando** el icono de cada fila (el orden de la lista es el orden en pantalla)
- **Borrar** una pestaña con la ✕ del diálogo (los huecos extra/admin se desactivan en vez de borrarse)
- Cambiar entre la lista de **pestañas** normales y las **admin** con el primer botón
- **Fila admin: Sí/No**: activa o desactiva la fila de pestañas admin sin tocar el JSON (al activarla, se encienden las pestañas admin integradas para que se vea algo al instante)
- **Esquina**: abre un menú desplegable con las **4 esquinas de la pantalla** (la activa resaltada) y aplica la que elijas
- **Por defecto**: restaura todas las pestañas a sus valores originales (con confirmación)
- Todo se guarda al momento en `config/cobbletabs/cobbletabs.json` y se aplica al instante

También se puede desactivar el botón con `"showEditButton": false` en la config.

## 🗂️ Presets

Desde el editor, el botón **Presets** abre la pantalla de presets: conjuntos de pestañas con nombre que puedes **crear, generar, cargar, sobrescribir, eliminar, importar y exportar**.

La pantalla tiene dos columnas: a la izquierda la **lista de presets** (integrados en cian, guardados en blanco, con su número de pestañas) y a la derecha el **panel de detalles** del preset seleccionado.

- **Selecciona un preset** con clic en su fila (o **↑/↓**): el panel de la derecha muestra su **contenido completo** (comando → nombre e icono, las admin marcadas) y sus acciones
- **Cargar**: botón del panel de detalles (pide confirmación, porque reemplaza tus pestañas actuales)
- **Crear un preset**: escribe un nombre y pulsa **Guardar actual** → se guarda la config tal cual está en `config/cobbletabs/presets/<nombre>.json`
- **Exportar** (botón del panel): copia su JSON al **portapapeles**, listo para pegarlo en Discord, un bloc de notas o donde quieras
- **Sobrescribir** (solo guardados): reemplaza el archivo del preset por tu config actual (con confirmación)
- **Eliminar** (solo guardados): borra su archivo (con confirmación; los integrados no se borran)
- **Importar**: botón **Importar** → guarda como preset el JSON de preset que haya en el portapapeles (con confirmación; el nombre se elige solo y, si ya existe, se numera `nombre2`, `nombre3`…). También puedes pulsar **Ctrl+V** en la pantalla con un JSON en el portapapeles
- **Color del preset**: en el panel de detalles hay una **paleta** para darle un color propio (su nombre en la lista y cabecera se pintan de ese color; en los guardados se guarda en su JSON, los integrados lo conservan solo durante la sesión; la opción gris con raya "sin color" vuelve al color por defecto del tipo)
- Si el preset trae pestañas admin, la fila admin se activa sola

### Plantillas integradas

| Preset | Pestañas |
|--------|----------|
| `default` | Las clásicas del mod: PC, Wiki, Daycare, Daily, STS, WT (+ los 4 extras desactivados) |
| `pokegalaxia` | Menu, Wiki, GTS, AH, Warps, STS, WT y Balance |

> La plantilla **pokegalaxia** pone los comandos `/menu`, `/wiki`, `/gts`, `/ah`, `/warps`, `/sts`, `/wt` y `/balance`. Si tu server usa otros nombres, cámbialos luego desde el editor o la config.

## ⚙️ Configuración

Carpeta del mod: `config/cobbletabs/`

```
config/cobbletabs/
├── cobbletabs.json      ← configuración (pestañas, admin, logo…)
└── presets/             ← tus presets (<nombre>.json)
```

> ¿Venías de una versión anterior? El archivo `config/cobbletabs.json` y la carpeta
> `config/cobbletabs_presets/` se **migran solos** a la nueva ubicación al arrancar
> (el archivo antiguo se renombra a `cobbletabs.json.old`).

Edítalo con el juego cerrado **o** edítalo y pulsa **F8** para aplicarlo al momento.

```jsonc
{
  // Muestra el botón de libro y pluma que abre el editor de pestañas en el juego
  "showEditButton": true,

  // Lista de pestañas: el orden en el archivo es el orden en pantalla.
  // La primera mitad se coloca a la izquierda y el resto a la derecha.
  "tabs": [
    {
      "id": "pc",              // identificador interno (único, sin espacios)
      "command": "/pc",        // comando a ejecutar (se añade "/" si falta)
      "icon": "cobblemon:pc",  // item usado como icono (cualquier mod)
      "enabled": true,         // false = pestaña oculta sin borrarla
      "label": "PC",           // nombre mostrado (vacío = traducción por defecto)
      "color": "gray",         // color del nombre (ver tabla de colores)
      "bold": true             // nombre en negrita
    },
    { "id": "wiki",    "command": "/wiki",    "icon": "cobblemon:pokedex_red",  "enabled": true, "label": "Wiki",    "color": "light_red", "bold": true },
    { "id": "daycare", "command": "/daycare", "icon": "minecraft:book",         "enabled": true, "label": "Daycare", "color": "pink",      "bold": true },
    { "id": "daily",   "command": "/daily",   "icon": "minecraft:clock",        "enabled": true, "label": "Daily",   "color": "yellow",    "bold": true },
    { "id": "sts",     "command": "/sts",     "icon": "cobblemon:verdant_ball", "enabled": true, "label": "STS",     "color": "white",     "bold": true },
    { "id": "wt",      "command": "/wt",      "icon": "cobblemon:premier_ball", "enabled": true, "label": "WT",      "color": "green",     "bold": true },

    // Huecos extra (2 más por lado): desactivados hasta que les pongas comando,
    // nombre e icono. Pon "enabled": true para mostrarlos.
    { "id": "extra1", "command": "/extra1", "icon": "minecraft:nether_star", "enabled": false, "label": "", "color": "", "bold": true },
    { "id": "extra2", "command": "/extra2", "icon": "minecraft:nether_star", "enabled": false, "label": "", "color": "", "bold": true },
    { "id": "extra3", "command": "/extra3", "icon": "minecraft:nether_star", "enabled": false, "label": "", "color": "", "bold": true },
    { "id": "extra4", "command": "/extra4", "icon": "minecraft:nether_star", "enabled": false, "label": "", "color": "", "bold": true }
  ],

  // Logo del inventario
  "logo": {
    "enabled": true,  // mostrar u ocultar el logo
    "size": 44,       // tamaño en "píxeles de GUI" (16-256); escala con la Escala de GUI de Minecraft
    "opacity": 50     // opacidad 0-100 (50 = marca de agua sutil, 100 = opaco)
  },

  "showToggleButton": true, // false = quita el botón de ocultar/mostrar pestañas
  "tabsVisible": true,      // estado actual del botón (se guarda solo, no hace falta tocarlo)

  // Pestañas de administración: fila en la esquina inferior derecha de la PANTALLA
  // (fuera del inventario). Desactivadas por defecto.
  "admin": {
    "enabled": false,           // true = activa la fila de pestañas admin
    "showToggleButton": true,   // false = quita el botón de ocultar/mostrar la fila admin
    "visible": true,            // estado actual del botón admin (se guarda solo)
    "corner": "bottom_right"    // esquina de la pantalla: bottom_right, bottom_left, top_right, top_left
  },
  "adminTabs": [
    // Mismas opciones que una pestaña normal (id, command, icon, enabled, label, color, bold).
    // Se colocan de derecha a izquierda: la primera queda más a la esquina.
    { "id": "admin_survival",   "command": "/gamemode survival",   "icon": "minecraft:grass_block",   "enabled": false, "label": "Survival",   "color": "green", "bold": true },
    { "id": "admin_creative",   "command": "/gamemode creative",   "icon": "minecraft:command_block", "enabled": false, "label": "Creative",   "color": "yellow", "bold": true },
    { "id": "admin_spectator",  "command": "/gamemode spectator",  "icon": "minecraft:ender_pearl",   "enabled": false, "label": "Spectator",  "color": "gray", "bold": true }

    // ¿Necesitas más comandos de staff? Añade más entradas a adminTabs y se
    // apilan hacia la izquierda solas:
    // , { "id": "admin_heal", "command": "/heal", "icon": "minecraft:golden_apple", "enabled": true, "label": "Heal", "color": "#FFAA00", "bold": true }
  ]
}
```

### Colores

El campo `color` acepta:

| Nombre (inglés) | Nombre (español) | Valor |
|-----------------|------------------|-------|
| `gray` | `gris` | `#AAAAAA` |
| `light_red` / `red` | `rojo_claro` / `rojo` | `#FF5555` |
| `pink` | `rosa` | `#FF9FDB` |
| `yellow` | `amarillo` | `#FFFF55` |
| `white` | `blanco` | `#FFFFFF` |
| `green` | `verde` | `#55FF55` |
| `#RRGGBB` | — | cualquier color **RGB en hexadecimal** (ej. `#00BFFF`) |

Si dejas `color` vacío o escribes un valor inválido, la pestaña usa su color clásico por defecto (PC gris, Wiki rojo claro, Daycare rosa, Daily amarillo, STS blanco, WT verde; extras: aqua, naranja, morado claro y azul).

### Ejemplos útiles

**Activar un hueco extra** (por ejemplo, el Extra 1 para un comando `/tienda`):
```json
{ "id": "extra1", "command": "/tienda", "icon": "minecraft:emerald", "enabled": true, "label": "Tienda", "color": "#50C878", "bold": true }
```

**Añadir una pestaña nueva totalmente personalizada** (se suma a las 10 y se reparte sola):
```json
{ "id": "tienda", "command": "/tienda", "icon": "minecraft:emerald", "enabled": true, "label": "Tienda", "color": "#50C878", "bold": true }
```

**Desactivar una pestaña sin borrarla:**
```json
{ "id": "sts", "command": "/sts", "icon": "cobblemon:verdant_ball", "enabled": false, "label": "STS", "color": "white", "bold": true }
```

**Quitar el logo y el botón** (mod mínimo):
```json
"logo": { "enabled": false, "size": 44, "opacity": 50 },
"showToggleButton": false
```

Si el archivo está roto o incompleto, el mod avisa en el log y regenera los valores por defecto; las pestañas clásicas recuperan su color por defecto automáticamente.

### Pestañas de administración

Fila aparte en una **esquina de la pantalla**, fuera de la GUI del inventario, pensada para comandos de staff (`/gamemode`, `/heal`, `/vanish`…):

- Se activan con `"admin": { "enabled": true }` **y** `"enabled": true` en cada pestaña que quieras ver — o directamente desde el **editor ingame** con el botón **"Fila admin: Sí/No"**, que las enciende solas
- Por defecto hay 3: **Survival**, **Creative** y **Spectator** (todas desactivadas)
- Puedes **añadir cuantas quieras** añadiendo entradas al array `adminTabs`; se apilan hacia la izquierda
- Usan las mismas opciones que una pestaña normal (`command`, `icon`, `label`, `color`, `bold`)
- Al hacer clic ejecutan el comando y cierran el inventario, igual que las demás
- **Esquina configurable** con `admin.corner`: `bottom_right` (por defecto), `bottom_left`, `top_right` o `top_left`. También en español: `abajo_derecha`, `abajo_izquierda`, `arriba_derecha`, `arriba_izquierda` (o abreviado `abajo_izq`, `sup_der`…). Si otro mod tapa la esquina, mueve el conjunto a otra — desde el **editor ingame** con el botón **"Esquina"**, un desplegable con las 4 opciones
- **Botón toggle propio**: un botoncito junto a la fila (icono de barrera = visible, ojo de ender = oculta) que muestra/oculta solo la fila admin; se guarda en `admin.visible` y es independiente del toggle de las pestañas laterales. Se puede quitar con `"showToggleButton": false` dentro de `admin`
- No aparecen en cofres ni enderchests

> **Migración automática:** si tu config es de una versión anterior, las 3 pestañas admin se añaden solas al final, desactivadas, sin tocar tus pestañas existentes. También con **F8**.

> **Migración automática:** si tu config es de una versión anterior (sin los 4 huecos extra), el mod
> los añade solos al final del array `tabs` la primera vez que carga, desactivados y sin tocar tus
> pestañas existentes. También con **F8**.

## 🛠️ Para desarrolladores

```bash
# Compilar el jar (requiere JDK 21)
./gradlew build          # resultado en build/libs/cobbletabs-1.2.0.jar

# Abrir el juego con el mod cargado (usa Cobblemon real descargado del Maven)
./gradlew runClient
```

- **Lenguaje:** Java 21 (sin mixins; usa eventos de Fabric API y un access widener mínimo)
- **Config:** Gson (incluido en Minecraft, sin dependencias extra)
- **Regenerar el logo:** `java -cp tools Resize <entrada> <salida> <tamaño> [relleno%]`

## 👤 Créditos

- **Creador:** [Juanmi64](https://github.com/Juanmi64)
- **Creado para:** Pokegalaxia (usable en cualquier server con Cobblemon)

## 📄 Licencia

CC0-1.0. Cobblemon es una propiedad de sus respectivos autores; este mod no está afiliado a ellos.
