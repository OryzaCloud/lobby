package com.oryza.lobby.scene;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;

public final class SceneBuilder {
    public static final Pos SPAWN_POSITION = new Pos(0.5, 1.0, 3.0, 0.0f, 0.0f);
    public static final Pos PANEL_FRONT_POSITION = new Pos(0.5, 2.5, -1.90, 0.0f, 0.0f);
    public static final Pos PANEL_BACK_POSITION = new Pos(0.5, 2.5, 7.90, 180.0f, 0.0f);

    public static void build(InstanceContainer container) {
        if (container.defaultClock() != null) {
            container.defaultClock().pause();
        }
        container.setTime(6000);

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 10; z++) {
                container.setBlock(x, 0, z, Block.DEEPSLATE_BRICKS);
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int y = 1; y <= 5; y++) {
                container.setBlock(x, y, -4, Block.POLISHED_DEEPSLATE);
                container.setBlock(x, y, 10, Block.POLISHED_DEEPSLATE);
            }
        }

        for (int z = -3; z <= 9; z++) {
            for (int y = 1; y <= 5; y++) {
                container.setBlock(-4, y, z, Block.POLISHED_DEEPSLATE);
                container.setBlock(4, y, z, Block.POLISHED_DEEPSLATE);
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 10; z++) {
                if (Math.abs(x) <= 2 && z >= -1 && z <= 7) {
                    container.setBlock(x, 6, z, Block.GLASS);
                } else {
                    container.setBlock(x, 6, z, Block.QUARTZ_BLOCK);
                }
            }
        }

        container.setBlock(-3, 1, -3, Block.SEA_LANTERN);
        container.setBlock(3, 1, -3, Block.SEA_LANTERN);
        container.setBlock(-3, 1, 9, Block.SEA_LANTERN);
        container.setBlock(3, 1, 9, Block.SEA_LANTERN);

        container.setBlock(-3, 5, -3, Block.GLOWSTONE);
        container.setBlock(3, 5, -3, Block.GLOWSTONE);
        container.setBlock(-3, 5, 9, Block.GLOWSTONE);
        container.setBlock(3, 5, 9, Block.GLOWSTONE);

        container.setBlock(-3, 4, 3, Block.LANTERN);
        container.setBlock(3, 4, 3, Block.LANTERN);

        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                container.setBlock(x, y, -2, Block.BLACK_CONCRETE);
                container.setBlock(x, y, 8, Block.BLACK_CONCRETE);
            }
        }

        container.setBlock(-2, 1, -2, Block.BIRCH_STAIRS.withProperty("facing", "east"));
        container.setBlock(2, 1, -2, Block.BIRCH_STAIRS.withProperty("facing", "west"));
        container.setBlock(-2, 2, -2, Block.BIRCH_PLANKS);
        container.setBlock(2, 2, -2, Block.BIRCH_PLANKS);
        container.setBlock(-2, 3, -2, Block.BIRCH_STAIRS.withProperty("facing", "east"));
        container.setBlock(2, 3, -2, Block.BIRCH_STAIRS.withProperty("facing", "west"));
        container.setBlock(-2, 4, -2, Block.BIRCH_PLANKS);
        container.setBlock(2, 4, -2, Block.BIRCH_PLANKS);

        container.setBlock(-2, 1, 8, Block.BIRCH_STAIRS.withProperty("facing", "east"));
        container.setBlock(2, 1, 8, Block.BIRCH_STAIRS.withProperty("facing", "west"));
        container.setBlock(-2, 2, 8, Block.BIRCH_PLANKS);
        container.setBlock(2, 2, 8, Block.BIRCH_PLANKS);
        container.setBlock(-2, 3, 8, Block.BIRCH_STAIRS.withProperty("facing", "east"));
        container.setBlock(2, 3, 8, Block.BIRCH_STAIRS.withProperty("facing", "west"));
        container.setBlock(-2, 4, 8, Block.BIRCH_PLANKS);
        container.setBlock(2, 4, 8, Block.BIRCH_PLANKS);

        for (int x = -1; x <= 1; x++) {
            container.setBlock(x, 1, -2, Block.BIRCH_SLAB);
            container.setBlock(x, 4, -2, Block.BIRCH_SLAB);
            container.setBlock(x, 1, 8, Block.BIRCH_SLAB);
            container.setBlock(x, 4, 8, Block.BIRCH_SLAB);
        }

        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                container.loadChunk(cx, cz).join();
            }
        }
    }
}
