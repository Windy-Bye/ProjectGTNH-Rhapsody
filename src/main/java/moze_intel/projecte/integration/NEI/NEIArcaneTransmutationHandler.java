package moze_intel.projecte.integration.NEI;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ArcaneRecipeTransferPKT;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public class NEIArcaneTransmutationHandler implements IOverlayHandler {

	@Override
	public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
		ItemStack[] recipeItems = new ItemStack[9];

		// NEI 默认的合成网格起始坐标是 relx=25, rely=6，每个格子 18x18
		for (PositionedStack stack : recipe.getIngredientStacks(recipeIndex)) {
			int col = (stack.relx - 25) / 18;
			int row = (stack.rely - 6) / 18;

			if (col >= 0 && col < 3 && row >= 0 && row < 3 && stack.items != null && stack.items.length > 0) {
				int index = row * 3 + col;
				// 获取第一种匹配的物品即可，因为我们可以从 EMC 或者背包扣除
				recipeItems[index] = stack.items[0].copy();
				recipeItems[index].stackSize = 1;
			}
		}

		// 发送数据包到服务端，让服务端去处理扣除 EMC 并放入物品
		PacketHandler.sendToServer(new ArcaneRecipeTransferPKT(recipeItems));
	}
}
