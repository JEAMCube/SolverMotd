// Package declaration for the plugin
package net.fyrxlab.solverMOTD;

// Import required dependencies
import me.clip.placeholderapi.PlaceholderAPI; // PlaceholderAPI integration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.fyrxlab.solverMOTD.org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor; // Minecraft color codes handling
import org.bukkit.command.Command; // Command handling
import org.bukkit.command.CommandSender; // Command sender handling
import org.bukkit.configuration.file.YamlConfiguration; // YAML configuration handling
import org.bukkit.event.EventHandler; // Event handler annotation
import org.bukkit.event.Listener; // Event listener interface
import org.bukkit.event.server.ServerListPingEvent; // Server ping/MOTD event
import org.bukkit.plugin.java.JavaPlugin; // Base plugin class
import org.yaml.snakeyaml.DumperOptions; // YAML formatting options
import org.yaml.snakeyaml.Yaml; // YAML parser/generator

// File handling imports
import java.awt.*;
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

// Main plugin class extending JavaPlugin and implementing event listener
public final class SolverMOTD extends JavaPlugin implements Listener {

    // Configuration variables
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private boolean papiEnabled; // Flag for PlaceholderAPI availability
    private YamlConfiguration messages; // Messages configuration
    private File messagesFile; // Messages file reference
    private CommentPreservingYaml commentYaml; // Custom YAML handler for preserving comments

    @Override
    public void onEnable() {

        // bStats ID
        int pluginId = 25243;
        Metrics metrics = new Metrics(this, pluginId);
        getLogger().info("SolverMOTD has connected with bStats");
        // Initialize custom YAML handler for comment preservation
        commentYaml = new CommentPreservingYaml();

        // Load main configuration
        reloadConfig();

        // Set up messages.yml file
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false); // Create if missing
        }

        // Update messages.yml while preserving comments
        updateYamlFile("messages.yml", map -> {
            // No additional actions needed for messages.yml
        });
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Update config.yml and reload configuration
        updateYamlFile("config.yml", map -> reloadConfig());

        // Register events and save default config
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        // Check for PlaceholderAPI availability
        papiEnabled = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papiEnabled) {
            getLogger().info("PlaceholderAPI is enabled! Solver has enabled the variables.");
        } else {
            getLogger().warning("PlaceholderAPI not found, Solver will not be able to use variables.");
        }
    }

    /**
     * Updates YAML files while preserving comments and structure
     * @param fileName Name of the YAML file to update
     * @param updater Consumer function for applying updates
     */
    private void updateYamlFile(String fileName, Consumer<Map<String, Object>> updater) {
        File file = new File(getDataFolder(), fileName);

        // Create file from resource if it doesn't exist
        if (!file.exists()) {
            saveResource(fileName, false);
            getLogger().info(getMessage("config_regenerated").replace("{file}", fileName).replace("§", "&"));
        }

        // Read existing header comments
        String header = readHeader(file);

        // Load current configuration
        Map<String, Object> currentMap;
        try {
            currentMap = commentYaml.load(file);
        } catch (IOException e) {
            getLogger().warning("Error while loading " + fileName + ": " + e.getMessage());
            currentMap = new LinkedHashMap<>();
        }

        // Load default configuration from plugin resources
        Map<String, Object> defaultMap;
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(getResource(fileName)))) {
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml defaultYaml = new Yaml(options);
            Object loaded = defaultYaml.load(reader);
            defaultMap = (loaded instanceof Map) ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        } catch (Exception e) {
            getLogger().warning("Error while loading default " + fileName + ": " + e.getMessage());
            defaultMap = new LinkedHashMap<>();
        }

        // Merge default values with existing configuration
        commentYaml.mergeMaps(defaultMap, currentMap);
        updater.accept(currentMap); // Apply custom updates

        // Save merged configuration with preserved header
        try {
            commentYaml.save(currentMap, file, header);
        } catch (IOException e) {
            getLogger().warning("Error while saving " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Reads header comments from YAML file
     * @param file Target YAML file
     * @return Concatenated header comments
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

    // Handle server list ping event to set custom MOTD
    @EventHandler
    public void onServerPing(ServerListPingEvent event) {

        // Get Config
        boolean useMiniMessage = getConfig().getBoolean("use_minimessage", false);
        boolean usePapi = getConfig().getBoolean("use_papi", true);

        // Get MOTD lines from config
        String line1 = getConfig().getString("motd.line1", "&a&lSolver&c&lMOTD &3Plugin &2[1.8 - 1.21] &4❤");
        String line2 = getConfig().getString("motd.line2", "&aSetup your &eConfig.yml &afile!");

        // Apply PlaceholderAPI if available and enabled
        if (papiEnabled && usePapi) {
            line1 = PlaceholderAPI.setPlaceholders(null, line1);
            line2 = PlaceholderAPI.setPlaceholders(null, line2);
        }

        // Format and set MOTD
        String finalMotd;
        if (useMiniMessage) {
            // If MiniMessage is active on config.yml
            Component motdComponent = miniMessage.deserialize(line1 + "\n" + line2);
            finalMotd = LegacyComponentSerializer.legacySection().serialize(motdComponent);
        } else {
            // If MiniMessage is desactivated it will use Legacy (&)
            finalMotd = ChatColor.translateAlternateColorCodes('&', line1) + "\n" +
                    ChatColor.translateAlternateColorCodes('&', line2);
        }
        event.setMotd(finalMotd);
    }



    // Retrieve formatted messages from messages.yml
    private String getMessage(String path) {
        if (messages == null) {
            messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        }
        String msg = messages.getString(path, "&cMessage not found: " + path);
        msg = msg.replace("{prefix}", messages.getString("prefix", ""));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    // Handle plugin commands
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
                    // Check permissions and handle reload
                    if (!sender.hasPermission("solvermotd.reload")) {
                        sender.sendMessage(getMessage("reload_no_permission").replace("{permission}", "solvermotd.reload"));
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
        // Plugin shutdown logic (currently empty)
    }
}