package ru.nbk.fasthopper;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.nbk.fasthopper.brewing.BrewerManager;
import ru.nbk.fasthopper.command.GiveCommand;
import ru.nbk.fasthopper.configure.Defs;
import ru.nbk.fasthopper.configure.Defs.BrewerDef;
import ru.nbk.fasthopper.configure.Defs.FurnaceDef;
import ru.nbk.fasthopper.configure.Defs.HopperDef;
import ru.nbk.fasthopper.furnace.FurnaceListener;
import ru.nbk.fasthopper.hopper.HopperManager;
import ru.nbk.fasthopper.listener.BlockListener;
import ru.nbk.fasthopper.misc.ItemFactory;

public final class FastHopperPlugin extends JavaPlugin {

    private NamespacedKey hopperKey;
    private NamespacedKey furnaceKey;
    private NamespacedKey brewKey;

    private Defs defs;
    private HopperManager hopperManager;
    private BrewerManager brewerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Те же ключи, что и в старой версии плагина: уже поставленные блоки остаются "быстрыми".
        hopperKey = NamespacedKey.minecraft("fasthopper");
        furnaceKey = NamespacedKey.minecraft("fastfurnace");
        brewKey = NamespacedKey.minecraft("fastbrew");

        defs = Defs.load(getConfig());

        hopperManager = new HopperManager(this);
        brewerManager = new BrewerManager(this);

        getServer().getPluginManager().registerEvents(hopperManager, this);
        getServer().getPluginManager().registerEvents(brewerManager, this);
        getServer().getPluginManager().registerEvents(new FurnaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        bind("whitecustomhoppers", new GiveCommand(
                "whitecustomhoppers", "whitecustomhoppers.command",
                () -> defs.hopperIds(),
                id -> {
                    HopperDef d = defs.hopper(id);
                    return d == null ? null : hopperItem(d);
                },
                this::reloadAll));

        bind("whitecustomfurnaces", new GiveCommand(
                "whitecustomfurnaces", "whitecustomfurnaces.command",
                () -> defs.furnaceIds(),
                id -> {
                    FurnaceDef d = defs.furnace(id);
                    return d == null ? null : furnaceItem(d);
                },
                this::reloadAll));

        bind("whitecustombrewers", new GiveCommand(
                "whitecustombrewers", "whitecustombrewers.command",
                () -> defs.brewerIds(),
                id -> {
                    BrewerDef d = defs.brewer(id);
                    return d == null ? null : brewerItem(d);
                },
                this::reloadAll));

        hopperManager.start();
        brewerManager.start();

        getLogger().info("Загружено: воронок " + defs.hopperIds().size()
                + ", печек " + defs.furnaceIds().size()
                + ", варочных стоек " + defs.brewerIds().size());
    }

    @Override
    public void onDisable() {
        if (hopperManager != null) {
            hopperManager.stop();
        }
        if (brewerManager != null) {
            brewerManager.stop();
        }
    }

    private void bind(String name, GiveCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    public void reloadAll() {
        reloadConfig();
        defs = Defs.load(getConfig());
        hopperManager.reload();
        brewerManager.reload();
    }

    // ---------- доступ ----------

    public Defs defs() {
        return defs;
    }

    public HopperManager hoppers() {
        return hopperManager;
    }

    public BrewerManager brewers() {
        return brewerManager;
    }

    public NamespacedKey hopperKey() {
        return hopperKey;
    }

    public NamespacedKey furnaceKey() {
        return furnaceKey;
    }

    public NamespacedKey brewKey() {
        return brewKey;
    }

    // ---------- предметы ----------

    public ItemStack hopperItem(HopperDef d) {
        return ItemFactory.build(Material.HOPPER, d.item(), hopperKey, d.id());
    }

    public ItemStack furnaceItem(FurnaceDef d) {
        return ItemFactory.build(Material.FURNACE, d.item(), furnaceKey, d.id());
    }

    public ItemStack brewerItem(BrewerDef d) {
        return ItemFactory.build(Material.BREWING_STAND, d.item(), brewKey, d.id());
    }

    // ---------- теги на блоках ----------

    public String readTag(Block block, NamespacedKey key) {
        BlockState state = block.getState(false);
        if (state instanceof TileState tile) {
            return tile.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        }
        return null;
    }

    public HopperDef hopperAt(Block block) {
        String v = readTag(block, hopperKey);
        return v == null ? null : defs.hopper(v);
    }

    public FurnaceDef furnaceAt(Block block) {
        String v = readTag(block, furnaceKey);
        return v == null ? null : defs.furnace(v);
    }

    public BrewerDef brewerAt(Block block) {
        String v = readTag(block, brewKey);
        return v == null ? null : defs.brewer(v);
    }
}
