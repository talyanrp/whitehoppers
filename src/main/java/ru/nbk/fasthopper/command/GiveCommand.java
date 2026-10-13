package ru.nbk.fasthopper.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/** /<команда> give <игрок> <предмет> [количество]  и  /<команда> reload */
public final class GiveCommand implements TabExecutor {

    private final String label;
    private final String permission;
    private final Supplier<Collection<String>> ids;
    private final Function<String, ItemStack> factory;
    private final Runnable reload;

    public GiveCommand(String label, String permission, Supplier<Collection<String>> ids,
                       Function<String, ItemStack> factory, Runnable reload) {
        this.label = label;
        this.permission = permission;
        this.ids = ids;
        this.factory = factory;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cУ вас нет прав.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reload.run();
            sender.sendMessage("§aКонфиг перезагружен.");
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cИгрок не найден: " + args[1]);
                return true;
            }

            ItemStack proto = factory.apply(args[2]);
            if (proto == null) {
                sender.sendMessage("§cТакого предмета нет. Доступные: §f" + String.join(", ", ids.get()));
                return true;
            }

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cКоличество должно быть числом.");
                    return true;
                }
            }
            if (amount < 1 || amount > 2304) {
                sender.sendMessage("§cКоличество должно быть от 1 до 2304.");
                return true;
            }

            int remaining = amount;
            while (remaining > 0) {
                int n = Math.min(remaining, proto.getMaxStackSize());
                ItemStack stack = proto.clone();
                stack.setAmount(n);
                for (ItemStack left : target.getInventory().addItem(stack).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), left);
                }
                remaining -= n;
            }

            sender.sendMessage("§aВыдано §f" + amount + " §aшт. [" + args[2] + "] игроку §f" + target.getName());
            return true;
        }

        sender.sendMessage("§e/" + label + " give <игрок> <предмет> [количество]");
        sender.sendMessage("§e/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(permission) || args.length == 0) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        boolean give = args[0].equalsIgnoreCase("give");

        if (args.length == 1) {
            out.add("give");
            out.add("reload");
        } else if (args.length == 2 && give) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 3 && give) {
            out.addAll(ids.get());
        } else if (args.length == 4 && give) {
            out.add("1");
            out.add("16");
            out.add("64");
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
