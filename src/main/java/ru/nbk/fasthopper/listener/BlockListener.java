package ru.nbk.fasthopper.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.nbk.fasthopper.FastHopperPlugin;
import ru.nbk.fasthopper.configure.Defs.BrewerDef;
import ru.nbk.fasthopper.configure.Defs.FurnaceDef;
import ru.nbk.fasthopper.configure.Defs.HopperDef;

/** Ставим/ломаем кастомные блоки: переносим тег с предмета на блок и обратно. */
public final class BlockListener implements Listener {

    private final FastHopperPlugin plugin;

    public BlockListener(FastHopperPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isFurnace(Material m) {
        return m == Material.FURNACE || m == Material.BLAST_FURNACE || m == Material.SMOKER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack inHand = e.getItemInHand();
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Block block = e.getBlockPlaced();
        Material type = block.getType();

        if (type == Material.HOPPER) {
            String v = pdc.get(plugin.hopperKey(), PersistentDataType.STRING);
            if (v != null && tag(block, plugin.hopperKey(), v)) {
                HopperDef d = plugin.defs().hopper(v);
                if (d != null) {
                    plugin.hoppers().register(block, d);
                }
            }
        } else if (isFurnace(type)) {
            String v = pdc.get(plugin.furnaceKey(), PersistentDataType.STRING);
            if (v != null) {
                tag(block, plugin.furnaceKey(), v);
            }
        } else if (type == Material.BREWING_STAND) {
            String v = pdc.get(plugin.brewKey(), PersistentDataType.STRING);
            if (v != null && tag(block, plugin.brewKey(), v)) {
                BrewerDef d = plugin.defs().brewer(v);
                if (d != null) {
                    plugin.brewers().register(block, d);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();

        if (type == Material.HOPPER) {
            HopperDef d = plugin.hopperAt(block);
            plugin.hoppers().unregister(block);
            if (d != null) {
                giveBack(e, block, plugin.hopperItem(d));
            }
        } else if (isFurnace(type)) {
            FurnaceDef d = plugin.furnaceAt(block);
            if (d != null) {
                giveBack(e, block, plugin.furnaceItem(d));
            }
        } else if (type == Material.BREWING_STAND) {
            BrewerDef d = plugin.brewerAt(block);
            plugin.brewers().unregister(block);
            if (d != null) {
                giveBack(e, block, plugin.brewerItem(d));
            }
        }
    }

    /** Вместо обычного блока выпадает кастомный предмет (содержимое контейнера выпадает как обычно). */
    private void giveBack(BlockBreakEvent e, Block block, ItemStack item) {
        GameMode mode = e.getPlayer().getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        e.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
    }

    private boolean tag(Block block, NamespacedKey key, String value) {
        BlockState state = block.getState();
        if (state instanceof TileState tile) {
            tile.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
            tile.update(true, false);
            return true;
        }
        return false;
    }
}
