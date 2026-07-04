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

import java.util.*;

/**
 * 超导红石粉 — 与强化红石粉行为一致，但信号不衰减。
 * 即输出强度 = 当前 POWER 值（而非 POWER-1）。
 * <p>
 * 整条连通网络作为一个整体计算外部信号强度，统一开关。
 * 截面为 2×2 棋盘格纹理（颜色/白/白/颜色），通过模型元素拆分实现。
 */
public class SuperconductingRedstoneBlock extends Block {

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

    /** 颜色索引 0-15，用于客户端染色。 */
    public final int colorIndex;

    /** 正在被更新的网络位置集，防止递归且不阻塞独立网络。 */
    private static final Set<BlockPos> UPDATING = new HashSet<>();

    public SuperconductingRedstoneBlock(Settings settings, int colorIndex) {
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

    // ========== 网络整体传播（解决零衰减导致的信号锁死） ==========

    /**
     * BFS 扫描整个同色同类型网络，统一根据外部信号源设置功率。
     * 整条线要么全开要么全关，不存在内部梯度。
     */
    private void updateNetwork(World world, BlockPos pos) {
        if (world.isClient || UPDATING.contains(pos)) return;
        try {
            BlockState startState = world.getBlockState(pos);
            if (!(startState.getBlock() instanceof SuperconductingRedstoneBlock self)) return;

            int myColor = self.colorIndex;

            // 1. BFS 找出整个连通网络
            Set<BlockPos> network = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            network.add(pos);
            queue.add(pos);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (network.contains(neighbor)) continue;
                    BlockState neighborState = world.getBlockState(neighbor);
                    if (neighborState.getBlock() instanceof SuperconductingRedstoneBlock sc) {
                        if (sc.colorIndex == myColor) {
                            network.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }

            // 标记本网络所有位置，防止递归且不阻塞其他独立网络
            UPDATING.addAll(network);

            // 2. 扫描整个网络的外部信号源（仅非超导方块）
            int maxPower = 0;
            for (BlockPos p : network) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = p.offset(dir);
                    if (network.contains(neighborPos)) continue;
                    BlockState neighborState = world.getBlockState(neighborPos);
                    if (!(neighborState.getBlock() instanceof SuperconductingRedstoneBlock)) {
                        int power = world.getEmittedRedstonePower(neighborPos, dir.getOpposite());
                        if (power > maxPower) maxPower = power;
                    }
                }
            }

            // 3. 统一更新整个网络的连接状态和功率
            for (BlockPos p : network) {
                BlockState state = world.getBlockState(p);
                // 更新连接（同色超导 + 实体方块）
                for (Direction dir : Direction.values()) {
                    BlockPos np = p.offset(dir);
                    boolean connected = false;
                    BlockState ns = world.getBlockState(np);
                    if (ns.getBlock() instanceof SuperconductingRedstoneBlock sc) {
                        connected = sc.colorIndex == myColor;
                    } else {
                        connected = ns.isSideSolidFullSquare(world, np, dir.getOpposite());
                    }
                    state = state.with(FACING_PROPERTIES.get(dir), connected);
                }
                BlockState newState = state.with(POWER, maxPower);
                if (!newState.equals(state)) {
                    world.setBlockState(p, newState, Block.NOTIFY_ALL);
                }
            }
        } finally {
            UPDATING.clear();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient) {
            updateNetwork(world, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos,
                             BlockState oldState, boolean notify) {
        if (!oldState.isOf(state.getBlock()) && !world.isClient) {
            updateNetwork(world, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void scheduledTick(BlockState state, ServerWorld world,
                              BlockPos pos, Random random) {
        if (!world.isClient) {
            updateNetwork(world, pos);
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

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakRedstonePower(BlockState state, BlockView world,
                                    BlockPos pos, Direction direction) {
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
        // 只更新初始连接状态，功率由 onBlockAdded → updateNetwork 处理
        return updateConnections(getDefaultState(), world, pos);
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
}
