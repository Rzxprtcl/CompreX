package org.examplez.autocompressor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AutoCompressor extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private File configFile;
    private Map<UUID, CompressionRule> pendingRules = new HashMap<>();
    private Map<String, CompressionRule> compressionRules = new HashMap<>();

    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Create config if it doesn't exist
        loadConfig();

        // Load compression rules from config
        loadCompressionRules();

        // Register commands
        getCommand("autocompressor").setExecutor(new AutoCompressorCommand(this));

        // Start compression task
        startCompressionTask();

        getLogger().info("AutoCompressor has been enabled!");
    }

    @Override
    public void onDisable() {
        saveCompressionRules();
        getLogger().info("AutoCompressor has been disabled!");
    }

    private void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save config to " + configFile);
            e.printStackTrace();
        }
    }

    private void loadCompressionRules() {
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            return;
        }

        for (String key : rulesSection.getKeys(false)) {
            ConfigurationSection ruleSection = rulesSection.getConfigurationSection(key);
            if (ruleSection != null) {
                Material inputMaterial = Material.valueOf(ruleSection.getString("input.material"));
                int inputAmount = ruleSection.getInt("input.amount");
                Material outputMaterial = Material.valueOf(ruleSection.getString("output.material"));
                int outputAmount = ruleSection.getInt("output.amount", 1);

                CompressionRule rule = new CompressionRule(inputMaterial, inputAmount, outputMaterial, outputAmount);
                compressionRules.put(key, rule);
            }
        }
    }

    private void saveCompressionRules() {
        config.set("rules", null); // Clear existing rules

        for (Map.Entry<String, CompressionRule> entry : compressionRules.entrySet()) {
            String key = entry.getKey();
            CompressionRule rule = entry.getValue();

            config.set("rules." + key + ".input.material", rule.getInputMaterial().toString());
            config.set("rules." + key + ".input.amount", rule.getInputAmount());
            config.set("rules." + key + ".output.material", rule.getOutputMaterial().toString());
            config.set("rules." + key + ".output.amount", rule.getOutputAmount());
        }

        saveConfig();
    }

    private void startCompressionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processCompression(player);
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Run every second
    }

    private void processCompression(Player player) {
        for (CompressionRule rule : compressionRules.values()) {
            // Count matching items in player inventory
            int matchingItems = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == rule.getInputMaterial()) {
                    matchingItems += item.getAmount();
                }
            }

            // Check if enough items are available for compression
            if (matchingItems >= rule.getInputAmount()) {
                // Remove input items
                int remainingToRemove = rule.getInputAmount();
                ItemStack[] contents = player.getInventory().getContents();

                for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
                    ItemStack item = contents[i];
                    if (item != null && item.getType() == rule.getInputMaterial()) {
                        if (item.getAmount() <= remainingToRemove) {
                            remainingToRemove -= item.getAmount();
                            player.getInventory().setItem(i, null);
                        } else {
                            item.setAmount(item.getAmount() - remainingToRemove);
                            remainingToRemove = 0;
                        }
                    }
                }

                // Add output item
                ItemStack outputItem = new ItemStack(rule.getOutputMaterial(), rule.getOutputAmount());
                player.getInventory().addItem(outputItem);
                player.sendMessage(ChatColor.GREEN + "Auto-compressed " + rule.getInputAmount() + " " +
                        formatMaterialName(rule.getInputMaterial()) + " into " +
                        rule.getOutputAmount() + " " +
                        formatMaterialName(rule.getOutputMaterial()) + "!");
            }
        }
    }

    public void openMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "AutoCompressor Menu");

        // Create rule button
        ItemStack createButton = createMenuItem(Material.EMERALD,
                ChatColor.GREEN + "Create New Rule",
                ChatColor.GRAY + "Click to create a new compression rule");
        menu.setItem(11, createButton);

        // List rules button
        ItemStack listButton = createMenuItem(Material.BOOK,
                ChatColor.AQUA + "List Existing Rules",
                ChatColor.GRAY + "Click to view and manage your rules");
        menu.setItem(15, listButton);

        player.openInventory(menu);
    }

    public void openCreateRuleMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Create Compression Rule");

        // Set input item slot
        ItemStack inputSlot = createMenuItem(Material.HOPPER,
                ChatColor.YELLOW + "Set Input Item",
                ChatColor.GRAY + "Click with an item to set input");
        menu.setItem(11, inputSlot);

        // Set output item slot
        ItemStack outputSlot = createMenuItem(Material.CHEST,
                ChatColor.YELLOW + "Set Output Item",
                ChatColor.GRAY + "Click with an item to set output");
        menu.setItem(15, outputSlot);

        // Quantity adjustment
        ItemStack decreaseButton = createMenuItem(Material.RED_CONCRETE,
                ChatColor.RED + "Decrease Quantity",
                ChatColor.GRAY + "Click to decrease required amount");
        menu.setItem(3, decreaseButton);

        ItemStack increaseButton = createMenuItem(Material.GREEN_CONCRETE,
                ChatColor.GREEN + "Increase Quantity",
                ChatColor.GRAY + "Click to increase required amount");
        menu.setItem(5, increaseButton);

        // Current quantity display (default: 9)
        ItemStack quantityDisplay = createMenuItem(Material.PAPER,
                ChatColor.GOLD + "Required Quantity: 9",
                ChatColor.GRAY + "Number of input items needed");
        menu.setItem(4, quantityDisplay);

        // Save button
        ItemStack saveButton = createMenuItem(Material.DIAMOND,
                ChatColor.GREEN + "Save Rule",
                ChatColor.GRAY + "Click to save this compression rule");
        menu.setItem(22, saveButton);

        // Cancel button
        ItemStack cancelButton = createMenuItem(Material.BARRIER,
                ChatColor.RED + "Cancel",
                ChatColor.GRAY + "Click to cancel rule creation");
        menu.setItem(26, cancelButton);

        // Initialize pending rule
        pendingRules.put(player.getUniqueId(), new CompressionRule(Material.COBBLESTONE, 9, Material.STONE, 1));

        player.openInventory(menu);
    }

    public void openRuleListMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Compression Rules");

        int slot = 0;
        for (Map.Entry<String, CompressionRule> entry : compressionRules.entrySet()) {
            if (slot >= 45) break; // Limit to inventory size

            CompressionRule rule = entry.getValue();

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Input: " + ChatColor.WHITE + rule.getInputAmount() + " " + formatMaterialName(rule.getInputMaterial()));
            lore.add(ChatColor.GRAY + "Output: " + ChatColor.WHITE + rule.getOutputAmount() + " " + formatMaterialName(rule.getOutputMaterial()));
            lore.add("");
            lore.add(ChatColor.RED + "Click to remove this rule");

            ItemStack ruleItem = createMenuItem(rule.getOutputMaterial(),
                    ChatColor.GOLD + "Compression Rule #" + (slot + 1),
                    lore);

            menu.setItem(slot, ruleItem);
            slot++;
        }

        // Back button
        ItemStack backButton = createMenuItem(Material.ARROW,
                ChatColor.AQUA + "Back to Main Menu",
                ChatColor.GRAY + "Return to the main menu");
        menu.setItem(49, backButton);

        player.openInventory(menu);
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }

        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // Main menu handler
        if (title.equals(ChatColor.DARK_PURPLE + "AutoCompressor Menu")) {
            event.setCancelled(true);

            if (event.getRawSlot() == 11) {
                // Create rule button
                openCreateRuleMenu(player);
            } else if (event.getRawSlot() == 15) {
                // List rules button
                openRuleListMenu(player);
            }
        }
        // Create rule menu handler
        else if (title.equals(ChatColor.DARK_PURPLE + "Create Compression Rule")) {
            CompressionRule pendingRule = pendingRules.get(player.getUniqueId());
            if (pendingRule == null) {
                event.setCancelled(true);
                return;
            }

            // Handle create rule menu clicks
            if (event.getRawSlot() < 27) {
                event.setCancelled(true);

                if (event.getRawSlot() == 11) {
                    // Set input item
                    if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                        pendingRule.setInputMaterial(event.getCursor().getType());

                        // Update display
                        ItemStack inputDisplay = createMenuItem(event.getCursor().getType(),
                                ChatColor.YELLOW + "Input: " + formatMaterialName(event.getCursor().getType()),
                                ChatColor.GRAY + "Quantity: " + pendingRule.getInputAmount());
                        event.getInventory().setItem(11, inputDisplay);
                    }
                } else if (event.getRawSlot() == 15) {
                    // Set output item
                    if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                        pendingRule.setOutputMaterial(event.getCursor().getType());

                        // Update display
                        ItemStack outputDisplay = createMenuItem(event.getCursor().getType(),
                                ChatColor.YELLOW + "Output: " + formatMaterialName(event.getCursor().getType()),
                                ChatColor.GRAY + "Amount: " + pendingRule.getOutputAmount());
                        event.getInventory().setItem(15, outputDisplay);
                    }
                } else if (event.getRawSlot() == 3) {
                    // Decrease quantity
                    if (pendingRule.getInputAmount() > 1) {
                        pendingRule.setInputAmount(pendingRule.getInputAmount() - 1);

                        // Update quantity display
                        ItemStack quantityDisplay = createMenuItem(Material.PAPER,
                                ChatColor.GOLD + "Required Quantity: " + pendingRule.getInputAmount(),
                                ChatColor.GRAY + "Number of input items needed");
                        event.getInventory().setItem(4, quantityDisplay);

                        // Update input display if exists
                        ItemStack inputItem = event.getInventory().getItem(11);
                        if (inputItem != null && inputItem.getItemMeta().getDisplayName().startsWith(ChatColor.YELLOW + "Input:")) {
                            ItemStack inputDisplay = createMenuItem(inputItem.getType(),
                                    ChatColor.YELLOW + "Input: " + formatMaterialName(inputItem.getType()),
                                    ChatColor.GRAY + "Quantity: " + pendingRule.getInputAmount());
                            event.getInventory().setItem(11, inputDisplay);
                        }
                    }
                } else if (event.getRawSlot() == 5) {
                    // Increase quantity
                    pendingRule.setInputAmount(pendingRule.getInputAmount() + 1);

                    // Update quantity display
                    ItemStack quantityDisplay = createMenuItem(Material.PAPER,
                            ChatColor.GOLD + "Required Quantity: " + pendingRule.getInputAmount(),
                            ChatColor.GRAY + "Number of input items needed");
                    event.getInventory().setItem(4, quantityDisplay);

                    // Update input display if exists
                    ItemStack inputItem = event.getInventory().getItem(11);
                    if (inputItem != null && inputItem.getItemMeta().getDisplayName().startsWith(ChatColor.YELLOW + "Input:")) {
                        ItemStack inputDisplay = createMenuItem(inputItem.getType(),
                                ChatColor.YELLOW + "Input: " + formatMaterialName(inputItem.getType()),
                                ChatColor.GRAY + "Quantity: " + pendingRule.getInputAmount());
                        event.getInventory().setItem(11, inputDisplay);
                    }
                } else if (event.getRawSlot() == 22) {
                    // Save rule
                    if (pendingRule.getInputMaterial() != null && pendingRule.getOutputMaterial() != null) {
                        String ruleId = pendingRule.getInputMaterial().toString() + "_TO_" + pendingRule.getOutputMaterial().toString();
                        compressionRules.put(ruleId, pendingRule);
                        saveCompressionRules();

                        player.sendMessage(ChatColor.GREEN + "Compression rule saved successfully!");
                        player.closeInventory();
                        openMainMenu(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "Please set both input and output items!");
                    }
                } else if (event.getRawSlot() == 26) {
                    // Cancel
                    pendingRules.remove(player.getUniqueId());
                    openMainMenu(player);
                }
            }
        }
        // Rule list menu handler
        else if (title.equals(ChatColor.DARK_PURPLE + "Compression Rules")) {
            event.setCancelled(true);

            if (event.getRawSlot() < 45 && event.getCurrentItem() != null) {
                // If clicking on a rule item (not the back button)
                if (event.getCurrentItem().getItemMeta().getDisplayName().startsWith(ChatColor.GOLD + "Compression Rule #")) {
                    // Find the rule and remove it
                    int index = event.getRawSlot();
                    int currentIndex = 0;

                    String ruleToRemove = null;
                    for (String ruleId : compressionRules.keySet()) {
                        if (currentIndex == index) {
                            ruleToRemove = ruleId;
                            break;
                        }
                        currentIndex++;
                    }

                    if (ruleToRemove != null) {
                        compressionRules.remove(ruleToRemove);
                        saveCompressionRules();
                        player.sendMessage(ChatColor.GREEN + "Compression rule removed successfully!");
                        openRuleListMenu(player); // Refresh the menu
                    }
                }
            } else if (event.getRawSlot() == 49) {
                // Back button
                openMainMenu(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.DARK_PURPLE + "Create Compression Rule")) {
            pendingRules.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Optional: Send a message about the plugin on join
        event.getPlayer().sendMessage(ChatColor.GREEN + "This server is running AutoCompressor! " +
                "Use /autocompressor to manage your compression rules.");
    }

    private String formatMaterialName(Material material) {
        String name = material.toString();
        name = name.replace('_', ' ').toLowerCase();

        StringBuilder formattedName = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (capitalizeNext && Character.isAlphabetic(c)) {
                formattedName.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formattedName.append(c);
                if (c == ' ') {
                    capitalizeNext = true;
                }
            }
        }

        return formattedName.toString();
    }

    // Inner class to represent a compression rule
    public static class CompressionRule {
        private Material inputMaterial;
        private int inputAmount;
        private Material outputMaterial;
        private int outputAmount;

        public CompressionRule(Material inputMaterial, int inputAmount, Material outputMaterial, int outputAmount) {
            this.inputMaterial = inputMaterial;
            this.inputAmount = inputAmount;
            this.outputMaterial = outputMaterial;
            this.outputAmount = outputAmount;
        }

        public Material getInputMaterial() {
            return inputMaterial;
        }

        public void setInputMaterial(Material inputMaterial) {
            this.inputMaterial = inputMaterial;
        }

        public int getInputAmount() {
            return inputAmount;
        }

        public void setInputAmount(int inputAmount) {
            this.inputAmount = inputAmount;
        }

        public Material getOutputMaterial() {
            return outputMaterial;
        }

        public void setOutputMaterial(Material outputMaterial) {
            this.outputMaterial = outputMaterial;
        }

        public int getOutputAmount() {
            return outputAmount;
        }

        public void setOutputAmount(int outputAmount) {
            this.outputAmount = outputAmount;
        }
    }
}