package ru.nbk.fasthopper.misc;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Собирает предметы из секции item в config.yml. */
public final class ItemFactory {

    private static final String COLOR_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    /** Старые названия зачарований (1.16) -> новые. */
    private static final Map<String, String> ENCHANT_ALIASES = Map.ofEntries(
            Map.entry("durability", "unbreaking"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("damage_all", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("protection_fall", "feather_falling"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("arrow_damage", "power"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("arrow_fire", "flame"),
            Map.entry("arrow_infinite", "infinity"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("water_worker", "aqua_affinity"),
            Map.entry("oxygen", "respiration"),
            Map.entry("luck", "luck_of_the_sea")
    );

    private ItemFactory() {
    }

    /** &a -> §a, &x&r&r&g&g&b&b -> §x§r§r§g§g§b§b */
    public static String color(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CHARS.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static ItemStack build(Material fallback, ConfigurationSection sec, NamespacedKey key, String value) {
        Material material = fallback;
        if (sec != null) {
            Material parsed = Material.matchMaterial(sec.getString("material", fallback.name()));
            if (parsed != null && parsed.isItem()) {
                material = parsed;
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (sec != null) {
            String name = sec.getString("name");
            if (name != null) {
                meta.setDisplayName(color(name));
            }

            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(color(line));
                }
                meta.setLore(colored);
            }

            for (String flag : sec.getStringList("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // неизвестный флаг пропускаем
                }
            }

            ConfigurationSection enchants = sec.getConfigurationSection("enchants");
            if (enchants != null) {
                for (String enchName : enchants.getKeys(false)) {
                    Enchantment enchantment = findEnchantment(enchName);
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, Math.max(1, enchants.getInt(enchName, 1)), true);
                    }
                }
            }
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment findEnchantment(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String mapped = ENCHANT_ALIASES.getOrDefault(lower, lower);
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(mapped));
    }
}
