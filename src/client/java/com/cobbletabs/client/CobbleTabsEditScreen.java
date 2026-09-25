package com.cobbletabs.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Editor de pestañas dentro del juego: lista las pestañas normales o admin y
 * permite añadir, editar (comando, icono, texto, color, negrita, activada),
 * reordenar arrastrando el icono y borrar. También gestiona la fila admin:
 * activarla/desactivarla y moverla entre las 4 esquinas de la pantalla.
 * Los cambios se guardan al momento en config/cobbletabs.json y se aplican
 * sin salir del inventario.
 *
 * <p>Rediseño 1.2.3: cada fila muestra un chip con su lado del inventario
 * (clicable para cambiarlo al instante), la lista tiene barra de scroll y el
 * diálogo de edición usa selectores cíclicos en vez de menús desplegables.
 * Las pestañas admin no muestran el selector de lado: su posición la decide
 * la esquina de la fila admin, no el campo "side".</p>
 */
public class CobbleTabsEditScreen extends Screen {

	/** Pantalla a la que volver al cerrar (normalmente el inventario). */
	private final Screen parent;

	// ==================================================================
	// Geometría del panel (pública para el auto-test)
	// ==================================================================
	static final int PANEL_X = 28;
	static final int PANEL_W = 264;
	static final int PANEL_Y = 12;
	static final int PANEL_H = 204;
	/** Alto de cada fila de la lista. */
	static final int ROW_H = 15;
	/** Filas visibles de la lista (con scroll si hay más pestañas). */
	private static final int VISIBLE_ROWS = 9;
	/** Y relativa al panel donde empieza la lista. */
	private static final int LIST_DY = 22;
	/** Ancho de la zona clicable del botón ✕ de borrado rápido (al final de cada fila). */
	private static final int DELBOX_W = 14;
	/** Y de las dos filas de botones del pie del panel. */
	private static final int ROW_A_Y = PANEL_Y + 164;
	private static final int ROW_B_Y = PANEL_Y + 184;

	// ==================================================================
	// Geometría del diálogo de edición (pública para el auto-test)
	// ==================================================================
	static final int DLG_X = 32;
	static final int DLG_W = 256;
	static final int DLG_Y = 14;
	static final int DLG_H = 212;
	private static final int FIELD_H = 12;

	/** Lados en el orden del ciclo del selector (vacío = automático). */
	private static final String[] SIDE_CYCLE = { "", "left", "right", "top", "bottom" };
	/** Esquinas en el orden del ciclo del selector de la fila admin. */
	private static final String[] CORNER_CYCLE = { "bottom_right", "bottom_left", "top_left", "top_right" };

	/** Pestaña en edición, o null si el diálogo está cerrado. */
	private CobbleTabsConfig.TabEntry editing;
	/** Índice de la pestaña en edición dentro de la lista activa (-1 = nueva). */
	private int editingIndex = -1;

	// Campos del diálogo
	private EditBox commandBox;
	private EditBox iconBox;
	private EditBox labelBox;
	/** Campo de color libre: nombre de paleta o hex #RRGGBB (vacío = color por defecto). */
	private EditBox colorBox;

	// Botones del panel (se ocultan mientras el diálogo de edición está abierto)
	private Button panelModeBtn;
	private Button panelAddBtn;
	private Button panelResetBtn;
	/** Botón activar/desactivar la fila de pestañas admin. */
	private Button adminRowBtn;

	/** true = la lista muestra las pestañas admin; false = las normales. */
	private boolean adminMode;
	/** Desplazamiento de scroll de la lista. */
	private int scroll;
	/** Fila arrastrada para reordenar, o -1. */
	private int draggingRow = -1;
	private double dragStartY;

	public CobbleTabsEditScreen(Screen parent) {
		super(Component.translatable("cobbletabs.edit.title"));
		this.parent = parent;
	}

	// ==================================================================
	// Accesores para el auto-test
	// ==================================================================

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

	/** X del diálogo (para el auto-test). */
	public static int dlgX() {
		return DLG_X;
	}

	/** Y del diálogo (para el auto-test). */
	public static int dlgY() {
		return DLG_Y;
	}

	/** Alto del diálogo (para el auto-test). */
	public static int dlgH() {
		return DLG_H;
	}

	/** true si el diálogo de edición está abierto (solo para el auto-test). */
	boolean selftestDialogOpen() {
		return editing != null;
	}

	/** true si la lista muestra las pestañas admin (solo para el auto-test). */
	boolean selftestAdminMode() {
		return adminMode;
	}

	/** X del botón ✕ de borrado rápido (solo para el auto-test). */
	static int selftestDelBoxX() {
		return delBoxX();
	}

	/** Lado de la pestaña en edición (solo para el auto-test). */
	String selftestEditingSide() {
		return editing == null ? null : CobbleTabsConfig.normalizeSide(editing.side);
	}

