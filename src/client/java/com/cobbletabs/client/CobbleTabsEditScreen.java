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
 */
public class CobbleTabsEditScreen extends Screen {

	/** Pantalla a la que volver al cerrar (normalmente el inventario). */
	private final Screen parent;

	// Panel principal (público para el auto-test)
	static final int PANEL_X = 25;
	static final int PANEL_W = 211;
	static final int PANEL_Y = 18;
	static final int PANEL_H = 214;
	static final int ROW_H = 14;
	/** Filas visibles de la lista (con scroll si hay más pestañas). */
	private static final int VISIBLE_ROWS = 11;
	/** Ancho de la zona clicable del botón ✕ de borrado rápido (al final de cada fila). */
	private static final int DELBOX_W = 9;

	// Diálogo de edición
	// Diálogo de edición (público para el auto-test)
	static final int DLG_X = 35;
	static final int DLG_W = 181;
	static final int DLG_Y = 26;
	static final int DLG_H = 152;
	private static final int FIELD_H = 12;

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
	/** Botón que despliega el menú de esquinas de la fila admin. */
	private Button cornerBtn;
	/** true si el menú desplegable de esquinas está abierto. */
	private boolean cornerMenuOpen;

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

	@Override
	protected void init() {
		int x0 = PANEL_X;
		int y1 = PANEL_Y + PANEL_H;
		int fx = x0 + 4;

		// Selector de lista: Pestañas / Admins (ocultos mientras el diálogo está abierto)
		panelModeBtn = Button.builder(modeLabel(), b -> {
			adminMode = !adminMode;
			scroll = 0;
			b.setMessage(modeLabel());
		}).bounds(fx, y1 - 20, 45, 16).build();
		addRenderableWidget(panelModeBtn);

		// Añadir nueva pestaña
		panelAddBtn = Button.builder(Component.translatable("cobbletabs.edit.add"), b -> openEditor(null, -1))
				.bounds(fx + 47, y1 - 20, 42, 16).build();
		addRenderableWidget(panelAddBtn);

		// Restaurar pestañas por defecto
		panelResetBtn = Button.builder(Component.translatable("cobbletabs.edit.reset"), b -> confirmReset())
				.bounds(fx + 91, y1 - 20, 58, 16).build();
		addRenderableWidget(panelResetBtn);

		// Opciones de la fila admin: activarla y elegir esquina (segunda fila de botones)
		adminRowBtn = Button.builder(adminRowLabel(), b -> toggleAdminRow())
				.bounds(fx, y1 - 40, 66, 16)
				.tooltip(Tooltip.create(Component.translatable("cobbletabs.edit.admin_row.tip")))
				.build();
		addRenderableWidget(adminRowBtn);

		cornerBtn = Button.builder(cornerLabel(), b -> cornerMenuOpen = !cornerMenuOpen)
				.bounds(fx + CORNER_BTN_DX, y1 - CORNER_BTN_DY, MENU_W, 16)
				.tooltip(Tooltip.create(Component.translatable("cobbletabs.edit.corner.tip")))
				.build();
		addRenderableWidget(cornerBtn);

		// Pantalla de presets (siempre disponible)
		addRenderableWidget(Button.builder(Component.translatable("cobbletabs.edit.presets"),
						b -> minecraft.setScreen(new CobbleTabsPresetsScreen(this)))
				.bounds(fx + 148, y1 - 40, 55, 16).build());

		// Campos del diálogo de edición (ocultos hasta abrirlo)
		commandBox = new EditBox(font, DLG_X + 6, DLG_Y + 25, DLG_W - 12, FIELD_H, Component.translatable("cobbletabs.edit.command"));
		commandBox.setMaxLength(256);
		commandBox.setHint(Component.literal("/comando"));
		iconBox = new EditBox(font, DLG_X + 6, DLG_Y + 47, DLG_W - 34, FIELD_H, Component.translatable("cobbletabs.edit.icon"));
		iconBox.setMaxLength(256);
		iconBox.setHint(Component.literal("minecraft:paper"));
		labelBox = new EditBox(font, DLG_X + 6, DLG_Y + 69, DLG_W - 12, FIELD_H, Component.translatable("cobbletabs.edit.label"));
		labelBox.setMaxLength(64);
		labelBox.setHint(Component.literal("Nombre"));
		// Color libre: nombre de paleta (gray, green…) o RGB hex #RRGGBB
		colorBox = new EditBox(font, DLG_X + 6, DLG_Y + 91, 80, FIELD_H, Component.translatable("cobbletabs.edit.color"));
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
		rebuildAdminButtons();
	}

