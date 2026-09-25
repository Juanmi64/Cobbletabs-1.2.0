package com.cobbletabs.mixin;

import com.cobbletabs.client.CobbleTabsClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Separa los efectos de estado de las pestañas del inventario (CobbleTabs 1.2.2):
 *
 * <ul>
 *   <li><b>Lado derecho:</b> vanilla dibuja la columna de efectos pegada a
 *       {@code leftPos + imageWidth + 2}, justo encima de las pestañas del lado
 *       derecho. Este mixin desplaza ese origen X hacia fuera de las pestañas
 *       cuando hay pestañas visibles en ese lado.</li>
 *   <li><b>Lado inferior:</b> vanilla decide si hay sitio para los efectos con
 *       {@link EffectRenderingInventoryScreen#canSeeEffects()}, que solo mira el
 *       hueco a la derecha de la GUI; con pestañas en el lado inferior (o superior)
 *       los efectos asomarían por debajo/encima de la fila, así que se ocultan.</li>
 * </ul>
 *
 * <p>Solo actúa en el inventario del jugador (survival), que es la única pantalla
 * donde CobbleTabs dibuja pestañas y vanilla dibuja efectos.</p>
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

	/**
	 * Desplaza el "+ 2" del origen X de la columna de efectos
	 * ({@code int i = this.leftPos + this.imageWidth + 2}) el ancho necesario para
	 * dejar sitio a las pestañas del lado derecho (0 = sin pestañas a la derecha).
	 */
	@ModifyConstant(method = "renderEffects", constant = @Constant(intValue = 2))
	private int cobbletabs$effectsXOffset(int original) {
		Object self = this;
		if (self instanceof AbstractContainerScreen<?> screen) {
			int offset = CobbleTabsClient.effectsOffsetFor(screen);
			if (offset > 0) {
				return original + offset;
			}
		}
		return original;
	}

	/**
	 * Con pestañas en la banda inferior/superior del inventario, los efectos ya no
	 * caben aunque haya hueco a la derecha: canSeeEffects() devuelve false y la
	 * columna de efectos se oculta (igual que cuando la ventana es estrecha).
	 */
	@Inject(method = "canSeeEffects", at = @At("TAIL"), cancellable = true)
	private void cobbletabs$hideEffectsWithBottomTabs(CallbackInfoReturnable<Boolean> cir) {
		Object self = this;
		if (cir.getReturnValueZ() && self instanceof AbstractContainerScreen<?> screen
				&& CobbleTabsClient.topBottomTabsCoverEffects(screen)) {
			cir.setReturnValue(false);
		}
	}
}
