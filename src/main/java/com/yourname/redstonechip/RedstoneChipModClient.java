package com.yourname.redstonechip;

import com.yourname.redstonechip.block.ReinforcedRedstoneBlock;
import com.yourname.redstonechip.block.SuperconductingRedstoneBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;

/**
 * 16 色调色板 (index → (R, G, B))，含红不含黑。
 * 亮度随 POWER 变化。
 */
public class RedstoneChipModClient implements ClientModInitializer {

    /** 16 色羊毛系调色板（去掉白/黑，保留红，加玫瑰和鸭绿）。 */
    private static final int[][] PALETTE = {
        {255, 50,  50},  // 0  red
        {240, 90,  120}, // 1  rose
        {245, 145, 175}, // 2  pink
        {210, 45,  185}, // 3  magenta
        {150, 55,  205}, // 4  purple
        {50,  85,  225}, // 5  blue
        {90,  165, 250}, // 6  light_blue
        {30,  200, 205}, // 7  cyan
        {30,  170, 145}, // 8  teal
        {60,  190, 50},  // 9  green
        {130, 215, 50},  // 10 lime
        {255, 235, 40},  // 11 yellow
        {255, 165, 30},  // 12 orange
        {170, 105, 50},  // 13 brown
        {145, 145, 145}, // 14 gray
        {195, 195, 195}, // 15 light_gray
    };

    /** 根据颜色索引和信号强度计算最终颜色。 */
    private static int getColor(int colorIndex, int power) {
        float f = power / 15.0f;
        float brightness = 0.15f + f * 0.85f; // 最低 15% 亮度
        int[] base = PALETTE[colorIndex % 16];
        int r = Math.min(255, (int)(base[0] * brightness));
        int g = Math.min(255, (int)(base[1] * brightness));
        int b = Math.min(255, (int)(base[2] * brightness));
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void onInitializeClient() {
        // 注册所有强化红石方块到颜色提供器
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> {
            if (state.getBlock() instanceof ReinforcedRedstoneBlock rb) {
                int power = state.get(ReinforcedRedstoneBlock.POWER);
                return getColor(rb.colorIndex, power);
            }
            return 0xFFFFFF;
        }, RedstoneChipMod.REINFORCED_REDSTONE_BLOCKS.toArray(new Block[0]));

        // 注册所有超导红石方块到颜色提供器
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> {
            if (state.getBlock() instanceof SuperconductingRedstoneBlock sb) {
                int power = state.get(SuperconductingRedstoneBlock.POWER);
                return getColor(sb.colorIndex, power);
            }
            return 0xFFFFFF;
        }, RedstoneChipMod.SUPERCONDUCTING_REDSTONE_BLOCKS.toArray(new Block[0]));

        // 强化红石物品颜色：根据对应方块的颜色索引，用最高亮度显示
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof BlockItem bi) {
                if (bi.getBlock() instanceof ReinforcedRedstoneBlock rb) {
                    return getColor(rb.colorIndex, 15);
                }
            }
            return getColor(0, 15);
        }, RedstoneChipMod.REINFORCED_REDSTONE_BLOCKS.stream().map(b -> (Item) b.asItem()).toArray(Item[]::new));

        // 超导红石物品颜色
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof BlockItem bi) {
                if (bi.getBlock() instanceof SuperconductingRedstoneBlock sb) {
                    return getColor(sb.colorIndex, 15);
                }
            }
            return getColor(0, 15);
        }, RedstoneChipMod.SUPERCONDUCTING_REDSTONE_BLOCKS.stream().map(b -> (Item) b.asItem()).toArray(Item[]::new));
    }
}
