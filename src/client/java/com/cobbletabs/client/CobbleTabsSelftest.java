package com.cobbletabs.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-test de desarrollo (no forma parte de la funcionalidad del mod):
 * se activa SOLO si existe el archivo flag config/cobbletabs_selftest.flag,
 * que crea ./gradlew runClient -Pselftest=true. Ejercita la lógica de presets
 * y las pantallas de editor/presets simulando clics reales, escribe el
 * resultado en config/cobbletabs_selftest_result.txt y cierra el juego.
 *
 * <p>Diseño: cada paso se reintenta hasta 300 ticks (los pasos de acción
 * comprueban su efecto y los de transición se auto-reparan, p. ej. reabriendo
 * el inventario si la pausa por pérdida de foco lo cerró). Los clics que
 * alternan estado (toggles) se ejecutan una sola vez.</p>
 */
final class CobbleTabsSelftest {
	private static final String FLAG_NAME = "cobbletabs_selftest.flag";
	private static final String RESULT_NAME = "cobbletabs_selftest_result.txt";
	/** Ticks máximos que puede reintentarse un paso antes de saltarlo con FAIL. */
	private static final int MAX_TRIES = 300;

	/** Punto de enganche: inerte salvo que exista el archivo flag. */
	static void register() {
		Path flag = FabricLoader.getInstance().getConfigDir().resolve(FLAG_NAME);
		if (!Files.isRegularFile(flag)) {
			return;
		}
		try {
			Files.deleteIfExists(flag);
		} catch (IOException ignored) {
		}
		CobbleTabsClient.LOGGER.info("[CobbleTabs] Auto-test activado (flag detectado): arrancando secuencia.");
		CobbleTabsSelftest test = new CobbleTabsSelftest(Minecraft.getInstance());
		test.build();
		ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}

	private interface Task {
		/** Ejecuta el paso; devuelve true para pasar al siguiente. */
		boolean run(Minecraft client) throws Exception;
	}

	/** Paso con contador de reintentos. */
	private static final class Step {
		final String name;
		final Task task;
		int tries;

		Step(String name, Task task) {
			this.name = name;
			this.task = task;
		}
	}

	private final Minecraft client;
	private final ArrayDeque<Step> steps = new ArrayDeque<>();
	private final Set<String> countedFails = new HashSet<>();
	private int fails;
	private int totalTicks;
	private boolean finished;
	private final StringBuilder out = new StringBuilder();

	private CobbleTabsSelftest(Minecraft client) {
		this.client = client;
	}

	// ==================================================================
	// Utilidades
	// ==================================================================

	private void step(String name, Task task) {
		steps.add(new Step(name, task));
	}

	private void log(String tag, String msg) {
		String line = "[" + tag + "] " + msg;
		out.append(line).append('\n');
		CobbleTabsClient.LOGGER.info("[CobbleTabs][selftest] {}", line);
	}

	/** Registra PASS/FAIL; cada comprobación fallida solo cuenta y se registra una vez. */
	private boolean check(String name, boolean cond) {
		if (cond) {
			log("PASS", name);
			return true;
		}
		String key = steps.isEmpty() ? "?" : steps.peek().name;
		if (countedFails.add(key + "::" + name)) {
			fails++;
			log("FAIL", name);
		}
		return false;
	}

	private CobbleTabsEditScreen editor() {
		return (CobbleTabsEditScreen) client.screen;
	}

	/** Busca el botón "Sí" de una ConfirmScreen abierta (sin depender de coordenadas). */
	private Button confirmYesButton() {
		if (client.screen instanceof ConfirmScreen cs) {
			for (GuiEventListener listener : cs.children()) {
				if (listener instanceof Button b) {
					String m = b.getMessage().getString();
					if (m.equalsIgnoreCase("Sí") || m.equalsIgnoreCase("Si") || m.equalsIgnoreCase("Yes")) {
						return b;
					}
				}
			}
		}
		return null;
	}

