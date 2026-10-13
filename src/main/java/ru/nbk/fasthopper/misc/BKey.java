package ru.nbk.fasthopper.misc;

import org.bukkit.block.Block;

import java.util.UUID;

/** Ключ блока: мир + координаты. */
public record BKey(UUID world, int x, int y, int z) {

    public static BKey of(Block block) {
        return new BKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
