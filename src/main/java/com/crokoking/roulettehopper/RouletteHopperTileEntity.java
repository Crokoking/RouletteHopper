package com.crokoking.roulettehopper;

import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.HopperContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.HopperTileEntity;
import net.minecraft.tileentity.IHopper;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@SuppressWarnings({"all"})
public class RouletteHopperTileEntity extends HopperTileEntity {
   private NonNullList<ItemStack> inventory = NonNullList.withSize(5, ItemStack.EMPTY);
   private int transferCooldown = -1;
   private long tickedGameTime;


   public void read(BlockState state, CompoundNBT nbt) {
      super.read(state, nbt);
      this.inventory = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
      if (!this.checkLootAndRead(nbt)) {
         ItemStackHelper.loadAllItems(nbt, this.inventory);
      }

      this.transferCooldown = nbt.getInt("TransferCooldown");
   }

   public CompoundNBT write(CompoundNBT compound) {
      super.write(compound);
      if (!this.checkLootAndWrite(compound)) {
         ItemStackHelper.saveAllItems(compound, this.inventory);
      }

      compound.putInt("TransferCooldown", this.transferCooldown);
      return compound;
   }

   /**
    * Returns the number of slots in the inventory.
    */
   public int getSizeInventory() {
      return this.inventory.size();
   }

   /**
    * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
    */
   public ItemStack decrStackSize(int index, int count) {
      this.fillWithLoot((PlayerEntity)null);
      return ItemStackHelper.getAndSplit(this.getItems(), index, count);
   }

   /**
    * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
    */
   public void setInventorySlotContents(int index, ItemStack stack) {
      this.fillWithLoot((PlayerEntity)null);
      this.getItems().set(index, stack);
      if (stack.getCount() > this.getInventoryStackLimit()) {
         stack.setCount(this.getInventoryStackLimit());
      }

   }

   protected ITextComponent getDefaultName() {
      return new TranslationTextComponent("roulettehopper:container.hopper");
   }

   @Override
   public void tick() {
      if (this.world != null && !this.world.isRemote) {
         --this.transferCooldown;
         this.tickedGameTime = this.world.getGameTime();
         if (!this.isOnTransferCooldown()) {
            this.setTransferCooldown(0);
            this.updateHopper(() -> {
               return pullItems(this);
            });
         }

      }
   }