	// ==================================================================
	// Inicialización
	// ==================================================================

	@Override
	protected void init() {
		int fx = PANEL_X + 4;

		// Fila B del pie: lista, añadir, restaurar y presets
		panelModeBtn = Button.builder(modeLabel(), b -> {
			adminMode = !adminMode;
			scroll = 0;
			b.setMessage(modeLabel());
		}).bounds(fx, ROW_B_Y, 52, 16).build();
		addRenderableWidget(panelModeBtn);

		// Añadir nueva pestaña
		panelAddBtn = Button.builder(Component.translatable("cobbletabs.edit.add"), b -> openEditor(null, -1))
				.bounds(fx + 54, ROW_B_Y, 65, 16).build();
		addRenderableWidget(panelAddBtn);

		// Restaurar pestañas por defecto
		panelResetBtn = Button.builder(Component.translatable("cobbletabs.edit.reset"), b -> confirmReset())
				.bounds(fx + 121, ROW_B_Y, 64, 16).build();
		addRenderableWidget(panelResetBtn);

		// Pantalla de presets
		addRenderableWidget(Button.builder(Component.translatable("cobbletabs.edit.presets"),
						b -> minecraft.setScreen(new CobbleTabsPresetsScreen(this)))
				.bounds(fx + 187, ROW_B_Y, 72, 16).build());

		// Fila A del pie: activar/desactivar la fila admin (la esquina es un chip propio)
		adminRowBtn = Button.builder(adminRowLabel(), b -> toggleAdminRow())
				.bounds(fx, ROW_A_Y, 78, 16)
				.tooltip(Tooltip.create(Component.translatable("cobbletabs.edit.admin_row.tip")))
				.build();
		addRenderableWidget(adminRowBtn);

		// Campos del diálogo de edición (ocultos hasta abrirlo)
		commandBox = new EditBox(font, DLG_X + 8, DLG_Y + 30, DLG_W - 16, FIELD_H, Component.translatable("cobbletabs.edit.command"));
		commandBox.setMaxLength(256);
		commandBox.setHint(Component.literal("/comando"));
		iconBox = new EditBox(font, DLG_X + 8, DLG_Y + 56, 200, FIELD_H, Component.translatable("cobbletabs.edit.icon"));
		iconBox.setMaxLength(256);
		iconBox.setHint(Component.literal("minecraft:paper"));
		labelBox = new EditBox(font, DLG_X + 8, DLG_Y + 82, DLG_W - 16, FIELD_H, Component.translatable("cobbletabs.edit.label"));
		labelBox.setMaxLength(64);
		labelBox.setHint(Component.literal("Nombre"));
		// Color libre: nombre de paleta (gray, green…) o RGB hex #RRGGBB
		colorBox = new EditBox(font, DLG_X + 8, DLG_Y + 108, 132, FIELD_H, Component.translatable("cobbletabs.edit.color"));
		colorBox.setMaxLength(16);
		colorBox.setHint(Component.literal("#RRGGBB"));
		commandBox.setVisible(false);
		iconBox.setVisible(false);
		labelBox.setVisible(false);
		colorBox.setVisible(false);
		addRenderableWidget(commandBox);
		addRenderableWidget(iconBox);
		addRenderableWidget(labelBox);
		addRenderableWidget(colorBox);
	}

	private Component modeLabel() {
		return Component.translatable(adminMode ? "cobbletabs.edit.mode.admin" : "cobbletabs.edit.mode.tabs");
	}

	/** Lista que se está editando según el modo. */
	private List<CobbleTabsConfig.TabEntry> currentList() {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		return adminMode ? cfg.adminTabs : cfg.tabs;
	}

	private void save() {
		CobbleTabsConfig.save(CobbleTabsClient.config());
		CobbleTabsClient.rebuildTabs();
	}

	// ==================================================================
	// Opciones de la fila admin (activar y esquina)
	// ==================================================================

	/** Etiqueta del botón que activa/desactiva la fila admin. */
	private Component adminRowLabel() {
		boolean on = CobbleTabsClient.config().admin.enabled;
		return Component.translatable(on ? "cobbletabs.edit.admin_row.on" : "cobbletabs.edit.admin_row.off");
	}

	/** Activa o desactiva la fila de pestañas admin al instante. */
	private void toggleAdminRow() {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		if (cfg.admin.enabled) {
			cfg.admin.enabled = false;
			CobbleTabsConfig.save(cfg);
		} else {
			// Al activar, enciende también las pestañas admin integradas para que se vea algo
			cfg.enableDefaultAdminTabs();
		}
		if (adminRowBtn != null) {
			adminRowBtn.setMessage(adminRowLabel());
		}
	}

	/** X del chip de esquina de la fila admin (junto al botón "Fila admin"). */
	private static int cornerChipX() {
		return PANEL_X + 86;
	}

