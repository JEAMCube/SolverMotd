package net.fyrxlab.solverMOTD;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.function.Consumer;

public final class SolverMOTD extends JavaPlugin implements Listener {

    private boolean papiEnabled;
    private FileConfiguration messages;
    private File messagesFile;

    @Override
    public void onEnable() {
        reloadConfig();


        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // File Loading
        updateYamlFile("config.yml", c -> {});
        updateYamlFile("messages.yml", c -> {});

        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        papiEnabled = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papiEnabled) {
            getLogger().info("PlaceholderAPI is enabled! Solver has enabled the variables.");
        } else {
            getLogger().warning("PlaceholderAPI not found, Solver will not be able to use variables.");
        }
    }

    private void updateYamlFile(String fileName, Consumer<YamlConfiguration> updater) {
        File file = new File(getDataFolder(), fileName);

        // 1. Regenerar archivo si no existe (parte esencial que falta en el segundo código)
        if (!file.exists()) {
            saveResource(fileName, false);
            String msg = getMessage("config_regenerated").replace("{file}", fileName);
            getLogger().info(msg.replace("§", "&"));
        }

        try {
            // 2. Actualizar solo si el archivo existe
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(Objects.requireNonNull(getResource(fileName)))
            );

            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);

            // 3. Mantener la lógica de merge de configuraciones
            for (String key : defConfig.getKeys(true)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defConfig.get(key));
                }
            }

            // 4. Mantener el Consumer para actualizaciones en caliente
            updater.accept(currentConfig);

            // 5. Mejor manejo de errores
            currentConfig.save(file);

        } catch (NullPointerException | IOException e) {
            // 6. Logging balanceado
            getLogger().warning("Error while processing " + fileName + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        String line1 = getConfig().getString("motd.line1", "&a§r      §f§k! §e§lSolver§c§lMOTD §3§lPlugin §2[1.21] §4❤ §f§k!§r");
        String line2 = getConfig().getString("motd.line2", "         &aSetup your &eConfig.yml &afile!");
        boolean usePapi = getConfig().getBoolean("use_papi", true);

        if (papiEnabled && getConfig().getBoolean("use_papi", true)) {
            line1 = PlaceholderAPI.setPlaceholders(null, line1);
            line2 = PlaceholderAPI.setPlaceholders(null, line2);
        }

        String finalMotd = line1.replace("&", "§") + "\n" + line2.replace("&", "§");
        event.setMotd(finalMotd);
    }

    private void setupConfig() {
        // Añadir sección motd con valores por defecto
        getConfig().addDefault("motd.line1", "§r      §f§k! §e§lSolver§c§lMOTD §3§lPlugin §2[1.21] §4❤ §f§k!§r");
        getConfig().addDefault("motd.line2", "         &aSetup your &eConfig.yml &afile!");
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("smotd")) {
            // Comando sin argumentos -> mostrar ayuda
            if (args.length == 0) {
                sender.sendMessage(getMessage("help_message"));
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "reload":
                    if (!sender.hasPermission("solvermotd.reload")) {
                        String noPerm = getMessage("reload_no_permission")
                                .replace("{permission}", "solvermotd.reload"); // Reemplazar placeholder
                        sender.sendMessage(noPerm);
                        return true;
                    }
                    updateYamlFile("config.yml", c -> reloadConfig());
                    updateYamlFile("messages.yml", c -> messages = c);

                    reloadConfig();
                    messagesFile = new File(getDataFolder(), "messages.yml");
                    messages = YamlConfiguration.loadConfiguration(messagesFile);
                    sender.sendMessage(getMessage("reload_success"));
                    break;


                case "help":
                    sender.sendMessage(getMessage("help_message"));
                    break;

                default: // Comando desconocido
                    sender.sendMessage(getMessage("invalid_command"));
                    break;
            }
            return true;
        }
        return false;
    }

    private String getMessage(String path) {
        // Añadir chequeo de null
        if (messages == null) {
            messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        }
        String msg = messages.getString(path, "&cMensaje no encontrado: " + path);
        msg = msg.replace("{prefix}", messages.getString("prefix", ""));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
