package com.yourname.redstonechip.block.entity;

import com.yourname.redstonechip.RedstoneChipMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * 强化红石比较器方块实体 — 存储输出信号值。
 */
public class ReinforcedComparatorBlockEntity extends BlockEntity {

    private int outputSignal;

    public ReinforcedComparatorBlockEntity(BlockPos pos, BlockState state) {
        super(RedstoneChipMod.REINFORCED_COMPARATOR_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putInt("outputSignal", this.outputSignal);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        this.outputSignal = nbt.getInt("outputSignal");
    }

    public int getOutputSignal() {
        return this.outputSignal;
    }

    public void setOutputSignal(int outputSignal) {
        this.outputSignal = outputSignal;
    }
}
