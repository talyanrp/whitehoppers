package ru.nbk.fasthopper.hopper;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import ru.nbk.fasthopper.FastHopperPlugin;
import ru.nbk.fasthopper.configure.Defs.HopperDef;
import ru.nbk.fasthopper.misc.BKey;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Быстрые воронки.
 *  send    - воронка отдаёт в контейнер, на который смотрит (каждые tick-rate тиков по amount-per-tick предметов,
 *            с шансом lose.chance % пропадает lose.amount предметов);
 *  receive - воронка забирает из контейнера над собой (каждые tick-rate тиков по amount-per-tick предметов).
 * Обычная передача этой воронки (ванильная) отключается.
 */
public final class HopperManager implements Listener {

    private static final Set<Material> BREW_INGREDIENTS = EnumSet.of(
            Material.NETHER_WART, Material.REDSTONE, Material.GLOWSTONE_DUST, Material.GUNPOWDER,
            Material.DRAGON_BREATH, Material.FERMENTED_SPIDER_EYE, Material.SUGAR, Material.RABBIT_FOOT,
            Material.GLISTERING_MELON_SLICE, Material.SPIDER_EYE, Material.PUFFERFISH, Material.MAGMA_CREAM,
            Material.GOLDEN_CARROT, Material.BLAZE_POWDER, Material.GHAST_TEAR, Material.TURTLE_HELMET,
            Material.PHANTOM_MEMBRANE, Material.COBWEB, Material.STONE, Material.SLIME_BLOCK,
            Material.BREEZE_ROD);

    private final FastHopperPlugin plugin;
    private final Map<BKey, HopperDef> active = new HashMap<>();
    private BukkitTask task;
    private long counter;

