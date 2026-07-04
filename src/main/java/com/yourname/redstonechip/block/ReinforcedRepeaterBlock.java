package com.yourname.redstonechip.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.TickPriority;

/**
 * 强化红石中继器 — 逻辑对齐原版 AbstractRedstoneGateBlock。
 */
public class ReinforcedRepeaterBlock extends Block {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final IntProperty DELAY = IntProperty.of("delay", 1, 4);
    public static final BooleanProperty LOCKED = BooleanProperty.of("locked");
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final VoxelShape SHAPE = VoxelShapes.fullCube();

    public ReinforcedRepeaterBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState()
                .with(FACING, Direction.NORTH).with(DELAY, 1).with(LOCKED, false).with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, DELAY, LOCKED, POWERED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }
    @Override @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    // ========== 红石输出 ==========

    @Override @SuppressWarnings("deprecation")
    public int getWeakRedstonePower(BlockState state, BlockView w, BlockPos p, Direction dir) {
        return (state.get(POWERED) && dir == state.get(FACING)) ? 15 : 0;
    }
    @Override @SuppressWarnings("deprecation")
    public int getStrongRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) {
        return getWeakRedstonePower(s, w, p, d);
    }
    @Override @SuppressWarnings("deprecation")
    public boolean emitsRedstonePower(BlockState s) { return true; }

    // ========== 输入检测（对齐原版） ==========

    /** 检测背面的红石信号强度（含通过实体方块的强信号）。 */
    protected int getPower(World world, BlockPos pos, BlockState state) {
        Direction back = state.get(FACING).getOpposite();
        BlockPos backPos = pos.offset(back);
        return world.getEmittedRedstonePower(backPos, back);
    }

    protected boolean hasPower(World world, BlockPos pos, BlockState state) {
        return getPower(world, pos, state) > 0;
    }

    // ========== 侧面锁定 ==========

    protected boolean isLocked(BlockView world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        if (facing.getAxis() == Direction.Axis.Y) return false; // 垂直方向无法锁定
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();
        return getPowerOnSide(world, pos, left) > 0 || getPowerOnSide(world, pos, right) > 0;
    }

    protected int getPowerOnSide(BlockView world, BlockPos pos, Direction side) {
        BlockPos p = pos.offset(side);
        BlockState s = world.getBlockState(p);
        return s.emitsRedstonePower() ? s.getWeakRedstonePower(world, p, side.getOpposite()) : 0;
    }

    // ========== 更新调度 ==========

    @Override @SuppressWarnings("deprecation")
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block src, BlockPos srcPos, boolean ntf) {
        if (!world.isClient) {
            if (state.canPlaceAt(world, pos)) updatePowered(world, pos, state);
            else { Block.dropStacks(state, world, pos); world.removeBlock(pos, false); }
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean ntf) {
        if (!old.isOf(state.getBlock()) && !world.isClient) {
            updatePowered(world, pos, state);
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random rand) {
        boolean powered = hasPower(world, pos, state);
        boolean locked = isLocked(world, pos, state);
        boolean changed = (powered != state.get(POWERED) || locked != state.get(LOCKED));
        if (changed) {
            BlockState newState = state.with(POWERED, powered).with(LOCKED, locked);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            updateTarget(world, pos, newState);
        }
    }

    protected void updatePowered(World world, BlockPos pos, BlockState state) {
        boolean wasPowered = state.get(POWERED);
        boolean nowPowered = hasPower(world, pos, state);
        if (wasPowered != nowPowered) {
            TickPriority priority = isLocked(world, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
            if (!world.getBlockTickScheduler().isTicking(pos, this))
                world.scheduleBlockTick(pos, this, getUpdateDelayInternal(state), priority);
        }
    }

    /** 延迟映射：1→0gt, 2→2gt, 3→4gt, 4→6gt */
    protected int getUpdateDelayInternal(BlockState state) {
        int d = state.get(DELAY);
        if (d == 1) return 0;
        return (d - 1) * 2;
    }

    protected void updateTarget(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        BlockPos targetPos = pos.offset(facing);
        world.updateNeighborsAlways(targetPos, this);
        world.updateNeighborsExcept(pos, this, facing);
    }

    @Override @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState ns, boolean moved) {
        if (!state.isOf(ns.getBlock()) && !moved) {
            world.updateNeighborsAlways(pos, this);
            super.onStateReplaced(state, world, pos, ns, moved);
        }
    }

    @Override @SuppressWarnings("deprecation")
    public boolean canPlaceAt(BlockState s, WorldView w, BlockPos p) { return true; }

    // ========== 交互 ==========

    @Override @SuppressWarnings("deprecation")
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            int d = state.get(DELAY) % 4 + 1;
            int gt = d == 1 ? 0 : (d - 1) * 2;
            world.setBlockState(pos, state.with(DELAY, d), Block.NOTIFY_ALL);
            player.sendMessage(net.minecraft.text.Text.literal("中继器延迟: " + (gt == 0 ? "0gt (直通)" : gt + "gt")), true);
        }
        return ActionResult.success(world.isClient);
    }
}
