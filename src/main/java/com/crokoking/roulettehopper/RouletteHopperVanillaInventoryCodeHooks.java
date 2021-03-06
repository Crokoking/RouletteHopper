/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.crokoking.roulettehopper;

import net.minecraft.block.HopperBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class RouletteHopperVanillaInventoryCodeHooks
{
    private static final Random RNG = new Random();

    /**
     * Copied from TileEntityHopper#captureDroppedItems and added capability support
     * @return Null if we did nothing {no IItemHandler}, True if we moved an item, False if we moved no items
     */
    @Nullable
    public static Boolean extractHook(IHopper dest)
    {
        return getItemHandler(dest, Direction.UP)
                .map(itemHandlerResult -> {
                    IItemHandler handler = itemHandlerResult.getKey();
                    int[] indexes = shuffle(handler.getSlots());
                    for (int i = 0; i < indexes.length; i++)
                    {
                        int index = indexes[i];
                        ItemStack extractItem = handler.extractItem(index, 1, true);
                        if (!extractItem.isEmpty())
                        {
                            for (int j = 0; j < dest.getSizeInventory(); j++)
                            {
                                ItemStack destStack = dest.getStackInSlot(j);
                                if (dest.isItemValidForSlot(j, extractItem) && (destStack.isEmpty() || destStack.getCount() < destStack.getMaxStackSize() && destStack.getCount() < dest.getInventoryStackLimit() && ItemHandlerHelper.canItemStacksStack(extractItem, destStack)))
                                {
                                    extractItem = handler.extractItem(index, 1, false);
                                    if (destStack.isEmpty())
                                        dest.setInventorySlotContents(j, extractItem);
                                    else
                                    {
                                        destStack.grow(1);
                                        dest.setInventorySlotContents(j, destStack);
                                    }
                                    dest.markDirty();
                                    return true;
                                }
                            }
                        }
                    }

                    return false;
                })
                .orElse(null); // TODO bad null
    }

    /**
     * Copied from TileEntityHopper#transferItemsOut and added capability support
     */
    public static boolean insertHook(RouletteHopperTileEntity hopper)
    {
        Direction hopperFacing = hopper.getBlockState().get(HopperBlock.FACING);
        return getItemHandler(hopper, hopperFacing)
                .map(destinationResult -> {
                    IItemHandler itemHandler = destinationResult.getKey();
                    Object destination = destinationResult.getValue();
                    if (isFull(itemHandler))
                    {
                        return false;
                    }
                    else
                    {
                        int[] indexes = shuffle(hopper.getSizeInventory());
                        for (int i = 0; i < indexes.length; ++i)
                        {
                            int index = indexes[i];
                            if (!hopper.getStackInSlot(index).isEmpty())
                            {
                                ItemStack originalSlotContents = hopper.getStackInSlot(index).copy();
                                ItemStack insertStack = hopper.decrStackSize(index, 1);
                                ItemStack remainder = putStackInInventoryAllSlots(hopper, destination, itemHandler, insertStack);

                                if (remainder.isEmpty())
                                {
                                    return true;
                                }

                                hopper.setInventorySlotContents(index, originalSlotContents);
                            }
                        }

                        return false;
                    }
                })
                .orElse(false);
    }

    public static int[] shuffle(int[] array) {
       for (int i=0; i<array.length; i++) {
          int index = RNG.nextInt(array.length);
          int temp = array[i];
          array[i] = array[index];
          array[index] = temp;
       }
       return array;
    }

    public static int[] shuffle(int max) {
       int[] out = new int[max];
       for(int i=0;i<max;i++) {
          out[i] = i;
       }
       return shuffle(out);
    }

    private static ItemStack putStackInInventoryAllSlots(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack)
    {
        for (int slot = 0; slot < destInventory.getSlots() && !stack.isEmpty(); slot++)
        {
            stack = insertStack(source, destination, destInventory, stack, slot);
        }
        return stack;
    }

    /**
     * Copied from TileEntityHopper#insertStack and added capability support
     */
    private static ItemStack insertStack(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack, int slot)
    {
        ItemStack itemstack = destInventory.getStackInSlot(slot);

        if (destInventory.insertItem(slot, stack, true).isEmpty())
        {
            boolean insertedItem = false;
            boolean inventoryWasEmpty = isEmpty(destInventory);

            if (itemstack.isEmpty())
            {
                destInventory.insertItem(slot, stack, false);
                stack = ItemStack.EMPTY;
                insertedItem = true;
            }
            else if (ItemHandlerHelper.canItemStacksStack(itemstack, stack))
            {
                int originalSize = stack.getCount();
                stack = destInventory.insertItem(slot, stack, false);
                insertedItem = originalSize < stack.getCount();
            }

            if (insertedItem)
            {
                if (inventoryWasEmpty && destination instanceof HopperTileEntity)
                {
                    HopperTileEntity destinationHopper = (HopperTileEntity)destination;

                    if (!destinationHopper.mayTransfer())
                    {
                        int k = 0;
                        if (source instanceof HopperTileEntity)
                        {
                            if (destinationHopper.getLastUpdateTime() >= ((HopperTileEntity) source).getLastUpdateTime())
                            {
                                k = 1;
                            }
                        }
                        destinationHopper.setTransferCooldown(8 - k);
                    }
                }
            }
        }

        return stack;
    }

    private static Optional<Pair<IItemHandler, Object>> getItemHandler(IHopper hopper, Direction hopperFacing)
    {
        double x = hopper.getXPos() + (double) hopperFacing.getXOffset();
        double y = hopper.getYPos() + (double) hopperFacing.getYOffset();
        double z = hopper.getZPos() + (double) hopperFacing.getZOffset();
        return getItemHandler(Objects.requireNonNull(hopper.getWorld()), x, y, z, hopperFacing.getOpposite());
    }

    private static boolean isFull(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || stackInSlot.getCount() < itemHandler.getSlotLimit(slot))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.getCount() > 0)
            {
                return false;
            }
        }
        return true;
    }

    public static Optional<Pair<IItemHandler, Object>> getItemHandler(World worldIn, double x, double y, double z, final Direction side)
    {
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        BlockPos blockpos = new BlockPos(i, j, k);
        net.minecraft.block.BlockState state = worldIn.getBlockState(blockpos);

        if (state.hasTileEntity())
        {
            TileEntity tileentity = worldIn.getTileEntity(blockpos);
            if (tileentity != null)
            {
                return tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)
                    .map(capability -> ImmutablePair.of(capability, tileentity));
            }
        }

        return Optional.empty();
    }
}
