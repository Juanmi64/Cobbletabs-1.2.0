package com.cobbletabs.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de presets (diseño de dos columnas): a la izquierda, la lista de
 * presets (integrados + guardados) con sus nombres a color; a la derecha, el
 * panel de detalles del preset seleccionado con su contenido completo y las
 * acciones <b>Cargar</b>, <b>Exportar</b>, <b>Sobrescribir</b> y <b>Eliminar</b>,
 * más una paleta para darle un color propio al preset. Los presets del usuario
 * se guardan en config/cobbletabs/presets/*.json.
 */
public class CobbleTabsPresetsScreen extends Screen {

	/** Pantalla a la que volver al cerrar (normalmente el editor). */
	private final Screen parent;

	// Panel principal (lista + panel de detalles)
	static final int PANEL_X = 15;
	static final int PANEL_W = 256;
	static final int PANEL_Y = 10;
	static final int PANEL_H = 222;
	/** Alto de fila de la lista. */
	static final int ROW_H = 15;
	/** Filas visibles de la lista. */
	static final int VISIBLE_ROWS = 8;
	/** Ancho de la columna de la lista (el resto del panel es el panel de detalles). */
	private static final int LIST_W = 100;

	/** Ancho de los botones de acción del panel de detalles (cuadrícula 2×2). */
	private static final int BTN_W = 66;
	/** Separación horizontal entre los botones de la cuadrícula 2×2. */
	private static final int BTN_GAP = 4;

	// Paleta de colores de preset: swatches cuadrados en el panel de detalles.
	private static final int SWATCH = 11;
	private static final int SWATCH_STEP = 12;

	/** Máximo de caracteres del campo de nombre (el mismo que se le aplica con setMaxLength). */
	private static final int NAME_MAX = 32;
	/** Ancho del campo de nombre. */
	private static final int NAME_W = 110;

	/** X del panel (para el auto-test). */
	public static int panelX() {
		return PANEL_X;
	}

	/** Y del panel (para el auto-test). */
	public static int panelY() {
		return PANEL_Y;
	}

	/** Alto del panel (para el auto-test). */
	public static int panelH() {
		return PANEL_H;
	}

	/** Alto de fila (para el auto-test). */
	public static int rowH() {
		return ROW_H;
	}

	/** Campo para el nombre del preset a guardar. */
	private EditBox nameBox;
	/** Botón guardar (deshabilitado si el nombre es inválido o ya existe). */
	private Button saveBtn;
	/** Botón importar (deshabilitado si el portapapeles no trae un preset válido). */
	private Button importBtn;
	/** Botones de acción del panel de detalles (cuadrícula 2×2). */
	private Button loadBtn;
	private Button exportBtn;
	private Button overwriteBtn;
	private Button deleteBtn;

	/** Lista combinada de presets (integrados + archivos) para el listado. */
	private record Row(CobbleTabsPresets.Preset preset, boolean canDelete, boolean canOverwrite) {
	}

	private List<Row> rows = List.of();
	private int scroll;
	/** Índice del preset seleccionado en rows (-1 = ninguno). El hover de la lista lo pisa temporalmente. */
	private int selected = -1;
	/** Última posición del ratón, para saber qué fila está bajo el cursor. */
	private double lastMouseX = -1;
	private double lastMouseY = -1;

	/** Línea de estado (éxito/error) mostrada bajo la lista; null = nada. */
	private Component status;
	/** true = estado en verde; false = en rojo. */
	private boolean statusOk;
	/** Nombre del último preset importado, para rellenar el campo de nombre. */
	private String lastImportName;
	/** Último portapapeles visto, para reevaluar el botón Importar solo al cambiar. */
	private String lastClipboard;

