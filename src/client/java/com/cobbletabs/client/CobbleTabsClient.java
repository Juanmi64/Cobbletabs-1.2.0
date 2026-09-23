package com.cobbletabs.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CobbleTabsClient implements ClientModInitializer {
	public static final String MOD_ID = "cobbletabs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Tamaño de cada pestaña, similar a las pestañas del inventario creativo. */
	private static final int TAB_WIDTH = 26;
	private static final int TAB_HEIGHT = 30;
	/** Solape de la pestaña sobre el borde del fondo de la GUI. */
	private static final int OVERLAP = 2;
	/** Espacio entre pestañas apiladas verticalmente en los laterales. */
	private static final int TAB_GAP = 2;
	/** Separación desde el borde izquierdo del fondo de la GUI. */
	private static final int MARGIN = 3;

	/** Tecla para recargar la configuración sin reiniciar (por defecto F8). */
	private static KeyMapping reloadKey;
	/** Tecla para abrir el editor de pestañas desde el juego (sin asignar por defecto; Shift+F8 también funciona). */
	private static KeyMapping editorKey;

	/** Logo del mod, dibujado en la esquina superior derecha del inventario. */
	private static final ResourceLocation LOGO_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/logo.png");

	private static CobbleTabsConfig config;
	private static List<Tab> tabs = List.of();
	/** Pestañas admin activas, en el orden de la config (fila inferior derecha de la pantalla). */
	private static List<AdminTab> adminTabs = List.of();

	private record Tab(String command, Component label, String iconId) {
	}

	/** Pestaña admin: igual que una normal pero posicionada fuera de la GUI del inventario. */
	private record AdminTab(String command, Component label, String iconId) {
	}

	@Override
	public void onInitializeClient() {
		config = CobbleTabsConfig.load();
		buildTabs();

		reloadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.cobbletabs.reload",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8,
				"key.categories.cobbletabs"
		));

		// Atajo sin asignar por defecto (asignable en Controles); Shift+F8 también abre el editor
		editorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.cobbletabs.editor",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				"key.categories.cobbletabs"
		));

		// F8 (recargar) / Shift+F8 (editor) en el juego, sin pantallas abiertas
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.screen == null) {
				while (reloadKey.consumeClick()) {
					if (Screen.hasShiftDown()) {
						openEditorScreen(client, null);
					} else {
						reloadConfig(client);
					}
				}
				while (editorKey.consumeClick()) {
					openEditorScreen(client, null);
				}
			}
		});

		ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			// En cofres y enderchests (ContainerScreen) no se muestran las pestañas
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof ContainerScreen)) {
				// En cofres y enderchests (ChestScreen) no se muestran las pestañas
				// F8 con una pantalla de contenedor abierta (inventario, cofres...)
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyCode, scanCode, modifiers) -> {
					if (reloadKey.matches(keyCode, scanCode)) {
						if (Screen.hasShiftDown()) {
							openEditorScreen(client, s);
						} else {
							reloadConfig(client);
						}
						return false;
					}
					return true;
				});					ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
						renderTabs(graphics, container, mouseX, mouseY);
						// Pestañas admin: esquina inferior derecha de la PANTALLA, fuera de la GUI
						renderAdminTabs(graphics, mouseX, mouseY);
						// Botón de edición de pestañas: en el inventario del jugador (survival y creativo)
						if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
							renderEditButton(graphics, container, mouseX, mouseY);
						}
						// El logo solo se muestra en el inventario survival (no en creativo, cofres ni enderchests)
						if (screen instanceof InventoryScreen) {
							renderLogo(graphics, container);
						}
					});

				ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
					if (button == 0 || button == 1) {
						// Botón de edición de pestañas (inventario survival y creativo)
						if ((screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)
								&& editButtonAt(container, mouseX, mouseY)) {
							client.setScreen(new CobbleTabsEditScreen(s));
							return false;
						}
						// Botón de mostrar/ocultar pestañas
						if (toggleAt(container, mouseX, mouseY)) {
							config.tabsVisible = !config.tabsVisible;
							CobbleTabsConfig.save(config);
							return false;
						}
						// Botón de mostrar/ocultar la fila admin
						if (!adminTabs.isEmpty() && config.admin.showToggleButton && adminToggleAt(mouseX, mouseY)) {
							config.admin.visible = !config.admin.visible;
							CobbleTabsConfig.save(config);
							return false;
						}
						// Pestañas admin (esquina inferior derecha de la pantalla)
						AdminTab admin = adminTabAt(mouseX, mouseY);
						if (admin != null) {
							runCommand(admin.command());
							s.onClose();
							return false; // cancela el clic vanilla aunque estén fuera de la GUI
						}
						if (config.tabsVisible) {
							Tab clicked = tabAt(container, mouseX, mouseY);
							if (clicked != null) {
								runCommand(clicked.command());
								s.onClose();
								return false; // cancela el procesamiento vanilla del clic
							}
						}
					}
					return true;
				});
			}
		});
		LOGGER.info("[CobbleTabs] Inicializado: {} pestañas activas (config: {}).", tabs.size(), config.tabs.size());

		// Auto-test de desarrollo: inerte salvo que exista el archivo flag
		// (./gradlew runClient -Pselftest=true lo crea y borra el propio test).
		CobbleTabsSelftest.register();
	}

	/** Construye la lista de pestañas admin activas a partir de la configuración. */
	private static void buildAdminTabs() {
		List<AdminTab> list = new ArrayList<>();
		if (!config.admin.enabled) {
			adminTabs = List.of();
			return;
		}
		for (CobbleTabsConfig.TabEntry entry : config.adminTabs) {
			if (!entry.enabled) {
				continue;
			}
			MutableComponent base = entry.label.isBlank()
					? Component.translatable("cobbletabs.admin_tab." + entry.id)
					: Component.literal(entry.label);
			int rgb = CobbleTabsConfig.parseColor(entry.color, CobbleTabsConfig.defaultColorFor(entry.id));
			Component label = base.withStyle(style -> style.withBold(entry.bold).withColor(TextColor.fromRgb(rgb)));
			list.add(new AdminTab(entry.command, label, entry.icon));
		}
		adminTabs = List.copyOf(list);
	}

	/** Construye la lista de pestañas visibles a partir de la configuración. */
	private static void buildTabs() {
		List<Tab> list = new ArrayList<>();
		for (CobbleTabsConfig.TabEntry entry : config.tabs) {
			if (!entry.enabled) {
				continue;
			}
			MutableComponent base = entry.label.isBlank()
					? Component.translatable("cobbletabs.tab." + entry.id)
					: Component.literal(entry.label);
			// Estilo desde la config: negrita y color (nombre o hex).
			// Si la pestaña es una de las de por defecto y no tiene color, se usa su color clásico
			// (así las configs antiguas se migran solas sin tocar nada).
			int rgb = CobbleTabsConfig.parseColor(entry.color, CobbleTabsConfig.defaultColorFor(entry.id));
			boolean bold = entry.bold;
			Component label = base.withStyle(style -> style.withBold(bold).withColor(TextColor.fromRgb(rgb)));
			list.add(new Tab(entry.command, label, entry.icon));
		}
		tabs = List.copyOf(list);
		adminCorner = AdminCorner.fromConfig(config.admin.corner);
		buildAdminTabs();
	}

	/** Resuelve el item del icono; fallback a papel si el id no existe o Cobblemon no está. */
	public static ItemStack iconStack(Tab tab) {
		try {
			ResourceLocation id = ResourceLocation.parse(tab.iconId());
		Item item = BuiltInRegistries.ITEM.get(id);
		if (item != null && item != Items.AIR) {
				return new ItemStack(item);
			}
		} catch (Exception ignored) {
			// id con formato inválido
		}
		return new ItemStack(Items.PAPER);
	}

	/** Item de un icono por id (para previsualizaciones del editor y de presets). */
	public static ItemStack itemStackFor(String iconId) {
		return iconStack(new Tab("", Component.empty(), iconId));
	}

	// ==================================================================
	// Renderizado
	// ==================================================================

