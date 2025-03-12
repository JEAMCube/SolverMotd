package net.fyrxlab.solverMOTD;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class SolverMOTD extends JavaPlugin implements Listener {

    private boolean papiEnabled;
    private YamlConfiguration messages;
    private File messagesFile;
    private CommentPreservingYaml commentYaml;

    @Override
    public void onEnable() {
        // Inicializamos nuestro manejador YAML que preserva comentarios.
        commentYaml = new CommentPreservingYaml();

        // Cargar la configuración principal del plugin
        reloadConfig();

        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        // Actualizamos messages.yml utilizando CommentPreservingYaml para preservar las ## Notas
        updateYamlFile("messages.yml", map -> {
            // No se requiere acción adicional, ya se fusionaron los valores por defecto.
        });
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Actualizamos config.yml utilizando CommentPreservingYaml y recargamos la configuración
        updateYamlFile("config.yml", map -> reloadConfig());

        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        papiEnabled = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papiEnabled) {
            getLogger().info("PlaceholderAPI is enabled! Solver has enabled the variables.");
        } else {
            getLogger().warning("PlaceholderAPI not found, Solver will not be able to use variables.");
        }
    }

    /**
     * Actualiza el archivo YAML utilizando CommentPreservingYaml para preservar los comentarios.
     *
     * @param fileName Nombre del archivo a actualizar.
     * @param updater  Función para aplicar modificaciones al mapa cargado.
     */
    private void updateYamlFile(String fileName, Consumer<Map<String, Object>> updater) {
        File file = new File(getDataFolder(), fileName);

        // Si el archivo no existe, se crea a partir del recurso incluido.
        if (!file.exists()) {
            saveResource(fileName, false);
            String msg = getMessage("config_regenerated").replace("{file}", fileName);
            getLogger().info(msg.replace("§", "&"));
        }

        // Leemos el header (comentarios iniciales) del archivo.
        String header = readHeader(file);

        // Cargar la configuración actual desde el archivo usando CommentPreservingYaml.
        Map<String, Object> currentMap;
        try {
            currentMap = commentYaml.load(file);
        } catch (IOException e) {
            getLogger().warning("Error while loading " + fileName + ": " + e.getMessage());
            currentMap = new LinkedHashMap<>();
        }

        // Cargar la configuración por defecto desde el recurso del plugin.
        Map<String, Object> defaultMap;
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(getResource(fileName)))) {
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml defaultYaml = new Yaml(options);
            Object loaded = defaultYaml.load(reader);
            if (loaded instanceof Map) {
                defaultMap = (Map<String, Object>) loaded;
            } else {
                defaultMap = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            getLogger().warning("Error while loading default " + fileName + ": " + e.getMessage());
            defaultMap = new LinkedHashMap<>();
        }

        // Fusionar los valores por defecto con la configuración actual.
        commentYaml.mergeMaps(defaultMap, currentMap);

        // Se permite aplicar actualizaciones adicionales.
        updater.accept(currentMap);

        // Guardamos el archivo, reinsertando el header leído.
        try {
            commentYaml.save(currentMap, file, header);
        } catch (IOException e) {
            getLogger().warning("Error while saving " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Lee y devuelve todas las líneas de comentario del archivo (todas las líneas que inician con "#").
     *
     * @param file Archivo YAML del que se extraerán los comentarios.
     * @return String con todos los comentarios encontrados, separados por saltos de línea.
     */
    private String readHeader(File file) {
        StringBuilder header = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#")) {
                    header.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            getLogger().warning("Error reading header from " + file.getName() + ": " + e.getMessage());
        }
        return header.toString();
    }


    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        String line1 = getConfig().getString("motd.line1", "&a§r      §f§k! §e§lSolver§c§lMOTD §3§lPlugin §2[1.8 - 1.21] §4❤ §f§k!§r");
        String line2 = getConfig().getString("motd.line2", "         &aSetup your &eConfig.yml &afile!");
        boolean usePapi = getConfig().getBoolean("use_papi", true);

        if (papiEnabled && usePapi) {
            line1 = PlaceholderAPI.setPlaceholders(null, line1);
            line2 = PlaceholderAPI.setPlaceholders(null, line2);
        }

        String finalMotd = line1.replace("&", "§") + "\n" + line2.replace("&", "§");
        event.setMotd(finalMotd);
    }

    private String getMessage(String path) {
        // Si messages es nulo, se recarga desde el archivo.
        if (messages == null) {
            messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        }
        String msg = messages.getString(path, "&cMensaje no encontrado: " + path);
        msg = msg.replace("{prefix}", messages.getString("prefix", ""));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("smotd")) {
            if (args.length == 0) {
                sender.sendMessage(getMessage("help_message"));
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "reload":
                    if (!sender.hasPermission("solvermotd.reload")) {
                        String noPerm = getMessage("reload_no_permission").replace("{permission}", "solvermotd.reload");
                        sender.sendMessage(noPerm);
                        return true;
                    }
                    updateYamlFile("config.yml", map -> reloadConfig());
                    updateYamlFile("messages.yml", map -> messages = YamlConfiguration.loadConfiguration(messagesFile));
                    sender.sendMessage(getMessage("reload_success"));
                    break;

                case "help":
                    sender.sendMessage(getMessage("help_message"));
                    break;

                default:
                    sender.sendMessage(getMessage("invalid_command"));
                    break;
            }
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        // Lógica de deshabilitación del plugin
    }
}
