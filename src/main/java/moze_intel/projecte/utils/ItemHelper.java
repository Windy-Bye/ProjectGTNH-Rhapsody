package moze_intel.projecte.utils;

import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.entity.EntityLootBall;
import moze_intel.projecte.integration.helpers.GTItemHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for Inventories, ItemStacks, Items, and the Ore Dictionary.
 * Notice: Please try to keep methods tidy and alphabetically ordered. Thanks!
 */
public final class ItemHelper {

	// Hash 包装器，用于 O(1) 的物品身份比对，避免深度 NBT 比较
	private static class ItemKey {
		public final Item item;
		public final int damage;
		public final NBTTagCompound nbt;
		private final int hash;

		public ItemKey(ItemStack stack) {
			this.item = stack.getItem();
			this.damage = stack.getItemDamage();
			this.nbt = stack.stackTagCompound;
			int h = Item.getIdFromItem(item);
			h = 31 * h + damage;
			if (nbt != null) h = 31 * h + nbt.hashCode();
			this.hash = h;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof ItemKey)) return false;
			ItemKey other = (ItemKey) obj;
			return this.item == other.item
				&& this.damage == other.damage
				&& (this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		public ItemStack createStack(int size) {
			ItemStack s = new ItemStack(item, size, damage);
			if (nbt != null) s.stackTagCompound = (NBTTagCompound) nbt.copy();
			return s;
		}
	}

	/**
	 * @return True if the only aspect these stacks differ by is stack size, false if item, meta, or nbt differ.
	 */
	public static boolean areItemStacksEqual(ItemStack stack1, ItemStack stack2) {
		if (stack1 == stack2) return true;
		if (stack1 == null || stack2 == null) return false;
		return stack1.isItemEqual(stack2) && ItemStack.areItemStackTagsEqual(stack1, stack2);
	}

	public static boolean basicAreStacksEqual(ItemStack stack1, ItemStack stack2) {
		if (stack1 == stack2) return true;
		if (stack1 == null || stack2 == null) return false;
		return (stack1.getItem() == stack2.getItem()) && (stack1.getItemDamage() == stack2.getItemDamage());
	}

	public static void compactItemList(List<ItemStack> list) {
		if (list.size() <= 1) return;

		// HashMap 聚合
		Map<ItemKey, Long> itemCounts = new HashMap<>(list.size());
		for (ItemStack stack : list) {
			if (stack == null || stack.getItem() == null || stack.stackSize <= 0) continue;
			ItemKey key = new ItemKey(stack);
			itemCounts.put(key, itemCounts.getOrDefault(key, 0L) + stack.stackSize);
		}

		list.clear();

		// 重组 ItemStack
		for (Map.Entry<ItemKey, Long> entry : itemCounts.entrySet()) {
			ItemKey key = entry.getKey();
			long totalAmount = entry.getValue();
			int maxStackSize = key.item.getItemStackLimit(key.createStack(1));

			while (totalAmount > 0) {
				int toAdd = (int) Math.min(totalAmount, maxStackSize);
				list.add(key.createStack(toAdd));
				totalAmount -= toAdd;
			}
		}
		list.sort(Comparators.ITEMSTACK_ASCENDING);
	}

	/**
	 * Compacts and sorts list of items, without regard for stack sizes
	 */
	public static void compactItemListIgnoreStacksize(List<ItemStack> list) {
		if (list.size() <= 1) return;

		// HashMap 聚合
		Map<ItemKey, Long> itemCounts = new HashMap<>(list.size());
		for (ItemStack stack : list) {
			if (stack == null || stack.getItem() == null || stack.stackSize <= 0) continue;
			ItemKey key = new ItemKey(stack);
			itemCounts.put(key, itemCounts.getOrDefault(key, 0L) + stack.stackSize);
		}

		list.clear();

		for (Map.Entry<ItemKey, Long> entry : itemCounts.entrySet()) {
			long totalAmount = entry.getValue();
			int toAdd = (int) Math.min(totalAmount, Integer.MAX_VALUE); // 无视堆叠上限
			list.add(entry.getKey().createStack(toAdd));
		}
		list.sort(Comparators.ITEMSTACK_ASCENDING);
	}

