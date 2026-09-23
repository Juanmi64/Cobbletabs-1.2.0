package com.cobbletabs.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Presets de pestañas: conjuntos de configuración con nombre que se pueden
 * guardar, borrar, sobrescribir, cargar, exportar e importar (portapapeles)
 * desde la pantalla de presets. Hay dos tipos:
 * <ul>
 *   <li><b>Integrados</b> (BUILT_IN): van dentro del mod y no se pueden borrar.</li>
 *   <li><b>Guardados</b>: archivos JSON en config/cobbletabs/presets/&lt;nombre&gt;.json,
 *       creados desde la pantalla de presets (el JSON actual o el preset Pokegalaxia).</li>
 * </ul>
 */
public final class CobbleTabsPresets {

	/** Subcarpeta de presets dentro de la carpeta del mod (config/cobbletabs/presets). */
	public static final String DIR_NAME = "presets";

	/** Gson para escribir archivos de preset (pretty-printed). */
	private static final Gson SAVE_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Gson para leer archivos de preset (tolerante). */
	private static final Gson LOAD_GSON = new Gson();

	private CobbleTabsPresets() {
	}

	/** Un preset: nombre + listas de pestañas que reemplazan las de la config + color propio. */
	public record Preset(String name, List<CobbleTabsConfig.TabEntry> tabs,
			List<CobbleTabsConfig.TabEntry> adminTabs, boolean builtIn, String color) {
		/** Normaliza el color: null → "" (sin color, se muestra con el de su tipo). */
		public Preset {
			if (color == null) {
				color = "";
			}
		}

		/** true si el preset usa el color por defecto de su tipo (integrado cian / guardado blanco). */
		public boolean colorIsDefault() {
			return color.isBlank() || colorIsDefault.equals(color.trim().toLowerCase());
		}

		/** Devuelve una copia con otro color (los presets son inmutables). */
		public Preset withColor(String newColor) {
			return new Preset(name, tabs, adminTabs, builtIn, newColor == null ? "" : newColor);
		}
	}

	// ==================================================================
	// Presets integrados
	// ==================================================================

	/** Valor especial de color: "por defecto" (integrado cian, guardado blanco). */
	public static final String colorIsDefault = "default";

	/** Preset por defecto del mod (pestañas clásicas + huecos extra + admin por defecto). */
	public static Preset defaultPreset() {
		return new Preset("default", CobbleTabsConfig.defaultTabs(), CobbleTabsConfig.defaultAdminTabs(), true, "");
	}

	/** Preset Pokegalaxia: menu, wiki, GTS, AH, Warps, STS, WT y Balance. */
	public static Preset pokegalaxiaPreset() {
		List<CobbleTabsConfig.TabEntry> tabs = new ArrayList<>();
		tabs.add(entry("menu", "/menu", "minecraft:compass", "Menu", "#55FFFF", true));
		tabs.add(entry("wiki", "/wiki", "cobblemon:pokedex_red", "Wiki", "#FF5555", true));
		tabs.add(entry("gts", "/gts", "minecraft:ender_chest", "GTS", "#FF55FF", true));
		tabs.add(entry("ah", "/ah", "minecraft:gold_ingot", "AH", "#FFAA00", true));
		tabs.add(entry("warps", "/warps", "minecraft:ender_pearl", "Warps", "#55FF55", true));
		tabs.add(entry("sts", "/sts", "cobblemon:verdant_ball", "STS", "#FFFFFF", true));
		tabs.add(entry("wt", "/wt", "cobblemon:premier_ball", "WT", "#FFFF55", true));
		tabs.add(entry("balance", "/balance", "minecraft:emerald", "Balance", "#50C878", true));
		return new Preset("pokegalaxia", tabs, new ArrayList<>(), true, "");
	}

	/** Devuelve un preset integrado por su nombre, o null. */
	public static Preset builtInByName(String name) {
		for (Preset p : builtInPresets()) {
			if (p.name().equals(name)) {
				return p;
			}
		}
		return null;
	}

	/** Lista de presets integrados (no se pueden borrar ni sobrescribir). */
	public static List<Preset> builtInPresets() {
		List<Preset> list = new ArrayList<>();
		list.add(defaultPreset());
		list.add(pokegalaxiaPreset());
		return list;
	}

	// ==================================================================
	// Presets guardados como archivos
	// ==================================================================