	/** Esquinas del menú desplegable, en el orden en que se muestran. */
	private static final String[] CORNER_KEYS = { "top_left", "top_right", "bottom_left", "bottom_right" };
	/** Ancho del menú desplegable de esquinas (igual que el botón que lo abre). */
	private static final int MENU_W = 78;
	/** Alto de cada opción del menú desplegable. */
	private static final int MENU_ITEM_H = 12;
	/** Posición del botón de esquina dentro de la segunda fila de botones. */
	private static final int CORNER_BTN_DX = 68;
	private static final int CORNER_BTN_DY = 40;

	/** Aplica una esquina a la fila admin y guarda al instante. */
	private void applyCorner(String corner) {
		CobbleTabsConfig cfg = CobbleTabsClient.config();
		cfg.admin.corner = CobbleTabsConfig.normalizeCorner(corner);
		CobbleTabsConfig.save(cfg);
		CobbleTabsClient.rebuildTabs();
		if (cornerBtn != null) {
			cornerBtn.setMessage(cornerLabel());
		}
	}

	/** Refresca la etiqueta del botón de fila admin tras un cambio. */
	private void rebuildAdminButtons() {
		if (adminRowBtn != null) {
			adminRowBtn.setMessage(adminRowLabel());
		}
	}

	/** Etiqueta del botón de esquina: el nombre corto de la esquina activa de la fila admin. */
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

	/** X del menú desplegable: alineado con el botón que lo abre. */
	private static int cornerMenuX() {
		return PANEL_X + 4 + CORNER_BTN_DX;
	}

	/** Y del menú desplegable: se abre hacia arriba desde el borde superior del botón. */
	private static int cornerMenuY() {
		return PANEL_Y + PANEL_H - CORNER_BTN_DY - CORNER_KEYS.length * MENU_ITEM_H;
	}

	/** Y del centro de la opción i del menú de esquinas (para el auto-test). */
	static int cornerMenuItemY(int index) {
		return cornerMenuY() + index * MENU_ITEM_H + MENU_ITEM_H / 2;
	}

	/** true si el menú desplegable de esquinas está abierto (solo para el auto-test). */
	boolean selftestCornerMenuOpen() {
		return cornerMenuOpen;
	}

	/** Dibuja el menú desplegable con las 4 esquinas (la activa resaltada en cian). */
	private void renderCornerMenu(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = cornerMenuX();
		int y = cornerMenuY();
		int h = CORNER_KEYS.length * MENU_ITEM_H;
		graphics.fill(x, y, x + MENU_W, y + h, 0xF01C1C24);
		int border = 0xFF7A7A88;
		graphics.fill(x, y, x + MENU_W, y + 1, border);
		graphics.fill(x, y, x + 1, y + h, border);
		graphics.fill(x + MENU_W - 1, y, x + MENU_W, y + h, border);
		graphics.fill(x, y + h - 1, x + MENU_W, y + h, border);

		String current = CobbleTabsConfig.normalizeCorner(CobbleTabsClient.config().admin.corner);
		for (int i = 0; i < CORNER_KEYS.length; i++) {
			int iy = y + i * MENU_ITEM_H;
			boolean hovered = mouseX >= x && mouseX < x + MENU_W && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
			if (hovered) {
				graphics.fill(x + 1, iy, x + MENU_W - 1, iy + MENU_ITEM_H, 0x80404A5A);
			}
			Component label = Component.translatable("cobbletabs.edit.corner." + CORNER_KEYS[i]);
			int color = CORNER_KEYS[i].equals(current) ? 0xFF55FFFF : 0xFFE0E0E8;
			graphics.drawString(font, label, x + 4, iy + 2, color, false);
		}
	}

