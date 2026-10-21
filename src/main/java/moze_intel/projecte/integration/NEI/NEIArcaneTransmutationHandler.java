package moze_intel.projecte.integration.NEI;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ArcaneRecipeTransferPKT;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NEIArcaneTransmutationHandler implements IOverlayHandler {

	// NEI 标准 3x3 合成网格原点与格宽
	private static final int GRID_X = 25;
	private static final int GRID_Y = 6;
	private static final int CELL = 18;
	private static final int SLOTS = 9;
	private static final int MAX_CANDIDATES = 8;

	@Override
	public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
		// maxTransfer 一律忽略：只填一份配方，不做 shift 批量填充
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) return;

		List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
		if (ingredients == null || ingredients.isEmpty()) return;

		List<List<ItemStack>> slots = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++)
			slots.add(new ArrayList<ItemStack>());

		Set<String> knowledgeIndex = ArcaneTransmutationContainer.knowledgeIndexOf(player);

		for (PositionedStack positioned : ingredients) {
			if (positioned == null) return;

			int col = positioned.relx - GRID_X;
			int row = positioned.rely - GRID_Y;
			// 任一格不是标准网格坐标，整包不发，绝不灌错料
			if (col % CELL != 0 || row % CELL != 0) return;

			col /= CELL;
			row /= CELL;
			if (col < 0 || col >= 3 || row < 0 || row >= 3) return;
			if (positioned.items == null || positioned.items.length == 0) return;

			appendCandidates(player, knowledgeIndex, positioned.items, requiredOf(positioned.items), slots.get(row * 3 + col));
		}

		PacketHandler.sendToServer(new ArcaneRecipeTransferPKT(slots));
	}

	@Override
	public List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) return IOverlayHandler.super.presenceOverlay(firstGui, recipe, recipeIndex);

		// 打开奥数转化平板时 EMC 的权威值在容器里，其余情况退回玩家数据
		double[] emcBudget = { firstGui.inventorySlots instanceof ArcaneTransmutationContainer
			? ((ArcaneTransmutationContainer) firstGui.inventorySlots).transmutationInventory.emc
			: Transmutation.getEmc(player) };

		// 背包镜像：所有模拟都在副本上做，模拟成功才提交
		List<ItemStack> invMirror = new ArrayList<>();
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack != null && stack.stackSize > 0)
				invMirror.add(stack.copy());
		}

		// 预览每帧都会走一遍：知识库索引只建一次，否则每帧几十次 O(知识库) 线性扫描
		Set<String> knowledgeIndex = ArcaneTransmutationContainer.knowledgeIndexOf(player);
		List<ItemOverlayState> states = new ArrayList<>();
		for (PositionedStack positioned : recipe.getIngredientStacks(recipeIndex)) {
			// slot 为 null 的 ItemOverlayState 会让 NEI 的 draw() 读 relx/rely 时 NPE，遇到就整只交回默认实现
			if (positioned == null) return IOverlayHandler.super.presenceOverlay(firstGui, recipe, recipeIndex);

			if (positioned.items == null || positioned.items.length == 0) {
				states.add(new ItemOverlayState(positioned, false));
				continue;
			}

			int required = requiredOf(positioned.items);
			boolean present = false;
			for (ItemStack candidate : positioned.items) {
				if (candidate == null || present) continue;
				List<ItemStack> invCopy = copyOf(invMirror);
				double[] budgetCopy = { emcBudget[0] };
				if (consume(knowledgeIndex, invCopy, budgetCopy, candidate, required)) {
					invMirror = invCopy;
					emcBudget = budgetCopy;
					present = true;
				}
			}
			states.add(new ItemOverlayState(positioned, present));
		}
		return states;
	}

	/**
	 * 背包里有的优先（保持 NEI 给出的候选顺序），再用「已解锁知识 + EMC 价值 > 0」按 EMC 升序补齐。
	 */
	private static void appendCandidates(EntityPlayer player, Set<String> knowledgeIndex, ItemStack[] items, int required, List<ItemStack> out) {
		for (ItemStack item : items) {
			if (out.size() >= MAX_CANDIDATES) return;
			if (item == null || !hasInInventory(player, item) || containsItem(out, item)) continue;
			out.add(withSize(item, required));
		}

		List<ItemStack> creatable = new ArrayList<>();
		for (ItemStack item : items) {
			if (item == null || containsItem(out, item) || containsItem(creatable, item)) continue;
			if (ArcaneTransmutationContainer.isCreatableFromEmc(knowledgeIndex, item))
				creatable.add(item);
		}
		creatable.sort((a, b) -> Double.compare(ArcaneTransmutationContainer.emcValueOf(a), ArcaneTransmutationContainer.emcValueOf(b)));

		for (ItemStack item : creatable) {
			if (out.size() >= MAX_CANDIDATES) return;
			out.add(withSize(item, required));
		}
	}

	private static boolean consume(Set<String> knowledgeIndex, List<ItemStack> inv, double[] emcBudget, ItemStack target, int required) {
		int remaining = required;
		for (ItemStack stack : inv) {
			if (remaining <= 0) break;
			if (stack == null || stack.stackSize <= 0 || !ArcaneTransmutationContainer.matchesStack(target, stack)) continue;
			int take = Math.min(remaining, stack.stackSize);
			stack.stackSize -= take;
			remaining -= take;
		}
		if (remaining <= 0) return true;

		if (!ArcaneTransmutationContainer.isCreatableFromEmc(knowledgeIndex, target)) return false;
		double cost = ArcaneTransmutationContainer.emcValueOf(target) * remaining;
		if (cost > emcBudget[0]) return false;

		emcBudget[0] -= cost;
		return true;
	}

	private static int requiredOf(ItemStack[] items) {
		for (ItemStack stack : items) {
			if (stack != null) return Math.max(1, stack.stackSize);
		}
		return 1;
	}

	private static boolean hasInInventory(EntityPlayer player, ItemStack target) {
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack != null && stack.stackSize > 0 && ArcaneTransmutationContainer.matchesStack(target, stack))
				return true;
		}
		return false;
	}

	private static boolean containsItem(List<ItemStack> list, ItemStack stack) {
		for (ItemStack other : list) {
			if (other.getItem() == stack.getItem() && other.getItemDamage() == stack.getItemDamage()
				&& ItemStack.areItemStackTagsEqual(other, stack))
				return true;
		}
		return false;
	}

	private static ItemStack withSize(ItemStack stack, int size) {
		ItemStack copy = stack.copy();
		copy.stackSize = Math.max(1, size);
		return copy;
	}

	private static List<ItemStack> copyOf(List<ItemStack> list) {
		List<ItemStack> copy = new ArrayList<>(list.size());
		for (ItemStack stack : list)
			copy.add(stack.copy());
		return copy;
	}
}