   private boolean updateHopper(Supplier<Boolean> p_200109_1_) {
      if (this.world != null && !this.world.isRemote) {
         if (!this.isOnTransferCooldown() && this.getBlockState().get(HopperBlock.ENABLED)) {
            boolean flag = false;
            if (!this.isEmpty()) {
               flag = this.transferItemsOut();
            }

            if (!this.isFull()) {
               flag |= p_200109_1_.get();
            }

            if (flag) {
               this.setTransferCooldown(8);
               this.markDirty();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isFull() {
      for(ItemStack itemstack : this.inventory) {
         if (itemstack.isEmpty() || itemstack.getCount() != itemstack.getMaxStackSize()) {
            return false;
         }
      }

      return true;
   }

   private boolean transferItemsOut() {
      if (net.minecraftforge.items.VanillaInventoryCodeHooks.insertHook(this)) return true;
      IInventory iinventory = this.getInventoryForHopperTransfer();
      if (iinventory == null) {
         return false;
      } else {
         Direction direction = this.getBlockState().get(HopperBlock.FACING).getOpposite();
         if (this.isInventoryFull(iinventory, direction)) {
            return false;
         } else {
            for(int i = 0; i < this.getSizeInventory(); ++i) {
               if (!this.getStackInSlot(i).isEmpty()) {
                  ItemStack itemstack = this.getStackInSlot(i).copy();
                  ItemStack itemstack1 = putStackInInventoryAllSlots(this, iinventory, this.decrStackSize(i, 1), direction);
                  if (itemstack1.isEmpty()) {
                     iinventory.markDirty();
                     return true;
                  }

                  this.setInventorySlotContents(i, itemstack);
               }
            }

            return false;
         }
      }
   }

   private static IntStream func_213972_a(IInventory p_213972_0_, Direction p_213972_1_) {
      return p_213972_0_ instanceof ISidedInventory ? IntStream.of(((ISidedInventory)p_213972_0_).getSlotsForFace(p_213972_1_)) : IntStream.range(0, p_213972_0_.getSizeInventory());
   }

   /**
    * Returns false if the inventory has any room to place items in
    */
   private boolean isInventoryFull(IInventory inventoryIn, Direction side) {
      return func_213972_a(inventoryIn, side).allMatch((p_213970_1_) -> {
         ItemStack itemstack = inventoryIn.getStackInSlot(p_213970_1_);
         return itemstack.getCount() >= itemstack.getMaxStackSize();
      });
   }

   /**
    * Returns false if the specified IInventory contains any items
    */
   private static boolean isInventoryEmpty(IInventory inventoryIn, Direction side) {
      return func_213972_a(inventoryIn, side).allMatch((p_213973_1_) -> {
         return inventoryIn.getStackInSlot(p_213973_1_).isEmpty();
      });
   }

   /**
    * Pull dropped {@link net.minecraft.entity.item.EntityItem EntityItem}s from the world above the hopper and items
    * from any inventory attached to this hopper into the hopper's inventory.
    *
    * @param hopper the hopper in question
    * @return whether any items were successfully added to the hopper
    */
   public static boolean pullItems(IHopper hopper) {
      Boolean ret = net.minecraftforge.items.VanillaInventoryCodeHooks.extractHook(hopper);
      if (ret != null) return ret;
      IInventory iinventory = getSourceInventory(hopper);
      if (iinventory != null) {
         Direction direction = Direction.DOWN;
         return isInventoryEmpty(iinventory, direction) ? false : func_213972_a(iinventory, direction).anyMatch((p_213971_3_) -> {
            return pullItemFromSlot(hopper, iinventory, p_213971_3_, direction);
         });
      } else {
         for(ItemEntity itementity : getCaptureItems(hopper)) {
            if (captureItem(hopper, itementity)) {
               return true;
            }
         }

         return false;
      }
   }

   /**
    * Pulls from the specified slot in the inventory and places in any available slot in the hopper. Returns true if the
    * entire stack was moved
    */
   private static boolean pullItemFromSlot(IHopper hopper, IInventory inventoryIn, int index, Direction direction) {
      ItemStack itemstack = inventoryIn.getStackInSlot(index);
      if (!itemstack.isEmpty() && canExtractItemFromSlot(inventoryIn, itemstack, index, direction)) {
         ItemStack itemstack1 = itemstack.copy();
         ItemStack itemstack2 = putStackInInventoryAllSlots(inventoryIn, hopper, inventoryIn.decrStackSize(index, 1), (Direction)null);
         if (itemstack2.isEmpty()) {
            inventoryIn.markDirty();
            return true;
         }

         inventoryIn.setInventorySlotContents(index, itemstack1);
      }

      return false;
   }

   public static boolean captureItem(IInventory p_200114_0_, ItemEntity p_200114_1_) {
      boolean flag = false;
      ItemStack itemstack = p_200114_1_.getItem().copy();
      ItemStack itemstack1 = putStackInInventoryAllSlots((IInventory)null, p_200114_0_, itemstack, (Direction)null);
      if (itemstack1.isEmpty()) {
         flag = true;
         p_200114_1_.remove();
      } else {
         p_200114_1_.setItem(itemstack1);
      }

      return flag;
   }

   /**
    * Can this hopper extract the specified item from the specified slot on the specified side?
    */
   private static boolean canExtractItemFromSlot(IInventory inventoryIn, ItemStack stack, int index, Direction side) {
      return !(inventoryIn instanceof ISidedInventory) || ((ISidedInventory)inventoryIn).canExtractItem(index, stack, side);
   }


   /**
    * Returns the IInventory that this hopper is pointing into
    */
   @Nullable
   private IInventory getInventoryForHopperTransfer() {
      Direction direction = this.getBlockState().get(HopperBlock.FACING);
      return getInventoryAtPosition(this.getWorld(), this.pos.offset(direction));
   }

   private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
      if (stack1.getItem() != stack2.getItem()) {
         return false;
      } else if (stack1.getDamage() != stack2.getDamage()) {
         return false;
      } else if (stack1.getCount() > stack1.getMaxStackSize()) {
         return false;
      } else {
         return ItemStack.areItemStackTagsEqual(stack1, stack2);
      }
   }

   /**
    * Gets the world X position for this hopper entity.
    */
   public double getXPos() {
      return (double)this.pos.getX() + 0.5D;
   }

   /**
    * Gets the world Y position for this hopper entity.
    */
   public double getYPos() {
      return (double)this.pos.getY() + 0.5D;
   }

   /**
    * Gets the world Z position for this hopper entity.
    */
   public double getZPos() {
      return (double)this.pos.getZ() + 0.5D;
   }

   public void setTransferCooldown(int ticks) {
      this.transferCooldown = ticks;
   }

   private boolean isOnTransferCooldown() {
      return this.transferCooldown > 0;
   }

   public boolean mayTransfer() {
      return this.transferCooldown > 8;
   }

   protected NonNullList<ItemStack> getItems() {
      return this.inventory;
   }

   protected void setItems(NonNullList<ItemStack> itemsIn) {
      this.inventory = itemsIn;
   }

   public void onEntityCollision(Entity p_200113_1_) {
      if (p_200113_1_ instanceof ItemEntity) {
         BlockPos blockpos = this.getPos();
         if (VoxelShapes.compare(VoxelShapes.create(p_200113_1_.getBoundingBox().offset((double)(-blockpos.getX()), (double)(-blockpos.getY()), (double)(-blockpos.getZ()))), this.getCollectionArea(), IBooleanFunction.AND)) {
            this.updateHopper(() -> {
               return captureItem(this, (ItemEntity)p_200113_1_);
            });
         }
      }

   }

   protected Container createMenu(int id, PlayerInventory player) {
      return new HopperContainer(id, player, this);
   }

   @Override
   protected net.minecraftforge.items.IItemHandler createUnSidedHandler() {
      return new net.minecraftforge.items.VanillaHopperItemHandler(this);
   }

   public long getLastUpdateTime() {
      return this.tickedGameTime;
   }
}