	// ==================================================================
	// Diálogo de edición
	// ==================================================================

	/** Abre el diálogo para editar la pestaña en index, o una nueva si entry es null. */
	private void openEditor(CobbleTabsConfig.TabEntry entry, int index) {
		editing = entry == null ? new CobbleTabsConfig.TabEntry() : entry;
		editingIndex = index;
		cornerMenuOpen = false;
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
		cornerBtn.visible = false;
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
		cornerBtn.visible = true;
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
			// Menú de esquinas: capa superior, sobre la lista
			if (cornerMenuOpen) {
				renderCornerMenu(graphics, mouseX, mouseY);
			}
		} else {
			// Capa superior del diálogo: bordes, títulos, swatches y botones de texto
			renderDialogOverlay(graphics, mouseX, mouseY);
		}
	}

	/** Dibuja el panel con la lista de pestañas. */
	private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		int x0 = PANEL_X;
		int x1 = PANEL_X + PANEL_W;
		int y0 = PANEL_Y;
		int y1 = PANEL_Y + PANEL_H;

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
			int y = y0 + 20 + row * ROW_H;
			boolean rowHovered = mouseX >= x0 + 3 && mouseX < x1 - 3 && mouseY >= y && mouseY < y + ROW_H;
			if (index == draggingRow) {
				graphics.fill(x0 + 3, y, x1 - 3, y + ROW_H, 0xFF2A3A5A);
			} else if (rowHovered) {
				graphics.fill(x0 + 3, y, x1 - 3, y + ROW_H, 0x8032323C);
			}
			// Icono (se puede arrastrar para reordenar)
			ItemStack icon = CobbleTabsClient.itemStackFor(t.icon);
			graphics.renderItem(icon, x0 + 5, y - 1);
			// Nombre + color de la pestaña, tachado si está desactivada
			int rgb = CobbleTabsConfig.parseColor(t.color, CobbleTabsConfig.defaultColorFor(t.id));
			int textColor = t.enabled ? (0xFF000000 | rgb) : 0xFF707078;
			String name = t.label.isBlank() ? t.id : t.label;
			int maxName = PANEL_W - 119;
			while (font.width(name) > maxName && name.length() > 1) {
				name = name.substring(0, name.length() - 1);
			}
			graphics.drawString(font, name, x0 + 26, y + 3, textColor, false);
			// Comando a la derecha, antes del botón ✕ de borrado rápido
			int cmdX = delBoxX() - 4 - font.width(t.command);
			graphics.drawString(font, t.command, cmdX, y + 3, 0xFF808090, false);
			// Botón ✕ de borrado rápido al final de la fila (rojo al pasar el ratón)
			graphics.drawString(font, "✕", delBoxX(), y + 3, rowHovered ? 0xFFFF5555 : 0xFF903030, false);
		}

		// Indicador de scroll (alineado a la derecha de la última fila visible)
		if (maxScroll > 0) {
			Component scrollHint = Component.translatable("cobbletabs.edit.scroll_hint");
			graphics.drawString(font, scrollHint, x1 - 4 - font.width(scrollHint), y1 - 49, 0xFF707078, false);
		}

		// Título del panel + modo y nº de pestañas a la derecha
		graphics.drawString(font, Component.translatable("cobbletabs.edit.title"), x0 + 4, y0 + 6, 0xFF55FFFF, true);
		Component mode = modeLabel().copy().append(Component.literal(" (" + list.size() + ")"));
		graphics.drawString(font, mode, x1 - 4 - font.width(mode), y0 + 6, 0xFFFFFF, true);

		// Hint del hueco seleccionado (solo lista de pestañas normales)
		if (!adminMode && list.isEmpty()) {
			graphics.drawString(font, Component.translatable("cobbletabs.edit.empty"), x0 + 4, y0 + 26, 0xFF808090, false);
		}
	}

	/** Fondo del diálogo: se dibuja antes que los widgets (campos de texto). */
	private void renderDialogBackground(GuiGraphics graphics) {
		graphics.fill(DLG_X, DLG_Y, DLG_X + DLG_W, DLG_Y + DLG_H, 0xF01C1C24);
	}

	/**
	 * Capa superior del diálogo: bordes, títulos, swatches y botones de texto.
	 * Se dibuja después de super.render para que no quede tapada por los campos.
	 */
	private void renderDialogOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
		int x0 = DLG_X;
		int x1 = DLG_X + DLG_W;
		int y0 = DLG_Y;
		int y1 = DLG_Y + DLG_H;

		int border = editingIndex >= 0 ? 0xFF7A7A88 : 0xFF9FBF7F;
		graphics.fill(x0, y0, x1, y0 + 1, border);
		graphics.fill(x0, y0, x0 + 1, y1, border);
		graphics.fill(x1 - 1, y0, x1, y1, border);
		graphics.fill(x0, y1 - 1, x1, y1, border);

		String title = editingIndex >= 0 ? editing.id : Component.translatable("cobbletabs.edit.new").getString();
		graphics.drawString(font, title, x0 + 6, y0 + 5, 0xFFFFA0, true);

		// ✕ = borrar la pestaña en edición (solo si ya existe)
		if (editingIndex >= 0) {
			graphics.drawString(font, "✕", x1 - 11, y0 + 5, 0xFFFF5555, true);
		}

		// Toggles de negrita y activada (botones de texto)
		graphics.drawString(font, Component.translatable("cobbletabs.edit.bold"), x0 + 6, y0 + 121,
				editing.bold ? 0xFFFFFF : 0xFF707078, true);
		graphics.drawString(font, enabledLabel(), x0 + 24, y0 + 121,
				editing.enabled ? 0x55FF55 : 0xFF5555, true);

		graphics.drawString(font, Component.translatable("cobbletabs.edit.command"), x0 + 6, y0 + 17, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.icon"), x0 + 6, y0 + 39, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.label"), x0 + 6, y0 + 61, 0xFFA0A0B0, false);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.color"), x0 + 6, y0 + 83, 0xFFA0A0B0, false);

		// Previsualización del icono
		graphics.renderItem(CobbleTabsClient.itemStackFor(iconBox.getValue()), x1 - 22, y0 + 42);

		// Swatches de color rápido (índice 0 = "sin color": usa el color clásico de la pestaña)
		int selIdx = CobbleTabsConfig.paletteIndexOf(editing.color);
		for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
			int sx = swatchX(i);
			int sy = y0 + 103;
			CobbleTabsConfig.PaletteEntry e = CobbleTabsConfig.PALETTE.get(i);
			int rgb = 0xFF000000 | Integer.parseInt(e.hex().substring(1), 16);
			boolean selected = i == selIdx;
			graphics.fill(sx, sy, sx + 12, sy + 12, selected ? 0xFFFFFFFF : rgb);
			if (selected) {
				graphics.fill(sx + 1, sy + 1, sx + 11, sy + 11, rgb);
			}
			if (i == 0) {
				// Raya diagonal gris en el swatch "sin color"
				graphics.fill(sx + 2, sy + 9, sx + 10, sy + 10, 0xFFA0A0B0);
				graphics.fill(sx + 4, sy + 7, sx + 8, sy + 8, 0xFFA0A0B0);
				graphics.fill(sx + 6, sy + 5, sx + 7, sy + 6, 0xFFA0A0B0);
			}
		}

		// Vista previa en vivo del color escrito (nombre de paleta o hex #RRGGBB)
		String typed = colorBox.getValue().trim();
		if (!typed.isBlank()) {
			int preview = CobbleTabsConfig.parseColor(typed, -1);
			if (preview >= 0) {
				int pvX = swatchX(CobbleTabsConfig.PALETTE.size()) + 2;
				graphics.fill(pvX, y0 + 103, pvX + 12, y0 + 115, 0xFF000000 | preview);
				graphics.fill(pvX, y0 + 103, pvX + 12, y0 + 104, 0xFF4A4A55);
				graphics.fill(pvX, y0 + 114, pvX + 12, y0 + 115, 0xFF4A4A55);
				graphics.fill(pvX, y0 + 103, pvX + 1, y0 + 115, 0xFF4A4A55);
				graphics.fill(pvX + 11, y0 + 103, pvX + 12, y0 + 115, 0xFF4A4A55);
			}
		}

		// Guardar / Cancelar (botones de texto)
		graphics.drawString(font, Component.translatable("cobbletabs.edit.save"), x0 + 6, y1 - 10, 0xFF7FE37F, true);
		graphics.drawString(font, Component.translatable("cobbletabs.edit.cancel"), x0 + 90, y1 - 10, 0xFFE37F7F, true);
	}

	private Component enabledLabel() {
		return Component.translatable(editing.enabled ? "cobbletabs.edit.on" : "cobbletabs.edit.off");
	}

	/** X del swatch de color i. */
	private int swatchX(int i) {
		return DLG_X + 6 + i * 14;
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
			if (editingIndex >= 0 && mouseY >= dy0 && mouseY < dy0 + 14 && mouseX >= dx1 - 16 && mouseX < dx1 - 2) {
				confirmDelete();
				return true;
			}
			// Swatches de color (índice 0 = "sin color": pone el color vacío)
			if (mouseY >= dy0 + 103 && mouseY < dy0 + 115) {
				for (int i = 0; i < CobbleTabsConfig.PALETTE.size(); i++) {
					int sx = swatchX(i);
					if (mouseX >= sx && mouseX < sx + 12) {
						CobbleTabsConfig.PaletteEntry e = CobbleTabsConfig.PALETTE.get(i);
						editing.color = e.key() == null ? "" : e.key();
						colorBox.setValue(editing.color);
						return true;
					}
				}
			}
			// Toggles de negrita y activada
			if (mouseY >= dy0 + 118 && mouseY < dy0 + 130) {
				if (mouseX >= dx0 + 4 && mouseX < dx0 + 22) {
					editing.bold = !editing.bold;
					return true;
				}
				if (mouseX >= dx0 + 22 && mouseX < dx0 + 60) {
					editing.enabled = !editing.enabled;
					return true;
				}
			}
			// Guardar / Cancelar (botones de texto del pie del diálogo)
			if (mouseY >= dy1 - 14 && mouseY < dy1 - 2) {
				if (mouseX >= dx0 + 4 && mouseX < dx0 + 86) {
					commitEditor();
					return true;
				}
				if (mouseX >= dx0 + 88 && mouseX < dx0 + 170) {
					closeEditor();
					return true;
				}
			}
			// Clic fuera del diálogo: lo cierra sin guardar si venía de una fila nueva
			if (mouseX < dx0 || mouseX > dx1 || mouseY < dy0 || mouseY > dy1) {
				closeEditor();
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		// Menú de esquinas abierto: el clic elige una opción o cierra el menú
		if (cornerMenuOpen) {
			int mx = cornerMenuX();
			int my = cornerMenuY();
			if (mouseX >= mx && mouseX < mx + MENU_W && mouseY >= my && mouseY < my + CORNER_KEYS.length * MENU_ITEM_H) {
				applyCorner(CORNER_KEYS[(int) ((mouseY - my) / MENU_ITEM_H)]);
			}
			cornerMenuOpen = false;
			return true;
		}

		// Filas: clic abre el editor; clic en el icono empieza a arrastrar
		List<CobbleTabsConfig.TabEntry> list = currentList();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row;
			if (index >= list.size()) {
				break;
			}
			int y = PANEL_Y + 20 + row * ROW_H;
			if (mouseY >= y && mouseY < y + ROW_H && mouseX >= x0 + 3 && mouseX < x1 - 3) {
				// ✕ al final de la fila: borrado rápido sin abrir el diálogo
				if (mouseX >= delBoxX() - 2 && button == 0) {
					deleteRow(index);
					return true;
				}
				if (mouseX < x0 + 22 && button == 0) {
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
			if (cornerMenuOpen) {
				return true;
			}
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