	/** Aplica una esquina a la fila admin y guarda al instante. */
	private void applyCorner(String corner) {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		cfg.admin.corner = CobbleTabsConfig.normalizeCorner(corner);
		CobbleTabsConfig.save(cfg);
		CobbleTabsClient.rebuildTabs();
	}

	/** Etiqueta del chip de esquina: el nombre de la esquina activa de la fila admin. */
	private Component cornerLabel() {
		String corner = CobbleTabsConfig.normalizeCorner(CobbleTabsClient.config().admin.corner);
		String key = switch (corner) {
			case "top_left" -> "cobbletabs.edit.corner.top_left";
			case "top_right" -> "cobbletabs.edit.corner.top_right";
			case "bottom_left" -> "cobbletabs.edit.corner.bottom_left";
			default -> "cobbletabs.edit.corner.bottom_right";
		};
		return Component.translatable(key);
	}

	// ==================================================================
	// Selector cíclico de lado (diálogo de edición)
	// ==================================================================

	/**
	 * Y de la fila de chips de negrita/activada dentro del diálogo: en las
	 * pestañas admin sube 16 px porque no hay selector de lado.
	 */
	private int togglesY() {
		return adminMode ? DLG_Y + 128 : DLG_Y + 144;
	}

	/** Y de la fila de swatches de color (también sube en admin). */
	private int paletteY() {
		return adminMode ? DLG_Y + 146 : DLG_Y + 162;
	}

	/** Región del chip de lado dentro del diálogo (oculto en admin). */
	private static int sideChipX() {
		return DLG_X + 8;
	}

	private static int sideChipY() {
		return DLG_Y + 126;
	}

	/** Avanza al siguiente lado del ciclo y actualiza la pestaña en edición. */
	private void cycleSide() {
		if (editing == null) {
			return;
		}
		String current = CobbleTabsConfig.normalizeSide(editing.side);
		int idx = 0;
		for (int i = 0; i < SIDE_CYCLE.length; i++) {
			if (SIDE_CYCLE[i].equals(current)) {
				idx = i;
				break;
			}
		}
		editing.side = CobbleTabsConfig.normalizeSide(SIDE_CYCLE[(idx + 1) % SIDE_CYCLE.length]);
	}

	/** Etiqueta completa del chip de lado: "Lado: <nombre>". */
	private Component sideChipLabel() {
		String side = editing == null ? "" : CobbleTabsConfig.normalizeSide(editing.side);
		String key = switch (side) {
			case "left" -> "cobbletabs.edit.side.left";
			case "right" -> "cobbletabs.edit.side.right";
			case "top" -> "cobbletabs.edit.side.top";
			case "bottom" -> "cobbletabs.edit.side.bottom";
			default -> "cobbletabs.edit.side.auto";
		};
		return Component.translatable("cobbletabs.edit.side.chip", Component.translatable(key));
	}

	/** Etiqueta corta del lado de una pestaña (para el chip de cada fila de la lista). */
	private static Component sideShortLabel(String side) {
		String key = switch (CobbleTabsConfig.normalizeSide(side)) {
			case "left" -> "cobbletabs.edit.side.short.left";
			case "right" -> "cobbletabs.edit.side.short.right";
			case "top" -> "cobbletabs.edit.side.short.top";
			case "bottom" -> "cobbletabs.edit.side.short.bottom";
			default -> "cobbletabs.edit.side.short.auto";
		};
		return Component.translatable(key);
	}

	// ==================================================================
	// Diálogo de edición
	// ==================================================================

	/** Abre el diálogo para editar la pestaña en index, o una nueva si entry es null. */
	private void openEditor(CobbleTabsConfig.TabEntry entry, int index) {
		editing = entry == null ? new CobbleTabsConfig.TabEntry() : entry;
		editingIndex = index;
		commandBox.setVisible(true);
		iconBox.setVisible(true);
		labelBox.setVisible(true);
		colorBox.setVisible(true);
		commandBox.setValue(entry == null ? "/" : entry.command);
		iconBox.setValue(entry == null ? "minecraft:paper" : entry.icon);
		labelBox.setValue(entry == null ? "" : entry.label);
		colorBox.setValue(entry == null ? "" : entry.color);
		setFocused(entry == null ? commandBox : null);
		panelModeBtn.visible = false;
		panelAddBtn.visible = false;
		panelResetBtn.visible = false;
		adminRowBtn.visible = false;
	}

	private void closeEditor() {
		editing = null;
		editingIndex = -1;
		commandBox.setVisible(false);
		iconBox.setVisible(false);
		labelBox.setVisible(false);
		colorBox.setVisible(false);
		setFocused(null);
		panelModeBtn.visible = true;
		panelAddBtn.visible = true;
		panelResetBtn.visible = true;
		adminRowBtn.visible = true;
	}

