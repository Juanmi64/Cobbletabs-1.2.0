package com.cobbletabs.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración del mod, guardada en config/cobbletabs.json.
 * Se crea con los valores por defecto la primera vez que se inicia el juego.
 */
public class CobbleTabsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Carpeta base del mod dentro de config/ (aquí viven la config y los presets). */
	public static final String DIR_NAME = "cobbletabs";

	/** Nombre del archivo de configuración dentro de la carpeta del mod. */
	public static final String FILE_NAME = "cobbletabs.json";

	/** Ruta final de la configuración: config/cobbletabs/cobbletabs.json. */
	private static final Path PATH = configDir().resolve(FILE_NAME);

	/** Carpeta base del mod: config/cobbletabs/. */
	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(DIR_NAME);
	}

	/**
	 * Migra la config antigua (config/cobbletabs.json) a la nueva ubicación
	 * (config/cobbletabs/cobbletabs.json). Se ejecuta una vez por arranque;
	 * el archivo antiguo se renombra a .old para que no se re-migre si borras el nuevo.
	 */
	private static void migrateFromLegacy() {
		Path legacy = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		if (!Files.isRegularFile(legacy) || Files.exists(PATH)) {
			return;
		}
		try {
			Files.createDirectories(PATH.getParent());
			Files.copy(legacy, PATH);
			Files.move(legacy, legacy.resolveSibling(FILE_NAME + ".old"));
			CobbleTabsClient.LOGGER.info("[CobbleTabs] Configuración migrada a {} (anterior guardada como {}).", PATH, legacy.getFileName() + ".old");
		} catch (Exception e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo migrar la configuración antigua a {}.", PATH, e);
		}
	}

	/**
	 * Lista de pestañas; edita este archivo para añadir, quitar o reordenar.
	 * Se reparten en los laterales de la GUI: la primera mitad a la izquierda,
	 * el resto a la derecha (máximo 5 por lado; con 10 pestañas: 5 a cada lado).
	 */
	public List<TabEntry> tabs = defaultTabs();

	/**
	 * Pestañas de administración (comandos de staff como /gamemode), dibujadas en
	 * una fila en la esquina inferior derecha de la pantalla, fuera del inventario.
	 */
	public AdminOptions admin = new AdminOptions();

	/** Pestañas de administración; mismas opciones que las normales (TabEntry). */
	public List<TabEntry> adminTabs = defaultAdminTabs();

	/** Opciones del logo del inventario. */
	public LogoOptions logo = new LogoOptions();

	/** Muestra un botoncito para ocultar/mostrar las pestañas. */
	public boolean showToggleButton = true;

	/** Estado actual de visibilidad de las pestañas (se guarda al alternar). */
	public boolean tabsVisible = true;

	/** Muestra el botón de libro y pluma que abre el editor de pestañas en el juego. */
	public boolean showEditButton = true;

	public static class LogoOptions {
		/** Mostrar el logo en el inventario del jugador. */
		public boolean enabled = true;
		/** Tamaño en "píxeles de GUI" (44 = discreto; se ajusta a la Escala de GUI). */
		public int size = 44;
		/** Opacidad del logo: 0-100 (50 = marca de agua sutil). */
		public int opacity = 50;
	}

	/** Opciones de la fila de pestañas de administración. */
	public static class AdminOptions {
		/** Activa la fila de pestañas admin (esquina inferior derecha de la pantalla). */
		public boolean enabled = false;
		/** Muestra un botoncito para ocultar/mostrar la fila admin. */
		public boolean showToggleButton = true;															/** Estado actual de visibilidad de la fila admin (se guarda al alternar). */
		public boolean visible = true;
		/**
		 * Esquina de la pantalla donde se dibuja el conjunto admin (fila + botón toggle).
		 * Valores admitidos (inglés o español): "bottom_right"/"abajo_derecha" (por defecto),
		 * "bottom_left"/"abajo_izquierda", "top_right"/"arriba_derecha", "top_left"/"arriba_izquierda".
		 */
		public String corner = "bottom_right";
	}

	public static class TabEntry {
		/** Identificador interno (usado para la clave de traducción cobbletabs.tab.<id>). */
		public String id = "";
		/** Comando que se ejecuta al hacer clic, por ejemplo "/pc". */
		public String command = "/";
		/** Item mostrado como icono, por ejemplo "cobblemon:pc". */
		public String icon = "minecraft:paper";
		/** true = la pestaña se muestra, false = oculta. */
		public boolean enabled = true;
		/** Texto del tooltip (opcional). Si se deja vacío usa la traducción o el comando. */
		public String label = "";
		/** Color del nombre: nombre (gray, yellow, green, white, light_red, pink...) o hex #RRGGBB. Vacío = color por defecto. */
		public String color = "";
		/** Nombre en negrita. */
		public boolean bold = true;

		/** Copia profunda de la entrada (para presets). */
		public TabEntry copy() {
			TabEntry c = new TabEntry();
			c.id = id;
			c.command = command;
			c.icon = icon;
			c.label = label;
			c.color = color;
			c.bold = bold;
			c.enabled = enabled;
			return c;
		}
	}

	public static CobbleTabsConfig load() {
		migrateFromLegacy();
		if (Files.exists(PATH)) {
			try {
				CobbleTabsConfig cfg = GSON.fromJson(Files.readString(PATH), CobbleTabsConfig.class);
				if (cfg != null) {
					if (cfg.tabs == null) {
						cfg.tabs = defaultTabs();
					}
					if (cfg.logo == null) {
						cfg.logo = new LogoOptions();
					}
					if (cfg.admin == null) {
						cfg.admin = new AdminOptions();
					}
					if (cfg.adminTabs == null) {
						cfg.adminTabs = defaultAdminTabs();
					}
					cfg.ensureExtraSlots();
					cfg.ensureAdminTabs();
					cfg.sanitize();
					return cfg;
				}
			} catch (Exception e) {
				CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo leer {}: usando valores por defecto.", PATH.getFileName(), e);
			}
		}
		CobbleTabsConfig cfg = new CobbleTabsConfig();
		cfg.sanitize();
		save(cfg);
		return cfg;
	}

	public static void save(CobbleTabsConfig cfg) {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(cfg));
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo guardar la configuración.", e);
		}
	}

	/** Normaliza los datos: ignora entradas inválidas y añade el "/" al comando si falta. */
	public void sanitize() {
		if (logo.size < 16) {
			logo.size = 16;
		} else if (logo.size > 256) {
			logo.size = 256;
		}
		if (logo.opacity < 0) {
			logo.opacity = 0;
		} else if (logo.opacity > 100) {
			logo.opacity = 100;
		}
		if (admin.corner == null) {
			admin.corner = "bottom_right";
		}
		admin.corner = normalizeCorner(admin.corner);
		tabs = sanitizeTabList(tabs);
		adminTabs = sanitizeTabList(adminTabs);
	}

	/** Normaliza una sola pestaña: trim, "/" inicial, icono y label por defecto. */
	public static void sanitizeEntry(TabEntry t) {
		if (t == null) {
			return;
		}
		if (t.id == null) {
			t.id = "";
		}
		if (t.command == null) {
			t.command = "";
		}
		t.command = t.command.trim();
		if (!t.command.isEmpty() && !t.command.startsWith("/")) {
			t.command = "/" + t.command;
		}
		if (t.icon == null || t.icon.isBlank()) {
			t.icon = "minecraft:paper";
		}
		if (t.label == null) {
			t.label = "";
		}
		if (t.color == null) {
			t.color = "";
		}
	}

	/** Busca una pestaña por id en una lista (null si no existe). */
	public static TabEntry findTabById(List<TabEntry> list, String id) {
		return findTabIn(list, id);
	}

	/** Convierte una esquina escrita en inglés o español a su valor canónico inglés. */
	public static String normalizeCorner(String s) {
		if (s == null) {
			return "bottom_right";
		}
		return switch (s.trim().toLowerCase().replace(" ", "_")) {
			case "top_left", "arriba_izquierda", "arriba_izq", "sup_izq" -> "top_left";
			case "top_right", "arriba_derecha", "arriba_der", "sup_der" -> "top_right";
			case "bottom_left", "abajo_izquierda", "abajo_izq", "inf_izq" -> "bottom_left";
			default -> "bottom_right";
		};
	}

	/** Normaliza una lista de pestañas: descarta entradas inválidas y añade el "/" al comando. */
	private static List<TabEntry> sanitizeTabList(List<TabEntry> list) {
		if (list == null) {
			return new ArrayList<>();
		}
		List<TabEntry> clean = new ArrayList<>();
		for (TabEntry t : list) {
			if (t == null || t.id == null || t.id.isBlank() || t.command == null || t.command.isBlank()) {
				continue;
			}
			t.id = t.id.trim();
			t.command = t.command.trim();
			if (!t.command.startsWith("/")) {
				t.command = "/" + t.command;
			}
			if (t.icon == null || t.icon.isBlank()) {
				t.icon = "minecraft:paper";
			}
			if (t.label == null) {
				t.label = "";
			}
			clean.add(t);
		}
		return clean;
	}

	/** Ids de los 4 huecos extra (2 por lado); se añaden solos a configs antiguas que no los tengan. */
	private static final String[] EXTRA_IDS = { "extra1", "extra2", "extra3", "extra4" };

	/** Ids de las 3 pestañas admin por defecto; se añaden solas a configs que no las tengan. */
	private static final String[] ADMIN_IDS = { "admin_survival", "admin_creative", "admin_spectator" };

	public static List<TabEntry> defaultTabs() {
		List<TabEntry> list = new ArrayList<>();
		// Pestañas clásicas (activadas)
		list.add(entry("pc", "/pc", "cobblemon:pc", "PC", "gray", true));
		list.add(entry("wiki", "/wiki", "cobblemon:pokedex_red", "Wiki", "light_red", true));
		list.add(entry("daycare", "/daycare", "minecraft:book", "Daycare", "pink", true));
		list.add(entry("daily", "/daily", "minecraft:clock", "Daily", "yellow", true));
		list.add(entry("sts", "/sts", "cobblemon:verdant_ball", "STS", "white", true));
		list.add(entry("wt", "/wt", "cobblemon:premier_ball", "WT", "green", true));
		// Huecos extra (2 más por lado): vienen desactivados. Actívalos en
		// config/cobbletabs.json y ponles comando, nombre e icono.
		list.add(entry("extra1", "/extra1", "minecraft:nether_star", "", "", false));
		list.add(entry("extra2", "/extra2", "minecraft:nether_star", "", "", false));
		list.add(entry("extra3", "/extra3", "minecraft:nether_star", "", "", false));
		list.add(entry("extra4", "/extra4", "minecraft:nether_star", "", "", false));
		return list;
	}

	/**
	 * Pestañas admin por defecto (desactivadas): gamemode survival/creative/spectator.
	 * Actívalas con "admin": { "enabled": true } y "enabled": true en cada una, y
	 * añade más entradas a "adminTabs" si necesitas más comandos de staff.
	 */
	public static List<TabEntry> defaultAdminTabs() {
		List<TabEntry> list = new ArrayList<>();
		list.add(entry("admin_survival", "/gamemode survival", "minecraft:grass_block", "Survival", "green", false));
		list.add(entry("admin_creative", "/gamemode creative", "minecraft:command_block", "Creative", "yellow", false));
		list.add(entry("admin_spectator", "/gamemode spectator", "minecraft:ender_pearl", "Spectator", "gray", false));
		return list;
	}

	private static TabEntry entry(String id, String command, String icon, String label, String color, boolean enabled) {
		TabEntry e = new TabEntry();
		e.id = id;
		e.command = command;
		e.icon = icon;
		e.label = label;
		e.color = color;
		e.bold = true;
		e.enabled = enabled;
		return e;
	}

	/**
	 * Garantiza que existan los 4 huecos extra: si la config es antigua y no los
	 * tiene, se añaden al final (desactivados) sin tocar el resto de pestañas.
	 */
	private void ensureExtraSlots() {
		for (TabEntry def : defaultTabs()) {
			if (isExtraSlot(def.id) && findTabIn(tabs, def.id) == null) {
				tabs.add(def);
			}
		}
	}

	/**
	 * Garantiza que existan las 3 pestañas admin por defecto: si la config no las
	 * tiene, se añaden al final (desactivadas) sin tocar las pestañas existentes.
	 */
	/**
	 * Activa la fila admin y, con ella, las pestañas admin integradas que sigan
	 * desactivadas (Survival/Creative/Spectator). Se usa desde el editor, para que
	 * "Activar admin" muestre algo sin tener que activar cada pestaña a mano.
	 */
	public void enableDefaultAdminTabs() {
		admin.enabled = true;
		admin.visible = true;
		for (TabEntry t : adminTabs) {
			if (isAdminTab(t.id)) {
				t.enabled = true;
			}
		}
		sanitize();
		save(this);
	}

	private void ensureAdminTabs() {
		for (TabEntry def : defaultAdminTabs()) {
			if (isAdminTab(def.id) && findTabIn(adminTabs, def.id) == null) {
				adminTabs.add(def);
			}
		}
	}

	private static boolean isAdminTab(String id) {
		for (String admin : ADMIN_IDS) {
			if (admin.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * true para los huecos que mantiene la migración automática (extra1..4 y admin_*):
	 * el editor los desactiva en vez de borrarlos, porque load() los volvería a añadir.
	 */
	public static boolean isProtectedId(String id) {
		return isExtraSlot(id) || isAdminTab(id);
	}

	private static boolean isExtraSlot(String id) {
		for (String extra : EXTRA_IDS) {
			if (extra.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static TabEntry findTabIn(List<TabEntry> list, String id) {
		for (TabEntry t : list) {
			if (t != null && t.id != null && t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Reemplaza las pestañas con las de un preset y guarda la config.
	 * Si el preset trae pestañas admin, activa la fila admin para que se vean.
	 */
	public void applyPreset(List<TabEntry> newTabs, List<TabEntry> newAdminTabs) {
		tabs = new ArrayList<>(newTabs);
		// Un preset solo sustituye las pestañas admin si trae alguna; si no, se
		// conservan las actuales (cargar "pokegalaxia" no borra tu fila admin).
		if (newAdminTabs != null && !newAdminTabs.isEmpty()) {
			adminTabs = new ArrayList<>(newAdminTabs);
		}
		// La fila admin se activa solo si hay alguna pestaña admin activada
		boolean anyAdminEnabled = false;
		for (TabEntry t : adminTabs) {
			if (t.enabled) {
				anyAdminEnabled = true;
				break;
			}
		}
		admin.enabled = anyAdminEnabled;
		admin.visible = true;
		sanitize();
		save(this);
	}

	/** Restaura todas las pestañas (normales y admin) a sus valores por defecto. */
	public void resetToDefaults() {
		tabs = defaultTabs();
		adminTabs = defaultAdminTabs();
		sanitize();
		save(this);
	}

	/**
	 * Restaura una pestaña integrada (clásica, extra o admin) a sus valores por
	 * defecto. Devuelve false si el id no corresponde a una de ellas.
	 */
	public boolean resetDefaultTab(String id) {
		TabEntry def = findDefault(id);
		TabEntry target = findTabIn(id.startsWith("admin_") ? adminTabs : tabs, id);
		if (def == null || target == null) {
			return false;
		}
		target.command = def.command;
		target.icon = def.icon;
		target.label = def.label;
		target.color = def.color;
		target.bold = def.bold;
		target.enabled = def.enabled;
		return true;
	}

	/** true si esa pestaña integrada tiene valores por defecto (se puede resetear desde el editor). */
	public boolean hasDefaultTab(String id) {
		return findDefault(id) != null;
	}

	/** Valores por defecto de una pestaña integrada por id, o null si no existe. */
	private TabEntry findDefault(String id) {
		for (TabEntry t : defaultTabs()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		for (TabEntry t : defaultAdminTabs()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** Colores por defecto de las pestañas clásicas, usado para migrar configs antiguas. */
	public static int defaultColorFor(String id) {
		if (id == null) {
			return 0xFFFFFF;
		}
		return switch (id) {
			case "pc" -> 0xAAAAAA;      // gris
			case "wiki" -> 0xFF5555;    // rojo claro
			case "daycare" -> 0xFF9FDB; // rosa
			case "daily" -> 0xFFFF55;   // amarillo
			case "sts" -> 0xFFFFFF;     // blanco
			case "wt" -> 0x55FF55;      // verde
			case "extra1" -> 0x55FFFF;  // aqua
			case "extra2" -> 0xFFAA00;  // naranja
			case "extra3" -> 0xFF55FF;  // morado claro
			case "extra4" -> 0x5555FF;  // azul
			case "admin_survival" -> 0x55FF55;   // verde
			case "admin_creative" -> 0xFFFF55;   // amarillo
			case "admin_spectator" -> 0xAAAAAA;  // gris
			default -> 0xFFFFFF;
		};
	}

/**
	 * Paleta de colores rápida compartida por el editor y la pantalla de presets:
	 * clave de config (o null = "sin color"), valor #RRGGBB y clave de traducción.
	 * parseColor() acepta todas estas claves, así que la config guarda la clave
	 * (más legible que el hex).
	 */
	public record PaletteEntry(String key, String hex) {
		/** Clave de traducción del nombre del color ("cobbletabs.color.<key>" o "sin_color" si key==null). */
		public String langKey() {
			return "cobbletabs.color." + (key == null ? "sin_color" : key);
		}
	}

	public static final List<PaletteEntry> PALETTE = List.of(
			new PaletteEntry(null, "#2A2A34"),
			new PaletteEntry("gray", "#AAAAAA"),
			new PaletteEntry("light_red", "#FF5555"),
			new PaletteEntry("pink", "#FF9FDB"),
			new PaletteEntry("yellow", "#FFFF55"),
			new PaletteEntry("white", "#FFFFFF"),
			new PaletteEntry("green", "#55FF55"),
			new PaletteEntry("aqua", "#55FFFF"),
			new PaletteEntry("orange", "#FFAA00"),
			new PaletteEntry("purple", "#FF55FF"),
			new PaletteEntry("blue", "#5555FF")
	);

	/** Índice en PALETTE del color de la pestaña (0 = sin color), o -1 si es hex/nombre fuera de paleta. */
	public static int paletteIndexOf(String color) {
		if (color == null || color.isBlank()) {
			return 0;
		}
		String c = color.trim().toLowerCase();
		for (int i = 0; i < PALETTE.size(); i++) {
			if (c.equals(PALETTE.get(i).key())) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Convierte un color de la config a RGB. Acepta nombres en inglés y español
	 * (gray/gris, yellow/amarillo, green/verde, white/blanco, red/rojo,
	 * light_red/rojo_claro, pink/rosa) o hex "#RRGGBB". Devuelve fallback si es
	 * inválido (fallback -1 = sin color personalizado).
	 */
	public static int parseColor(String s, int fallback) {
		if (s == null || s.isBlank()) {
			return fallback;
		}
		try {
			String c = s.trim().toLowerCase();
			return switch (c) {
				case "gray", "gris" -> 0xAAAAAA;
				case "yellow", "amarillo" -> 0xFFFF55;
				case "green", "verde" -> 0x55FF55;
				case "white", "blanco" -> 0xFFFFFF;
				case "light_red", "rojo_claro", "rojo claro", "red", "rojo" -> 0xFF5555;
				case "pink", "rosa" -> 0xFF9FDB;
				case "aqua", "cian", "turquesa" -> 0x55FFFF;
				case "orange", "naranja" -> 0xFFAA00;
				case "purple", "morado", "lila", "magenta" -> 0xFF55FF;
				case "blue", "azul" -> 0x5555FF;
				default -> c.startsWith("#") ? Integer.parseInt(c.substring(1), 16) : fallback;
			};
		} catch (Exception e) {
			return fallback;
		}
	}
}