	/** Espera a que se abra una ConfirmScreen y pulsa su botón "Sí". */
	private void confirmYes(String ctx) {
		step("[" + ctx + "] esperar confirmación", c -> c.screen instanceof ConfirmScreen);
		step("[" + ctx + "] pulsar Sí", c -> {
			Button b = confirmYesButton();
			if (b == null) {
				return false;
			}
			check(ctx + ": botón Sí presente", true);
			b.onPress();
			return true;
		});
	}

	/** ESC idempotente: lo pulsa mientras la pantalla siga ahí. */
	private void pressEscUntil(String ctx, java.util.function.Predicate<Minecraft> done) {
		step("[" + ctx + "] ESC", c -> {
			if (done.test(c)) {
				return true;
			}
			Screen s = c.screen;
			if (s != null) {
				s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
			}
			return false;
		});
	}

	/** Reabre el inventario cada tick hasta que se mantenga abierto. */
	private boolean ensureInventory(Minecraft c) {
		if (c.screen instanceof InventoryScreen) {
			return true;
		}
		// Si la pausa por pérdida de foco tapó la pantalla, restauramos el foco
		if (c.screen instanceof PauseScreen) {
			c.setWindowActive(true);
		}
		if (c.player != null) {
			c.setScreen(new InventoryScreen(c.player));
		}
		return false;
	}

	// ==================================================================
	// Secuencia del test
	// ==================================================================

	private void build() {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		int baseTabs = cfg.tabs.size();
		int baseAdmin = cfg.adminTabs.size();
		log("INFO", "config inicial: " + baseTabs + " pestañas, " + baseAdmin + " admin");

		// La ventana sin foco pausa el juego y cierra las pantallas: forzamos el foco
		step("forzar foco", c -> {
			c.setWindowActive(true);
			return true;
		});

		step("esperar mundo", c -> c.player != null && c.level != null && c.screen == null);

		// --- Lógica pura ---
		step("lógica de presets", c -> logicPresets());
		step("lógica de config", c -> logicConfig());

		// --- Editor: abrir desde el botón del inventario ---
		step("abrir inventario", this::ensureInventory);
		step("abrir editor con el botón", c -> {
			if (c.screen instanceof CobbleTabsEditScreen) {
				return true;
			}
			if (!ensureInventory(c)) {
				return false;
			}
			InventoryScreen inv = (InventoryScreen) c.screen;
			int bx = CobbleTabsClient.editButtonX(inv) + 6;
			int by = CobbleTabsClient.editButtonY(inv) + 6;
			// Misma comprobación de geometría que usa el handler del clic en el inventario
			check("botón de edición bajo el cursor", CobbleTabsClient.editButtonAt(inv, bx, by));
			// screen.mouseClicked() no dispara ScreenMouseEvents (los lanza MouseHandler),
			// así que abrimos por la misma vía que el handler: setScreen(editor).
			c.setScreen(new CobbleTabsEditScreen(inv));
			return c.screen instanceof CobbleTabsEditScreen;
		});
		step("comprobar editor abierto", c -> check("editor abierto", c.screen instanceof CobbleTabsEditScreen));

		// --- Editor: añadir pestaña con el diálogo (color/negrita/activada) ---
		step("clic Añadir", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(97, 220, 0);
			return editor().selftestDialogOpen();
		});
		step("comprobar diálogo", c -> check("diálogo abierto", editor().selftestDialogOpen()));
		step("clic color verde", c -> c.screen.mouseClicked(130, 134, 0));
		step("clic negrita", c -> c.screen.mouseClicked(48, 148, 0));
		step("clic activada", c -> c.screen.mouseClicked(76, 148, 0));
		step("clic Guardar", c -> {
			if (!editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(80, 170, 0);
			return !editor().selftestDialogOpen();
		});
		step("comprobar pestaña añadida", c -> {
			check("diálogo cerrado tras guardar", !editor().selftestDialogOpen());
			List<CobbleTabsConfig.TabEntry> tabs = CobbleTabsClient.config().tabs;
			check("pestaña añadida", tabs.size() == baseTabs + 1);
			if (tabs.size() == baseTabs + 1) {
				CobbleTabsConfig.TabEntry added = tabs.get(tabs.size() - 1);
				check("color verde aplicado", "green".equals(added.color));
				// Una pestaña nueva nace con negrita y activada: los clics deben dejarlas en false
				check("toggle negrita aplicado", !added.bold);
				check("toggle activada aplicado", !added.enabled);
			}
			return true;
		});

		// --- Editor: cancelar con ESC sin cambios ---
		step("clic fila 0", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(130, 45, 0);
			return editor().selftestDialogOpen();
		});
		step("comprobar diálogo edición", c -> check("diálogo edición abierto", editor().selftestDialogOpen()));
		pressEscUntil("cancelar diálogo", c -> !editor().selftestDialogOpen());
		step("comprobar cancelación", c -> {
			check("diálogo cerrado tras ESC", !editor().selftestDialogOpen());
			check("sin cambios tras cancelar", CobbleTabsClient.config().tabs.size() == baseTabs + 1);
			return true;
		});

