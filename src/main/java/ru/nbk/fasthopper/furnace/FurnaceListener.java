package ru.nbk.fasthopper.furnace;

import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;
import ru.nbk.fasthopper.FastHopperPlugin;
import ru.nbk.fasthopper.configure.Defs.FurnaceDef;

/**
 * Быстрые печки:
 *  - ticks-for-smelt  : время плавки одного предмета (в тиках),
 *  - fuel-mlt         : расход топлива (0.2 = топливо горит в 5 раз дольше),
 *  - resources-out    : сколько предметов получается из одного.
 */
public final class FurnaceListener implements Listener {

    private final FastHopperPlugin plugin;

    public FurnaceListener(FastHopperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent e) {
        FurnaceDef d = plugin.furnaceAt(e.getBlock());
        if (d == null || d.fuelMlt() <= 0 || d.fuelMlt() == 1.0) {
            return;
        }
        int time = (int) Math.round(e.getBurnTime() / d.fuelMlt());
        e.setBurnTime(Math.max(1, time));
    }

    @EventHandler
    public void onStart(FurnaceStartSmeltEvent e) {
        FurnaceDef d = plugin.furnaceAt(e.getBlock());
        if (d == null) {
            return;
        }
        e.setTotalCookTime(Math.max(1, d.ticksForSmelt()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        FurnaceDef d = plugin.furnaceAt(e.getBlock());
        if (d == null || d.resourcesOut() <= 1) {
            return;
        }

        ItemStack result = e.getResult().clone();
        int base = result.getAmount();
        int want = base * d.resourcesOut();

        // не вылезаем за размер стака в выходном слоте
        int current = 0;
        BlockState state = e.getBlock().getState(false);
        if (state instanceof Furnace furnace) {
            ItemStack out = furnace.getInventory().getResult();
            if (out != null && !out.getType().isAir()) {
                current = out.getAmount();
            }
        }
        int space = result.getMaxStackSize() - current;
        result.setAmount(Math.max(base, Math.min(want, space)));
        e.setResult(result);
    }
}
