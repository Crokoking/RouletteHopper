package com.crokoking.roulettehopper;

import net.minecraft.block.HopperBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

@SuppressWarnings("NullableProblems")
public class RouletteHopperBlock extends HopperBlock {
    public RouletteHopperBlock(Properties properties) {
        super(properties);
    }

    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return new RouletteHopperTileEntity();
    }
}
