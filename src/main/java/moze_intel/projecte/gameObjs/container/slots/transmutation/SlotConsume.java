package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;

public class SlotConsume extends Slot
{
	private final TransmutationInventory inv;

	public SlotConsume(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null) {
			super.putStack(null);
			return;
		}

		inv.addEmc(EMCHelper.getEmcValue(stack) * stack.stackSize);
		inv.handleKnowledge(stack);
		super.putStack(stack);
		this.onSlotChanged();
		inv.updateOutputs();
	}

	@Override
	public ItemStack decrStackSize(int amount)
	{
		// 配合上面的修复：如果 NEI 在模拟操作中强行把物品拿回去(回滚)，我们需要扣除已经加上的 EMC
		ItemStack stack = super.decrStackSize(amount);
		if (stack != null) {
			inv.removeEmc(EMCHelper.getEmcValue(stack) * stack.stackSize);
			inv.updateOutputs();
		}
		return stack;
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return !inv.hasMaxedEmc() && (EMCHelper.doesItemHaveEmc(stack) || stack.getItem() == ObjHandler.tome);
	}

	@Override
	public boolean canTakeStack(EntityPlayer player)
	{
		return false;
	}
}