	public CobbleTabsPresetsScreen(Screen parent) {
		super(Component.translatable("cobbletabs.presets.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x0 = PANEL_X;
		int y1 = PANEL_Y + PANEL_H;
		int fx = x0 + 4;

		nameBox = new EditBox(font, fx, y1 - 32, NAME_W, 12, Component.translatable("cobbletabs.presets.name"));
		nameBox.setMaxLength(NAME_MAX);
		nameBox.setHint(Component.translatable("cobbletabs.presets.name_hint"));
		nameBox.setResponder(s -> refreshSaveButton());
		addRenderableWidget(nameBox);

		saveBtn = Button.builder(Component.translatable("cobbletabs.presets.save"), b -> saveCurrent())
				.bounds(fx + 114, y1 - 34, 52, 16).build();
		addRenderableWidget(saveBtn);

		addRenderableWidget(Button.builder(Component.translatable("cobbletabs.presets.back"), b -> onClose())
				.bounds(fx + 170, y1 - 34, 52, 16).build());

		importBtn = Button.builder(Component.translatable("cobbletabs.presets.import"), b -> importFromClipboard())
				.bounds(fx, y1 - 52, 52, 16)
				.tooltip(net.minecraft.client.gui.components.Tooltip
						.create(Component.translatable("cobbletabs.presets.import.tip")))
				.build();
		addRenderableWidget(importBtn);

		// Botones de acción del panel de detalles, en cuadrícula 2×2
		int dx = detailX();
		int dy = PANEL_Y + 24;
		loadBtn = Button.builder(Component.translatable("cobbletabs.presets.load"), b -> {
				Row r = displayRow();
				if (r != null) {
					loadPreset(r.preset());
				}
			}).bounds(dx, dy, BTN_W, 16).build();
		addRenderableWidget(loadBtn);

		exportBtn = Button.builder(Component.translatable("cobbletabs.presets.export"), b -> {
				Row r = displayRow();
				if (r != null) {
					exportPreset(r.preset());
				}
			}).bounds(dx + BTN_W + BTN_GAP, dy, BTN_W, 16).build();
		addRenderableWidget(exportBtn);

		overwriteBtn = Button.builder(Component.translatable("cobbletabs.presets.overwrite"), b -> {
				Row r = displayRow();
				if (r != null) {
					overwritePreset(r.preset());
				}
			}).bounds(dx, dy + 18, BTN_W, 16).build();
		addRenderableWidget(overwriteBtn);

		deleteBtn = Button.builder(Component.translatable("cobbletabs.presets.delete"), b -> {
				Row r = displayRow();
				if (r != null) {
					deletePreset(r.preset());
				}
			}).bounds(dx + BTN_W + BTN_GAP, dy + 18, BTN_W, 16).build();
		addRenderableWidget(deleteBtn);

		refreshList();
		refreshSaveButton();
		refreshImportButton();
		refreshDetailButtons();
	}

	/** X donde empieza la columna de acciones del panel de detalles. */
	private static int detailX() {
		return PANEL_X + LIST_W + 8;
	}

	/** Solo para el auto-test: escribe el nombre del preset a guardar. */
	void selftestSetName(String name) {
		nameBox.setValue(name);
	}

	/** Solo para el auto-test: reevalúa el estado del botón Importar. */
	void selftestRefreshImport() {
		refreshImportButton();
	}

	/** Solo para el auto-test: true si hay un preset seleccionado. */
	boolean selftestHasSelection() {
		return selected >= 0;
	}

	/** Recarga la lista de presets (integrados + archivos guardados). */
	private void refreshList() {
		List<Row> list = new ArrayList<>();
		for (CobbleTabsPresets.Preset p : CobbleTabsPresets.builtInPresets()) {
			list.add(new Row(p, false, false));
		}
		for (CobbleTabsPresets.Preset p : CobbleTabsPresets.savedPresets()) {
			list.add(new Row(p, true, true));
		}
		rows = List.copyOf(list);
		if (selected >= rows.size()) {
			selected = rows.size() - 1;
		}
	}

	/** Fila seleccionada, o null. */
	private Row selectedRow() {
		int i = selected;
		if (i < 0 || i >= rows.size()) {
			return null;
		}
		return rows.get(i);
	}

	/**
	 * Fila mostrada en el panel de detalles: el preset bajo el cursor si hay
	 * alguno (al pasar el ratón los datos salen al lateral al instante), y si no
	 * el último seleccionado con clic (o ↑/↓).
	 */
	private Row displayRow() {
		int hover = listIndexAt(lastMouseX, lastMouseY);
		if (hover >= 0) {
			return rows.get(hover);
		}
		return selectedRow();
	}

	/** Activa/desactiva los botones del panel de detalles según el preset mostrado. */
	private void refreshDetailButtons() {
		Row r = displayRow();
		boolean has = r != null;
		loadBtn.active = has;
		exportBtn.active = has;
		overwriteBtn.active = has && r.canOverwrite();
		deleteBtn.active = has && r.canDelete();
	}

	/** Activa/desactiva el botón Guardar según el nombre. */
	private void refreshSaveButton() {
		String name = nameBox.getValue().trim();
		saveBtn.active = !name.isBlank() && !CobbleTabsPresets.exists(name);
	}

	/** Activa el botón Importar solo si el portapapeles trae un JSON de preset válido. */
	private void refreshImportButton() {
		importBtn.active = CobbleTabsPresets.parseImport(clientClipboard()) != null;
	}

	/** Texto actual del portapapeles (vacío si no se puede leer). */
	private String clientClipboard() {
		try {
			String s = minecraft.keyboardHandler.getClipboard();
			return s == null ? "" : s;
		} catch (Exception e) {
			return "";
		}
	}

	/** Muestra una línea de estado bajo la lista. */
	private void setStatus(Component text, boolean ok) {
		status = text;
		statusOk = ok;
	}

	/** Crea un preset nuevo con la config actual. */
	private void saveCurrent() {
		String name = nameBox.getValue().trim();
		if (name.isBlank() || CobbleTabsPresets.exists(name)) {
			return;
		}
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		if (CobbleTabsPresets.save(name, cfg.tabs, cfg.adminTabs)) {
			setStatus(Component.translatable("cobbletabs.presets.saved", name), true);
			nameBox.setValue("");
			refreshList();
			refreshSaveButton();
			selectByName(name);
			refreshDetailButtons();
		} else {
			setStatus(Component.translatable("cobbletabs.presets.error"), false);
		}
	}

	/** Copia el JSON del preset al portapapeles (para compartirlo). */
	private void exportPreset(CobbleTabsPresets.Preset preset) {
		String json = CobbleTabsPresets.exportJson(preset);
		minecraft.keyboardHandler.setClipboard(json);
		int n = preset.tabs().size() + preset.adminTabs().size();
		setStatus(Component.translatable("cobbletabs.presets.exported", preset.name(), n), true);
	}

	/** Pide confirmación y reemplaza el archivo del preset por la config actual. */
	private void overwritePreset(CobbleTabsPresets.Preset preset) {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		int n = cfg.tabs.size() + cfg.adminTabs.size();
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				if (CobbleTabsPresets.save(preset.name(), cfg.tabs, cfg.adminTabs, preset.color())) {
					setStatus(Component.translatable("cobbletabs.presets.overwritten", preset.name(), n), true);
				} else {
					setStatus(Component.translatable("cobbletabs.presets.error"), false);
				}
				refreshList();
				selectByName(preset.name());
				refreshDetailButtons();
			}
			minecraft.setScreen(this);
		}, Component.translatable("cobbletabs.presets.overwrite_title", preset.name()),
				Component.translatable("cobbletabs.presets.overwrite_confirm", preset.name(), n)));
	}

	/** Aplica un preset tras confirmación (reemplaza las pestañas actuales). */
	private void loadPreset(CobbleTabsPresets.Preset preset) {
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				CobbleTabsPresets.apply(preset);
			}
			minecraft.setScreen(this);
		}, Component.translatable("cobbletabs.presets.load_title", preset.name()),
				Component.translatable("cobbletabs.presets.load_confirm")));
	}

	/** Borra un preset guardado (con confirmación). */
	private void deletePreset(CobbleTabsPresets.Preset preset) {
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				CobbleTabsPresets.delete(preset.name());
				refreshList();
				refreshSaveButton();
				refreshDetailButtons();
			}
			minecraft.setScreen(this);
		}, Component.translatable("cobbletabs.presets.delete_title", preset.name()),
				Component.translatable("cobbletabs.presets.delete_confirm")));
	}

	/**
	 * Lee el JSON del portapapeles y, si es un preset válido, lo guarda con
	 * confirmación con el primer nombre libre. Si el campo de nombre está vacío,
	 * se rellena con el nombre elegido para facilitar reexportar o sobrescribir.
	 */
	private void importFromClipboard() {
		CobbleTabsPresets.Preset parsed = CobbleTabsPresets.parseImport(clientClipboard());
		if (parsed == null) {
			setStatus(Component.translatable("cobbletabs.presets.import_invalid"), false);
			return;
		}
		int n = parsed.tabs().size() + parsed.adminTabs().size();
		String name = CobbleTabsPresets.uniqueName(lastImportName != null ? lastImportName : parsed.name());
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				if (CobbleTabsPresets.save(name, parsed.tabs(), parsed.adminTabs(), parsed.color())) {
					lastImportName = name;
					setStatus(Component.translatable("cobbletabs.presets.imported", name, n), true);
					refreshList();
					selectByName(name);
					refreshDetailButtons();
				} else {
					setStatus(Component.translatable("cobbletabs.presets.error"), false);
				}
			}
			// init() recrea los widgets: primero setScreen, luego rellenar el campo
			minecraft.setScreen(this);
			if (yes && nameBox.getValue().isBlank()) {
				nameBox.setValue(name);
			}
		}, Component.translatable("cobbletabs.presets.import_title", n),
				Component.translatable("cobbletabs.presets.import_confirm", name)));
	}

	/** Selecciona en la lista el preset con ese nombre (si está). */
	private void selectByName(String name) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).preset().name().equals(name)) {
				selected = i;
				ensureVisible();
				return;
			}
		}
	}

	/** Asegura que la fila seleccionada esté visible (ajusta el scroll). */
	private void ensureVisible() {
		if (selected < scroll) {
			scroll = selected;
		} else if (selected >= scroll + VISIBLE_ROWS) {
			scroll = selected - VISIBLE_ROWS + 1;
		}
	}

	// ==================================================================
	// Render
	// ==================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// El portapapeles puede cambiar en cualquier momento: reevalúa el botón al detectar cambios
		String clip = clientClipboard();
		if (!clip.equals(lastClipboard)) {
			lastClipboard = clip;
			refreshImportButton();
		}
		// El panel de detalles sigue al ratón: recordar la posición para displayRow()
		lastMouseX = mouseX;
		lastMouseY = mouseY;

		super.render(graphics, mouseX, mouseY, partialTick);

		int x0 = PANEL_X;
		int x1 = PANEL_X + PANEL_W;
		int y0 = PANEL_Y;
		int y1 = PANEL_Y + PANEL_H;

		// Fondo del panel
		graphics.fill(x0, y0, x1, y1, 0xE0101018);
		int border = 0xFF4A4A55;
		graphics.fill(x0, y0, x1, y0 + 1, border);
		graphics.fill(x0, y0, x0 + 1, y1, border);
		graphics.fill(x1 - 1, y0, x1, y1, border);
		graphics.fill(x0, y1 - 1, x1, y1, border);

		// Separador vertical entre lista y panel de detalles
		int sepX = x0 + LIST_W;
		graphics.fill(sepX, y0 + 1, sepX + 1, y1 - 1, 0xFF2E2E38);

		graphics.drawString(font, title, x0 + 4, y0 + 6, 0xFF55FFFF, true);

		// ===================== Columna izquierda: lista =====================

		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row;
			if (index >= rows.size()) {
				break;
			}
			Row r = rows.get(index);
			CobbleTabsPresets.Preset p = r.preset();
			int y = y0 + 18 + row * ROW_H;
			boolean sel = index == selected;
			boolean hovered = mouseX >= x0 + 3 && mouseX < sepX - 1 && mouseY >= y && mouseY < y + ROW_H;
			if (sel) {
				graphics.fill(x0 + 3, y, sepX - 2, y + ROW_H, 0x80204A5A);
			} else if (hovered) {
				graphics.fill(x0 + 3, y, sepX - 2, y + ROW_H, 0x8032323C);
			}
			// Marcador de selección a la izquierda, del color del preset
			if (sel) {
				graphics.fill(x0 + 3, y, x0 + 5, y + ROW_H, 0xFF000000 | presetRgb(p));
			}
			// El nombre se recorta para no invadir el contador de la derecha
			String name = p.name();
			int maxName = LIST_W - 28;
			while (font.width(name) > maxName && name.length() > 1) {
				name = name.substring(0, name.length() - 1);
			}
			graphics.drawString(font, name, x0 + 8, y + 4, presetRgb(p), p.builtIn());
			// Contador de pestañas a la derecha del nombre
			String count = String.valueOf(p.tabs().size() + p.adminTabs().size());
			graphics.drawString(font, count, sepX - 5 - font.width(count), y + 4, 0xFF808090, false);
		}

		// Indicador de que hay más filas
		if (rows.size() > VISIBLE_ROWS) {
			graphics.drawString(font, Component.translatable("cobbletabs.edit.scroll_hint"), x0 + 4, y1 - 66, 0xFF707078, false);
		}

		// Línea de estado (banda libre bajo los botones Guardar/Volver)
		if (status != null) {
			String text = status.getString();
			int maxW = PANEL_W - 8;
			while (font.width(text) > maxW && text.length() > 1) {
				text = text.substring(0, text.length() - 1);
			}
			graphics.drawString(font, text, x0 + 4, y1 - 12, statusOk ? 0xFF7FE37F : 0xFFFF7F7F, false);
		}

		// ===================== Columna derecha: detalles =====================

		Row sel = displayRow();
		int dx = detailX();
		int dw = x1 - dx - 4;
		if (sel == null) {
			graphics.drawString(font, Component.translatable("cobbletabs.presets.none"), dx, y0 + 64, 0xFF808090, false);
			graphics.drawString(font, Component.translatable("cobbletabs.presets.none2"), dx, y0 + 74, 0xFF808090, false);
			return;
		}
		CobbleTabsPresets.Preset p = sel.preset();

		// Cabecera: nombre del preset a color + tipo
		String header = p.name();
		while (font.width(header) > dw && header.length() > 1) {
			header = header.substring(0, header.length() - 1);
		}
		graphics.drawString(font, header, dx, y0 + 6, presetRgb(p), true);
		String type = p.builtIn()
				? Component.translatable("cobbletabs.presets.builtin").getString()
				: Component.translatable("cobbletabs.presets.saved_count",
						p.tabs().size() + p.adminTabs().size()).getString();
		graphics.drawString(font, type, dx, y0 + 16, 0xFF808090, false);

		// Contenido del preset (bajo la cuadrícula 2×2 de botones)
		int listY = PANEL_Y + 24 + 2 * 18 + 6;
		List<CobbleTabsConfig.TabEntry> all = new ArrayList<>(p.tabs());
		for (CobbleTabsConfig.TabEntry t : p.adminTabs()) {
			all.add(t);
		}
		// El último hueco se reserva para el indicador "… y N más" si no caben todos
		int cap = y1 - 68;
		int maxSlots = Math.max(1, (cap - listY) / 10);
		boolean truncated = all.size() > maxSlots;
		int shown = truncated ? maxSlots - 1 : all.size();
		for (int i = 0; i < shown; i++, listY += 10) {
			CobbleTabsConfig.TabEntry t = all.get(i);
			String prefix = i >= p.tabs().size() ? "§8[§7a§8]§r " : "";
			String line = prefix + CobbleTabsPresets.summaryLine(t);
			while (font.width(line) > dw && line.length() > 1) {
				line = line.substring(0, line.length() - 1);
			}
			graphics.drawString(font, line, dx, listY, 0xFFE0E0E8, false);
		}
		if (truncated) {
			graphics.drawString(font, Component.translatable("cobbletabs.presets.more", all.size() - shown), dx, listY, 0xFF808090, false);
		}
		if (all.isEmpty()) {
			graphics.drawString(font, Component.translatable("cobbletabs.presets.empty_tabs"), dx, listY, 0xFF808090, false);
		}

		// ===================== Paleta de colores del preset =====================

		int palY = y1 - 48;
		graphics.drawString(font, Component.translatable("cobbletabs.presets.color"), dx, palY - 10, 0xFFA0A0B0, false);
		int idx = p.colorIsDefault() ? 0 : CobbleTabsConfig.paletteIndexOf(p.color());
		for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
			int sx = dx + i * SWATCH_STEP;
			if (sx + SWATCH > x1 - 4) {
				break;
			}
			CobbleTabsConfig.PaletteEntry e = CobbleTabsConfig.PALETTE.get(i);
			int rgb = 0xFF000000 | Integer.parseInt(e.hex().substring(1), 16);
			boolean isSel = i == idx;
			graphics.fill(sx, palY, sx + SWATCH, palY + SWATCH, isSel ? 0xFFFFFFFF : rgb);
			if (isSel) {
				graphics.fill(sx + 1, palY + 1, sx + SWATCH - 1, palY + SWATCH - 1, rgb);
			}
			// Raya gris en el swatch "sin color" cuando el preset usa su color por defecto
			if (i == 0 && p.colorIsDefault()) {
				graphics.fill(sx + 2, palY + SWATCH / 2, sx + SWATCH - 2, palY + SWATCH / 2 + 1, 0xFFA0A0B0);
			}
		}
		// Tooltip del color bajo el cursor
		int sw = swatchAt(mouseX, mouseY);
		if (sw >= 0) {
			graphics.renderTooltip(font, Component.translatable(CobbleTabsConfig.PALETTE.get(sw).langKey()), mouseX, mouseY);
		}
	}

	/** RGB de la fila/cabecera de un preset: color propio, o el de su tipo (integrado cian / guardado blanco). */
	private static int presetRgb(CobbleTabsPresets.Preset p) {
		return CobbleTabsConfig.parseColor(p.color(), p.builtIn() ? 0x55FFFF : 0xFFFFFF) & 0xFFFFFF;
	}

	// ==================================================================
	// Geometría y entrada
	// ==================================================================

	/** Índice de swatch de la paleta bajo el cursor, o -1. */
	private int swatchAt(double mouseX, double mouseY) {
		int dx = detailX();
		int palY = PANEL_Y + PANEL_H - 48;
		int x1 = PANEL_X + PANEL_W;
		for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
			int sx = dx + i * SWATCH_STEP;
			if (sx + SWATCH > x1 - 4) {
				break;
			}
			if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= palY && mouseY < palY + SWATCH) {
				return i;
			}
		}
		return -1;
	}

	/** Índice de fila de la lista bajo el cursor, o -1. */
	private int listIndexAt(double mouseX, double mouseY) {
		int x0 = PANEL_X;
		int sepX = x0 + LIST_W;
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row;
			if (index >= rows.size()) {
				break;
			}
			int y = PANEL_Y + 18 + row * ROW_H;
			if (mouseY >= y && mouseY < y + ROW_H && mouseX >= x0 + 3 && mouseX < sepX - 1) {
				return index;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Selección de color del preset (paleta del panel de detalles)
		int sw = swatchAt(mouseX, mouseY);
		if (sw >= 0) {
			Row r = displayRow();
			if (r != null) {
				setPresetColor(r.preset(), sw);
			}
			return true;
		}

		// Clic en la lista: selecciona el preset (cargar es un botón del panel de detalles)
		int index = listIndexAt(mouseX, mouseY);
		if (index >= 0) {
			selected = index;
			refreshDetailButtons();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Aplica el color de paleta sw al preset (guardando el archivo si es de usuario). */
	private void setPresetColor(CobbleTabsPresets.Preset p, int sw) {
		CobbleTabsConfig.PaletteEntry entry = CobbleTabsConfig.PALETTE.get(sw);
		// "Sin color" (índice 0) = vuelve al color por defecto de su tipo
		String newColor = entry.key() == null ? "" : entry.key();
		Row r = displayRow();
		// Fija la selección para que el panel siga mostrando este preset tras el cambio
		selected = rows.indexOf(r);
		if (r != null && r.canOverwrite()) {
			// Guardado: persiste en su archivo y refresca la fila en memoria
			if (CobbleTabsPresets.save(p.name(), p.tabs(), p.adminTabs(), newColor)) {
				setStatus(Component.translatable("cobbletabs.presets.color_saved", p.name()), true);
			} else {
				setStatus(Component.translatable("cobbletabs.presets.error"), false);
			}
			refreshList();
			selectByName(p.name());
		} else {
			// Integrado: solo cambia el color en memoria (no es persistente)
			int i = rows.indexOf(r);
			if (i >= 0) {
				rows.set(i, new Row(p.withColor(newColor), r.canDelete(), r.canOverwrite()));
			}
			setStatus(Component.translatable("cobbletabs.presets.color_builtin"), false);
		}
		refreshDetailButtons();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
		int newScroll = scroll - (int) Math.signum(scrollY);
		scroll = Math.max(0, Math.min(maxScroll, newScroll));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Ctrl+V en la pantalla: pega un nombre (o un JSON de preset, del que se extrae el nombre)
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
			pasteFromClipboard();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (nameBox.isFocused() && saveBtn.active) {
				saveCurrent();
				return true;
			}
		}
		// ↑/↓ mueven la selección sin ratón (si el campo de nombre no tiene el foco)
		if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
			if (nameBox.isFocused()) {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
			int dir = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
			if (!rows.isEmpty()) {
				selected = Math.max(0, Math.min(rows.size() - 1, selected + dir));
				ensureVisible();
				refreshDetailButtons();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/**
	 * Acción Ctrl+V: si el portapapeles trae un JSON de preset válido guarda ese
	 * nombre (o uno libre parecido) en el campo; si no, pega el texto recortado.
	 */
	private void pasteFromClipboard() {
		String clip = clientClipboard().trim();
		String name = clip;
		if (CobbleTabsPresets.parseImport(clip) != null) {
			name = CobbleTabsPresets.uniqueName(lastImportName != null ? lastImportName : "import");
		} else if (clip.contains("{")) {
			name = "";
		}
		if (!name.isBlank()) {
			String clean = name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
			nameBox.setValue(clean);
			setFocused(nameBox);
			setStatus(Component.translatable("cobbletabs.presets.paste_name", clean), true);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
