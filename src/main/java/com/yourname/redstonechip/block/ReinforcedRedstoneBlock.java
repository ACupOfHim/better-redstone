package com.yourname.redstonechip.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.TickPriority;

import java.util.Map;

public class ReinforcedRedstoneBlock extends Block {

    public static final IntProperty POWER = IntProperty.of("power", 0, 15);
    public static final BooleanProperty UP   = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty EAST  = Properties.EAST;
    public static final BooleanProperty WEST  = Properties.WEST;

    private static final Map<Direction, BooleanProperty> FACING_PROPERTIES =
            ImmutableMap.<Direction, BooleanProperty>builder()
                    .put(Direction.UP, UP).put(Direction.DOWN, DOWN)
                    .put(Direction.NORTH, NORTH).put(Direction.SOUTH, SOUTH)
                    .put(Direction.EAST, EAST).put(Direction.WEST, WEST)
                    .build();

    private static final VoxelShape CORE = createCuboidShape(7, 7, 7, 9, 9, 9);
    private static final VoxelShape ARM_UP    = createCuboidShape(7,  9,  7,  9, 16,  9);
    private static final VoxelShape ARM_DOWN  = createCuboidShape(7,  0,  7,  9,  7,  9);
    private static final VoxelShape ARM_NORTH = createCuboidShape(7,  7,  0,  9,  9,  7);
    private static final VoxelShape ARM_SOUTH = createCuboidShape(7,  7,  9,  9,  9, 16);
    private static final VoxelShape ARM_EAST  = createCuboidShape(9,  7,  7, 16,  9,  9);
    private static final VoxelShape ARM_WEST  = createCuboidShape(0,  7,  7,  7,  9,  9);

    private static final Map<Direction, VoxelShape> ARM_SHAPES =
            ImmutableMap.<Direction, VoxelShape>builder()
                    .put(Direction.UP, ARM_UP).put(Direction.DOWN, ARM_DOWN)
                    .put(Direction.NORTH, ARM_NORTH).put(Direction.SOUTH, ARM_SOUTH)
                    .put(Direction.EAST, ARM_EAST).put(Direction.WEST, ARM_WEST)
                    .build();

    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = Maps.newHashMap();

    /** 颜色索引 0-31，用于客户端染色。 */
    public final int colorIndex;

    public ReinforcedRedstoneBlock(Settings settings, int colorIndex) {
        super(settings);
        this.colorIndex = colorIndex;
        setDefaultState(getStateManager().getDefaultState()
                .with(POWER, 0)
                .with(UP, false).with(DOWN, false)
                .with(NORTH, false).with(SOUTH, false)
                .with(EAST, false).with(WEST, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWER, UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }

    private BlockState updateConnections(BlockState state, World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            boolean connected = neighborState.isOf(this)
                    || neighborState.isSideSolidFullSquare(world, neighborPos, dir.getOpposite());
            state = state.with(FACING_PROPERTIES.get(dir), connected);
        }
        return state;
    }

    /** 单次重算（不循环） */
    private BlockState recalculate(BlockState state, World world, BlockPos pos, boolean limitToSource, BlockPos sourcePos) {
        state = updateConnections(state, world, pos);
        int power = calculatePower(world, pos, limitToSource, sourcePos);
        state = state.with(POWER, power);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient) {
            boolean limitToSource = !(sourceBlock instanceof ReinforcedRedstoneBlock);
            BlockState current = state;
            // 循环直到功率稳定（至多 16 轮）
            for (int i = 0; i < 16; i++) {
                BlockState newState = recalculate(current, world, pos, limitToSource, sourcePos);
                if (newState.equals(current)) break;
                world.setBlockState(pos, newState, Block.NOTIFY_ALL);
                current = newState;
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos,
                             BlockState oldState, boolean notify) {
        if (!oldState.isOf(state.getBlock()) && !world.isClient) {
            world.scheduleBlockTick(pos, this, 1, TickPriority.NORMAL);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world,
                              BlockPos pos, Random random) {
        // 初始放置后的重算：正常模式
        BlockState newState = recalculate(state, world, pos, false, null);
        if (!newState.equals(state)) {
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            world.updateNeighborsAlways(pos, this);
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    private int calculatePower(World world, BlockPos pos, boolean limitToSource, BlockPos sourcePos) {
        int maxPower = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof ReinforcedRedstoneBlock neighbor) {
                if (neighbor.colorIndex == this.colorIndex) {
                    // RR 间传播：读功率后减 1（原版链式衰减）
                    int power = world.getEmittedRedstonePower(neighborPos, dir.getOpposite());
                    if (power > 0) power--;
                    if (power > maxPower) maxPower = power;
                }
            } else if (limitToSource) {
                // 限制模式：只读 sourcePos 处的非 RR 邻居
                if (neighborPos.equals(sourcePos)) {
                    int power = world.getEmittedRedstonePower(neighborPos, dir.getOpposite());
                    if (power > maxPower) maxPower = power;
                }
            } else {
                // 正常模式：读所有非 RR 邻居
                int power = world.getEmittedRedstonePower(neighborPos, dir.getOpposite());
                if (power > maxPower) maxPower = power;
            }
        }
        return maxPower;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakRedstonePower(BlockState state, BlockView world,
                                    BlockPos pos, Direction direction) {
        // 与原版红石粉一致：输出满功率
        return state.get(POWER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongRedstonePower(BlockState state, BlockView world,
                                      BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView world,
                                      BlockPos pos, ShapeContext context) {
        return getShape(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockView world,
                                        BlockPos pos, ShapeContext context) {
        return getShape(state);
    }

    private VoxelShape getShape(BlockState state) {
        return SHAPE_CACHE.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Direction dir : Direction.values()) {
                if (s.get(FACING_PROPERTIES.get(dir))) {
                    shape = VoxelShapes.union(shape, ARM_SHAPES.get(dir));
                }
            }
            return shape;
        });
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        return recalculate(getDefaultState(), world, pos, false, null);
    }
}
