package gregtech.api.util;

import gregtech.api.recipes.FluidKey;
import gregtech.api.recipes.KeySharedStack;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GTHashMaps {
    /**
     * Maps all items in the {@link IItemHandler} into a {@link ItemStackKey}, {@link Integer} value as amount
     *
     * @param inputs The inventory handler of the inventory
     * @return a {@link Map} of {@link ItemStackKey} and {@link Integer} as amount on the inventory
     */
    public static Map<ItemStackKey, Integer> fromItemHandler(IItemHandler inputs) {
        final Object2IntMap<ItemStackKey> map = new Object2IntLinkedOpenHashMap<>();

        // Create a single stack of the combined count for each item

        for (int i = 0; i < inputs.getSlots(); i++) {
            ItemStack stack = inputs.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStackKey key = KeySharedStack.getRegisteredStack(stack);
                map.put(key, map.getInt(key) + stack.getCount());
            }
        }

        return map;
    }

    /**
     * Maps all items in the {@link ItemStack} {@link Collection} into a {@link ItemStackKey}, {@link Integer} value as amount
     *
     * @param inputs The inventory handler of the inventory
     * @return a {@link Map} of {@link ItemStackKey} and {@link Integer} as amount on the inventory
     */
    public static Map<ItemStackKey, Integer> fromItemStackCollection(Iterable<ItemStack> inputs) {
        final Object2IntMap<ItemStackKey> map = new Object2IntLinkedOpenHashMap<>();

        // Create a single stack of the combined count for each item

        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                ItemStackKey key = KeySharedStack.getRegisteredStack(stack);
                map.put(key, map.getInt(key) + stack.getCount());
            }
        }

        return map;
    }

    /**
     * Maps all fluids in the {@link IFluidHandler} into a {@link FluidKey}, {@link Integer} value as amount
     *
     * @param fluidInputs The combined fluid input inventory handler, in the form of an {@link IFluidHandler}
     * @return a {@link Set} of unique {@link FluidKey}s for each fluid in the handler. Will be oversized stacks if required
     */
    public static Map<FluidKey, Integer> fromFluidHandler(IFluidHandler fluidInputs) {
        final Object2IntMap<FluidKey> map = new Object2IntLinkedOpenHashMap<>();

        // Create a single stack of the combined count for each item

        for (int i = 0; i < fluidInputs.getTankProperties().length; i++) {
            FluidStack fluidStack = fluidInputs.getTankProperties()[i].getContents();
            if (fluidStack != null && fluidStack.amount > 0) {
                FluidKey key = new FluidKey(fluidStack);
                map.put(key, map.getInt(key) + fluidStack.amount);
            }
        }

        return map;
    }

    /**
     * Maps all fluids in the {@link FluidStack} {@link Collection} into a {@link FluidKey}, {@link Integer} value as amount
     *
     * @param fluidInputs The combined fluid input inventory handler, in the form of an {@link IFluidHandler}
     * @return a {@link Set} of unique {@link FluidKey}s for each fluid in the handler. Will be oversized stacks if required
     */
    public static Map<FluidKey, Integer> fromFluidCollection(Collection<FluidStack> fluidInputs) {
        final Object2IntMap<FluidKey> map = new Object2IntLinkedOpenHashMap<>();

        // Create a single stack of the combined count for each item

        for (FluidStack fluidStack : fluidInputs) {
            if (fluidStack != null && fluidStack.amount > 0) {
                FluidKey key = new FluidKey(fluidStack);
                map.put(key, map.getInt(key) + fluidStack.amount);
            }
        }

        return map;
    }
}
