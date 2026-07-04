package com.yourname.redstonechip;

import com.yourname.redstonechip.block.ReinforcedComparatorBlock;
import com.yourname.redstonechip.block.ReinforcedRedstoneBlock;
import com.yourname.redstonechip.block.ReinforcedRepeaterBlock;
import com.yourname.redstonechip.block.SuperconductingRedstoneBlock;
import com.yourname.redstonechip.block.entity.ReinforcedComparatorBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RedstoneChipMod implements ModInitializer {
    public static final String MOD_ID = "redstone_chip";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final List<Block> REINFORCED_REDSTONE_BLOCKS = new ArrayList<>();
    public static final List<Block> SUPERCONDUCTING_REDSTONE_BLOCKS = new ArrayList<>();

    public static final Block REINFORCED_REPEATER = new ReinforcedRepeaterBlock(
            AbstractBlock.Settings.copy(Blocks.REPEATER));
    public static final Block REINFORCED_COMPARATOR = new ReinforcedComparatorBlock(
            AbstractBlock.Settings.copy(Blocks.COMPARATOR));

    public static final BlockEntityType<ReinforcedComparatorBlockEntity> REINFORCED_COMPARATOR_BLOCK_ENTITY =
            BlockEntityType.Builder.create(ReinforcedComparatorBlockEntity::new, REINFORCED_COMPARATOR).build();

    @Override
    public void onInitialize() {
        // 16 色强化红石粉
        for (int i = 0; i < 16; i++) {
            String name = COLOR_NAMES[i];
            Block block = new ReinforcedRedstoneBlock(
                    AbstractBlock.Settings.create().strength(0.5f, 0.5f).nonOpaque(),
                    i);
            Identifier id = Identifier.of(MOD_ID, "reinforced_redstone_" + name);
            Registry.register(Registries.BLOCK, id, block);
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
            REINFORCED_REDSTONE_BLOCKS.add(block);
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(block));
        }

        // 强化中继器
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "reinforced_repeater"), REINFORCED_REPEATER);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "reinforced_repeater"),
                new BlockItem(REINFORCED_REPEATER, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(REINFORCED_REPEATER));

        // 强化比较器
        Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, "reinforced_comparator"), REINFORCED_COMPARATOR);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "reinforced_comparator"),
                new BlockItem(REINFORCED_COMPARATOR, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(REINFORCED_COMPARATOR));

        // 比较器方块实体
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "reinforced_comparator"),
                REINFORCED_COMPARATOR_BLOCK_ENTITY);

        // 16 色超导红石粉（信号不衰减）
        for (int i = 0; i < 16; i++) {
            String name = COLOR_NAMES[i];
            Block block = new SuperconductingRedstoneBlock(
                    AbstractBlock.Settings.create().strength(0.5f, 0.5f).nonOpaque(),
                    i);
            Identifier id = Identifier.of(MOD_ID, "superconducting_redstone_" + name);
            Registry.register(Registries.BLOCK, id, block);
            Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
            SUPERCONDUCTING_REDSTONE_BLOCKS.add(block);
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(block));
        }

        LOGGER.info("Reinforced and Superconducting Redstone mod loaded!");
    }

    public static final String[] COLOR_NAMES = {
        "red", "rose", "pink", "magenta",
        "purple", "blue", "light_blue", "cyan",
        "teal", "green", "lime", "yellow",
        "orange", "brown", "gray", "light_gray"
    };
}
