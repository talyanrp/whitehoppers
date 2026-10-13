package ru.nbk.fasthopper.brewing;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import ru.nbk.fasthopper.FastHopperPlugin;
import ru.nbk.fasthopper.configure.Defs.BrewerDef;
import ru.nbk.fasthopper.misc.BKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Быстрые варочные стойки:
 *  brew-fuel-multiplier  - во сколько раз больше "зарядов" даёт топливо (2.0 = тратится в 2 раза меньше),
 *  save-ingrid-chance    - шанс (в %), что ингредиент не потратится при варке,
 *  brew-time-dec-*       - ускорение варки: каждые Interval тиков время варки уменьшается на value.
 */
public final class BrewerManager implements Listener {

    private final FastHopperPlugin plugin;
    private final Map<BKey, BrewerDef> active = new HashMap<>();
    private BukkitTask task;
    private long counter;

    public BrewerManager(FastHopperPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- жизненный цикл ----------

    public void start() {
        scanAll();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active.clear();
    }

    public void reload() {
        active.clear();
        scanAll();
    }

    private static boolean speedsUp(BrewerDef d) {
        return d.decInterval() > 0 && d.decValue() > 0;
    }

    public void register(Block block, BrewerDef def) {
        if (speedsUp(def)) {
            active.put(BKey.of(block), def);
        }
    }

    public void unregister(Block block) {
        active.remove(BKey.of(block));
    }

    private void scanAll() {
        if (!plugin.defs().anyBrewerSpeedup()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        if (!plugin.defs().anyBrewerSpeedup()) {
            return;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof BrewingStand stand) {
                String v = stand.getPersistentDataContainer().get(plugin.brewKey(), PersistentDataType.STRING);
                if (v == null) {
                    continue;
                }
                BrewerDef def = plugin.defs().brewer(v);
                if (def != null) {
                    register(stand.getBlock(), def);
                }
            }
        }
    }

    // ---------- события ----------

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        scanChunk(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (active.isEmpty()) {
            return;
        }
        UUID world = e.getWorld().getUID();
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        active.keySet().removeIf(k -> k.world().equals(world) && k.chunkX() == cx && k.chunkZ() == cz);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(BrewingStandFuelEvent e) {
        BrewerDef d = plugin.brewerAt(e.getBlock());
        if (d == null || d.fuelMult() <= 0 || d.fuelMult() == 1.0) {
            return;
        }
        int power = (int) Math.round(e.getFuelPower() * d.fuelMult());
        e.setFuelPower(Math.max(1, power));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        BrewerDef d = plugin.brewerAt(e.getBlock());
        if (d == null || d.saveChance() <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= d.saveChance()) {
            return;
        }

        ItemStack ingredient = e.getContents().getIngredient();
        if (ingredient == null || ingredient.getType().isAir()) {
            return;
        }

        // ванильная варка забирает ингредиент уже после события - возвращаем его на следующем тике
        final ItemStack saved = ingredient.clone();
        final Block block = e.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> restoreIngredient(block, saved));
    }

    private void restoreIngredient(Block block, ItemStack saved) {
        BlockState state = block.getState(false);
        if (!(state instanceof BrewingStand stand)) {
            return;
        }
        BrewerInventory inv = stand.getInventory();
        ItemStack current = inv.getIngredient();
        if (current == null || current.getType().isAir()) {
            inv.setIngredient(saved);
        } else if (current.isSimilar(saved)) {
            current.setAmount(saved.getAmount());
            inv.setIngredient(current);
        }
    }

    // ---------- ускорение варки ----------

    private void tick() {
        counter++;
        if (active.isEmpty()) {
            return;
        }

        for (Map.Entry<BKey, BrewerDef> entry : new ArrayList<>(active.entrySet())) {
            BrewerDef def = entry.getValue();
            if (counter % def.decInterval() != 0) {
                continue;
            }

            BKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                active.remove(key);
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() != Material.BREWING_STAND) {
                active.remove(key);
                continue;
            }

            BlockState state = block.getState(false);
            if (state instanceof BrewingStand stand) {
                int time = stand.getBrewingTime();
                if (time > 1) {
                    stand.setBrewingTime(Math.max(1, time - def.decValue()));
                }
            }
        }
    }
}