	/** Guarda el diálogo: nueva pestaña o cambios sobre la existente. */
	private void commitEditor() {
		if (editing == null) {
			return;
		}
		editing.command = commandBox.getValue();
		editing.icon = iconBox.getValue();
		editing.label = labelBox.getValue();
		// Color libre: acepta nombre de paleta o hex #RRGGBB (vacío = color por defecto)
		editing.color = colorBox.getValue().trim();
		// Las admin se colocan solas en la esquina de la fila: el campo "side" no les aplica
		if (adminMode) {
			editing.side = "";
		}
		CobbleTabsConfig.sanitizeEntry(editing);
		if (editing.command.isBlank()) {
			editing.command = "/desconocido";
		}
		List<CobbleTabsConfig.TabEntry> list = currentList();
		if (editingIndex >= 0 && editingIndex < list.size()) {
			list.set(editingIndex, editing);
		} else {
			// Nueva pestaña: id único a partir del texto o del comando
			editing.id = uniqueId(list, editing.label.isBlank() ? editing.command.substring(1) : editing.label);
			list.add(editing);
		}
		save();
		closeEditor();
	}

	/** Pide confirmación y borra la pestaña en edición (los huecos protegidos se desactivan). */
	private void confirmDelete() {
		final CobbleTabsConfig.TabEntry target = editing;
		final int idx = editingIndex;
		if (target == null || idx < 0) {
			return;
		}
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				List<CobbleTabsConfig.TabEntry> list = currentList();
				if (idx < list.size() && list.get(idx) == target) {
					if (CobbleTabsConfig.isProtectedId(target.id)) {
						target.enabled = false;
					} else {
						list.remove(idx);
					}
					save();
				}
				// La pestaña borrada ya no existe: cerramos el diálogo y volvemos a la lista
				closeEditor();
			}
			minecraft.setScreen(this);
		}, Component.translatable("cobbletabs.edit.delete_title", target.id),
				Component.translatable("cobbletabs.edit.delete_confirm")));
	}

	/** X del botón ✕ de borrado rápido (pegado al borde derecho del panel). */
	private static int delBoxX() {
		return PANEL_X + PANEL_W - DELBOX_W - 4;
	}

	/** Genera un id único sin espacios a partir de un texto base. */
	private String uniqueId(List<CobbleTabsConfig.TabEntry> list, String base) {
		String clean = base.toLowerCase().trim().replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
		if (clean.isBlank()) {
			clean = "tab";
		}
		String id = clean;
		int n = 2;
		while (CobbleTabsConfig.findTabById(list, id) != null) {
			id = clean + n++;
		}
		return id;
	}

	/**
	 * Borrado rápido desde la lista (botón ✕ de la fila): misma regla que la ✕ del
	 * diálogo. Los huecos protegidos (extras y admin integradas) se desactivan en
	 * vez de borrarse, porque load() los volvería a añadir.
	 */
	private void deleteRow(int index) {
		List<CobbleTabsConfig.TabEntry> list = currentList();
		if (index < 0 || index >= list.size()) {
			return;
		}
		CobbleTabsConfig.TabEntry target = list.get(index);
		if (CobbleTabsConfig.isProtectedId(target.id)) {
			target.enabled = false;
		} else {
			list.remove(index);
		}
		save();
	}

	/** Confirmación antes de restaurar todas las pestañas por defecto. */
	private void confirmReset() {
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				CobbleTabsClient.config().resetToDefaults();
				save();
			}
			minecraft.setScreen(this);
		}, Component.translatable("cobbletabs.edit.reset_confirm_title"),
				Component.translatable("cobbletabs.edit.reset_confirm")));
	}

	// ==================================================================
	// Render
	// ==================================================================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// El fondo del diálogo va antes que los widgets (campos de texto)
		if (editing != null) {
			renderDialogBackground(graphics);
		}
		// Widgets: campos de texto del diálogo y botones del panel (ocultos al editar)
		super.render(graphics, mouseX, mouseY, partialTick);

		if (editing == null) {
			renderPanel(graphics, mouseX, mouseY);
		} else {
			// Capa superior del diálogo: bordes, títulos, chips, swatches y botones de texto
			renderDialogOverlay(graphics, mouseX, mouseY);
		}
	}

	/** Dibuja el panel con la lista de pestañas. */
	private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		int x0 = PANEL_X;
		int x1 = PANEL_X + PANEL_W;
		int y0 = PANEL_Y;
		int y1 = PANEL_Y + PANEL_H;
		int listY = y0 + LIST_DY;

		graphics.fill(x0, y0, x1, y1, 0xE0101018);
		int border = 0xFF4A4A55;
		graphics.fill(x0, y0, x1, y0 + 1, border);
		graphics.fill(x0, y0, x0 + 1, y1, border);
		graphics.fill(x1 - 1, y0, x1, y1, border);
		graphics.fill(x0, y1 - 1, x1, y1, border);

		List<CobbleTabsConfig.TabEntry> list = currentList();
		int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
		scroll = Math.min(scroll, maxScroll);

		// Filas visibles
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row;
			if (index >= list.size()) {
				break;
			}
			CobbleTabsConfig.TabEntry t = list.get(index);
			int y = listY + row * ROW_H;
			boolean rowHovered = mouseX >= x0 + 3 && mouseX < x1 - 3 && mouseY >= y && mouseY < y + ROW_H;
			if (index == draggingRow) {
				graphics.fill(x0 + 3, y, x1 - 3, y + ROW_H, 0xFF2A3A5A);
			} else if (rowHovered) {
				graphics.fill(x0 + 3, y, x1 - 3, y + ROW_H, 0x8032323C);
			}
			// Icono (se puede arrastrar para reordenar)
			ItemStack icon = CobbleTabsClient.itemStackFor(t.icon);
			graphics.renderItem(icon, x0 + 5, y);
			// Nombre + color de la pestaña, tachado (gris) si está desactivada
			int rgb = CobbleTabsConfig.parseColor(t.color, CobbleTabsConfig.defaultColorFor(t.id));
			int textColor = t.enabled ? (0xFF000000 | rgb) : 0xFF707078;
			String name = t.label.isBlank() ? t.id : t.label;
			int maxName = 92;
			while (font.width(name) > maxName && name.length() > 1) {
				name = name.substring(0, name.length() - 1);
			}
			graphics.drawString(font, name, x0 + 24, y + 4, textColor, false);
			// Chip con el lado del inventario (solo pestañas normales: las admin van a la esquina de su fila)
			if (!adminMode) {
				String side = CobbleTabsConfig.normalizeSide(t.side);
				boolean fixed = !side.isEmpty();
				int cx = x0 + 118;
				int cy = y + 2;
				graphics.fill(cx, cy, cx + 44, cy + 11, 0xFF1E1E28);
				int chipBorder = fixed ? 0xFF2E6E70 : 0xFF3A3A46;
				graphics.fill(cx, cy, cx + 44, cy + 1, chipBorder);
				graphics.fill(cx, cy + 10, cx + 44, cy + 11, chipBorder);
				graphics.fill(cx, cy, cx + 1, cy + 11, chipBorder);
				graphics.fill(cx + 43, cy, cx + 44, cy + 11, chipBorder);
				Component shortSide = sideShortLabel(t.side);
				graphics.drawString(font, shortSide, cx + 4, cy + 2, fixed ? 0xFF7ADCDE : 0xFF9A9AA8, false);
			}
			// Comando a la derecha, antes del botón ✕ de borrado rápido
			int cmdX = delBoxX() - 4 - font.width(t.command);
			graphics.drawString(font, t.command, cmdX, y + 4, 0xFF6A6A78, false);
			// Botón ✕ de borrado rápido al final de la fila (rojo al pasar el ratón)
			graphics.drawString(font, "✕", delBoxX() + 3, y + 4, rowHovered ? 0xFFFF5555 : 0xFF903030, false);
		}

		// Barra de scroll (cuando la lista no cabe entera)
		if (maxScroll > 0) {
			int trackY = listY;
			int trackH = VISIBLE_ROWS * ROW_H;
			graphics.fill(x1 - 3, trackY, x1 - 1, trackY + trackH, 0xFF23232C);
			int thumbH = Math.max(12, trackH * VISIBLE_ROWS / list.size());
			int thumbY = trackY + (trackH - thumbH) * scroll / maxScroll;
			graphics.fill(x1 - 3, thumbY, x1 - 1, thumbY + thumbH, 0xFF4A4A55);
		}

		// Título del panel + modo y nº de pestañas a la derecha
		graphics.drawString(font, Component.translatable("cobbletabs.edit.title"), x0 + 4, y0 + 5, 0xFF55FFFF, true);
		Component mode = modeLabel().copy().append(Component.literal(" (" + list.size() + ")"));
		graphics.drawString(font, mode, x1 - 4 - font.width(mode), y0 + 5, 0xFFFFFF, true);

		// Separador bajo la cabecera
		graphics.fill(x0 + 1, y0 + 18, x1 - 1, y0 + 19, 0xFF33333E);

		// Chip de esquina de la fila admin (junto al botón "Fila admin" del pie)
		boolean chipHovered = mouseX >= cornerChipX() && mouseX < cornerChipX() + 64
				&& mouseY >= ROW_A_Y && mouseY < ROW_A_Y + 16;
		boolean adminOn = CobbleTabsClient.config().admin.enabled;
		renderChip(graphics, cornerChipX(), ROW_A_Y, 64, 16, cornerLabel(),
				adminOn ? 0xFF7ADCDE : 0xFF9A9AA8, chipHovered ? 0xFF7ADCDE : 0xFF3A3A46);
		if (chipHovered) {
			graphics.renderTooltip(font, Component.translatable("cobbletabs.edit.corner.tip"), mouseX, mouseY);
		}

		// Hint del hueco seleccionado (lista vacía)
		if (list.isEmpty()) {
			graphics.drawString(font, Component.translatable("cobbletabs.edit.empty"), x0 + 6, listY + 10, 0xFF808090, false);
		}
	}

	/** Fondo del diálogo: se dibuja antes que los widgets (campos de texto). */
	private void renderDialogBackground(GuiGraphics graphics) {
		graphics.fill(DLG_X, DLG_Y, DLG_X + DLG_W, DLG_Y + DLG_H, 0xF01C1C24);
	}

	/**
	 * Capa superior del diálogo: bordes, títulos, chips, swatches y botones de texto.
	 * Se dibuja después de super.render para que no quede tapada por los campos.
	 */
	private void renderDialogOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		int x0 = DLG_X;
		int x1 = DLG_X + DLG_W;
		int y0 = DLG_Y;
		int y1 = DLG_Y + DLG_H;
		int lx = x0 + 8;

		int border = editingIndex >= 0 ? 0xFF7A7A88 : 0xFF9FBF7F;
		graphics.fill(x0, y0, x1, y0 + 1, border);
		graphics.fill(x0, y0, x0 + 1, y1, border);
		graphics.fill(x1 - 1, y0, x1, y1, border);
		graphics.fill(x0, y1 - 1, x1, y1, border);

		String title = editingIndex >= 0 ? editing.id : Component.translatable("cobbletabs.edit.new").getString();
		graphics.drawString(font, title, lx, y0 + 5, 0xFFFFA0, true);

		// ✕ = borrar la pestaña en edición (solo si ya existe)
		if (editingIndex >= 0) {
			boolean delHovered = mouseX >= x1 - 22 && mouseX < x1 - 6 && mouseY >= y0 + 2 && mouseY < y0 + 18;
			graphics.drawString(font, "✕", x1 - 17, y0 + 5, delHovered ? 0xFFFF5555 : 0xFFB05050, true);
		}

		// Separador bajo el título
		graphics.fill(x0 + 1, y0 + 17, x1 - 1, y0 + 18, 0xFF33333E);

		// Etiquetas de los campos (encima de cada caja de texto)
		graphics.drawString(font, Component.translatable("cobbletabs.edit.command"), lx, y0 + 22, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.icon"), lx, y0 + 48, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.label"), lx, y0 + 74, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.color"), lx, y0 + 100, 0xFFA0A0B0, false);

		// Previsualización del icono
		graphics.renderItem(CobbleTabsClient.itemStackFor(iconBox.getValue()), x1 - 26, y0 + 52);

		// Chip de lado (oculto en las pestañas admin: su posición la marca la fila admin)
		if (!adminMode) {
			boolean sideHovered = mouseX >= sideChipX() && mouseX < sideChipX() + 92
					&& mouseY >= sideChipY() && mouseY < sideChipY() + 14;
			String side = CobbleTabsConfig.normalizeSide(editing.side);
			renderChip(graphics, sideChipX(), sideChipY(), 92, 14, sideChipLabel(),
					side.isEmpty() ? 0xFFE0E0E8 : 0xFF7ADCDE, sideHovered ? 0xFF55FFFF : 0xFF3A3A46);
			graphics.drawString(font, Component.translatable("cobbletabs.edit.side.cycle_hint"),
					sideChipX() + 98, sideChipY() + 3, 0xFF707078, false);
			if (sideHovered) {
				graphics.renderTooltip(font, Component.translatable("cobbletabs.edit.side.tip"), mouseX, mouseY);
			}
		}

		// Chips de negrita y activada
		int ty = togglesY();
		boolean boldHovered = mouseX >= lx && mouseX < lx + 26 && mouseY >= ty && mouseY < ty + 14;
		boolean onHovered = mouseX >= lx + 30 && mouseX < lx + 64 && mouseY >= ty && mouseY < ty + 14;
		renderChip(graphics, lx, ty, 26, 14, Component.translatable("cobbletabs.edit.bold"),
				editing.bold ? 0xFFFFFF : 0xFF707078, editing.bold ? 0xFF9A9AB0 : 0xFF3A3A46);
		renderChip(graphics, lx + 30, ty, 34, 14, enabledLabel(),
				editing.enabled ? 0x55FF55 : 0xFF5555, editing.enabled ? 0xFF3F8F3F : 0xFF8F3F3F);
		if (boldHovered) {
			graphics.renderTooltip(font, Component.translatable("cobbletabs.edit.bold.tip"), mouseX, mouseY);
		}
		if (onHovered) {
			graphics.renderTooltip(font, Component.translatable("cobbletabs.edit.enabled.tip"), mouseX, mouseY);
		}

		// Swatches de color rápido (índice 0 = "sin color": usa el color clásico de la pestaña)
		int py = paletteY();
		int selIdx = CobbleTabsConfig.paletteIndexOf(editing.color);
		for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
			int sx = swatchX(i);
			CobbleTabsConfig.PaletteEntry e = CobbleTabsConfig.PALETTE.get(i);
			int rgb = 0xFF000000 | Integer.parseInt(e.hex().substring(1), 16);
			boolean selected = i == selIdx;
			graphics.fill(sx, py, sx + 13, py + 13, selected ? 0xFFFFFFFF : rgb);
			if (selected) {
				graphics.fill(sx + 1, py + 1, sx + 12, py + 12, rgb);
			}
			if (i == 0) {
				// Raya diagonal gris en el swatch "sin color"
				graphics.fill(sx + 2, py + 10, sx + 11, py + 11, 0xFFA0A0B0);
				graphics.fill(sx + 4, py + 8, sx + 9, py + 9, 0xFFA0A0B0);
				graphics.fill(sx + 6, py + 6, sx + 8, py + 7, 0xFFA0A0B0);
			}
		}

		// Vista previa en vivo del color escrito (nombre de paleta o hex #RRGGBB)
		String typed = colorBox.getValue().trim();
		if (!typed.isBlank()) {
			int preview = CobbleTabsConfig.parseColor(typed, -1);
			if (preview >= 0) {
				int pvX = swatchX(CobbleTabsConfig.PALETTE.size()) + 1;
				graphics.fill(pvX, py, pvX + 13, py + 13, 0xFF000000 | preview);
				graphics.fill(pvX, py, pvX + 13, py + 1, 0xFF4A4A55);
				graphics.fill(pvX, py + 12, pvX + 13, py + 13, 0xFF4A4A55);
				graphics.fill(pvX, py, pvX + 1, py + 13, 0xFF4A4A55);
				graphics.fill(pvX + 12, py, pvX + 13, py + 13, 0xFF4A4A55);
			}
		}

		// Separador del pie + Guardar / Cancelar (botones de texto)
		graphics.fill(x0 + 1, y1 - 24, x1 - 1, y1 - 23, 0xFF33333E);
		boolean saveHovered = mouseX >= lx && mouseX < lx + 110 && mouseY >= y1 - 20 && mouseY < y1 - 6;
		boolean cancelHovered = mouseX >= lx + 120 && mouseX < x1 - 8 && mouseY >= y1 - 20 && mouseY < y1 - 6;
		if (saveHovered) {
			graphics.fill(lx - 2, y1 - 21, lx + 112, y1 - 5, 0x40204020);
		}
		if (cancelHovered) {
			graphics.fill(lx + 118, y1 - 21, x1 - 6, y1 - 5, 0x40402020);
		}
		graphics.drawString(font, Component.translatable("cobbletabs.edit.save"), lx, y1 - 17,
				saveHovered ? 0xFFB8FFB8 : 0xFF7FE37F, true);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.cancel"), lx + 120, y1 - 17,
				cancelHovered ? 0xFFFFB8B8 : 0xFFE37F7F, true);
	}

	/** Chip plano con borde y texto: control compacto reutilizable. */
	private void renderChip(GuiGraphics graphics, int x, int y, int w, int h, Component text, int textColor, int borderColor) {
		graphics.fill(x, y, x + w, y + h, 0xFF22222C);
		graphics.fill(x, y, x + w, y + 1, borderColor);
		graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
		graphics.fill(x, y, x + 1, y + h, borderColor);
		graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
		graphics.drawString(font, text, x + 4, y + (h - 8) / 2, textColor, false);
	}

	private Component enabledLabel() {
		return Component.translatable(editing.enabled ? "cobbletabs.edit.on" : "cobbletabs.edit.off");
	}

	/** X del swatch de color i. */
	private int swatchX(int i) {
		return DLG_X + 8 + i * 15;
	}

	// ==================================================================
	// Entrada: ratón
	// ==================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int x0 = PANEL_X;
		int x1 = PANEL_X + PANEL_W;

		if (editing != null) {
			int dx0 = DLG_X;
			int dx1 = DLG_X + DLG_W;
			int dy0 = DLG_Y;
			int dy1 = DLG_Y + DLG_H;

			// ✕ = borrar la pestaña en edición (con confirmación)
			if (editingIndex >= 0 && mouseY >= dy0 + 2 && mouseY < dy0 + 18 && mouseX >= dx1 - 22 && mouseX < dx1 - 6) {
				confirmDelete();
				return true;
			}
			// Chip de lado: cicla Automático → Izquierda → Derecha → Arriba → Abajo (oculto en admin)
			if (!adminMode && mouseX >= sideChipX() && mouseX < sideChipX() + 92
					&& mouseY >= sideChipY() && mouseY < sideChipY() + 14) {
				cycleSide();
				return true;
			}
			// Chips de negrita y activada
			int ty = togglesY();
			if (mouseY >= ty && mouseY < ty + 14) {
				if (mouseX >= DLG_X + 8 && mouseX < DLG_X + 34) {
					editing.bold = !editing.bold;
					return true;
				}
				if (mouseX >= DLG_X + 38 && mouseX < DLG_X + 72) {
					editing.enabled = !editing.enabled;
					return true;
				}
			}
			// Swatches de color (índice 0 = "sin color": pone el color vacío)
			int py = paletteY();
			if (mouseY >= py && mouseY < py + 13) {
				for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
					int sx = swatchX(i);
					if (mouseX >= sx && mouseX < sx + 13) {
						CobbleTabsConfig.PaletteEntry e = CobbleTabsConfig.PALETTE.get(i);
						editing.color = e.key() == null ? "" : e.key();
						colorBox.setValue(editing.color);
						return true;
					}
				}
			}
			// Guardar / Cancelar (botones de texto del pie del diálogo)
			if (mouseY >= dy1 - 20 && mouseY < dy1 - 6) {
				if (mouseX >= dx0 + 8 && mouseX < dx0 + 118) {
					commitEditor();
					return true;
				}
				if (mouseX >= dx0 + 128 && mouseX < dx1 - 8) {
					closeEditor();
					return true;
				}
			}
			// Clic fuera del diálogo: lo cierra sin guardar
			if (mouseX < dx0 || mouseX > dx1 || mouseY < dy0 || mouseY > dy1) {
				closeEditor();
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		// Chip de esquina de la fila admin: cicla entre las 4 esquinas al instante
		if (mouseX >= cornerChipX() && mouseX < cornerChipX() + 64 && mouseY >= ROW_A_Y && mouseY < ROW_A_Y + 16) {
			String current = CobbleTabsConfig.normalizeCorner(CobbleTabsClient.config().admin.corner);
			int idx = 0;
			for (int i = 0; i < CORNER_CYCLE.length; i++) {
				if (CORNER_CYCLE[i].equals(current)) {
					idx = i;
					break;
				}
			}
			applyCorner(CORNER_CYCLE[(idx + 1) % CORNER_CYCLE.length]);
			return true;
		}

		// Filas: clic abre el editor, chip de lado cambia el lado, clic en el icono arrastra
		int listY = PANEL_Y + LIST_DY;
		List<CobbleTabsConfig.TabEntry> list = currentList();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row;
			if (index >= list.size()) {
				break;
			}
			int y = listY + row * ROW_H;
			if (mouseY >= y && mouseY < y + ROW_H && mouseX >= x0 + 3 && mouseX < x1 - 3) {
				// ✕ al final de la fila: borrado rápido sin abrir el diálogo
				if (mouseX >= delBoxX() - 2 && button == 0) {
					deleteRow(index);
					return true;
				}
				// Chip de lado de la fila: cicla el lado y guarda al instante (solo pestañas normales)
				if (!adminMode && mouseX >= x0 + 118 && mouseX < x0 + 162 && button == 0) {
					CobbleTabsConfig.TabEntry t = list.get(index);
					String current = CobbleTabsConfig.normalizeSide(t.side);
					int idx = 0;
					for (int i = 0; i < SIDE_CYCLE.length; i++) {
						if (SIDE_CYCLE[i].equals(current)) {
							idx = i;
							break;
						}
					}
					t.side = CobbleTabsConfig.normalizeSide(SIDE_CYCLE[(idx + 1) % SIDE_CYCLE.length]);
					save();
					return true;
				}
				if (mouseX < x0 + 24 && button == 0) {
					draggingRow = index;
					dragStartY = mouseY;
				} else {
					openEditor(list.get(index), index);
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingRow >= 0) {
			if (Math.abs(mouseY - dragStartY) > ROW_H / 2.0) {
				int steps = (int) Math.round((mouseY - dragStartY) / (double) ROW_H);
				if (steps != 0) {
					moveRow(draggingRow, steps);
					dragStartY += steps * ROW_H;
				}
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingRow = -1;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (editing == null) {
			int maxScroll = Math.max(0, currentList().size() - VISIBLE_ROWS);
			int newScroll = scroll - (int) Math.signum(scrollY);
			scroll = Math.max(0, Math.min(maxScroll, newScroll));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Mueve una pestaña n posiciones arriba/abajo dentro de la lista. */
	private void moveRow(int index, int steps) {
		List<CobbleTabsConfig.TabEntry> list = currentList();
		int target = Math.max(0, Math.min(list.size() - 1, index + steps));
		if (target == index) {
			return;
		}
		CobbleTabsConfig.TabEntry t = list.remove(index);
		list.add(target, t);
		draggingRow = target;
		save();
	}

	// ==================================================================
	// Entrada: teclado
	// ==================================================================

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (editing != null) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitEditor();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeEditor();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