	/** Carpeta de presets del usuario: config/cobbletabs/presets/. */
	public static Path dir() {
		Path d = CobbleTabsConfig.configDir().resolve(DIR_NAME);
		migrateFromLegacy(d);
		return d;
	}

	/** Migra una vez los presets de la carpeta antigua (config/cobbletabs_presets). */
	private static void migrateFromLegacy(Path target) {
		if (migrated) {
			return;
		}
		migrated = true;
		Path legacy = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cobbletabs_presets");
		if (!Files.isDirectory(legacy)) {
			return;
		}
		try {
			Files.createDirectories(target);
			int moved = 0;
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacy, "*.json")) {
				for (Path file : stream) {
					Path dest = target.resolve(file.getFileName());
					if (!Files.exists(dest)) {
						Files.move(file, dest);
						moved++;
					}
				}
			}
			if (moved > 0) {
				CobbleTabsClient.LOGGER.info("[CobbleTabs] {} preset(s) migrados a {}.", moved, target);
			}
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo migrar la carpeta de presets antigua.", e);
		}
	}

	/** true si ya se intentó la migración de presets (una sola vez por arranque). */
	private static boolean migrated;

	/** Lista los presets guardados en archivos, ordenados alfabéticamente. */
	public static List<Preset> savedPresets() {
		List<Preset> list = new ArrayList<>();
		Path dir = dir();
		if (!Files.isDirectory(dir)) {
			return list;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
			for (Path file : stream) {
				Preset p = loadFile(file);
				if (p != null) {
					list.add(p);
				}
			}
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo leer la carpeta de presets {}.", dir, e);
		}
		list.sort(Comparator.comparing(Preset::name, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	/** Carga un preset desde un archivo; null si es inválido o está roto. */
	private static Preset loadFile(Path file) {
		try {
			SavedPreset saved = LOAD_GSON.fromJson(Files.readString(file), SavedPreset.class);
			if (saved == null || saved.tabs == null || saved.tabs.isEmpty()) {
				return null;
			}
			if (saved.adminTabs == null) {
				saved.adminTabs = new ArrayList<>();
			}
			return new Preset(fileNameToName(file.getFileName().toString()), saved.tabs, saved.adminTabs, false, saved.color);
		} catch (Exception e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] Preset inválido: {}.", file.getFileName(), e);
			return null;
		}
	}

	/** Guarda un preset como archivo &lt;nombre&gt;.json (nombre de archivo seguro). */
	public static boolean save(String name, List<CobbleTabsConfig.TabEntry> tabs,
			List<CobbleTabsConfig.TabEntry> adminTabs) {
		return save(name, tabs, adminTabs, "");
	}

	/** Guarda un preset con color propio ("" o "default" = sin color). */
	public static boolean save(String name, List<CobbleTabsConfig.TabEntry> tabs,
			List<CobbleTabsConfig.TabEntry> adminTabs, String color) {
		String fileName = nameToFileName(name);
		if (fileName.isBlank()) {
			return false;
		}
		try {
			Files.createDirectories(dir());
			SavedPreset saved = new SavedPreset();
			saved.tabs = deepCopy(tabs);
			saved.adminTabs = deepCopy(adminTabs);
			saved.color = color == null ? "" : color.trim().toLowerCase();
			Files.writeString(dir().resolve(fileName + ".json"), SAVE_GSON.toJson(saved));
			return true;
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo guardar el preset {}.", name, e);
			return false;
		}
	}

	/** Borra el archivo de un preset guardado. Devuelve false si falla o no existe. */
	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(dir().resolve(nameToFileName(name) + ".json"));
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo borrar el preset {}.", name, e);
			return false;
		}
	}

	/** true si el nombre de preset ya existe (integrado o guardado). */
	public static boolean exists(String name) {
		if (builtInByName(name) != null) {
			return true;
		}
		return Files.isRegularFile(dir().resolve(nameToFileName(name) + ".json"));
	}

	/** true si el nombre pertenece a un preset integrado (no se puede sobrescribir ni borrar). */
	public static boolean isBuiltIn(String name) {
		return builtInByName(name) != null;
	}

	// ==================================================================
	// Exportar / importar (portapapeles)
	// ==================================================================

	/** Serializa un preset a JSON (mismo formato que los archivos de presets/). */
	public static String exportJson(Preset preset) {
		SavedPreset saved = new SavedPreset();
		saved.tabs = deepCopy(preset.tabs());
		saved.adminTabs = deepCopy(preset.adminTabs());
		saved.color = preset.color().trim().toLowerCase();
		return SAVE_GSON.toJson(saved);
	}

	/**
	 * Parsea un JSON de preset (p. ej. del portapapeles) y lo sanea; null si
	 * no contiene pestañas válidas.
	 */
	public static Preset parseImport(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			SavedPreset saved = LOAD_GSON.fromJson(json.trim(), SavedPreset.class);
			if (saved == null || saved.tabs == null) {
				return null;
			}
			saved.tabs.removeIf(t -> {
				if (t == null) {
					return true;
				}
				CobbleTabsConfig.sanitizeEntry(t);
				return t.id.isBlank() || t.command.isBlank();
			});
			if (saved.adminTabs == null) {
				saved.adminTabs = new ArrayList<>();
			}
			saved.adminTabs.removeIf(t -> {
				if (t == null) {
					return true;
				}
				CobbleTabsConfig.sanitizeEntry(t);
				return t.id.isBlank() || t.command.isBlank();
			});
		if (saved.tabs.isEmpty()) {
			return null;
		}
		return new Preset("import", saved.tabs, saved.adminTabs, false, saved.color);
	} catch (Exception e) {
			CobbleTabsClient.LOGGER.info("[CobbleTabs] El JSON del portapapeles no es un preset válido.");
			return null;
		}
	}

	/** Primer nombre libre parecido a base: base, base2, base3… */
	public static String uniqueName(String base) {
		String clean = nameToFileName(base);
		if (clean.isBlank()) {
			clean = "preset";
		}
		String name = clean;
		int n = 2;
		while (exists(name)) {
			name = clean + n++;
		}
		return name;
	}

	// ==================================================================
	// Aplicación a la config
	// ==================================================================

	/** Reemplaza las pestañas de la config por las del preset, guarda y reconstruye. */
	public static void apply(Preset preset) {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		cfg.applyPreset(deepCopy(preset.tabs()), deepCopy(preset.adminTabs()));
		CobbleTabsClient.rebuildTabs();
	}

	// ==================================================================
	// Utilidades
	// ==================================================================

	/** Copia profunda de una lista de pestañas (para no tocar el preset original). */
	public static List<CobbleTabsConfig.TabEntry> deepCopy(List<CobbleTabsConfig.TabEntry> tabs) {
		List<CobbleTabsConfig.TabEntry> out = new ArrayList<>();
		if (tabs == null) {
			return out;
		}
		for (CobbleTabsConfig.TabEntry t : tabs) {
			out.add(t.copy());
		}
		return out;
	}

	/** Nombre del preset → nombre de archivo seguro (minúsculas, sin espacios ni símbolos). */
	static String nameToFileName(String name) {
		return name.toLowerCase().trim().replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
	}

	/** Nombre de archivo → nombre del preset (sin la extensión .json). */
	static String fileNameToName(String fileName) {
		return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
	}

	/** Representación serializable de un preset en archivo. */
	private static class SavedPreset {
		public List<CobbleTabsConfig.TabEntry> tabs;
		public List<CobbleTabsConfig.TabEntry> adminTabs;
		/** Color propio del preset (nombre de paleta o #hex); null/""/"default" = color por defecto. */
		public String color;
	}

	private static CobbleTabsConfig.TabEntry entry(String id, String command, String icon, String label,
			String color, boolean enabled) {
		CobbleTabsConfig.TabEntry e = new CobbleTabsConfig.TabEntry();
		e.id = id;
		e.command = command;
		e.icon = icon;
		e.label = label;
		e.color = color;
		e.bold = true;
		e.enabled = enabled;
		return e;
	}

	/** Resumen de una pestaña para el listado de la UI ("Comando → icono"). */
	public static String summaryLine(CobbleTabsConfig.TabEntry t) {
		String label = (t.label == null || t.label.isBlank()) ? t.id : t.label;
		return t.command + " → " + label + " (" + t.icon + ")";
	}

	/** Lista compacta de pestañas para tooltips: "• /pc → PC" por línea. */
	public static String summaryText(List<CobbleTabsConfig.TabEntry> tabs) {
		return tabs.stream().map(CobbleTabsPresets::summaryLine).collect(Collectors.joining("\n• ", "• ", ""));
	}
}