	public static boolean containsItemStack(List<ItemStack> list, ItemStack toSearch) {
		if (toSearch == null || toSearch.getItem() == null) return false;
		Item searchItem = toSearch.getItem();
		int searchDamage = toSearch.getItemDamage();
		boolean hasSubtypes = toSearch.getHasSubtypes();

		for (ItemStack stack : list) {
			if (stack == null || stack.getItem() == null) continue;
			if (stack.getItem() == searchItem) {
				if (!hasSubtypes || stack.getItemDamage() == searchDamage) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean containsItemStack(ItemStack[] stacks, ItemStack toSearch) {
		if (toSearch == null || toSearch.getItem() == null) return false;
		Item searchItem = toSearch.getItem();
		int searchDamage = toSearch.getItemDamage();
		boolean hasSubtypes = toSearch.getHasSubtypes();

		for (ItemStack stack : stacks) {
			if (stack == null || stack.getItem() == null) continue;
			if (stack.getItem() == searchItem) {
				if (!hasSubtypes || stack.getItemDamage() == searchDamage) {
					return true;
				}
			}
		}
		return false;
	}

	public static ItemStack[] copyIndexedNBTToArray(NBTTagList list, ItemStack[] dest) {
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			dest[entry.getByte("index")] = ItemStack.loadItemStackFromNBT(entry);
		}
		return dest;
	}

	public static NBTTagCompound filterNBT(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return null;
		NBTTagCompound original = stack.stackTagCompound;
		if (original == null || original.hasNoTags()) return null;

		NBTTagCompound result = new NBTTagCompound();

		List<String> nbtList = ProjectEConfig.nbtDistinctlist.get(stack.getItem());
		if (nbtList != null) {
			for (String key : nbtList) {
				NBTBase tag = original.getTag(key);
				if (tag != null)
					result.setTag(key, tag.copy());
			}
		}

		if (GTItemHelper.isGTtool(stack) && original.hasKey("GT.ToolStats")) {
			NBTTagCompound toolStats = original.getCompoundTag("GT.ToolStats");
			NBTTagCompound newStats = new NBTTagCompound();

			NBTBase tag;
			if ((tag = toolStats.getTag("PrimaryMaterial")) != null)
				newStats.setTag("PrimaryMaterial", tag.copy());
			if ((tag = toolStats.getTag("SecondaryMaterial")) != null)
				newStats.setTag("SecondaryMaterial", tag.copy());
			if ((tag = toolStats.getTag("MaxDamage")) != null)
				newStats.setTag("MaxDamage", tag.copy());

			if (!newStats.hasNoTags())
				result.setTag("GT.ToolStats", newStats);
		}

		return result.hasNoTags() ? null : result;
	}

	public static ItemStack getNormalizedStack(ItemStack stack) {
		ItemStack result = stack.copy();
		result.stackSize = 1;
		return result;
	}

	public static List<ItemStack> getODItems(String oreName) {
		List<ItemStack> result = new ArrayList<>();

		for (ItemStack stack : OreDictionary.getOres(oreName)) {
			if (stack == null) continue;

			if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
				List<ItemStack> list = new ArrayList<>();
				ItemStack copy = stack.copy();
				copy.setItemDamage(0);

				list.add(copy.copy());
				String startName = copy.getUnlocalizedName();

				for (int i = 1; i <= 128; i++) {
					try {
						copy.setItemDamage(i);
						if (copy.getUnlocalizedName() == null || copy.getUnlocalizedName().equals(startName)) {
							result.addAll(list);
							break;
						}
					} catch (Exception e) {
						PELogger.logFatal("Couldn't retrieve OD items for: " + oreName);
						PELogger.logFatal("Caused by: " + e);
						result.addAll(list);
						break;
					}

					list.add(copy.copy());

					if (i == 128) {
						copy.setItemDamage(0);
						result.add(copy);
					}
				}
			} else {
				result.add(stack.copy());
			}
		}

		return result;
	}

	public static String getOreDictionaryName(ItemStack stack) {
		int[] oreIds = OreDictionary.getOreIDs(stack);
		if (oreIds.length == 0) return "Unknown";
		return OreDictionary.getOreName(oreIds[0]);
	}

	public static ItemStack getStackFromInv(IInventory inv, ItemStack stack) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack s = inv.getStackInSlot(i);
			if (s != null && basicAreStacksEqual(stack, s)) {
				return s;
			}
		}
		return null;
	}

	public static ItemStack getStackFromInv(ItemStack[] inv, ItemStack stack) {
		for (ItemStack s : inv) {
			if (s != null && basicAreStacksEqual(stack, s)) {
				return s;
			}
		}
		return null;
	}

	public static ItemStack getStackFromString(String internal, int metaData) {
		Item item = (Item) Item.itemRegistry.getObject(internal);
		if (item == null) return null;
		return new ItemStack(item, 1, metaData);
	}

	public static int getSpaceFor(IInventory inv, ItemStack stack) {
		int stackable = 0;
		final int maxStack = stack.getMaxStackSize();
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack invStack = inv.getStackInSlot(i);
			if (invStack == null) {
				stackable += maxStack;
			} else if (areItemStacksEqual(stack, invStack) && invStack.stackSize < maxStack) {
				stackable += maxStack - invStack.stackSize;
			}
		}
		return stackable;
	}

	public static int getSpaceFor(ItemStack[] inv, ItemStack stack) {
		int stackable = 0;
		final int maxStack = stack.getMaxStackSize();
		for (ItemStack invStack : inv) {
			if (invStack == null) {
				stackable += maxStack;
			} else if (areItemStacksEqual(stack, invStack) && invStack.stackSize < maxStack) {
				stackable += maxStack - invStack.stackSize;
			}
		}
		return stackable;
	}

	@Deprecated
	public static boolean hasSpace(IInventory inv, ItemStack stack) {
		return hasSpaceForSingle(inv, stack);
	}

	@Deprecated
	public static boolean hasSpace(ItemStack[] inv, ItemStack stack) {
		return hasSpaceForSingle(inv, stack);
	}

	public static boolean hasSpaceForSingle(IInventory inv, ItemStack stack) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack invStack = inv.getStackInSlot(i);
			if (invStack == null) return true;
			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasSpaceForSingle(ItemStack[] inv, ItemStack stack) {
		for (ItemStack invStack : inv) {
			if (invStack == null) return true;
			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	public static boolean invContainsItem(IInventory inv, ItemStack toSearch) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (stack != null && basicAreStacksEqual(stack, toSearch)) return true;
		}
		return false;
	}

	public static boolean invContainsItem(ItemStack[] inv, ItemStack toSearch) {
		for (ItemStack stack : inv) {
			if (stack != null && basicAreStacksEqual(stack, toSearch)) return true;
		}
		return false;
	}

	public static boolean invContainsItem(ItemStack[] inv, Item toSearch) {
		for (ItemStack stack : inv) {
			if (stack != null && stack.getItem() == toSearch) return true;
		}
		return false;
	}

	public static boolean isOre(Block block, int meta) {
		if (block == Blocks.lit_redstone_ore) return true;
		String oreDictName = getOreDictionaryName(new ItemStack(block, 1, meta));
		return oreDictName.startsWith("ore") || oreDictName.startsWith("denseore");
	}

	public static boolean isOre(ItemStack stack) {
		final int[] oreIds = OreDictionary.getOreIDs(stack);
		for (int oreId : oreIds)
			if (isOreOD(OreDictionary.getOreName(oreId)))
				return true;
		return false;
	}

	public static boolean isOreOD(String s) {
		return s != null && !s.startsWith("oreberry") && !s.equals("crushedPineMaterial") && (s.startsWith("ore") || s.startsWith("rawOre")
			|| s.startsWith("crushed") || s.startsWith("dustPure") || s.startsWith("dustImpure"));
	}

	public static ItemStack[] nbtToArray(NBTTagList list) {
		ItemStack[] stacks = new ItemStack[list.tagCount()];
		for (int i = 0; i < list.tagCount(); i++) {
			stacks[i] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
		}
		return stacks;
	}

	public static void pushLootBallInInv(IInventory inv, EntityLootBall ball) {
		List<ItemStack> results = new ArrayList<>();
		for (ItemStack s : ball.getItemList()) {
			ItemStack result = pushStackInInv(inv, s);
			if (result != null) {
				results.add(result);
			}
		}
		ball.setItemList(results);
	}

	public static ItemStack pushStackInInv(IInventory inv, ItemStack stack) {
		int limit = (inv instanceof InventoryPlayer) ? ((InventoryPlayer) inv).mainInventory.length : inv.getSizeInventory();

		for (int i = 0; i < limit; i++) {
			ItemStack invStack = inv.getStackInSlot(i);

			if (invStack == null) {
				if (inv.isItemValidForSlot(i, stack)) {
					inv.setInventorySlotContents(i, stack);
					return null;
				}
				continue;
			}

			if (inv.isItemValidForSlot(i, stack) && areItemStacksEqual(stack, invStack)
				&& invStack.stackSize < invStack.getMaxStackSize())
			{
				int remaining = invStack.getMaxStackSize() - invStack.stackSize;

				if (remaining >= stack.stackSize) {
					invStack.stackSize += stack.stackSize;
					inv.setInventorySlotContents(i, invStack);
					return null;
				}

				invStack.stackSize += remaining;
				inv.setInventorySlotContents(i, invStack);
				stack.stackSize -= remaining;
			}
		}

		return stack.copy();
	}

	public static ItemStack pushStackInInv(ItemStack[] inv, ItemStack stack) {
		for (int i = 0; i < inv.length; i++) {
			ItemStack invStack = inv[i];

			if (invStack == null) {
				inv[i] = stack;
				return null;
			}

			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				int remaining = invStack.getMaxStackSize() - invStack.stackSize;

				if (remaining >= stack.stackSize) {
					invStack.stackSize += stack.stackSize;
					inv[i] = invStack;
					return null;
				}

				invStack.stackSize += remaining;
				inv[i] = invStack;
				stack.stackSize -= remaining;
			}
		}

		return stack.copy();
	}

	public static NBTTagList toIndexedNBTList(ItemStack[] stacks) {
		NBTTagList list = new NBTTagList();
		for (int i = 0; i < stacks.length; i++) {
			if (stacks[i] != null) {
				NBTTagCompound entry = new NBTTagCompound();
				entry.setByte("index", ((byte) i));
				stacks[i].writeToNBT(entry);
				list.appendTag(entry);
			}
		}
		return list;
	}

	public static void trimItemList(List<ItemStack> list) {
		list.removeIf(s -> s == null || s.stackSize <= 0);
	}
}