		// --- Editor: borrar la pestaña añadida (botón ✕ de la última fila) ---
		step("clic última fila", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			// Con 13 pestañas la última fila queda fuera de las 11 visibles: baja el
			// scroll al fondo (cada notcho de rueda mueve una fila)
			for (int i = 0; i < 15; i++) {
				c.screen.mouseScrolled(130, 100, 0, -1);
			}
			int visible = Math.min(CobbleTabsClient.config().tabs.size(), 11);
			c.screen.mouseClicked(130, 45 + (visible - 1) * 14, 0);
			return editor().selftestDialogOpen();
		});
		step("comprobar diálogo borrado", c -> check("diálogo abierto", editor().selftestDialogOpen()));
		step("clic ✕", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (editor().selftestDialogOpen()) {
				c.screen.mouseClicked(207, 33, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("borrar pestaña");
		step("comprobar borrado", c -> {
			check("pestaña borrada", CobbleTabsClient.config().tabs.size() == baseTabs);
			check("diálogo cerrado tras borrar", !editor().selftestDialogOpen());
			return true;
		});

		// --- Editor: borrado rápido con el ✕ de la fila (sin abrir el diálogo) ---
		step("clic Añadir (borrado rápido)", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(97, 220, 0);
			return editor().selftestDialogOpen();
		});
		step("guardar nueva pestaña (borrado rápido)", c -> {
			if (!editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(80, 170, 0);
			return !editor().selftestDialogOpen();
		});
		step("clic ✕ de la última fila (borrado rápido)", c -> {
			int size = CobbleTabsClient.config().tabs.size();
			if (size != baseTabs + 1) {
				return false; // la pestaña extra aún no está guardada
			}
			for (int i = 0; i < 15; i++) {
				c.screen.mouseScrolled(130, 100, 0, -1);
			}
			int visible = Math.min(size, 11);
			int rowY = 45 + (visible - 1) * 14;
			c.screen.mouseClicked(CobbleTabsEditScreen.selftestDelBoxX() + 4, rowY, 0);
			return CobbleTabsClient.config().tabs.size() == baseTabs;
		});
		step("comprobar borrado rápido", c -> check("borrado rápido sin diálogo", CobbleTabsClient.config().tabs.size() == baseTabs));

		// --- Editor: modo admin, añadir y borrar ---
		step("clic modo Admin", c -> {
			if (editor().selftestDialogOpen()) {
				return false;
			}
			// Idempotente: solo pulsa si el modo admin aún no está activo
			if (editor().selftestAdminMode()) {
				return true;
			}
			c.screen.mouseClicked(51, 220, 0);
			return editor().selftestAdminMode();
		});
		step("clic Añadir (admin)", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(97, 220, 0);
			return editor().selftestDialogOpen();
		});
		step("comprobar diálogo admin", c -> check("diálogo abierto (admin)", editor().selftestDialogOpen()));
		step("clic Guardar (admin)", c -> {
			if (!editor().selftestDialogOpen()) {
				return true;
			}
			c.screen.mouseClicked(80, 170, 0);
			return !editor().selftestDialogOpen();
		});
		step("comprobar admin añadida", c -> check("admin añadida", CobbleTabsClient.config().adminTabs.size() == baseAdmin + 1));
		step("clic última fila admin", c -> {
			if (editor().selftestDialogOpen()) {
				return true;
			}
			for (int i = 0; i < 15; i++) {
				c.screen.mouseScrolled(130, 100, 0, -1);
			}
			int visible = Math.min(CobbleTabsClient.config().adminTabs.size(), 11);
			c.screen.mouseClicked(130, 45 + (visible - 1) * 14, 0);
			return editor().selftestDialogOpen();
		});
		step("clic ✕ (admin)", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (editor().selftestDialogOpen()) {
				c.screen.mouseClicked(207, 33, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("borrar admin");
		step("comprobar admin borrada", c -> {
			check("admin borrada", CobbleTabsClient.config().adminTabs.size() == baseAdmin);
			check("diálogo cerrado tras borrar admin", !editor().selftestDialogOpen());
			return true;
		});

		// --- Opciones de la fila admin: activarla, girar la esquina y desactivarla ---
		step("clic fila admin ON", c -> {
			boolean before = CobbleTabsClient.config().admin.enabled;
			c.screen.mouseClicked(35, 200, 0);
			boolean after = CobbleTabsClient.config().admin.enabled;
			check("fila admin activada", !before && after);
			List<Boolean> states = new ArrayList<>();
			for (CobbleTabsConfig.TabEntry t : CobbleTabsClient.config().adminTabs) {
				states.add(t.enabled);
			}
			check("admin integradas activadas", states.containsAll(List.of(true, true, true)));
			return true;
		});
		step("clic botón esquina (abre menú)", c -> {
			c.screen.mouseClicked(103, 200, 0);
			return editor().selftestCornerMenuOpen();
		});
		step("elegir Abajo izq. del menú", c -> {
			c.screen.mouseClicked(103, CobbleTabsEditScreen.cornerMenuItemY(2), 0);
			return CobbleTabsConfig.normalizeCorner(CobbleTabsClient.config().admin.corner).equals("bottom_left");
		});
		step("elegir Abajo der. del menú", c -> {
			if (!editor().selftestCornerMenuOpen()) {
				c.screen.mouseClicked(103, 200, 0);
			}
			c.screen.mouseClicked(103, CobbleTabsEditScreen.cornerMenuItemY(3), 0);
			return CobbleTabsConfig.normalizeCorner(CobbleTabsClient.config().admin.corner).equals("bottom_right");
		});
		step("clic fila admin OFF", c -> {
			c.screen.mouseClicked(35, 200, 0);
			return !CobbleTabsClient.config().admin.enabled;
		});

		// --- Presets: abrir pantalla y cargar los dos integrados ---
		step("clic botón Presets", c -> {
			if (c.screen instanceof CobbleTabsPresetsScreen) {
				return true;
			}
			if (c.screen instanceof CobbleTabsEditScreen) {
				c.screen.mouseClicked(205, 200, 0);
			}
			return c.screen instanceof CobbleTabsPresetsScreen;
		});
		step("seleccionar preset default (fila 0)", c -> {
			if (!(c.screen instanceof CobbleTabsPresetsScreen presets)) {
				return false;
			}
			c.screen.mouseClicked(60, 38, 0);
			return presets.selftestHasSelection();
		});
		step("clic Cargar (default)", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (c.screen instanceof CobbleTabsPresetsScreen) {
				c.screen.mouseClicked(143, 40, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("cargar default");
		step("comprobar default cargado", c -> {
			List<CobbleTabsConfig.TabEntry> tabs = CobbleTabsClient.config().tabs;
			check("default: 12 pestañas", tabs.size() == 12);
			check("default: primera = menu", !tabs.isEmpty() && "menu".equals(tabs.get(0).id));
			return true;
		});
		step("seleccionar preset pokegalaxia (fila 1)", c -> {
			if (!(c.screen instanceof CobbleTabsPresetsScreen presets)) {
				return false;
			}
			c.screen.mouseClicked(60, 53, 0);
			return presets.selftestHasSelection();
		});
		step("clic Cargar (pokegalaxia)", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (c.screen instanceof CobbleTabsPresetsScreen) {
				c.screen.mouseClicked(143, 40, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("cargar pokegalaxia");
		step("comprobar pokegalaxia cargado", c -> {
			List<CobbleTabsConfig.TabEntry> tabs = CobbleTabsClient.config().tabs;
			List<String> ids = new ArrayList<>();
			for (CobbleTabsConfig.TabEntry t : tabs) {
				ids.add(t.id);
			}
			check("pokegalaxia: 8 pestañas", tabs.size() == 8);
			check("pokegalaxia: ids correctos", ids.containsAll(List.of("menu", "wiki", "gts", "ah", "warps", "sts", "wt", "balance")));
			check("pokegalaxia: admin intactas", CobbleTabsClient.config().adminTabs.size() == baseAdmin);
			return true;
		});

		// --- Presets: crear uno escribiendo el nombre y borrarlo ---
		step("clic campo nombre", c -> c.screen.mouseClicked(74, 206, 0));
		step("escribir nombre", c -> {
			if (c.screen instanceof CobbleTabsPresetsScreen presets) {
				presets.selftestSetName("selftestui");
				return true;
			}
			return false;
		});
		step("clic Guardar preset", c -> {
			c.screen.mouseClicked(169, 206, 0);
			return CobbleTabsPresets.exists("selftestui");
		});
		step("comprobar preset guardado", c -> check("preset 'selftestui' creado", CobbleTabsPresets.exists("selftestui")));
		step("clic ✕ preset guardado", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (c.screen instanceof CobbleTabsPresetsScreen) {
				// Seleccionar el preset (fila 2) y pulsar Eliminar del panel de detalles
				c.screen.mouseClicked(60, 68, 0);
				c.screen.mouseClicked(203, 60, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("borrar preset");
		step("comprobar preset borrado", c -> check("preset 'selftestui' borrado", !CobbleTabsPresets.exists("selftestui")));

		// --- Presets: exportar (portapapeles), sobrescribir e importar ---
		step("seleccionar default y exportar (botón Exportar)", c -> {
			if (!(c.screen instanceof CobbleTabsPresetsScreen)) {
				return false;
			}
			c.screen.mouseClicked(60, 38, 0);
			c.screen.mouseClicked(203, 40, 0);
			return true;
		});
		step("comprobar export al portapapeles", c -> {
			if (!(c.screen instanceof CobbleTabsPresetsScreen presets)) {
				return false;
			}
			presets.selftestRefreshImport();
			String clip = c.keyboardHandler.getClipboard();
			check("portapapeles con JSON del preset", CobbleTabsPresets.parseImport(clip) != null && clip.contains("/pc"));
			return true;
		});
		step("importar del portapapeles", c -> {
			if (!(c.screen instanceof CobbleTabsPresetsScreen presets)) {
				return false;
			}
			presets.selftestRefreshImport();
			c.screen.mouseClicked(35, 188, 0);
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("importar preset");
		step("comprobar preset importado", c -> {
			check("preset 'import' creado", CobbleTabsPresets.exists("import"));
			boolean listed = false;
			for (CobbleTabsPresets.Preset p : CobbleTabsPresets.savedPresets()) {
				if (p.name().equals("import")) {
					listed = true;
					check("import: pestañas del JSON", p.tabs().size() == 12);
				}
			}
			check("import listado", listed);
			return true;
		});
		step("sobrescribir 'import' (botón Sobrescribir)", c -> {
			if (c.screen instanceof ConfirmScreen) {
				return true;
			}
			if (c.screen instanceof CobbleTabsPresetsScreen) {
				c.screen.mouseClicked(60, 68, 0);
				c.screen.mouseClicked(143, 60, 0);
			}
			return c.screen instanceof ConfirmScreen;
		});
		confirmYes("sobrescribir preset");
		step("comprobar preset sobrescrito", c -> check("preset 'import' sigue existiendo", CobbleTabsPresets.exists("import")));
		step("borrar preset importado", c -> CobbleTabsPresets.delete("import") && !CobbleTabsPresets.exists("import"));

		// --- Restaurar y salir ---
		step("restaurar config", c -> {
			CobbleTabsPresets.apply(CobbleTabsPresets.defaultPreset());
			CobbleTabsClient.config().admin.enabled = false;
			CobbleTabsConfig.save(CobbleTabsClient.config());
			CobbleTabsClient.rebuildTabs();
			return true;
		});
		step("comprobar config restaurada", c -> {
			List<CobbleTabsConfig.TabEntry> tabs = CobbleTabsClient.config().tabs;
			check("tabs restauradas", !tabs.isEmpty() && "menu".equals(tabs.get(0).id));
			check("admin desactivada", !CobbleTabsClient.config().admin.enabled);
			return true;
		});
		pressEscUntil("presets→editor", c -> c.screen instanceof CobbleTabsEditScreen);
		pressEscUntil("editor→inventario", c -> c.screen instanceof InventoryScreen);
		pressEscUntil("inventario→juego", c -> c.screen == null);
		step("cerrar todo", c -> {
			check("sin pantalla", c.screen == null);
			return true;
		});
	}

	/** Pruebas de lógica de presets (guardar/listar/cargar/borrar/existe). */
	private boolean logicPresets() {
		CobbleTabsPresets.Preset poke = CobbleTabsPresets.pokegalaxiaPreset();
		check("pokegalaxia: 8 pestañas", poke.tabs().size() == 8);
		List<String> ids = new ArrayList<>();
		for (CobbleTabsConfig.TabEntry t : poke.tabs()) {
			ids.add(t.id);
		}
		check("pokegalaxia: ids correctos", ids.containsAll(List.of("menu", "wiki", "gts", "ah", "warps", "sts", "wt", "balance")));
		check("pokegalaxia: comandos correctos", poke.tabs().get(2).command.equals("/gts")
				&& poke.tabs().get(3).command.equals("/ah")
				&& poke.tabs().get(4).command.equals("/warps")
				&& poke.tabs().get(7).command.equals("/balance"));

		CobbleTabsConfig cfg = CobbleTabsClient.config();
		List<CobbleTabsConfig.TabEntry> custom = new ArrayList<>();
		for (CobbleTabsConfig.TabEntry t : cfg.tabs) {
			CobbleTabsConfig.TabEntry copy = t.copy();
			copy.command = "/selftest_" + t.id;
			custom.add(copy);
		}
		check("guardar preset", CobbleTabsPresets.save("selftest_preset", custom, cfg.adminTabs));
		check("preset existe", CobbleTabsPresets.exists("selftest_preset"));
		boolean listed = false;
		for (CobbleTabsPresets.Preset p : CobbleTabsPresets.savedPresets()) {
			if (p.name().equals("selftest_preset")) {
				listed = true;
				check("preset cargado con cambios", !p.tabs().isEmpty() && p.tabs().get(0).command.startsWith("/selftest_"));
			}
		}
		check("preset listado en savedPresets", listed);
		check("borrar preset", CobbleTabsPresets.delete("selftest_preset") && !CobbleTabsPresets.exists("selftest_preset"));
		check("integrados no borrables", !CobbleTabsPresets.delete("default"));
		check("integrados existen", CobbleTabsPresets.exists("default") && CobbleTabsPresets.exists("pokegalaxia"));

		// Exportar / importar (portapapeles)
		CobbleTabsPresets.Preset export = CobbleTabsPresets.builtInByName("pokegalaxia");
		String json = CobbleTabsPresets.exportJson(export);
		check("exportar produce JSON", json.contains("\"tabs\"") && json.contains("/gts"));
		CobbleTabsPresets.Preset imported = CobbleTabsPresets.parseImport(json);
		check("importar JSON redondo", imported != null && imported.tabs().size() == 8 && imported.tabs().get(2).command.equals("/gts"));
		check("importar rechaza basura", CobbleTabsPresets.parseImport("no es json") == null
				&& CobbleTabsPresets.parseImport("{\"tabs\":[]}") == null);
		CobbleTabsPresets.Preset partial = CobbleTabsPresets.parseImport(
				"{\"tabs\":[{\"id\":\"x\",\"command\":\"x\"},{\"id\":\"\",\"command\":\"/y\"},{\"id\":\"ok\",\"command\":\"sin_slash\"}]}");
		check("importar sanea entradas", partial != null && partial.tabs().size() == 2 && partial.tabs().get(1).command.equals("/sin_slash"));
		check("uniqueName da nombre libre", "selftest_preset".equals(CobbleTabsPresets.uniqueName("selftest_preset")));
		return true;
	}

	/** Pruebas de lógica de config (colores, protegidos, esquinas, sanitize). */
	private boolean logicConfig() {
		check("parseColor gris", CobbleTabsConfig.parseColor("gris", -1) == 0xAAAAAA);
		check("parseColor hex", CobbleTabsConfig.parseColor("#50C878", -1) == 0x50C878);
		check("parseColor inválido → fallback", CobbleTabsConfig.parseColor("zzz", 7) == 7);
		check("isProtectedId", CobbleTabsConfig.isProtectedId("extra1")
				&& CobbleTabsConfig.isProtectedId("admin_creative")
				&& !CobbleTabsConfig.isProtectedId("pc"));
		check("normalizeCorner es/en", CobbleTabsConfig.normalizeCorner("abajo_izquierda").equals("bottom_left")
				&& CobbleTabsConfig.normalizeCorner("arriba der").equals("top_right"));
		CobbleTabsConfig cfgMpr = CobbleTabsClient.config();
		int savedMaxPerRow = cfgMpr.admin.maxPerRow;
		cfgMpr.admin.maxPerRow = 99;
		cfgMpr.sanitize();
		check("maxPerRow se clampa a 8", cfgMpr.admin.maxPerRow == 8);
		cfgMpr.admin.maxPerRow = 0;
		cfgMpr.sanitize();
		check("maxPerRow mínimo 1", cfgMpr.admin.maxPerRow == 1);
		cfgMpr.admin.maxPerRow = savedMaxPerRow;
		cfgMpr.sanitize();
		CobbleTabsConfig.save(cfgMpr);
		CobbleTabsConfig cfgAdm = CobbleTabsClient.config();
		cfgAdm.admin.enabled = false;
		cfgAdm.adminTabs.get(0).enabled = false;
		cfgAdm.enableDefaultAdminTabs();
		check("enableDefaultAdminTabs", cfgAdm.admin.enabled && cfgAdm.adminTabs.get(0).enabled);
		cfgAdm.admin.enabled = false;
		CobbleTabsConfig.save(cfgAdm);
		CobbleTabsConfig.TabEntry e = new CobbleTabsConfig.TabEntry();
		e.command = "menu";
		CobbleTabsConfig.sanitizeEntry(e);
		check("sanitizeEntry añade /", e.command.equals("/menu"));
		return true;
	}

	// ==================================================================
	// Bucle principal
	// ==================================================================

	void tick(Minecraft c) {
		if (finished) {
			return;
		}
		totalTicks++;
		if (totalTicks > 6000) {
			fails++;
			log("FAIL", "timeout global (5 min) en paso: " + (steps.isEmpty() ? "?" : steps.peek().name));
			finish();
			return;
		}
		Step s = steps.peek();
		if (s == null) {
			finish();
			return;
		}
		boolean done;
		try {
			done = s.task.run(c);
		} catch (Throwable t) {
			if (countedFails.add("exc::" + s.name)) {
				fails++;
			}
			log("FAIL", s.name + " → " + t);
			steps.clear();
			finish();
			return;
		}
		if (done) {
			log("ok  ", s.name);
			steps.poll();
			return;
		}
		if (++s.tries > MAX_TRIES) {
			if (countedFails.add("cap::" + s.name)) {
				fails++;
			}
			log("FAIL", s.name + " (agotado tras " + s.tries + " ticks)");
			steps.poll();
		}
	}

	/** Escribe el archivo de resultados y cierra el juego. */
	private void finish() {
		if (finished) {
			return;
		}
		finished = true;
		String verdict = fails == 0 ? "PASS" : "FAIL";
		log("----", "RESULTADO: " + verdict + " (" + fails + " fallos)");
		Path result = FabricLoader.getInstance().getConfigDir().resolve(RESULT_NAME);
		try {
			Files.writeString(result, out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			CobbleTabsClient.LOGGER.error("[CobbleTabs] No se pudo escribir el resultado del auto-test.", e);
		}
		CobbleTabsClient.LOGGER.info("[CobbleTabs] AUTO-TEST {}: {} fallos. Detalles en {}", verdict, fails, result);
		client.stop();
	}
}
