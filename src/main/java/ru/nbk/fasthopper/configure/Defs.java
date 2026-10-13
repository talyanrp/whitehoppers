package ru.nbk.fasthopper.configure;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Все описания из config.yml: воронки, печки, варочные стойки. */
public final class Defs {

    public record HopperDef(String id, String alias,
                            int sendRate, int sendAmount, double loseChance, int loseAmount,
                            int recvRate, int recvAmount,
                            ConfigurationSection item) {
    }

    public record FurnaceDef(String id, String alias,
                             double fuelMlt, int ticksForSmelt, int resourcesOut,
                             ConfigurationSection item) {
    }

    public record BrewerDef(String id, String alias,
                            int decInterval, int decValue, double fuelMult, double saveChance,
                            ConfigurationSection item) {
    }

    private final Map<String, HopperDef> hopperById = new LinkedHashMap<>();
    private final Map<String, HopperDef> hopperLookup = new HashMap<>();
    private final Map<String, FurnaceDef> furnaceById = new LinkedHashMap<>();
    private final Map<String, FurnaceDef> furnaceLookup = new HashMap<>();
    private final Map<String, BrewerDef> brewerById = new LinkedHashMap<>();
    private final Map<String, BrewerDef> brewerLookup = new HashMap<>();

    public static Defs load(FileConfiguration cfg) {
        Defs defs = new Defs();

        ConfigurationSection hoppers = cfg.getConfigurationSection("hoppers");
        if (hoppers != null) {
            for (String id : hoppers.getKeys(false)) {
                ConfigurationSection s = hoppers.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                defs.hopperById.put(id, new HopperDef(
                        id, s.getString("key"),
                        Math.max(1, s.getInt("send.tick-rate", 20)),
                        Math.max(1, s.getInt("send.amount-per-tick", 1)),
                        s.getDouble("send.lose.chance", 0.0),
                        Math.max(0, s.getInt("send.lose.amount", 0)),
                        Math.max(1, s.getInt("receive.tick-rate", 20)),
                        Math.max(1, s.getInt("receive.amount-per-tick", 1)),
                        s.getConfigurationSection("item")));
            }
        }

        ConfigurationSection furnaces = cfg.getConfigurationSection("furnaces");
        if (furnaces != null) {
            for (String id : furnaces.getKeys(false)) {
                ConfigurationSection s = furnaces.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                defs.furnaceById.put(id, new FurnaceDef(
                        id, s.getString("key"),
                        s.getDouble("fuel-mlt", 1.0),
                        Math.max(1, s.getInt("ticks-for-smelt", 200)),
                        Math.max(1, s.getInt("resources-out", 1)),
                        s.getConfigurationSection("item")));
            }
        }

        ConfigurationSection brewers = cfg.getConfigurationSection("brewers");
        if (brewers != null) {
            for (String id : brewers.getKeys(false)) {
                ConfigurationSection s = brewers.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                defs.brewerById.put(id, new BrewerDef(
                        id, s.getString("key"),
                        Math.max(0, s.getInt("brew-time-dec-Interval", 0)),
                        Math.max(0, s.getInt("brew-time-dec-value", 0)),
                        s.getDouble("brew-fuel-multiplier", 1.0),
                        s.getDouble("save-ingrid-chance", 0.0),
                        s.getConfigurationSection("item")));
            }
        }

        fillLookup(defs.hopperById, defs.hopperLookup, HopperDef::alias);
        fillLookup(defs.furnaceById, defs.furnaceLookup, FurnaceDef::alias);
        fillLookup(defs.brewerById, defs.brewerLookup, BrewerDef::alias);
        return defs;
    }

    /** Ищем и по имени секции, и по полю key (в старом плагине в тегах мог лежать любой из них). */
    private static <T> void fillLookup(Map<String, T> byId, Map<String, T> lookup, Function<T, String> alias) {
        lookup.putAll(byId);
        for (T def : byId.values()) {
            String a = alias.apply(def);
            if (a != null && !a.isEmpty() && !lookup.containsKey(a)) {
                lookup.put(a, def);
            }
        }
    }

    public HopperDef hopper(String idOrKey) {
        return hopperLookup.get(idOrKey);
    }

    public FurnaceDef furnace(String idOrKey) {
        return furnaceLookup.get(idOrKey);
    }

    public BrewerDef brewer(String idOrKey) {
        return brewerLookup.get(idOrKey);
    }

    public Collection<String> hopperIds() {
        return hopperById.keySet();
    }

    public Collection<String> furnaceIds() {
        return furnaceById.keySet();
    }

    public Collection<String> brewerIds() {
        return brewerById.keySet();
    }

    /** Есть ли хоть одна стойка с ускорением варки. */
    public boolean anyBrewerSpeedup() {
        for (BrewerDef d : brewerById.values()) {
            if (d.decInterval() > 0 && d.decValue() > 0) {
                return true;
            }
        }
        return false;
    }
}