/**
	 * Cuántas pestañas caben por lado sin salirse del fondo de la GUI (2 de más
	 * con las pestañas extra). Si no caben todas, se reparten mejor entre ambos lados.
	 */
	public static int maxPerSide(AbstractContainerScreen<?> screen) {
		int available = screen.imageHeight - 2 * MARGIN + TAB_GAP;
		return Math.max(1, available / (TAB_HEIGHT + TAB_GAP));
	}

	/** Pestañas que van al lado izquierdo (la mitad, sin pasarse del alto de la GUI). */
	public static int leftCount(AbstractContainerScreen<?> screen) {
		return Math.min((tabs.size() + 1) / 2, maxPerSide(screen));
	}

	/** Pestañas en los laterales de la GUI: la mitad izquierda a la izquierda, el resto a la derecha. */
	private static void renderTabs(GuiGraphics graphics, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		renderToggle(graphics, client, screen, mouseX, mouseY);
		if (!config.tabsVisible) {
			return;
		}
		int leftCount = leftCount(screen);

		// Lado izquierdo
		int y = screen.topPos + MARGIN;
		for (int i = 0; i < leftCount; i++) {
			renderTab(graphics, client, tabs.get(i), screen.leftPos - TAB_WIDTH + OVERLAP, y, mouseX, mouseY);
			y += TAB_HEIGHT + TAB_GAP;
		}

		// Lado derecho
		y = screen.topPos + MARGIN;
		for (int i = leftCount; i < tabs.size(); i++) {
			renderTab(graphics, client, tabs.get(i), screen.leftPos + screen.imageWidth - OVERLAP, y, mouseX, mouseY);
			y += TAB_HEIGHT + TAB_GAP;
		}
	}

	// ==================================================================
	// Pestañas admin (fila en una esquina de la pantalla, fuera de la GUI)
	// ==================================================================

	/** Separación del conjunto admin respecto a los bordes de la pantalla. */
	private static final int ADMIN_MARGIN = 3;
	/** Separación entre el botón toggle admin y la fila de pestañas admin. */
	private static final int ADMIN_TOGGLE_GAP = 2;

	/** Esquinas donde puede anclarse el conjunto admin (fila + botón toggle). */
	private enum AdminCorner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		/** Esquina configurada, o inferior derecha si el valor es inválido. */
		static AdminCorner fromConfig(String corner) {
			try {
				return valueOf(CobbleTabsConfig.normalizeCorner(corner).toUpperCase());
			} catch (Exception e) {
				return BOTTOM_RIGHT;
			}
		}

		/** Ancla X de la primera pestaña admin (pegada al borde de la esquina). */
		int firstTabX(int screenWidth) {
			return switch (this) {
				case TOP_LEFT, BOTTOM_LEFT -> ADMIN_MARGIN;
				case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - ADMIN_MARGIN - TAB_WIDTH;
			};
		}

		/** Ancla Y de la primera pestaña admin. */
		int firstTabY(int screenHeight) {
			return switch (this) {
				case TOP_LEFT, TOP_RIGHT -> ADMIN_MARGIN;
				case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - ADMIN_MARGIN - TAB_HEIGHT;
			};
		}

		/** Ancla X del botón toggle (alineado con el borde de la esquina). */
		int toggleX(int screenWidth) {
			return switch (this) {
				case TOP_LEFT, BOTTOM_LEFT -> ADMIN_MARGIN;
				case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - ADMIN_MARGIN - TOGGLE_SIZE;
			};
		}

		/** Ancla Y del botón toggle, separado de la fila por ADMIN_TOGGLE_GAP. */
		int toggleY(int screenHeight) {
			return switch (this) {
				case TOP_LEFT, TOP_RIGHT -> ADMIN_MARGIN + TAB_HEIGHT + ADMIN_TOGGLE_GAP;
				case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - ADMIN_MARGIN - TAB_HEIGHT - ADMIN_TOGGLE_GAP - TOGGLE_SIZE;
			};
		}

		/** Dirección horizontal de apilado de la fila (+1 hacia la derecha, -1 hacia la izquierda). */
		int stackDirX() {
			return switch (this) {
				case TOP_LEFT, BOTTOM_LEFT -> +1;
				case TOP_RIGHT, BOTTOM_RIGHT -> -1;
			};
		}
	}

	/** Esquina activa del conjunto admin (config, resuelta al construir las pestañas). */
	private static AdminCorner adminCorner = AdminCorner.BOTTOM_RIGHT;

	/** Dibuja la fila de pestañas admin en la esquina configurada de la pantalla. */
	private static void renderAdminTabs(GuiGraphics graphics, int mouseX, int mouseY) {
		if (adminTabs.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		// Botón de mostrar/ocultar la fila admin (pegado a la esquina)
		if (config.admin.showToggleButton) {
			renderAdminToggle(graphics, client, mouseX, mouseY);
		}
		if (!config.admin.visible) {
			return;
		}
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int dirX = adminCorner.stackDirX();
		// Primera pestaña pegada a la esquina; las siguientes se apilan hacia el centro de la pantalla
		int x = adminCorner.firstTabX(screenWidth);
		int y = adminCorner.firstTabY(screenHeight);
		for (AdminTab tab : adminTabs) {
			renderTab(graphics, client, new Tab(tab.command(), tab.label(), tab.iconId()), x, y, mouseX, mouseY);
			x += dirX * (TAB_WIDTH + TAB_GAP);
		}
	}

	/** Devuelve la pestaña admin bajo el cursor, o null. */
	private static AdminTab adminTabAt(double mouseX, double mouseY) {
		if (adminTabs.isEmpty() || !config.admin.visible) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int dirX = adminCorner.stackDirX();
		int x = adminCorner.firstTabX(screenWidth);
		int y = adminCorner.firstTabY(screenHeight);
		for (AdminTab tab : adminTabs) {
			if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
				return tab;
			}
			x += dirX * (TAB_WIDTH + TAB_GAP);
		}
		return null;
	}

	/** Dibuja el botón de mostrar/ocultar la fila admin, pegado a la esquina configurada. */
	private static void renderAdminToggle(GuiGraphics graphics, Minecraft client, int mouseX, int mouseY) {
		int x = adminToggleX();
		int y = adminToggleY();
		boolean hovered = mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;

		graphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, hovered ? 0xE032323C : 0xD01C1C24);
		int border = hovered ? 0xFFF0F0F0 : 0xFF4A4A55;
		graphics.fill(x, y, x + TOGGLE_SIZE, y + 1, border);
		graphics.fill(x, y, x + 1, y + TOGGLE_SIZE, border);
		graphics.fill(x + TOGGLE_SIZE - 1, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, border);
		graphics.fill(x, y + TOGGLE_SIZE - 1, x + TOGGLE_SIZE, y + TOGGLE_SIZE, 0xFF000000);

		// Icono: barrera cuando se ve la fila admin (clic = ocultar), ojo de ender cuando está oculta
		String iconId = config.admin.visible ? "minecraft:barrier" : "minecraft:ender_eye";
		ItemStack icon = iconStack(new Tab("", Component.empty(), iconId));
		graphics.renderItem(icon, x + 1, y + 1);

		if (hovered) {
			Component tip = Component.translatable(config.admin.visible ? "cobbletabs.admin_toggle.hide" : "cobbletabs.admin_toggle.show");
			graphics.renderTooltip(client.font, tip, mouseX, mouseY);
		}
	}

	private static int adminToggleX() {
		return adminCorner.toggleX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
	}

	private static int adminToggleY() {
		return adminCorner.toggleY(Minecraft.getInstance().getWindow().getGuiScaledHeight());
	}

	private static boolean adminToggleAt(double mouseX, double mouseY) {
		int x = adminToggleX();
		int y = adminToggleY();
		return mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;
	}

	/**
	 * Dibuja el logo en la esquina superior derecha del inventario.
	 * El tamaño en pantalla se ajusta a la Escala de GUI de Minecraft para que
	 * ocupe siempre el mismo espacio relativo (por defecto 54 "píxeles de GUI",
	 * configurable en config/cobbletabs.json).
	 */
	private static void renderLogo(GuiGraphics graphics, AbstractContainerScreen<?> screen) {
		int texSize = config.logo.size;
		int guiLogoSize = scaleGui(texSize);
		int logoX = screen.leftPos + screen.imageWidth - guiLogoSize - MARGIN;
		// Dentro del fondo del inventario; si la GUI fuese muy pequeña, se recorta hacia dentro.
		int logoY = Math.max(screen.topPos + MARGIN, 2);

		// Opacidad tipo marca de agua para que no moleste visualmente
		float alpha = config.logo.opacity / 100.0F;
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		graphics.blit(LOGO_TEXTURE, logoX, logoY, guiLogoSize, guiLogoSize, 0.0F, 0.0F, texSize, texSize, texSize, texSize);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}

	/**
	 * Convierte "píxeles de GUI" a píxeles reales en pantalla según la Escala de GUI
	 * configurada en Minecraft, de modo que el tamaño relativo no cambie entre escalas.
	 */
	private static int scaleGui(int guiPixels) {
		double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		return Math.max(1, (int) Math.round(guiPixels * guiScale));
	}

	public static void renderTab(GuiGraphics graphics, Minecraft client, Tab tab, int x, int y, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;

		// Fondo y borde estilo pestaña
		graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, hovered ? 0xE032323C : 0xD01C1C24);
		int border = hovered ? 0xFFF0F0F0 : 0xFF4A4A55;
		graphics.fill(x, y, x + TAB_WIDTH, y + 1, border);
		graphics.fill(x, y, x + 1, y + TAB_HEIGHT, border);
		graphics.fill(x + TAB_WIDTH - 1, y, x + TAB_WIDTH, y + TAB_HEIGHT, border);
		graphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, 0xFF000000);

		// Icono del item centrado
		graphics.renderItem(iconStack(tab), x + (TAB_WIDTH - 16) / 2, y + (TAB_HEIGHT - 16) / 2);

		if (hovered) {
			graphics.renderTooltip(client.font, tab.label(), mouseX, mouseY);
		}
	}

	// ==================================================================
	// Clic y ejecución de comandos
	// ==================================================================

	// ==================================================================
	// Botón para mostrar/ocultar las pestañas
	// ==================================================================

	private static final int TOGGLE_SIZE = 18;

	/** Dibuja el botón de mostrar/ocultar encima de la esquina superior derecha de la GUI. */
	private static void renderToggle(GuiGraphics graphics, Minecraft client, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
		int x = toggleX(screen);
		int y = toggleY(screen);
		boolean hovered = mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;

		graphics.fill(x, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, hovered ? 0xE032323C : 0xD01C1C24);
		int border = hovered ? 0xFFF0F0F0 : 0xFF4A4A55;
		graphics.fill(x, y, x + TOGGLE_SIZE, y + 1, border);
		graphics.fill(x, y, x + 1, y + TOGGLE_SIZE, border);
		graphics.fill(x + TOGGLE_SIZE - 1, y, x + TOGGLE_SIZE, y + TOGGLE_SIZE, border);
		graphics.fill(x, y + TOGGLE_SIZE - 1, x + TOGGLE_SIZE, y + TOGGLE_SIZE, 0xFF000000);

		// Icono: barrera cuando se ven las pestañas (clic = ocultar), ojo de ender cuando están ocultas
		String iconId = config.tabsVisible ? "minecraft:barrier" : "minecraft:ender_eye";
		ItemStack icon = iconStack(new Tab("", Component.empty(), iconId));
		graphics.renderItem(icon, x + 1, y + 1);

		if (hovered) {
			Component tip = Component.translatable(config.tabsVisible ? "cobbletabs.toggle.hide" : "cobbletabs.toggle.show");
			graphics.renderTooltip(client.font, tip, mouseX, mouseY);
		}
	}

	private static int toggleX(AbstractContainerScreen<?> screen) {
		return screen.leftPos + screen.imageWidth - TOGGLE_SIZE;
	}

	private static int toggleY(AbstractContainerScreen<?> screen) {
		// En creativo, la tira de pestañas vanilla tapa la franja justo encima de la GUI:
		// los botones suben por encima de esa tira (quedan fuera del inventario) para
		// que el toggle y el botón del editor siempre se vean y sean clicables.
		int topGap = screen instanceof CreativeModeInventoryScreen ? 32 : 1;
		return Math.max(screen.topPos - TOGGLE_SIZE - topGap, 2);
	}

	private static boolean toggleAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		int x = toggleX(screen);
		int y = toggleY(screen);
		return mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;
	}

	// ==================================================================
	// Botón para abrir el editor de pestañas (inventario)
	// ==================================================================

	/** Tamaño del botón de edición (público para el auto-test). */
	public static final int EDIT_SIZE = 12;

	/**
	 * Posición X del botón de edición: a la izquierda del toggle de pestañas. Estar
	 * separado del toggle evita quedar tapado por el botón de pestañas del creativo
	 * que Minecraft dibuja en la esquina superior derecha del inventario.
	 */
	public static int editButtonX(AbstractContainerScreen<?> screen) {
		return toggleX(screen) - EDIT_SIZE - 3;
	}

	/** Posición Y del botón de edición (misma fila que el toggle de pestañas). */
	public static int editButtonY(AbstractContainerScreen<?> screen) {
		return toggleY(screen) + (TOGGLE_SIZE - EDIT_SIZE) / 2;
	}

	/** Dibuja el botón de libro y pluma que abre el editor de pestañas. */
	private static void renderEditButton(GuiGraphics graphics, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
		if (!config.showEditButton) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int x = editButtonX(screen);
		int y = editButtonY(screen);
		boolean hovered = mouseX >= x && mouseX < x + EDIT_SIZE && mouseY >= y && mouseY < y + EDIT_SIZE;

		graphics.fill(x, y, x + EDIT_SIZE, y + EDIT_SIZE, hovered ? 0xE032323C : 0xD01C1C24);
		int border = hovered ? 0xFFF0F0F0 : 0xFF4A4A55;
		graphics.fill(x, y, x + EDIT_SIZE, y + 1, border);
		graphics.fill(x, y, x + 1, y + EDIT_SIZE, border);
		graphics.fill(x + EDIT_SIZE - 1, y, x + EDIT_SIZE, y + EDIT_SIZE, border);
		graphics.fill(x, y + EDIT_SIZE - 1, x + EDIT_SIZE, y + EDIT_SIZE, 0xFF000000);

		graphics.renderItem(itemStackFor("minecraft:writable_book"), x + (EDIT_SIZE - 16) / 2 + 2, y + (EDIT_SIZE - 16) / 2 - 2);

		if (hovered) {
			graphics.renderTooltip(client.font, Component.translatable("cobbletabs.edit.open"), mouseX, mouseY);
		}
	}

	/** true si (mouseX, mouseY) cae en el botón de edición (package para el auto-test). */
	static boolean editButtonAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		if (!config.showEditButton) {
			return false;
		}
		int x = editButtonX(screen);
		int y = editButtonY(screen);
		return mouseX >= x && mouseX < x + EDIT_SIZE && mouseY >= y && mouseY < y + EDIT_SIZE;
	}

	private static Tab tabAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		if (!config.tabsVisible) {
			return null;
		}
		int leftCount = leftCount(screen);

		// Lado izquierdo
		int y = screen.topPos + MARGIN;
		for (int i = 0; i < leftCount; i++) {
			int x = screen.leftPos - TAB_WIDTH + OVERLAP;
			if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
				return tabs.get(i);
			}
			y += TAB_HEIGHT + TAB_GAP;
		}

		// Lado derecho
		y = screen.topPos + MARGIN;
		for (int i = leftCount; i < tabs.size(); i++) {
			int x = screen.leftPos + screen.imageWidth - OVERLAP;
			if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
				return tabs.get(i);
			}
			y += TAB_HEIGHT + TAB_GAP;
		}
		return null;
	}

	/** Config actual (lectura para las pantallas de edición y presets). */
	public static CobbleTabsConfig config() {
		return config;
	}

	/** Recarga las pestañas desde la config (tras editarlas o aplicar un preset). */
	public static void rebuildTabs() {
		buildTabs();
	}

	/** Recarga config/cobbletabs.json y reconstruye las pestañas. */
	public static void reloadConfig(Minecraft client) {
		config = CobbleTabsConfig.load();
		buildTabs();
		if (client.player != null) {
			client.player.displayClientMessage(
					Component.literal("§a[CobbleTabs]§r Configuración recargada (" + tabs.size() + " pestañas)."),
					true);
		}
		LOGGER.info("[CobbleTabs] Configuración recargada: {} pestañas activas.", tabs.size());
	}

	/** Abre el editor de pestañas; al cerrar vuelve a parent (o al juego si es null). */
	private static void openEditorScreen(Minecraft client, Screen parent) {
		client.setScreen(new CobbleTabsEditScreen(parent));
	}

	private static void runCommand(String command) {
		Minecraft client = Minecraft.getInstance();
		var handler = client.getConnection();
		if (handler == null) {
			return;
		}
		handler.sendCommand(command.substring(1)); // sin el "/" inicial
	}
}
