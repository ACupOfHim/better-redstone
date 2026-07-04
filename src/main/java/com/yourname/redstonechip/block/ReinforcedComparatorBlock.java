package com.yourname.redstonechip.block;

import com.yourname.redstonechip.block.entity.ReinforcedComparatorBlockEntity;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ComparatorMode;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RedstoneView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.TickPriority;

import java.util.List;
import java.util.function.Predicate;

/**
 * 强化红石比较器 — 完全对齐原版 ComparatorBlock 逻辑。
 */
public class ReinforcedComparatorBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final EnumProperty<ComparatorMode> MODE = Properties.COMPARATOR_MODE;
    private static final VoxelShape SHAPE = VoxelShapes.fullCube();

    public ReinforcedComparatorBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState()
                .with(FACING, Direction.NORTH).with(POWERED, false).with(MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, MODE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }
    @Override @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    // ========== 方块实体 ==========
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ReinforcedComparatorBlockEntity(pos, state);
    }

    // ========== 红石输出 ==========
    @Override @SuppressWarnings("deprecation")
    public int getWeakRedstonePower(BlockState state, BlockView w, BlockPos pos, Direction dir) {
        if (dir == state.get(FACING)) {
            BlockEntity be = w.getBlockEntity(pos);
            if (be instanceof ReinforcedComparatorBlockEntity rc) return rc.getOutputSignal();
        }
        return 0;
    }
    @Override @SuppressWarnings("deprecation")
    public int getStrongRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) {
        return getWeakRedstonePower(s, w, p, d);
    }
    @Override @SuppressWarnings("deprecation")
    public boolean emitsRedstonePower(BlockState s) { return true; }

    // ========== 输入检测（完全对齐原版） ==========

    /** 背面输入 — 对齐 AbstractRedstoneGateBlock.getPower，对 RR 特殊处理。 */
    protected int getBackPower(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        BlockPos backPos = pos.offset(facing.getOpposite());
        int power = world.getEmittedRedstonePower(backPos, facing.getOpposite());
        if (power >= 15) return 15;
        BlockState backState = world.getBlockState(backPos);
        // 原版 AbstractRedstoneGateBlock.getPower 对 REDSTONE_WIRE 直接读 POWER 属性
        if (backState.isOf(Blocks.REDSTONE_WIRE)) {
            power = Math.max(power, backState.get(RedstoneWireBlock.POWER));
        } else if (backState.getBlock() instanceof ReinforcedRedstoneBlock) {
            power = Math.max(power, backState.get(ReinforcedRedstoneBlock.POWER));
        }
        return power;
    }

    /** 侧面输入最大值（AbstractRedstoneGateBlock.getMaxInputLevelSides）。 */
    protected int getMaxInputLevelSides(RedstoneView world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        if (facing.getAxis() == Direction.Axis.Y) return 0;
        Direction left = facing.rotateYClockwise();
        Direction right = facing.rotateYCounterclockwise();
        int leftPower = world.getEmittedRedstonePower(pos.offset(left), left.getOpposite(), true);
        int rightPower = world.getEmittedRedstonePower(pos.offset(right), right.getOpposite(), true);
        return Math.max(leftPower, rightPower);
    }

    /** getPower = super.getPower() + 前方比较器输出检测 + 物品展示框检测 */
    protected int getPower(World world, BlockPos pos, BlockState state) {
        int power = getBackPower(world, pos, state);
        Direction facing = state.get(FACING);
        BlockPos outputPos = pos.offset(facing);
        BlockState outputState = world.getBlockState(outputPos);

        // 检查输出方向是否有比较器输出
        if (outputState.hasComparatorOutput()) {
            power = outputState.getComparatorOutput(world, outputPos);
            if (power >= 15) return 15;
        }

        // 检查前方实体方块后面是否有比较器输出或物品展示框
        if (power < 15 && outputState.isSolidBlock(world, outputPos)) {
            BlockPos behindPos = outputPos.offset(facing);
            BlockState behindState = world.getBlockState(behindPos);

            // 物品展示框
            int framePower = Integer.MIN_VALUE;
            ItemFrameEntity frame = getAttachedItemFrame(world, facing, behindPos);
            if (frame != null) framePower = frame.getComparatorPower();

            // 比较器输出
            int behindComparatorPower = behindState.hasComparatorOutput()
                    ? behindState.getComparatorOutput(world, behindPos)
                    : Integer.MIN_VALUE;

            int max = Math.max(framePower, behindComparatorPower);
            if (max != Integer.MIN_VALUE) {
                power = max;
            }
        }
        return power;
    }

    /** 获取附着在指定位置、面向指定方向的物品展示框。 */
    private ItemFrameEntity getAttachedItemFrame(World world, Direction facing, BlockPos pos) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        Predicate<ItemFrameEntity> predicate = frame -> frame != null && frame.getHorizontalFacing() == facing;
        List<ItemFrameEntity> frames = world.getEntitiesByClass(ItemFrameEntity.class, box, predicate);
        return frames.size() == 1 ? frames.get(0) : null;
    }

    /** 计算输出信号（原版 ComparatorBlock.calculateOutputSignal）。 */
    private int calculateOutputSignal(World world, BlockPos pos, BlockState state) {
        int backPower = getPower(world, pos, state);
        if (backPower == 0) return 0;
        int sidePower = getMaxInputLevelSides(world, pos, state);
        if (sidePower > backPower) return 0;
        if (state.get(MODE) == ComparatorMode.SUBTRACT) return backPower - sidePower;
        return backPower;
    }

    // ========== 更新调度 ==========

    @Override @SuppressWarnings("deprecation")
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block src, BlockPos srcPos, boolean ntf) {
        if (!world.isClient) {
            if (state.canPlaceAt(world, pos)) {
                // 立即更新 BlockEntity 输出信号，让前方方块能读到新值
                int output = calculateOutputSignal(world, pos, state);
                BlockEntity be = world.getBlockEntity(pos);
                boolean outputChanged = false;
                if (be instanceof ReinforcedComparatorBlockEntity rc) {
                    if (rc.getOutputSignal() != output) {
                        rc.setOutputSignal(output);
                        outputChanged = true;
                    }
                }
                // 如果输出变了，立即通知前方方块，不等到 tick
                if (outputChanged) {
                    updateTarget(world, pos, state.with(POWERED, hasPower(world, pos, state)));
                }
                // 然后正常调度
                updatePowered(world, pos, state);
            } else {
                Block.dropStacks(state, world, pos);
                world.removeBlock(pos, false);
            }
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean ntf) {
        if (!old.isOf(state.getBlock()) && !world.isClient) {
            updateTarget(world, pos, state);
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random rand) {
        updateComparator(world, pos, state);
    }

    /** updatePowered — ComparatorBlock 覆写版，调度前计算输出。 */
    protected void updatePowered(World world, BlockPos pos, BlockState state) {
        if (world.getBlockTickScheduler().isTicking(pos, this)) return;

        int output = calculateOutputSignal(world, pos, state);
        BlockEntity be = world.getBlockEntity(pos);
        int currentOutput = (be instanceof ReinforcedComparatorBlockEntity rc) ? rc.getOutputSignal() : 0;

        if (output != currentOutput || state.get(POWERED) != hasPower(world, pos, state)) {
            TickPriority priority = isTargetNotAligned(world, pos, state)
                    ? TickPriority.HIGH : TickPriority.NORMAL;
            world.scheduleBlockTick(pos, this, 2, priority);
        }
    }

    /** update — ComparatorBlock 私有的 update 方法。 */
    private void updateComparator(World world, BlockPos pos, BlockState state) {
        int output = calculateOutputSignal(world, pos, state);
        BlockEntity be = world.getBlockEntity(pos);
        int oldOutput = 0;
        if (be instanceof ReinforcedComparatorBlockEntity rc) {
            oldOutput = rc.getOutputSignal();   // 先读取旧值
            rc.setOutputSignal(output);          // 再设置新值
        }

        boolean wasPowered = state.get(POWERED);
        boolean nowPowered = hasPower(world, pos, state);

        if (output != oldOutput || wasPowered != nowPowered) {
            BlockState newState = state.with(POWERED, nowPowered);
            world.setBlockState(pos, newState, 2);
        }

        updateTarget(world, pos, state.with(POWERED, nowPowered));
    }

    protected boolean hasPower(World world, BlockPos pos, BlockState state) {
        int power = getPower(world, pos, state);
        if (power == 0) return false;
        int sidePower = getMaxInputLevelSides(world, pos, state);
        if (power > sidePower) return true;
        return power == sidePower && state.get(MODE) == ComparatorMode.COMPARE;
    }

    @Override @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState ns, boolean moved) {
        if (!state.isOf(ns.getBlock()) && !moved) {
            super.onStateReplaced(state, world, pos, ns, false);
            updateTarget(world, pos, state);
        }
    }

    @Override @SuppressWarnings("deprecation")
    public boolean canPlaceAt(BlockState s, WorldView w, BlockPos p) { return true; }

    protected void updateTarget(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        BlockPos targetPos = pos.offset(facing.getOpposite());
        world.updateNeighbor(targetPos, this, pos);
        world.updateNeighborsExcept(pos, this, facing);
    }

    protected boolean isTargetNotAligned(BlockView world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        BlockPos targetPos = pos.offset(facing.getOpposite());
        BlockState targetState = world.getBlockState(targetPos);
        return !AbstractRedstoneGateBlock.isRedstoneGate(targetState);
    }

    // ========== 交互 ==========

    @Override @SuppressWarnings("deprecation")
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getAbilities().allowModifyWorld) return ActionResult.PASS;
        if (!world.isClient) {
            BlockState newState = state.cycle(MODE);
            float pitch = newState.get(MODE) == ComparatorMode.SUBTRACT ? 0.55f : 0.5f;
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, pitch);
            world.setBlockState(pos, newState, 2);
            updateComparator(world, pos, newState);
            player.sendMessage(Text.literal("比较器: " + (newState.get(MODE) == ComparatorMode.COMPARE ? "比较模式" : "减法模式")), true);
        }
        return ActionResult.success(world.isClient);
    }
}
