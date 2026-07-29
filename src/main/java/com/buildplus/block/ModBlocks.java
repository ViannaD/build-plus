package com.buildplus.block;

import com.buildplus.BuildPlusMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

/**
 * Registro central dos blocos, itens e block entity types do Build+.
 */
public final class ModBlocks {

	private ModBlocks() {
	}

	public static final BuildingBlock BUILDING_BLOCK = new BuildingBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.LAPIS_BLUE)
					.strength(3.0f, 6.0f)
					.sounds(BlockSoundGroup.METAL)
					.nonOpaque()
					.luminance(state -> 4)
	);

	public static final BlockItem BUILDING_BLOCK_ITEM = new BlockItem(BUILDING_BLOCK, new Item.Settings());

	public static BlockEntityType<BuildingBlockEntity> BUILDING_BLOCK_ENTITY_TYPE;

	public static void register() {
		Registry.register(Registries.BLOCK, id("building_block"), BUILDING_BLOCK);
		Registry.register(Registries.ITEM, id("building_block"), BUILDING_BLOCK_ITEM);

		BUILDING_BLOCK_ENTITY_TYPE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				id("building_block"),
				FabricBlockEntityTypeBuilder.create(BuildingBlockEntity::new, BUILDING_BLOCK).build()
		);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(BUILDING_BLOCK_ITEM));
	}

	private static Identifier id(String path) {
		return new Identifier(BuildPlusMod.MOD_ID, path);
	}
}