    public HopperManager(FastHopperPlugin plugin) {
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

    public void register(Block block, HopperDef def) {
        active.put(BKey.of(block), def);
    }

    public void unregister(Block block) {
        active.remove(BKey.of(block));
    }

    private void scanAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof org.bukkit.block.Hopper hopper) {
                String v = hopper.getPersistentDataContainer().get(plugin.hopperKey(), PersistentDataType.STRING);
                if (v == null) {
                    continue;
                }
                HopperDef def = plugin.defs().hopper(v);
                if (def != null) {
                    active.put(BKey.of(hopper.getBlock()), def);
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
        UUID world = e.getWorld().getUID();
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        active.keySet().removeIf(k -> k.world().equals(world) && k.chunkX() == cx && k.chunkZ() == cz);
    }

    /** Отключаем ванильную передачу у быстрых воронок - они работают сами. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (active.isEmpty()) {
            return;
        }
        Location loc = e.getInitiator().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        BKey key = new BKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (active.containsKey(key)) {
            e.setCancelled(true);
        }
    }

    // ---------- основной цикл ----------

    private void tick() {
        counter++;
        if (active.isEmpty()) {
            return;
        }

        for (Map.Entry<BKey, HopperDef> entry : new ArrayList<>(active.entrySet())) {
            BKey key = entry.getKey();
            HopperDef def = entry.getValue();

            boolean doSend = counter % def.sendRate() == 0;
            boolean doReceive = counter % def.recvRate() == 0;
            if (!doSend && !doReceive) {
                continue;
            }

            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                active.remove(key);
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() != Material.HOPPER) {
                active.remove(key);
                continue;
            }

            // воронка под редстоуном не работает (как в ванили)
            if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Hopper data) || !data.isEnabled()) {
                continue;
            }

            BlockState state = block.getState(false);
            if (!(state instanceof org.bukkit.block.Hopper hopper)) {
                continue;
            }
            Inventory inv = hopper.getInventory();

            if (doReceive) {
                receive(block, inv, def);
            }
            if (doSend) {
                send(block, inv, def, data.getFacing());
            }
        }
    }

    private void receive(Block hopperBlock, Inventory hopperInv, HopperDef def) {
        Block source = loadedRelative(hopperBlock, BlockFace.UP);
        if (source == null) {
            return;
        }
        Inventory inv = containerInventory(source);
        if (inv == null) {
            return;
        }
        int[] slots = extractSlots(inv);
        if (slots == null) {
            return;
        }

        int budget = def.recvAmount();
        for (int slot : slots) {
            if (budget <= 0) {
                break;
            }
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            int take = Math.min(stack.getAmount(), budget);
            int inserted = addGeneric(hopperInv, stack, take);
            if (inserted <= 0) {
                continue;
            }
            budget -= inserted;
            int left = stack.getAmount() - inserted;
            if (left <= 0) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(left);
                inv.setItem(slot, stack);
            }
        }
    }

    private void send(Block hopperBlock, Inventory hopperInv, HopperDef def, BlockFace facing) {
        Block dst = loadedRelative(hopperBlock, facing);
        if (dst == null) {
            return;
        }
        Inventory target = containerInventory(dst);
        if (target == null || !acceptsItems(target.getType())) {
            return;
        }

        int budget = def.sendAmount();
        int lose = 0;
        if (def.loseAmount() > 0 && def.loseChance() > 0
                && ThreadLocalRandom.current().nextDouble() * 100.0 < def.loseChance()) {
            lose = def.loseAmount();
        }

        for (int slot = 0; slot < hopperInv.getSize() && budget > 0; slot++) {
            ItemStack stack = hopperInv.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            int take = Math.min(stack.getAmount(), budget);
            int destroy = Math.min(lose, take);
            int deliver = take - destroy;

            int inserted = 0;
            if (deliver > 0) {
                inserted = insert(target, stack, deliver, facing);
                if (inserted <= 0) {
                    continue; // цель не принимает этот предмет
                }
            }

            int removed = inserted + destroy;
            lose -= destroy;
            budget -= removed;

            int left = stack.getAmount() - removed;
            if (left <= 0) {
                hopperInv.setItem(slot, null);
            } else {
                stack.setAmount(left);
                hopperInv.setItem(slot, stack);
            }
        }
    }

    // ---------- вспомогательное ----------

    private static Block loadedRelative(Block block, BlockFace face) {
        int x = block.getX() + face.getModX();
        int z = block.getZ() + face.getModZ();
        if (!block.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return block.getRelative(face);
    }

    private static Inventory containerInventory(Block block) {
        if (block.getType().isAir()) {
            return null;
        }
        BlockState state = block.getState(false);
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private static boolean isPlainStorage(InventoryType t) {
        return t == InventoryType.CHEST || t == InventoryType.BARREL || t == InventoryType.SHULKER_BOX
                || t == InventoryType.HOPPER || t == InventoryType.DROPPER || t == InventoryType.DISPENSER;
    }

    private static boolean isFurnaceType(InventoryType t) {
        return t == InventoryType.FURNACE || t == InventoryType.BLAST_FURNACE || t == InventoryType.SMOKER;
    }

    private static boolean acceptsItems(InventoryType t) {
        return isPlainStorage(t) || isFurnaceType(t) || t == InventoryType.BREWING;
    }

    /** Из каких слотов воронка может забирать (как в ванили). */
    private static int[] extractSlots(Inventory inv) {
        InventoryType t = inv.getType();
        if (isFurnaceType(t)) {
            return new int[]{2};
        }
        if (t == InventoryType.BREWING) {
            return new int[]{0, 1, 2};
        }
        if (isPlainStorage(t)) {
            int[] all = new int[inv.getSize()];
            for (int i = 0; i < all.length; i++) {
                all[i] = i;
            }
            return all;
        }
        return null;
    }

    /** Вставка с учётом правил печек и варочных стоек. Возвращает, сколько реально вставили. */
    private int insert(Inventory target, ItemStack proto, int amount, BlockFace direction) {
        InventoryType t = target.getType();
        Material type = proto.getType();

        if (isFurnaceType(t)) {
            if (direction == BlockFace.DOWN) {
                return insertSlot(target, 0, proto, amount);       // сверху -> плавимый предмет
            }
            if (type.isFuel()) {
                return insertSlot(target, 1, proto, amount);       // сбоку -> топливо
            }
            return 0;
        }

        if (t == InventoryType.BREWING) {
            if (direction == BlockFace.DOWN) {
                return BREW_INGREDIENTS.contains(type) ? insertSlot(target, 3, proto, amount) : 0;
            }
            if (type == Material.BLAZE_POWDER) {
                return insertSlot(target, 4, proto, amount);
            }
            if (type == Material.GLASS_BOTTLE || type == Material.POTION
                    || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                int inserted = 0;
                for (int slot = 0; slot < 3 && inserted < amount; slot++) {
                    ItemStack cur = target.getItem(slot);
                    if (cur == null || cur.getType().isAir()) {
                        ItemStack one = proto.clone();
                        one.setAmount(1);
                        target.setItem(slot, one);
                        inserted++;
                    }
                }
                return inserted;
            }
            return 0;
        }

        // шалкер в шалкер нельзя
        if (t == InventoryType.SHULKER_BOX && Tag.SHULKER_BOXES.isTagged(type)) {
            return 0;
        }
        return addGeneric(target, proto, amount);
    }

    private static int addGeneric(Inventory target, ItemStack proto, int amount) {
        ItemStack copy = proto.clone();
        copy.setAmount(amount);
        Map<Integer, ItemStack> leftover = target.addItem(copy);
        int notInserted = 0;
        for (ItemStack s : leftover.values()) {
            notInserted += s.getAmount();
        }
        return amount - notInserted;
    }

    private static int insertSlot(Inventory inv, int slot, ItemStack proto, int amount) {
        int max = Math.min(proto.getMaxStackSize(), inv.getMaxStackSize());
        ItemStack cur = inv.getItem(slot);
        if (cur == null || cur.getType().isAir()) {
            int n = Math.min(amount, max);
            ItemStack copy = proto.clone();
            copy.setAmount(n);
            inv.setItem(slot, copy);
            return n;
        }
        if (!cur.isSimilar(proto)) {
            return 0;
        }
        int space = max - cur.getAmount();
        if (space <= 0) {
            return 0;
        }
        int n = Math.min(space, amount);
        cur.setAmount(cur.getAmount() + n);
        inv.setItem(slot, cur);
        return n;
    }
}
