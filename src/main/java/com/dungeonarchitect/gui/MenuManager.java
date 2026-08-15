package com.dungeonarchitect.gui;

import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.AuthoringSession;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorConnectionRules;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.domain.TagDomain;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.template.DiagnosticText;
import com.dungeonarchitect.template.TemplateDiagnostic;
import com.dungeonarchitect.template.TemplateDiagnostics;
import com.dungeonarchitect.template.TemplateLoadStatus;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomTemplateValidator;
import com.dungeonarchitect.template.AssetRenameCoordinator;
import com.dungeonarchitect.template.IdentityRules;
import com.dungeonarchitect.loot.LootTableRegistry;
import com.dungeonarchitect.loot.LootTable;
import com.dungeonarchitect.loot.LootEntry;
import com.dungeonarchitect.loot.LootTableStatus;
import com.dungeonarchitect.loot.LootBinding;
import com.dungeonarchitect.loot.LootPoolEntry;
import com.dungeonarchitect.loot.LootTableEntry;
import com.dungeonarchitect.loot.LootOutcomePreview;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class MenuManager implements Listener {
    private final Plugin plugin;
    private final AuthoringManager authoringManager;
    private final RoomTemplateRegistry templateRegistry;
    private final FeatureTemplateRegistry featureRegistry;
    private final DoorTemplateRegistry doorRegistry;
    private final LootTableRegistry lootRegistry;
    private final DungeonManager dungeonManager;
    private final ChatPromptManager prompts;
    private final Runnable reloadAll;
    private final AssetRenameCoordinator assetRenameCoordinator;
    private final TagCatalog tagCatalog;
    private final Map<UUID, PlayerMenuActions> actions = new HashMap<>();
    private final Map<UUID, MultiEditSession> multiEdits = new HashMap<>();
    private final Map<UUID, TagSelection> tagSelections = new HashMap<>();
    private final Map<UUID, LootEditSession> lootEdits = new HashMap<>();
    private final Map<UUID, LootMultiEditSession> lootMultiEdits = new HashMap<>();
    private final LootEditorLeaseRegistry lootLeases = new LootEditorLeaseRegistry();
    private final Set<UUID> suppressLootClose = new LinkedHashSet<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, Runnable reloadAll) {
        this(plugin, authoringManager, templateRegistry, featureRegistry, doorRegistry, new LootTableRegistry(plugin.getDataFolder().toPath().resolve("loot-tables")), dungeonManager, prompts,
            new AssetRenameCoordinator(templateRegistry, featureRegistry, doorRegistry), new TagCatalog(plugin.getDataFolder().toPath().resolve("tags.yml")), reloadAll);
    }

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, AssetRenameCoordinator assetRenameCoordinator, Runnable reloadAll) {
        this(plugin, authoringManager, templateRegistry, featureRegistry, doorRegistry, new LootTableRegistry(plugin.getDataFolder().toPath().resolve("loot-tables")), dungeonManager, prompts, assetRenameCoordinator, new TagCatalog(plugin.getDataFolder().toPath().resolve("tags.yml")), reloadAll);
    }

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, AssetRenameCoordinator assetRenameCoordinator, TagCatalog tagCatalog, Runnable reloadAll) {
        this(plugin, authoringManager, templateRegistry, featureRegistry, doorRegistry, new LootTableRegistry(plugin.getDataFolder().toPath().resolve("loot-tables")), dungeonManager, prompts, assetRenameCoordinator, tagCatalog, reloadAll);
    }

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, LootTableRegistry lootRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, AssetRenameCoordinator assetRenameCoordinator, TagCatalog tagCatalog, Runnable reloadAll) {
        this.plugin = plugin;
        this.authoringManager = authoringManager;
        this.templateRegistry = templateRegistry;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
        this.lootRegistry = lootRegistry;
        this.dungeonManager = dungeonManager;
        this.prompts = prompts;
        this.assetRenameCoordinator = assetRenameCoordinator;
        this.tagCatalog = tagCatalog;
        this.reloadAll = reloadAll;
    }

    public void openMain(Player player) {
        Menu menu = menu("da:main", 27, "DungeonArchitect");
        button(menu, 10, Material.BOOKSHELF, "Rooms", List.of("Edit room metadata and validate templates."), this::openRooms);
        button(menu, 12, Material.OAK_DOOR, "Doors", List.of("Edit reusable door templates."), this::openDoors);
        button(menu, 14, Material.STRUCTURE_BLOCK, "Features", List.of("Edit reusable feature templates."), this::openFeatures);
        button(menu, 15, Material.CHEST, "Loot Tables", List.of("Create reusable container loot tables."), this::openLootTables);
        button(menu, 16, Material.ENDER_PEARL, "Dungeons", List.of("Manage active dungeon instances."), this::openDungeons);
        button(menu, 18, Material.SPYGLASS, "Diagnostics", diagnosticSummaryLore(), this::openDiagnostics);
        button(menu, 22, Material.COMPARATOR, "Config", List.of("Edit config.yml."), this::openConfig);
        open(player, menu);
    }

    public void openLootTables(Player player) {
        openLootTables(player, 0);
    }

    private void openLootTables(Player player, int page) {
        List<LootTableStatus> statuses = lootRegistry.statuses().stream().sorted(java.util.Comparator.comparing(LootTableStatus::id)).toList();
        int pages = LootEditorInteractionRules.pageCount(statuses.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = menu("da:loot-tables:" + current, 54, LootEditorInteractionRules.pageTitle("Loot Tables", current, pages));
        int slot = 0;
        int start = current * 45;
        for (int index = start; index < statuses.size() && slot < 45; index++) {
            LootTableStatus status = statuses.get(index);
            LootTable table = status.table();
            LootEditorLeaseRegistry.Lease lease = lootLeases.lease(status.id()).orElse(null);
            List<String> lore = new ArrayList<>();
            if (table != null) {
                long nested = table.entries().stream().filter(LootTableEntry.class::isInstance).count();
                lore.addAll(List.of("Entries: " + table.entries().size(), "Nested tables: " + nested));
                if (table.entries().isEmpty()) lore.add("Incomplete: add an item before assigning this pool.");
            }
            lore.addAll(status.errors());
            if (lease != null) lore.add("Currently edited by " + lease.ownerName() + ".");
            lore.add(status.valid() ? "Click to edit. Right click to delete." : "Invalid entry. Fix the listed errors, then reload.");
            button(menu, slot++, status.valid() ? Material.CHEST : Material.RED_CONCRETE, status.id(), lore, p -> {
                if (table != null) openLootEditor(p, table); else p.sendMessage(Component.text("Loot table " + status.id() + " cannot be loaded: " + String.join("; ", status.errors())));
            }, p -> {
                if (!denyLockedLootTable(p, status.id())) openDeleteLootConfirm(p, status.id());
            });
        }
        if (LootEditorInteractionRules.hasPreviousPage(current)) button(menu, 45, Material.ARROW, "Previous Page", List.of(), p -> openLootTables(p, current - 1));
        button(menu, 48, Material.LIME_CONCRETE, "Create Loot Table", List.of("Enter a new id in chat."), p -> prompts.prompt(p, "Loot table id", id -> {
            LootTable table = new LootTable(id, List.of());
            try { lootRegistry.save(table); openLootEditor(p, table); } catch (IOException ex) { p.sendMessage(Component.text("Create failed: " + ex.getMessage())); openLootTables(p); }
        }));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        if (LootEditorInteractionRules.hasNextPage(current, pages)) button(menu, 53, Material.ARROW, "Next Page", List.of(), p -> openLootTables(p, current + 1));
        open(player, menu);
    }

    private void openLootEditor(Player player, LootTable table) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            LootTable current = lootRegistry.get(table.id()).orElse(null);
            if (current == null) {
                player.sendMessage(Component.text("Loot table " + table.id() + " no longer exists. Reopen the list to refresh it."));
                openLootTables(player);
                return;
            }
            LootEditorLeaseRegistry.Lease lease = lootLeases.lease(current.id()).orElse(null);
            if (lease != null && !lease.ownerId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text(lease.ownerName() + " is editing loot table " + current.id() + ". Try again when they are done."));
                return;
            }
            LootTable editorTable = current;
            if (LootEditorTemplates.requiresNormalization(current)) {
                editorTable = LootEditorTemplates.normalize(current);
                if (!saveLootCandidate(player, editorTable)) return;
            }
            LootEditSession previous = lootEdits.get(player.getUniqueId());
            if (previous != null && !previous.closed) closeLootEditorSession(player, previous);
            lootLeases.acquire(current.id(), player.getUniqueId(), player.getName());
            LootEditSession session = new LootEditSession(editorTable, player.getUniqueId(), player.getName());
            openLootEditorNow(player, session);
        });
    }

    private void openLootEditorNow(Player player, LootEditSession session) {
        if (!player.isOnline() || session.closed) return;
        lootEdits.put(player.getUniqueId(), session);
        refreshLootEditor(player, session);
    }

    private void refreshLootEditor(Player player, LootEditSession session) {
        String title = LootEditorInteractionRules.pageTitle("Loot: " + session.id, session.page, session.pageCount());
        if (session.inventory == null || !title.equals(session.inventoryTitle)) {
            Inventory previous = session.inventory;
            session.inventory = Bukkit.createInventory(new MenuHolder("da:loot:" + session.sessionId), 54, Component.text(title));
            session.inventoryTitle = title;
            if (previous != null && player.getOpenInventory().getTopInventory() == previous) {
                suppressLootClose.add(player.getUniqueId());
            }
        }
        renderLootEditor(player, session);
        if (player.getOpenInventory().getTopInventory() != session.inventory) {
            player.openInventory(session.inventory);
        }
        player.updateInventory();
    }

    private void returnToLootEditor(Player player, LootEditSession session) {
        scheduleLootTransition(player, () -> openLootEditorNow(player, session));
    }

    private void renderLootEditor(Player player, LootEditSession session) {
        session.sort();
        session.inventory.clear();
        Menu menu = new Menu(session.inventory, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        renderLootPage(session.inventory, session);
        if (LootEditorInteractionRules.hasPreviousPage(session.page)) {
            button(menu, 45, Material.ARROW, "Previous Page", List.of("Show page " + session.page + "."), p -> {
                queueLootTransaction(p, session, () -> session.page--);
            });
        }
        button(menu, 46, Material.WRITABLE_BOOK, "Bulk Edit", List.of("Select multiple entries to configure together."), p -> {
            queueLootTransaction(p, session, () -> {
                suppressLootClose.add(p.getUniqueId());
                openLootMultiSelectNow(p, new LootMultiEditSession(session));
            });
        });
        button(menu, 47, Material.ENDER_CHEST, "Add Table", List.of("Reuse one item from another loot pool."), p -> {
            queueLootTransaction(p, session, () -> {
                suppressLootClose.add(p.getUniqueId());
                openNestedLootPicker(p, session, 0);
            });
        });
        if (dungeonItemsAvailable()) {
            button(menu, 48, Material.NETHER_STAR, "Add DungeonItem", List.of("Choose an RPG item template."), p -> {
                suppressLootClose.add(p.getUniqueId());
                scheduleLootTransition(p, () -> openDungeonItemsPicker(p, session, 0));
            });
        }
        menu.inventory.setItem(49, GuiItems.item(Material.KNOWLEDGE_BOOK, "Editor Help", List.of(
            "Left-click an inventory item to add its template.",
            "Your inventory is never changed.",
            "Stored template amount is always one.",
            "Configure min/max for generated stack sizes.",
            "Left-click an entry to remove it.",
            "Right-click an entry to configure it.",
            "Use Bulk Edit to configure several entries.",
            "Add Table reuses another weighted pool.",
            "Container rolls are configured on markers.",
            "Every completed change saves immediately."
        )));
        button(menu, 50, session.showWeights ? Material.GLOWSTONE_DUST : Material.GUNPOWDER, "Chance Details: " + (session.showWeights ? "On" : "Off"), List.of("Show or hide entry weights and calculated chances.", "This changes only the editor display."), p -> {
            queueLootTransaction(p, session, () -> session.showWeights = !session.showWeights);
        });
        button(menu, 52, Material.EMERALD_BLOCK, "Done", List.of("All completed changes are already saved."), p -> finishLootEditor(p, session));
        if (LootEditorInteractionRules.hasNextPage(session.page, session.pageCount())) {
            button(menu, 53, Material.ARROW, "Next Page", List.of("Show page " + (session.page + 2) + "."), p -> {
                queueLootTransaction(p, session, () -> session.page++);
            });
        }
        actions.put(player.getUniqueId(), new PlayerMenuActions(menu.actions, menu.rightClickActions, menu.shiftLeftActions, menu.shiftRightActions));
    }

    private void openLootEntryConfigNow(Player player, LootEditSession session, String draftId) {
        DraftLootEntry draft = session.entry(draftId);
        if (draft == null) { openLootEditorNow(player, session); return; }
        if (draft.value() instanceof LootTableEntry nested) {
            openNestedLootConfigNow(player, session, draftId, nested);
            return;
        }
        LootEntry entry = (LootEntry) draft.value();
        Menu menu = menu("da:loot-entry:" + session.sessionId + ":" + draftId, 27, "Loot Entry");
        menu.inventory.setItem(4, cleanLootEditorItem(entry.item()));
        button(menu, 10, Material.GOLD_NUGGET, "Weight: " + entry.weight(), List.of("Relative chance for this entry.", "Click to set a positive value."), p -> promptLootEntryField(p, session, draftId, LootEntryField.WEIGHT));
        button(menu, 12, Material.HOPPER, "Min Count: " + entry.minimumAmount(), List.of("Minimum item count granted by a roll."), p -> promptLootEntryField(p, session, draftId, LootEntryField.MINIMUM_AMOUNT));
        button(menu, 14, Material.HOPPER, "Max Count: " + entry.maximumAmount(), List.of("Maximum item count granted by a roll."), p -> promptLootEntryField(p, session, draftId, LootEntryField.MAXIMUM_AMOUNT));
        button(menu, 16, Material.CHEST, "Max Per Container: " + (entry.maximumPerContainer() == 0 ? "Unlimited" : entry.maximumPerContainer()), List.of("Maximum times this entry can win in one container.", "0 means unlimited."), p -> promptLootEntryField(p, session, draftId, LootEntryField.MAXIMUM_PER_CONTAINER));
        button(menu, 22, Material.ARROW, "Back", List.of("Return to the loot editor."), p -> returnToLootEditor(p, session));
        open(player, menu);
    }

    private void openNestedLootConfigNow(Player player, LootEditSession session, String draftId, LootTableEntry entry) {
        Menu menu = menu("da:loot-entry:" + session.sessionId + ":" + draftId, 27, "Nested Loot Table");
        menu.inventory.setItem(4, GuiItems.item(Material.ENDER_CHEST, entry.tableId(), List.of("Resolves one final item from this table.")));
        button(menu, 11, Material.GOLD_NUGGET, "Weight: " + entry.weight(), List.of("Relative chance for this table reference."), p -> promptNestedLootField(p, session, draftId, true));
        button(menu, 13, Material.SPYGLASS, "Outcome Preview", List.of("Show final-item chances for the first draw."), p -> scheduleLootTransition(p, () -> openLootOutcomePreview(p, session, draftId, 0)));
        button(menu, 15, Material.CHEST, "Max Per Container: " + (entry.maximumPerContainer() == 0 ? "Unlimited" : entry.maximumPerContainer()), List.of("Maximum times this reference can be selected.", "0 means unlimited."), p -> promptNestedLootField(p, session, draftId, false));
        button(menu, 22, Material.ARROW, "Back", List.of("Return to the loot editor."), p -> returnToLootEditor(p, session));
        open(player, menu);
    }

    private void openLootOutcomePreview(Player player, LootEditSession session, String draftId, int page) {
        DraftLootEntry draft = session.entry(draftId);
        if (draft == null || !(draft.value() instanceof LootTableEntry nested)) { returnToLootEditor(player, session); return; }
        LootTable child = lootRegistry.get(nested.tableId()).orElse(null);
        if (child == null) { player.sendMessage(Component.text("Referenced table no longer exists.")); openLootEntryConfigNow(player, session, draftId); return; }
        List<LootOutcomePreview.Outcome> outcomes = LootOutcomePreview.flatten(child, lootRegistry);
        int pages = LootEditorInteractionRules.pageCount(outcomes.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = menu("da:loot-outcomes:" + session.sessionId + ":" + draftId + ":" + current, 54, LootEditorInteractionRules.pageTitle("Outcomes: " + nested.tableId(), current, pages));
        int start = current * 45;
        for (int slot = 0; slot < 45 && start + slot < outcomes.size(); slot++) {
            LootOutcomePreview.Outcome outcome = outcomes.get(start + slot);
            ItemStack item = outcome.item(); item.setAmount(1);
            ItemMeta meta = item.getItemMeta(); List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text("First-draw chance: " + outcome.percent(), NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text("Amount: " + outcome.minimumAmount() + "-" + outcome.maximumAmount(), NamedTextColor.GRAY));
            outcome.paths().stream().limit(3).forEach(path -> lore.add(Component.text(path, NamedTextColor.DARK_GRAY)));
            lore.add(Component.text("Later draws may change as caps are reached.", NamedTextColor.GRAY));
            meta.lore(lore); item.setItemMeta(meta); menu.inventory.setItem(slot, item);
        }
        if (outcomes.isEmpty()) menu.inventory.setItem(22, GuiItems.item(Material.RED_CONCRETE, "No Reachable Outcomes", List.of("Repair the referenced table before using it.")));
        if (LootEditorInteractionRules.hasPreviousPage(current)) button(menu, 45, Material.ARROW, "Previous Page", List.of(), p -> openLootOutcomePreview(p, session, draftId, current - 1));
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openLootEntryConfigNow(p, session, draftId));
        if (LootEditorInteractionRules.hasNextPage(current, pages)) button(menu, 53, Material.ARROW, "Next Page", List.of(), p -> openLootOutcomePreview(p, session, draftId, current + 1));
        open(player, menu);
    }

    private void promptNestedLootField(Player player, LootEditSession session, String draftId, boolean weight) {
        scheduleLootTransition(player, () -> prompts.prompt(player, weight ? "Enter a positive weight" : "Enter max selections per container (0 for unlimited)", value -> {
            try {
                DraftLootEntry draft = session.entry(draftId);
                if (draft == null || !(draft.value() instanceof LootTableEntry entry)) { openLootEditorNow(player, session); return; }
                int number = Integer.parseInt(value.trim());
                LootTableEntry updated = weight ? new LootTableEntry(entry.tableId(), number, entry.maximumPerContainer()) : new LootTableEntry(entry.tableId(), entry.weight(), number);
                if (saveLootCandidate(player, session.tableReplacing(draftId, updated))) session.replace(draftId, updated);
            } catch (RuntimeException ex) { player.sendMessage(Component.text("Edit failed: " + ex.getMessage())); }
            openLootEntryConfigNow(player, session, draftId);
        }, () -> openLootEntryConfigNow(player, session, draftId)));
    }

    private void openNestedLootPicker(Player player, LootEditSession session, int page) {
        List<LootTable> candidates = lootRegistry.all().stream().filter(table -> !table.id().equals(session.id)).sorted(java.util.Comparator.comparing(LootTable::id)).toList();
        int pages = LootEditorInteractionRules.pageCount(candidates.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = menu("da:loot-nested-picker:" + session.sessionId + ":" + current, 54, LootEditorInteractionRules.pageTitle("Add Loot Table", current, pages));
        int start = current * 45;
        int slot = 0;
        for (int index = start; index < candidates.size() && slot < 45; index++, slot++) {
            LootTable candidateTable = candidates.get(index);
            DraftLootEntry duplicateDraft = session.entries().stream().filter(draft -> draft.value() instanceof LootTableEntry nested && nested.tableId().equals(candidateTable.id())).findFirst().orElse(null);
            boolean duplicate = duplicateDraft != null;
            List<LootPoolEntry> prospectiveEntries = new ArrayList<>(session.values());
            prospectiveEntries.add(new LootTableEntry(candidateTable.id(), 1, 0));
            List<String> errors = duplicate ? List.of("Already referenced by this table.") : lootRegistry.validationErrors(new LootTable(session.id, prospectiveEntries));
            boolean available = !duplicate && errors.isEmpty() && lootRegistry.usable(candidateTable.id());
            List<String> lore = duplicate ? List.of("Already added. Click to configure it.") : available ? List.of("Add with weight 1 and unlimited selections.") : errors.isEmpty() ? List.of("This table has no reachable item yet.") : errors;
            int targetSlot = slot;
            button(menu, targetSlot, available || duplicate ? Material.ENDER_CHEST : Material.RED_CONCRETE, candidateTable.id(), lore, p -> {
                if (duplicateDraft != null) { openLootEntryConfigNow(p, session, duplicateDraft.id()); return; }
                if (!available) { p.sendMessage(Component.text("Cannot add " + candidateTable.id() + ": " + String.join("; ", lore))); return; }
                LootTableEntry addedValue = new LootTableEntry(candidateTable.id(), 1, 0);
                LootTable candidate = new LootTable(session.id, java.util.stream.Stream.concat(session.values().stream(), java.util.stream.Stream.of(addedValue)).toList());
                if (saveLootCandidate(p, candidate)) {
                    DraftLootEntry added = session.add(addedValue);
                    session.sort();
                    session.page = session.pageOf(added.id());
                    openLootEntryConfigNow(p, session, added.id());
                } else openNestedLootPicker(p, session, current);
            });
        }
        if (LootEditorInteractionRules.hasPreviousPage(current)) button(menu, 45, Material.ARROW, "Previous Page", List.of(), p -> scheduleLootTransition(p, () -> openNestedLootPicker(p, session, current - 1)));
        button(menu, 49, Material.ARROW, "Back", List.of("Return without adding a table."), p -> returnToLootEditor(p, session));
        if (LootEditorInteractionRules.hasNextPage(current, pages)) button(menu, 53, Material.ARROW, "Next Page", List.of(), p -> scheduleLootTransition(p, () -> openNestedLootPicker(p, session, current + 1)));
        open(player, menu);
    }

    private void openDungeonItemsPicker(Player player, LootEditSession session, int page) {
        if (!dungeonItemsAvailable()) { player.sendMessage(Component.text("DungeonItems is no longer available.")); returnToLootEditor(player, session); return; }
        List<DungeonItemsBridge.Entry> candidates = DungeonItemsBridge.entries();
        int pages = LootEditorInteractionRules.pageCount(candidates.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = menu("da:loot-di-picker:" + session.sessionId + ":" + current, 54, LootEditorInteractionRules.pageTitle("Add DungeonItem", current, pages));
        int start = current * 45;
        for (int slot = 0; slot < 45 && start + slot < candidates.size(); slot++) {
            DungeonItemsBridge.Entry info = candidates.get(start + slot);
            ItemStack preview = info.item().clone();
            if (preview == null) continue;
            int target = slot;
            menu.inventory.setItem(target, preview);
            menu.actions.put(target, p -> {
                ItemStack created = DungeonItemsBridge.create(info.id());
                ItemStack template = created == null ? null : LootEditorTemplates.normalize(created);
                if (template == null) { p.sendMessage(Component.text("DungeonItem " + info.id() + " is unavailable.")); openDungeonItemsPicker(p, session, current); return; }
                LootEntry value = new LootEntry(template, 1, 1, 1, 1);
                LootTable candidate = new LootTable(session.id, java.util.stream.Stream.concat(session.values().stream(), java.util.stream.Stream.of(value)).toList());
                if (saveLootCandidate(p, candidate)) {
                    DraftLootEntry added = session.add(value); session.sort(); session.page = session.pageOf(added.id()); openLootEntryConfigNow(p, session, added.id());
                } else openDungeonItemsPicker(p, session, current);
            });
        }
        if (candidates.isEmpty()) menu.inventory.setItem(22, GuiItems.item(Material.KNOWLEDGE_BOOK, "No DungeonItems", List.of("Create a template with /di create <id>.")));
        if (LootEditorInteractionRules.hasPreviousPage(current)) button(menu, 45, Material.ARROW, "Previous Page", List.of(), p -> scheduleLootTransition(p, () -> openDungeonItemsPicker(p, session, current - 1)));
        button(menu, 49, Material.ARROW, "Back", List.of("Return without adding an item."), p -> returnToLootEditor(p, session));
        if (LootEditorInteractionRules.hasNextPage(current, pages)) button(menu, 53, Material.ARROW, "Next Page", List.of(), p -> scheduleLootTransition(p, () -> openDungeonItemsPicker(p, session, current + 1)));
        open(player, menu);
    }

    private boolean dungeonItemsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("DungeonItems");
    }

    private void promptLootEntryField(Player player, LootEditSession session, String draftId, LootEntryField field) {
        scheduleLootTransition(player, () -> prompts.prompt(player, field.prompt(), value -> {
            try {
                DraftLootEntry draft = session.entry(draftId);
                if (draft == null) { openLootEditorNow(player, session); return; }
                if (!(draft.value() instanceof LootEntry itemEntry)) throw new IllegalArgumentException("This field applies only to item entries");
                LootEntry updated = updateLootEntry(itemEntry, field, Integer.parseInt(value.trim()));
                LootTable candidate = session.tableReplacing(draftId, updated);
                if (saveLootCandidate(player, candidate)) {
                    session.replace(draftId, updated);
                    session.sort();
                }
            } catch (RuntimeException ex) {
                player.sendMessage(Component.text("Edit failed: " + ex.getMessage()));
            }
            openLootEntryConfigNow(player, session, draftId);
        }, () -> openLootEntryConfigNow(player, session, draftId)));
    }

    private void scheduleLootTransition(Player player, Runnable transition) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isLootSessionMenu(player.getOpenInventory().getTopInventory())) suppressLootClose.add(player.getUniqueId());
            transition.run();
        });
    }

    private boolean isLootSessionMenu(Inventory inventory) {
        if (!(inventory.getHolder() instanceof MenuHolder holder)) return false;
        return holder.id().startsWith("da:loot:") || holder.id().startsWith("da:loot-entry:") || holder.id().startsWith("da:loot-nested-picker:") || holder.id().startsWith("da:loot-outcomes:") || holder.id().startsWith("da:loot-multi-");
    }

    private LootEntry updateLootEntry(LootEntry entry, LootEntryField field, int value) {
        return switch (field) {
            case WEIGHT -> new LootEntry(entry.item(), value, entry.minimumAmount(), entry.maximumAmount(), entry.maximumPerContainer());
            case MINIMUM_AMOUNT -> new LootEntry(entry.item(), entry.weight(), value, entry.maximumAmount(), entry.maximumPerContainer());
            case MAXIMUM_AMOUNT -> new LootEntry(entry.item(), entry.weight(), entry.minimumAmount(), value, entry.maximumPerContainer());
            case MAXIMUM_PER_CONTAINER -> new LootEntry(entry.item(), entry.weight(), entry.minimumAmount(), entry.maximumAmount(), value);
        };
    }

    private void openLootMultiSelectNow(Player player, LootMultiEditSession multi) {
        lootMultiEdits.put(player.getUniqueId(), multi);
        List<DraftLootEntry> entries = multi.editor.entries();
        int pageCount = LootEditorInteractionRules.pageCount(entries.size());
        multi.page = Math.min(multi.page, pageCount - 1);
        Menu menu = menu("da:loot-multi-select:" + multi.editor.id + ":" + multi.page, 54,
            LootEditorInteractionRules.pageTitle("Select Loot", multi.page, pageCount));
        int start = multi.page * 45;
        for (int slot = 0; slot < 45 && start + slot < entries.size(); slot++) {
            int index = start + slot;
            DraftLootEntry entry = entries.get(index);
            menu.inventory.setItem(slot, lootMultiSelectionItem(entry.value(), multi.selectedIds.contains(entry.id())));
            menu.actions.put(slot, p -> {
                boolean changed = false;
                if (multi.selectedIds.contains(entry.id())) { multi.selectedIds.remove(entry.id()); changed = true; }
                else if (!multi.accepts(entry.value())) p.sendMessage(Component.text("Bulk Edit can select item entries or table references, not both at once."));
                else { multi.selectedIds.add(entry.id()); changed = true; }
                if (changed) multi.initialized = false;
                scheduleLootTransition(p, () -> openLootMultiSelectNow(p, multi));
            });
        }
        if (LootEditorInteractionRules.hasPreviousPage(multi.page)) {
            button(menu, 45, Material.ARROW, "Previous Page", List.of("Show page " + multi.page + "."), p -> { multi.page--; scheduleLootTransition(p, () -> openLootMultiSelectNow(p, multi)); });
        }
        button(menu, 46, Material.ARROW, "Back", List.of("Discard this selection and return to the editor."), p -> cancelLootMultiEdit(p, multi));
        menu.inventory.setItem(49, GuiItems.item(Material.PAPER, "Selected: " + multi.selectedIds.size(), List.of("Click entries to select or deselect them.")));
        if (multi.selectedIds.isEmpty()) {
            button(menu, 52, Material.RED_CONCRETE, "Continue", List.of("Select at least one entry first."), p -> p.sendMessage(Component.text("Select at least one loot entry.")));
        } else {
            button(menu, 52, Material.LIME_CONCRETE, "Continue", List.of("Configure selected entries."), p -> {
                multi.initializeDraft();
                scheduleLootTransition(p, () -> openLootMultiConfigNow(p, multi));
            });
        }
        if (LootEditorInteractionRules.hasNextPage(multi.page, pageCount)) {
            button(menu, 53, Material.ARROW, "Next Page", List.of("Show page " + (multi.page + 2) + "."), p -> { multi.page++; scheduleLootTransition(p, () -> openLootMultiSelectNow(p, multi)); });
        }
        open(player, menu);
    }

    private ItemStack lootMultiSelectionItem(LootPoolEntry entry, boolean selected) {
        ItemStack item = entry instanceof LootEntry lootItem ? cleanLootEditorItem(lootItem.item()) : GuiItems.item(Material.ENDER_CHEST, ((LootTableEntry) entry).tableId(), List.of("Nested loot table"));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.add(Component.text(selected ? "Selected" : "Click to select", selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void openLootMultiConfigNow(Player player, LootMultiEditSession multi) {
        Menu menu = menu("da:loot-multi-config:" + multi.editor.id, 27, "Configure Loot Entries");
        LootPoolEntry first = multi.firstSelectedEntry();
        if (first instanceof LootEntry item) menu.inventory.setItem(4, cleanLootEditorItem(item.item()));
        else if (first instanceof LootTableEntry nested) menu.inventory.setItem(4, GuiItems.item(Material.ENDER_CHEST, nested.tableId(), List.of("Nested loot table")));
        button(menu, 10, Material.GOLD_NUGGET, "Weight: " + multi.weight, List.of("Applied to every selected entry."), p -> promptLootMultiField(p, multi, LootEntryField.WEIGHT));
        if (!multi.tableMode) {
            button(menu, 12, Material.HOPPER, "Min Count: " + multi.minimumAmount, List.of("Applied to every selected entry."), p -> promptLootMultiField(p, multi, LootEntryField.MINIMUM_AMOUNT));
            button(menu, 14, Material.HOPPER, "Max Count: " + multi.maximumAmount, List.of("Applied to every selected entry."), p -> promptLootMultiField(p, multi, LootEntryField.MAXIMUM_AMOUNT));
        }
        button(menu, 16, Material.CHEST, "Max Per Container: " + (multi.maximumPerContainer == 0 ? "Unlimited" : multi.maximumPerContainer), List.of("0 means unlimited. Applied to every selected entry."), p -> promptLootMultiField(p, multi, LootEntryField.MAXIMUM_PER_CONTAINER));
        button(menu, 21, Material.ARROW, "Back", List.of("Keep this draft and selection."), p -> scheduleLootTransition(p, () -> openLootMultiSelectNow(p, multi)));
        button(menu, 23, Material.BARRIER, "Cancel", List.of("Discard multi-edit changes."), p -> cancelLootMultiEdit(p, multi));
        button(menu, 25, Material.EMERALD_BLOCK, "Apply", List.of(multi.tableMode ? "Apply weight and cap to selected references." : "Apply all four settings to selected entries."), p -> applyLootMultiEdit(p, multi));
        open(player, menu);
    }

    private void promptLootMultiField(Player player, LootMultiEditSession multi, LootEntryField field) {
        scheduleLootTransition(player, () -> prompts.prompt(player, field.prompt(), value -> {
            try {
                int number = Integer.parseInt(value.trim());
                if (multi.tableMode) {
                    if (field != LootEntryField.WEIGHT && field != LootEntryField.MAXIMUM_PER_CONTAINER) throw new IllegalArgumentException("Item counts do not apply to table references");
                    LootTableEntry first = (LootTableEntry) multi.firstSelectedEntry();
                    LootTableEntry validated = field == LootEntryField.WEIGHT ? new LootTableEntry(first.tableId(), number, multi.maximumPerContainer) : new LootTableEntry(first.tableId(), multi.weight, number);
                    multi.weight = validated.weight(); multi.maximumPerContainer = validated.maximumPerContainer();
                } else {
                    LootEntry first = (LootEntry) multi.firstSelectedEntry();
                    LootEntry validated = updateLootEntry(new LootEntry(first.item(), multi.weight, multi.minimumAmount, multi.maximumAmount, multi.maximumPerContainer), field, number);
                    multi.weight = validated.weight(); multi.minimumAmount = validated.minimumAmount(); multi.maximumAmount = validated.maximumAmount(); multi.maximumPerContainer = validated.maximumPerContainer();
                }
            } catch (RuntimeException ex) {
                player.sendMessage(Component.text("Edit failed: " + ex.getMessage()));
            }
            openLootMultiConfigNow(player, multi);
        }, () -> openLootMultiConfigNow(player, multi)));
    }

    private void applyLootMultiEdit(Player player, LootMultiEditSession multi) {
        Map<String, LootPoolEntry> replacements = new LinkedHashMap<>();
        for (String id : multi.selectedIds) {
            DraftLootEntry draft = multi.editor.entry(id);
            if (draft == null) continue;
            if (draft.value() instanceof LootEntry item) replacements.put(id, new LootEntry(item.item(), multi.weight, multi.minimumAmount, multi.maximumAmount, multi.maximumPerContainer));
            else replacements.put(id, new LootTableEntry(((LootTableEntry) draft.value()).tableId(), multi.weight, multi.maximumPerContainer));
        }
        LootTable candidate = multi.editor.tableReplacing(replacements);
        if (!saveLootCandidate(player, candidate)) {
            openLootMultiConfigNow(player, multi);
            return;
        }
        replacements.forEach(multi.editor::replace);
        multi.editor.sort();
        lootMultiEdits.remove(player.getUniqueId());
        returnToLootEditor(player, multi.editor);
    }

    private void cancelLootMultiEdit(Player player, LootMultiEditSession multi) {
        lootMultiEdits.remove(player.getUniqueId());
        returnToLootEditor(player, multi.editor);
    }

    private void finishLootEditor(Player player, LootEditSession session) {
        queueLootTransaction(player, session, () -> {
            closeLootEditorSession(player, session);
            suppressLootClose.add(player.getUniqueId());
            openLootTables(player);
        });
    }

    private boolean saveLootCandidate(Player player, LootTable candidate) {
        try {
            lootRegistry.save(candidate);
            return true;
        } catch (Exception ex) {
            player.sendMessage(Component.text("Loot table change was not saved: " + ex.getMessage() + ". Your previous settings are unchanged."));
            return false;
        }
    }

    private void openDeleteLootConfirm(Player player, String tableId) {
        if (denyLockedLootTable(player, tableId)) return;
        List<String> parents = lootRegistry.parentsOf(tableId);
        Menu menu = menu("da:delete-loot:" + tableId, 27, "Delete Loot Table?");
        List<String> lore = new ArrayList<>(List.of("Delete " + tableId + " and remove every reference.", "Parent tables: " + parents.size(), "Marker bindings will also be cleared."));
        lore.addAll(parents.stream().limit(5).map(parent -> "- " + parent).toList());
        button(menu, 11, Material.RED_CONCRETE, "Confirm Cascade Delete", lore, p -> {
            List<String> currentParents = lootRegistry.parentsOf(tableId);
            if (denyLockedLootTable(p, tableId) || currentParents.stream().anyMatch(parent -> denyLockedLootTable(p, parent))) { openLootTables(p); return; }
            Map<Path, byte[]> backup = lootCascadeBackup(tableId, currentParents);
            try {
                for (String parentId : currentParents) {
                    LootTable parent = lootRegistry.get(parentId).orElseThrow();
                    lootRegistry.save(new LootTable(parent.id(), parent.entries().stream().filter(entry -> !(entry instanceof LootTableEntry nested) || !nested.tableId().equalsIgnoreCase(tableId)).toList()));
                }
                clearLootBindings(tableId);
                lootRegistry.delete(tableId);
                authoringManager.removeLootTableBindings(tableId);
                reloadContent();
                p.sendMessage(Component.text("Deleted loot table and removed its parent references and marker bindings."));
                openLootTables(p);
            } catch (Exception ex) {
                restoreLootCascadeBackup(backup);
                reloadContent();
                p.sendMessage(Component.text("Delete failed safely: " + ex.getMessage() + ". All affected files were restored."));
                openLootTables(p);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), this::openLootTables);
        open(player, menu);
    }

    private Map<Path, byte[]> lootCascadeBackup(String tableId, List<String> parents) {
        try {
            Map<Path, byte[]> backup = new LinkedHashMap<>();
            List<Path> paths = new ArrayList<>();
            paths.add(lootRegistry.directory().resolve(tableId.toLowerCase(Locale.ROOT) + ".yml"));
            parents.forEach(parent -> paths.add(lootRegistry.directory().resolve(parent + ".yml")));
            templateRegistry.visible().stream().filter(template -> template.lootBindings().values().stream().anyMatch(binding -> binding.tableId().equalsIgnoreCase(tableId))).forEach(template -> paths.add(template.structureFile().resolveSibling("room.yml")));
            doorRegistry.visible().stream().filter(template -> template.lootBindings().values().stream().anyMatch(binding -> binding.tableId().equalsIgnoreCase(tableId))).forEach(template -> paths.add(template.structureFile().resolveSibling("door.yml")));
            featureRegistry.visible().stream().filter(template -> template.lootBindings().values().stream().anyMatch(binding -> binding.tableId().equalsIgnoreCase(tableId))).forEach(template -> paths.add(template.structureFile().resolveSibling("feature.yml")));
            for (Path path : paths.stream().distinct().toList()) backup.put(path, Files.readAllBytes(path));
            return backup;
        } catch (IOException ex) { throw new IllegalArgumentException("Could not prepare a safe delete: " + ex.getMessage(), ex); }
    }

    private void restoreLootCascadeBackup(Map<Path, byte[]> backup) {
        backup.forEach((path, bytes) -> {
            try { Files.write(path, bytes); }
            catch (IOException ex) { plugin.getLogger().severe("Failed to restore " + path + ": " + ex.getMessage()); }
        });
    }

    private void closeLootEditorSession(Player player, LootEditSession session) {
        if (session.closed) return;
        session.closed = true;
        session.drafts.clear();
        session.transactions.clear();
        lootEdits.remove(player.getUniqueId(), session);
        lootMultiEdits.remove(player.getUniqueId());
        lootLeases.release(session.id, session.ownerId);
        prompts.discard(player.getUniqueId());
    }

    private boolean denyLockedLootTable(Player player, String tableId) {
        LootEditorLeaseRegistry.Lease lease = lootLeases.lease(tableId).orElse(null);
        if (lease == null || lease.ownerId().equals(player.getUniqueId())) return false;
        player.sendMessage(Component.text(lease.ownerName() + " is editing loot table " + tableId + ". Try again when they are done."));
        return true;
    }

    private void renderLootPage(Inventory inventory, LootEditSession session) {
        List<DraftLootEntry> pageEntries = session.pageEntries();
        int totalWeight = session.values().stream().mapToInt(LootPoolEntry::weight).sum();
        for (int slot = 0; slot < 45 && slot < pageEntries.size(); slot++) {
            DraftLootEntry entry = pageEntries.get(slot);
            ItemStack item = lootEditorItem(session, entry, totalWeight);
            inventory.setItem(slot, item);
        }
    }

    private ItemStack lootEditorItem(LootEditSession session, DraftLootEntry draft, int totalWeight) {
        LootPoolEntry entry = draft.value();
        ItemStack item = entry instanceof LootEntry lootItem
            ? lootItem.item()
            : GuiItems.item(Material.ENDER_CHEST, ((LootTableEntry) entry).tableId(), List.of("Nested loot table", "Resolves one final item."));
        if (session.showWeights) {
            int visualWeight = Math.max(1, Math.min(entry.weight(), 99));
            item.setData(DataComponentTypes.MAX_STACK_SIZE, visualWeight);
            item.setAmount(visualWeight);
        } else item.setAmount(1);
        if (session.showWeights) {
            Component name = item.displayName();
            double chance = totalWeight == 0 ? 0D : entry.weight() * 100D / totalWeight;
            Component suffix = Component.text(String.format(Locale.ROOT, " [%.2f%%]", chance), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(name.append(suffix));
            item.setItemMeta(meta);
        }
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.add(Component.text("Left-click: remove", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click: configure", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack cleanLootEditorItem(ItemStack item) {
        return item.clone();
    }

    private void clearLootBindings(String tableId) {
        for (RoomTemplate room : templateRegistry.visible()) {
            Map<String, LootBinding> bindings = new LinkedHashMap<>(room.lootBindings());
            if (bindings.values().removeIf(value -> value.tableId().equalsIgnoreCase(tableId))) saveRoom(new RoomTemplate(room.id(), room.category(), room.weight(), room.minimumConnections(), room.tags(), room.size(), room.spawn(), room.doors(), room.markers(), room.featureSlots(), bindings, room.structureFile()));
        }
        for (DoorTemplate door : doorRegistry.visible()) {
            Map<String, LootBinding> bindings = new LinkedHashMap<>(door.lootBindings());
            if (bindings.values().removeIf(value -> value.tableId().equalsIgnoreCase(tableId))) saveDoorTemplate(new DoorTemplate(door.id(), door.size(), door.tags(), door.markers(), door.featureSlots(), bindings, door.gateway(), door.structureFile()));
        }
        for (FeatureTemplate feature : featureRegistry.visible()) {
            Map<String, LootBinding> bindings = new LinkedHashMap<>(feature.lootBindings());
            if (bindings.values().removeIf(value -> value.tableId().equalsIgnoreCase(tableId))) saveFeatureTemplate(new FeatureTemplate(feature.id(), feature.size(), feature.tags(), feature.markers(), feature.featureSlots(), bindings, feature.structureFile()));
        }
    }

    public void openDiagnostics(Player player) {
        openDiagnostics(player, 0);
    }

    private void openDiagnostics(Player player, int page) {
        TemplateValidationResult result = TemplateDiagnostics.analyze(templateRegistry, featureRegistry, doorRegistry, lootRegistry);
        List<TemplateDiagnostic> diagnostics = result.diagnostics();
        Menu menu = menu("da:diagnostics:" + page, 54, "Diagnostics");
        button(menu, 4, result.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, result.valid() ? "No Blocking Issues" : "Issues Found", List.of("Errors: " + result.errors().size(), "Warnings: " + result.warnings().size(), "Repairs: " + result.repairs().size()), p -> openDiagnostics(p, page));
        int start = page * 36;
        int slot = 9;
        for (int i = start; i < diagnostics.size() && slot < 45; i++) {
            TemplateDiagnostic diagnostic = diagnostics.get(i);
            Material material = diagnostic.severity() == com.dungeonarchitect.template.DiagnosticSeverity.ERROR ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE;
            button(menu, slot++, material, diagnostic.severity() + " " + targetName(diagnostic), diagnosticLore(diagnostic), p -> {
                p.sendMessage(Component.text(diagnostic.display()));
                if (diagnostic.suggestion() != null) {
                    p.sendMessage(Component.text("Fix: " + diagnostic.suggestion()));
                }
            });
        }
        if (page > 0) {
            button(menu, 45, Material.ARROW, "Previous", List.of(), p -> openDiagnostics(p, page - 1));
        }
        if (diagnostics.size() > start + 36) {
            button(menu, 53, Material.ARROW, "Next", List.of(), p -> openDiagnostics(p, page + 1));
        }
        button(menu, 49, Material.BARRIER, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void prompt(Player player, String message, Consumer<String> handler) {
        prompts.prompt(player, message, handler);
    }

    public void openRooms(Player player) {
        Menu menu = menu("da:rooms", 54, "DungeonArchitect Rooms");
        int slot = 0;
        for (TemplateLoadStatus<RoomTemplate> status : templateRegistry.loadStatuses()) {
            int target = slot++;
            RoomTemplate template = status.template();
            List<String> lore = loadStatusLore(status, template == null ? "Template could not be loaded." : "Category: " + template.category(), template == null ? "Fix the errors and reload." : "Door Slots: " + template.doors().size(), template == null ? "" : "Click to edit.");
            button(menu, target, statusValid(status) ? Material.PAPER : Material.RED_CONCRETE, status.id(), lore, p -> openOrExplainInvalid(p, "room", status.id(), status.errors(), template == null ? null : () -> openRoom(p, template.id())));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openRoom(Player player, String roomId) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        Menu menu = menu("da:room:" + roomId, 54, "Room: " + roomId);
        TemplateLoadStatus<RoomTemplate> status = templateRegistry.status(template.id()).orElse(null);
        var validation = validator.validate(template);
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        var doors = activeEdit == null ? template.doors() : activeEdit.doors();
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        button(menu, 4, statusValid(status) && validation.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, statusValid(status) && validation.valid() ? "Validation OK" : "Validation Errors", status == null ? validation.errors() : loadStatusLore(status), p -> openRoom(p, roomId));
        button(menu, 10, Material.NAME_TAG, "Category: " + template.category(), enumNames(RoomCategory.class), p -> {
            RoomCategory next = nextCategory(template.category());
            if (activeEdit != null) {
                activeEdit.category(next);
            }
            saveRoom(new RoomTemplate(template.id(), next, template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            p.sendMessage(Component.text("Room category changed to " + next + "."));
            openRoom(p, roomId);
        });
        button(menu, 12, Material.GOLD_NUGGET, "Weight: " + template.weight(), List.of("Click to edit generation weight."), p -> prompts.prompt(p, "Enter positive integer weight", value -> {
            saveRoom(new RoomTemplate(template.id(), template.category(), Integer.parseInt(value), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            p.sendMessage(Component.text("Room weight updated."));
            openRoom(p, roomId);
        }));
        button(menu, 14, Material.COMPASS, "Spawn: " + (template.spawn() == null ? "unset" : template.spawn()), List.of("Format: x,y,z or empty to clear."), p -> prompts.prompt(p, "Enter spawn as x,y,z or empty", value -> {
            IntVector3 spawn = value.isBlank() ? null : parseVector(value);
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), spawn, template.doors(), template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            p.sendMessage(Component.text("Room spawn updated."));
            openRoom(p, roomId);
        }));
        Set<String> currentRoomTags = activeEdit == null ? template.tags() : activeEdit.tags();
        button(menu, 16, Material.OAK_SIGN, "Tags: " + String.join(",", currentRoomTags), List.of("Click to select room tags."), p -> openRoomTags(p, template));
        button(menu, 18, Material.IRON_BARS, "Minimum Connections: " + template.minimumConnections(), List.of("Connections required for every generated copy.", "0 allows this room to end a branch."), p -> prompts.prompt(p, "Enter a non-negative minimum connection count", value -> {
            int minimumConnections = Integer.parseInt(value);
            if (minimumConnections < 0) {
                throw new IllegalArgumentException("Minimum connections cannot be negative");
            }
            if (minimumConnections > template.doors().size()) {
                throw new IllegalArgumentException("Minimum connections cannot exceed this room's " + template.doors().size() + " door slots");
            }
            if (activeEdit != null) {
                activeEdit.minimumConnections(minimumConnections);
            }
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), minimumConnections, template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            p.sendMessage(Component.text("Minimum connections updated."));
            openRoom(p, roomId);
        }));
        button(menu, 22, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this room into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted " + template.id() + " into the edit world."));
            });
        });
        button(menu, 23, authoringManager.isEditingRoom(player, roomId) ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this room from the edit world."), p -> {
            if (!authoringManager.isEditingRoom(p, roomId)) {
                p.sendMessage(Component.text("Paste this room for editing first."));
                openRoom(p, roomId);
                return;
            }
            try {
                TemplateValidationResult result = authoringManager.save(p, roomId);
                sendValidation(p, result);
                if (!result.valid()) {
                    authoringManager.highlightInvalid(p, result);
                }
                templateRegistry.reload();
                openRoom(p, roomId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openRoom(p, roomId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Room edit session cancelled."));
            openRoom(p, roomId);
        });
        button(menu, 28, Material.IRON_DOOR, "Door Slots: " + doors.size(), doors.stream().map(door -> door.id() + " " + door.size() + " " + door.facing()).toList(), p -> openComponents(p, roomId, "door"));
        button(menu, 30, Material.REDSTONE_TORCH, "Markers: " + markers.size(), markers.stream().map(marker -> marker.name() + " " + marker.type()).toList(), p -> openComponents(p, roomId, "marker"));
        button(menu, 32, Material.CHEST, "Feature Slots: " + features.size(), features.stream().map(slot -> slot.id() + " size=" + slot.size()).toList(), p -> openComponents(p, roomId, "feature"));
        button(menu, 36, Material.NAME_TAG, "Rename Room", List.of("Move this room template to a new id."), p -> promptRenameRoom(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Room", List.of("Copy this room template to a new id."), p -> promptDuplicateRoom(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Room", List.of("Permanently delete this room template."), p -> openDeleteRoomConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openRooms);
        open(player, menu);
    }

    private void openComponents(Player player, String roomId, String type) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        Menu menu = menu("da:components:" + roomId + ":" + type, 54, titleCase(type) + "s: " + roomId);
        int slot = 0;
        if (type.equals("door")) {
            var doors = activeEdit == null ? template.doors() : activeEdit.doors();
            for (var door : doors) {
                button(menu, slot++, Material.IRON_DOOR, door.id(), List.of("Position: " + door.position(), "Size: " + door.size(), "Facing: " + door.facing(), "Entries: " + door.entries().size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openDoorSlot(p, roomId, door.id()), p -> selectComponent(p, roomId, type, door.id()), p -> promptRenameComponent(p, roomId, type, door.id()), p -> openDeleteComponentConfirm(p, roomId, type, door.id()));
                if (slot >= 45) {
                    break;
                }
            }
        } else if (type.equals("marker")) {
            var markers = activeEdit == null ? template.markers() : activeEdit.markers();
            var lootBindings = activeEdit == null ? template.lootBindings() : activeEdit.lootBindings();
            for (var marker : markers) {
                button(menu, slot++, Material.REDSTONE_TORCH, marker.name(), lootMarkerLore(List.of("Type: " + marker.type(), "Position: " + marker.position()), lootBindings.get(marker.name()), "Click to configure loot.", "Right click to select in edit world."), p -> openRoomMarkerLoot(p, roomId, marker.name()), p -> selectComponent(p, roomId, type, marker.name()), p -> promptRenameComponent(p, roomId, type, marker.name()), p -> openDeleteComponentConfirm(p, roomId, type, marker.name()));
                if (slot >= 45) {
                    break;
                }
            }
        } else if (type.equals("feature")) {
            var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
            for (var feature : features) {
                button(menu, slot++, Material.CHEST, feature.id(), List.of("Position: " + feature.position(), "Size: " + feature.size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openFeatureSlot(p, roomId, feature.id()), p -> selectComponent(p, roomId, type, feature.id()), p -> promptRenameComponent(p, roomId, type, feature.id()), p -> openDeleteComponentConfirm(p, roomId, type, feature.id()));
                if (slot >= 45) {
                    break;
                }
            }
        }
        if (type.equals("door") || type.equals("feature") || type.equals("marker")) {
            Material material = type.equals("door") ? Material.OAK_DOOR : Material.HOPPER;
            MultiEditSlotType slotType = switch (type) {
                case "door" -> MultiEditSlotType.DOOR;
                case "feature" -> MultiEditSlotType.FEATURE;
                default -> MultiEditSlotType.MARKER;
            };
            button(menu, 45, material, "Multi-edit", List.of("Configure multiple " + type + "s at once."), p -> beginMultiEdit(p, MultiEditOwner.ROOM, roomId, slotType));
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openRoom(p, roomId));
        open(player, menu);
    }

    private void openRoomMarkerLoot(Player player, String roomId, String marker) {
        RoomTemplate room = templateRegistry.getVisible(roomId).orElseThrow();
        openLootBinding(player, "da:room-loot:" + roomId + ":" + marker, marker, room.lootBindings(), bindings -> {
            saveRoom(new RoomTemplate(room.id(), room.category(), room.weight(), room.minimumConnections(), room.tags(), room.size(), room.spawn(), room.doors(), room.markers(), room.featureSlots(), bindings, room.structureFile()));
            authoringManager.synchronizeRoomLootBindings(room.id(), bindings);
        },
            () -> openComponents(player, roomId, "marker"));
    }

    private void openFeatureSlot(Player player, String roomId, String slotId) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = roomEditInWorkspace(player, roomId);
        List<RoomFeatureSlot> featureSlots = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        RoomFeatureSlot featureSlot = featureSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature slot " + slotId));
        Menu menu = menu("da:feature-slot:" + roomId + ":" + slotId, 54, "Feature Slot: " + slotId);
        button(menu, 4, Material.HOPPER, "Slot " + slotId, List.of("Size: " + featureSlot.size(), "Position: " + featureSlot.position()), p -> openFeatureSlot(p, roomId, slotId));
        int slot = 9;
        if (featureSlot.entries().isEmpty()) {
            button(menu, 5, Material.YELLOW_CONCRETE, "No Entries Selected", List.of("This slot always pastes nothing."), p -> openFeatureSlot(p, roomId, slotId));
        }
        button(menu, slot++, Material.BARRIER, "empty", entryLore(featureSlot, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> toggleFeatureEntry(p, template, featureSlot, FeatureSlotEntry.EMPTY, 1), null, p -> promptFeatureWeight(p, template, featureSlot, FeatureSlotEntry.EMPTY));
        List<FeatureTemplate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.all()) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            if (!match.matched()) {
                unavailable.add(feature);
                continue;
            }
            Rotation rotation = match.rotation();
            IntVector3 rotatedSize = rotation.rotateSize(feature.size());
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), entryLore(featureSlot, feature.id(), "Size: " + feature.size(), "Paste size: " + rotatedSize, "Offset: " + FeatureMatcher.placementOffset(featureSlot.size(), rotatedSize), "Rotation: " + rotation), p -> toggleFeatureEntry(p, template, featureSlot, feature.id(), 1), null, p -> promptFeatureWeight(p, template, featureSlot, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableFeatureEntries(menu, slot, featureSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openComponents(p, roomId, "feature"));
        open(player, menu);
    }

    private void openDoorSlot(Player player, String roomId, String slotId) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        List<DoorSocket> doorSlots = activeEdit == null ? template.doors() : activeEdit.doors();
        DoorSocket doorSlot = doorSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown door slot " + slotId));
        Menu menu = menu("da:door-slot:" + roomId + ":" + slotId, 54, "Door Slot: " + slotId);
        button(menu, 0, Material.NAME_TAG, "Tags: " + String.join(",", doorSlot.tags()), List.of("Click to select door tags."), p -> promptDoorTags(p, template, doorSlot));
        button(menu, 1, doorSlot.connectionRules().mustConnect() ? Material.TRIPWIRE_HOOK : Material.GRAY_CONCRETE, "Must Connect: " + doorSlot.connectionRules().mustConnect(), List.of("A generated dungeon is invalid until this slot connects."), p -> toggleDoorMustConnect(p, template, doorSlot));
        button(menu, 2, Material.LIME_DYE, "Accept Tags: " + String.join(",", doorSlot.connectionRules().allowedTags()), List.of("Select opposite-door tags.", "Empty accepts any tag unless rejected."), p -> promptDoorAllowedTags(p, template, doorSlot));
        button(menu, 3, Material.RED_DYE, "Reject Tags: " + String.join(",", doorSlot.connectionRules().deniedTags()), List.of("Select opposite-door tags."), p -> promptDoorDeniedTags(p, template, doorSlot));
        button(menu, 4, Material.IRON_DOOR, "Slot " + slotId, List.of("Size: " + doorSlot.size(), "Position: " + doorSlot.position(), "Facing: " + doorSlot.facing(), "Tags: " + String.join(",", doorSlot.tags())), p -> openDoorSlot(p, roomId, slotId));
        button(menu, 5, Material.COMPASS, "Connection Preview", connectionPreviewLore(template, doorSlot), p -> openDoorSlot(p, roomId, slotId));
        button(menu, 6, Material.LIME_BANNER, "Accept Room Tags: " + String.join(",", doorSlot.connectionRules().allowedRoomTags()), List.of("Select opposite-room tags.", "Empty accepts any room unless rejected."), p -> promptDoorAllowedRoomTags(p, template, doorSlot));
        button(menu, 7, Material.RED_BANNER, "Reject Room Tags: " + String.join(",", doorSlot.connectionRules().deniedRoomTags()), List.of("Select opposite-room tags."), p -> promptDoorDeniedRoomTags(p, template, doorSlot));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", doorEntryLore(doorSlot, DoorSlotEntry.EMPTY, "Virtual door; leaves room blueprint unchanged."), p -> toggleDoorEntry(p, template, doorSlot, DoorSlotEntry.EMPTY, 1), null, p -> promptDoorWeight(p, template, doorSlot, DoorSlotEntry.EMPTY));
        List<DoorTemplate> unavailable = new ArrayList<>();
        for (DoorTemplate door : doorRegistry.all()) {
            DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(doorSlot, door);
            if (!match.matched()) {
                unavailable.add(door);
                continue;
            }
            button(menu, slot++, Material.OAK_DOOR, door.id(), doorEntryLore(doorSlot, door.id(), "Bounds: " + door.size(), "Gateway: " + door.gateway().size(), "Door rotation: " + match.rotation(), "Tags: " + String.join(",", door.tags())), p -> toggleDoorEntry(p, template, doorSlot, door.id(), 1), null, p -> promptDoorWeight(p, template, doorSlot, door.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableDoorEntries(menu, slot, doorSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openComponents(p, roomId, "door"));
        open(player, menu);
    }

    private int addUnavailableFeatureEntries(Menu menu, int slot, RoomFeatureSlot featureSlot, List<FeatureTemplate> unavailable) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These features do not fit this slot."), p -> {});
        for (FeatureTemplate feature : unavailable) {
            if (slot >= 45) {
                break;
            }
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            button(menu, slot++, Material.GRAY_CONCRETE, feature.id(), List.of("Unavailable: " + match.reason(), "Size: " + DiagnosticText.size(feature.size())), p -> p.sendMessage(Component.text("Feature " + feature.id() + " unavailable: " + match.reason())));
        }
        return slot;
    }

    private int addUnavailableDoorEntries(Menu menu, int slot, DoorSocket doorSlot, List<DoorTemplate> unavailable) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These doors do not match this slot."), p -> {});
        for (DoorTemplate door : unavailable) {
            if (slot >= 45) {
                break;
            }
            DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(doorSlot, door);
            button(menu, slot++, Material.GRAY_CONCRETE, door.id(), List.of("Unavailable: " + match.reason(), "Bounds: " + DiagnosticText.size(door.size()), "Gateway: " + (door.gateway() == null ? "unset" : DiagnosticText.size(door.gateway().size()) + " " + door.gateway().facing())), p -> p.sendMessage(Component.text("Door " + door.id() + " unavailable: " + match.reason())));
        }
        return slot;
    }

    private List<String> doorEntryLore(DoorSocket slot, String doorId, String... extra) {
        List<String> lore = new ArrayList<>();
        slot.entries().stream()
            .filter(entry -> entry.doorId().equalsIgnoreCase(doorId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits weight.");
        return lore;
    }

    private void toggleDoorEntry(Player player, RoomTemplate template, DoorSocket slot, String doorId, int defaultWeight) {
        List<DoorSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
        if (!removed) {
            entries.add(new DoorSlotEntry(doorId, defaultWeight));
        }
        saveDoorSlot(player, template, slot.withEntries(entries));
    }

    private void promptDoorTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Door Slot Tags", slot.tags(), tags -> {
            saveDoorSlot(player, template, slot.withTags(tags));
            player.sendMessage(Component.text("Door slot tags updated."));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void toggleDoorMustConnect(Player player, RoomTemplate template, DoorSocket slot) {
        DoorConnectionRules rules = slot.connectionRules();
        saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), !rules.mustConnect())));
    }

    private void promptDoorAllowedTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Accept Door Tags", slot.connectionRules().allowedTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(tags, rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorDeniedTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Reject Door Tags", slot.connectionRules().deniedTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), tags, rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorAllowedRoomTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.ROOM, "Accept Room Tags", slot.connectionRules().allowedRoomTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), tags, rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorDeniedRoomTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.ROOM, "Reject Room Tags", slot.connectionRules().deniedRoomTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), tags, rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private Set<String> parseTags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : value.split(",")) {
            if (!tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private void openRoomTags(Player player, RoomTemplate template) {
        Set<String> current = authoringManager.editingSession(player, template.id()).map(AuthoringSession::tags).orElse(template.tags());
        openTagSelector(player, TagDomain.ROOM, "Room Tags", current, tags -> {
            RoomTemplate latest = templateRegistry.getVisible(template.id()).orElse(template);
            authoringManager.editingSession(player, template.id()).ifPresent(session -> session.tags(tags));
            saveRoom(new RoomTemplate(latest.id(), latest.category(), latest.weight(), latest.minimumConnections(), tags, latest.size(), latest.spawn(), latest.doors(), latest.markers(), latest.featureSlots(), latest.lootBindings(), latest.structureFile()));
            player.sendMessage(Component.text("Room tags updated."));
            openRoom(player, template.id());
        }, p -> openRoom(p, template.id()));
    }

    private void openDoorTemplateTags(Player player, DoorTemplate template) {
        Set<String> current = authoringManager.editingDoorSession(player, template.id()).map(AuthoringSession::tags).orElse(template.tags());
        openTagSelector(player, TagDomain.DOOR, "Door Template Tags", current, tags -> {
            DoorTemplate latest = doorRegistry.getVisible(template.id()).orElse(template);
            authoringManager.editingDoorSession(player, template.id()).ifPresent(session -> session.tags(tags));
            saveDoorTemplate(new DoorTemplate(latest.id(), latest.size(), tags, latest.markers(), latest.featureSlots(), latest.lootBindings(), latest.gateway(), latest.structureFile()));
            player.sendMessage(Component.text("Door template tags updated."));
            openDoor(player, template.id());
        }, p -> openDoor(p, template.id()));
    }

    private void openTagSelector(Player player, TagDomain domain, String title, Set<String> selected, Consumer<Set<String>> onSave, Consumer<Player> onCancel) {
        tagSelections.put(player.getUniqueId(), new TagSelection(domain, title, selected, onSave, onCancel));
        openTagSelector(player);
    }

    private void openTagSelector(Player player) {
        TagSelection selection = requireTagSelection(player);
        List<String> filtered = tagCatalog.tags(selection.domain, selection.filter);
        int pageCount = Math.max(1, (filtered.size() + 44) / 45);
        selection.page = Math.min(selection.page, pageCount - 1);
        Menu menu = menu("da:tags:" + selection.domain.name().toLowerCase(Locale.ROOT), 54, selection.title);
        int start = selection.page * 45;
        for (int index = start; index < filtered.size() && index < start + 45; index++) {
            String tag = filtered.get(index);
            int slot = index - start;
            boolean selected = containsTag(selection.selected, tag);
            button(menu, slot, selected ? Material.LIME_DYE : Material.NAME_TAG, tag,
                List.of(selected ? "Selected. Click to remove from this field." : "Click to add to this field.", "Shift-right-click to delete globally."),
                p -> {
                    toggleTag(selection.selected, tag);
                    openTagSelector(p);
                }, null, null, p -> openTagDeleteConfirm(p, tag));
        }
        if (selection.page > 0) {
            button(menu, 45, Material.ARROW, "Previous", List.of(), p -> {
                selection.page--;
                openTagSelector(p);
            });
        }
        button(menu, 47, Material.ANVIL, "Add New Tag", List.of("Create one " + selection.domain.label().toLowerCase(Locale.ROOT) + " entry."), this::promptNewTag);
        button(menu, 49, Material.BARRIER, "Back", List.of("Discard tag changes."), this::cancelTagSelection);
        button(menu, 51, Material.SPYGLASS, "Search: " + (selection.filter.isBlank() ? "all tags" : selection.filter), List.of("Click to filter tags."), this::promptTagSearch);
        if (start + 45 < filtered.size()) {
            button(menu, 52, Material.ARROW, "Next", List.of(), p -> {
                selection.page++;
                openTagSelector(p);
            });
        }
        button(menu, 53, Material.EMERALD_BLOCK, "Done", List.of("Save " + selection.selected.size() + " selected tag(s)."), this::saveTagSelection);
        open(player, menu);
    }

    private void promptTagSearch(Player player) {
        TagSelection selection = requireTagSelection(player);
        prompts.prompt(player, "Search " + selection.domain.label() + " (empty shows all)", value -> {
            selection.filter = value.trim();
            selection.page = 0;
            openTagSelector(player);
        });
    }

    private void promptNewTag(Player player) {
        TagSelection selection = requireTagSelection(player);
        prompts.prompt(player, "Enter one new " + selection.domain.label().toLowerCase(Locale.ROOT), value -> {
            String tag = tagCatalog.add(selection.domain, value);
            addTag(selection.selected, tag);
            selection.filter = "";
            selection.page = 0;
            openTagSelector(player);
        });
    }

    private void saveTagSelection(Player player) {
        TagSelection selection = requireTagSelection(player);
        tagSelections.remove(player.getUniqueId());
        selection.onSave.accept(Set.copyOf(selection.selected));
    }

    private void cancelTagSelection(Player player) {
        TagSelection selection = tagSelections.remove(player.getUniqueId());
        if (selection != null) {
            selection.onCancel.accept(player);
        }
    }

    private void openTagDeleteConfirm(Player player, String tag) {
        TagSelection selection = requireTagSelection(player);
        TagCleanupService.Result result = TagCleanupService.remove(selection.domain, tag, templateRegistry.visible(), doorRegistry.visible());
        Menu menu = menu("da:tag-delete:" + selection.domain.name().toLowerCase(Locale.ROOT), 27, "Delete Tag: " + tag);
        button(menu, 11, Material.RED_CONCRETE, "Delete Globally", List.of("Remove from " + result.affectedFields() + " tag field(s).", "This cannot be undone."), p -> deleteTagGlobally(p, tag));
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), this::openTagSelector);
        open(player, menu);
    }

    private void deleteTagGlobally(Player player, String tag) {
        TagSelection selection = requireTagSelection(player);
        TagCleanupService.Result result = TagCleanupService.remove(selection.domain, tag, templateRegistry.visible(), doorRegistry.visible());
        try {
            for (RoomTemplate room : result.rooms()) {
                RoomTemplateIO.save(room, room.structureFile().getParent());
            }
            for (DoorTemplate door : result.doors()) {
                DoorTemplateIO.save(door, door.structureFile().getParent());
            }
            tagCatalog.remove(selection.domain, tag);
            authoringManager.removeTag(selection.domain, tag);
            reloadContent();
            player.sendMessage(Component.text("Deleted " + tag + " from " + result.affectedFields() + " tag field(s)."));
            cancelTagSelection(player);
        } catch (IOException ex) {
            player.sendMessage(Component.text("Tag deletion failed: " + ex.getMessage()));
            openTagSelector(player);
        }
    }

    private TagSelection requireTagSelection(Player player) {
        TagSelection selection = tagSelections.get(player.getUniqueId());
        if (selection == null) {
            throw new IllegalStateException("No active tag selection");
        }
        return selection;
    }

    private static boolean containsTag(Collection<String> tags, String tag) {
        return tags.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(tag));
    }

    private static void addTag(Set<String> tags, String tag) {
        tags.removeIf(candidate -> candidate.equalsIgnoreCase(tag));
        tags.add(tag);
    }

    private static void toggleTag(Set<String> tags, String tag) {
        if (!tags.removeIf(candidate -> candidate.equalsIgnoreCase(tag))) {
            tags.add(tag);
        }
    }

    private List<String> connectionPreviewLore(RoomTemplate owner, DoorSocket slot) {
        List<String> lore = new ArrayList<>();
        int matches = 0;
        for (RoomTemplate candidateRoom : templateRegistry.all()) {
            for (DoorSocket candidate : candidateRoom.doors()) {
                if (candidateRoom.id().equals(owner.id()) && candidate.id().equals(slot.id())) {
                    continue;
                }
                if (candidate.facing() != slot.facing().opposite()) {
                    continue;
                }
                DoorSocket.ConnectionMatch match = slot.connectionMatch(candidate, owner.tags(), candidateRoom.tags());
                if (match.compatible()) {
                    matches++;
                    if (lore.size() < 5) {
                        lore.add("Compatible: " + candidateRoom.id() + ":" + candidate.id());
                    }
                } else if (lore.size() < 5) {
                    lore.add("Unavailable: " + candidateRoom.id() + ":" + candidate.id() + " - " + match.reason());
                }
            }
        }
        lore.add(0, "Opposite-facing rule matches: " + matches);
        if (lore.size() == 1) {
            lore.add("No opposite-facing room slots found.");
        }
        return lore;
    }

    private void promptDoorWeight(Player player, RoomTemplate template, DoorSocket slot, String doorId) {
        prompts.prompt(player, "Enter weight for " + doorId, value -> {
            int weight = Integer.parseInt(value);
            List<DoorSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
            entries.add(new DoorSlotEntry(doorId, weight));
            saveDoorSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveDoorSlot(Player player, RoomTemplate template, DoorSocket updatedSlot) {
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            activeEdit.removeDoor(updatedSlot.id());
            activeEdit.addDoorSlot(updatedSlot);
            List<DoorSocket> persistedSlots = new ArrayList<>(template.doors());
            persistedSlots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), persistedSlots, template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            player.sendMessage(Component.text("Door slot updated in the active edit session."));
            openDoorSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<DoorSocket> slots = new ArrayList<>(template.doors());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), slots, template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
        player.sendMessage(Component.text("Door slot updated."));
        openDoorSlot(player, template.id(), updatedSlot.id());
    }

    private List<String> entryLore(RoomFeatureSlot slot, String featureId, String... extra) {
        List<String> lore = new ArrayList<>();
        slot.entries().stream()
            .filter(entry -> entry.featureId().equalsIgnoreCase(featureId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits weight.");
        return lore;
    }

    private void toggleFeatureEntry(Player player, RoomTemplate template, RoomFeatureSlot slot, String featureId, int defaultWeight) {
        List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            entries.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
        saveFeatureSlot(player, template, slot.withEntries(entries));
    }

    private void promptFeatureWeight(Player player, RoomTemplate template, RoomFeatureSlot slot, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            int weight = Integer.parseInt(value);
            List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            entries.add(new FeatureSlotEntry(featureId, weight));
            saveFeatureSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveFeatureSlot(Player player, RoomTemplate template, RoomFeatureSlot updatedSlot) {
        AuthoringSession activeEdit = roomEditInWorkspace(player, template.id());
        if (activeEdit != null) {
            replaceFeatureSlot(activeEdit, updatedSlot);
            player.sendMessage(Component.text("Feature slot updated in the edit workspace. Use Save Edit to persist it."));
            openFeatureSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<RoomFeatureSlot> slots = new ArrayList<>(template.featureSlots());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.lootBindings(), template.structureFile()));
        AuthoringSession retainedEdit = retainedRoomEdit(player, template.id());
        if (retainedEdit != null) {
            replaceFeatureSlot(retainedEdit, updatedSlot);
        }
        player.sendMessage(Component.text("Feature slot updated."));
        openFeatureSlot(player, template.id(), updatedSlot.id());
    }

    private RoomTemplate removeComponent(RoomTemplate template, String type, String id) {
        List<DoorSocket> doors = new ArrayList<>(template.doors());
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean removed = switch (type) {
            case "door" -> doors.removeIf(door -> door.id().equalsIgnoreCase(id));
            case "marker" -> markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
            case "feature" -> features.removeIf(slot -> slot.id().equalsIgnoreCase(id));
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (!removed) {
            return template;
        }
        Map<String, LootBinding> bindings = new LinkedHashMap<>(template.lootBindings());
        if (type.equals("marker")) bindings.keySet().removeIf(name -> name.equalsIgnoreCase(id));
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), doors, markers, features, bindings, template.structureFile());
    }

    private void selectComponent(Player player, String roomId, String type, String id) {
        if (!authoringManager.isEditingRoom(player, roomId)) {
            player.sendMessage(Component.text("Paste this room for editing before inspecting components."));
            openRoom(player, roomId);
            return;
        }
        var selection = authoringManager.selectComponent(player, type, id);
        player.closeInventory();
        player.sendMessage(Component.text("Selected " + type + " " + id + ": " + selection.worldBounds().describe()));
    }

    private void openDeleteComponentConfirm(Player player, String roomId, String type, String id) {
        Menu menu = menu("da:delete-component:" + roomId + ":" + type + ":" + id, 27, "Delete Component?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Deletes " + type + " " + id), p -> {
            boolean removed;
            if (authoringManager.isEditingRoom(p, roomId)) {
                removed = authoringManager.removeComponent(p, type, id);
            } else {
                RoomTemplate template = templateRegistry.getVisible(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
                RoomTemplate updated = removeComponent(template, type, id);
                removed = updated != template;
                if (removed) {
                    saveRoom(updated);
                }
            }
            p.sendMessage(Component.text(removed ? "Deleted " + type + " " + id + "." : "No matching " + type + " named " + id + "."));
            openComponents(p, roomId, type);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openComponents(p, roomId, type));
        open(player, menu);
    }

    private void promptRenameComponent(Player player, String roomId, String type, String oldId) {
        prompts.prompt(player, "Enter new " + type + " id", value -> {
            try {
                String newId = value.trim();
                boolean renamed;
                if (authoringManager.isEditingRoom(player, roomId)) {
                    renamed = authoringManager.renameComponent(player, type, oldId, newId);
                } else {
                    RoomTemplate template = templateRegistry.getVisible(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
                    RoomTemplate updated = renameComponent(template, type, oldId, newId);
                    renamed = updated != template;
                    if (renamed) {
                        saveRoom(updated);
                    }
                }
                player.sendMessage(Component.text(renamed ? "Renamed " + type + " " + oldId + " to " + newId + "." : "No matching " + type + " named " + oldId + "."));
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
            }
            openComponents(player, roomId, type);
        });
    }

    private RoomTemplate renameComponent(RoomTemplate template, String type, String oldId, String newId) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("New id is required");
        }
        List<DoorSocket> doors = new ArrayList<>(template.doors());
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        IdentityRules.requireRoomComponentAvailable(newId, doors, markers, features, oldId);
        boolean renamed = switch (type) {
            case "door" -> {
                boolean found = false;
                for (int i = 0; i < doors.size(); i++) {
                    DoorSocket door = doors.get(i);
                    if (door.id().equalsIgnoreCase(oldId)) {
                        doors.set(i, new DoorSocket(newId, door.position(), door.facing(), door.socketType(), door.width(), door.height(), door.size(), door.tags(), door.entries(), door.connectionRules()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "marker" -> {
                boolean found = false;
                for (int i = 0; i < markers.size(); i++) {
                    RoomMarker marker = markers.get(i);
                    if (marker.name().equalsIgnoreCase(oldId)) {
                        markers.set(i, new RoomMarker(newId, marker.type(), marker.position()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "feature" -> {
                boolean found = false;
                for (int i = 0; i < features.size(); i++) {
                    RoomFeatureSlot slot = features.get(i);
                    if (slot.id().equalsIgnoreCase(oldId)) {
                        features.set(i, new RoomFeatureSlot(newId, slot.position(), slot.size(), slot.facing(), slot.entries()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (!renamed) {
            return template;
        }
        Map<String, LootBinding> bindings = new LinkedHashMap<>(template.lootBindings());
        if (type.equals("marker")) {
            bindings.keySet().stream().filter(key -> key.equalsIgnoreCase(oldId)).findFirst().ifPresent(key -> {
                LootBinding binding = bindings.remove(key);
                bindings.put(newId, binding);
            });
        }
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), doors, markers, features, bindings, template.structureFile());
    }

    public void openDoors(Player player) {
        Menu menu = menu("da:doors", 54, "DungeonArchitect Doors");
        int slot = 0;
        for (TemplateLoadStatus<DoorTemplate> status : doorRegistry.loadStatuses()) {
            DoorTemplate template = status.template();
            List<String> lore = loadStatusLore(status, template == null ? "Template could not be loaded." : "Size: " + template.size(), template == null ? "Fix the errors and reload." : "Tags: " + String.join(",", template.tags()), template == null ? "" : "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size()), template == null ? "" : "Click to edit.");
            button(menu, slot++, statusValid(status) ? Material.OAK_DOOR : Material.RED_CONCRETE, status.id(), lore, p -> openOrExplainInvalid(p, "door", status.id(), status.errors(), template == null ? null : () -> openDoor(p, template.id())));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openDoor(Player player, String doorId) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        TemplateLoadStatus<DoorTemplate> status = doorRegistry.status(template.id()).orElse(null);
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        Menu menu = menu("da:door-template:" + doorId, 54, "Door: " + doorId);
        button(menu, 4, statusValid(status) ? Material.OAK_DOOR : Material.RED_CONCRETE, "Size: " + template.size(), loadStatusLore(status, "Captured door footprint."), p -> openDoor(p, doorId));
        button(menu, 10, template.gateway() == null ? Material.RED_CONCRETE : Material.LIME_CONCRETE, "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size() + " " + template.gateway().facing()), List.of("Set with /da door gateway.", "Right click to select in edit world."), p -> openDoor(p, doorId), p -> selectDoorComponent(p, doorId, "gateway", "gateway"));
        Set<String> currentDoorTags = activeEdit == null ? template.tags() : activeEdit.tags();
        button(menu, 12, Material.OAK_SIGN, "Tags: " + String.join(",", currentDoorTags), List.of("Click to select door tags."), p -> openDoorTemplateTags(p, template));
        button(menu, 20, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this door into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editDoorSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted door " + template.id() + " into the edit world."));
            });
        });
        button(menu, 22, authoringManager.activeDoorId(player).filter(id -> id.equalsIgnoreCase(doorId)).isPresent() ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this door from the edit world."), p -> {
            try {
                TemplateValidationResult result = authoringManager.saveDoor(p, doorId);
                sendValidation(p, result);
                doorRegistry.reload();
                templateRegistry.reload();
                openDoor(p, doorId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openDoor(p, doorId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Door edit session cancelled."));
            openDoor(p, doorId);
        });
        button(menu, 28, Material.REDSTONE_TORCH, "Markers: " + markers.size(), markers.stream().map(marker -> marker.name() + " " + marker.type()).toList(), p -> openDoorComponents(p, doorId, "marker"));
        button(menu, 30, Material.CHEST, "Feature Slots: " + features.size(), features.stream().map(slot -> slot.id() + " size=" + slot.size()).toList(), p -> openDoorComponents(p, doorId, "feature"));
        button(menu, 36, Material.NAME_TAG, "Rename Door", List.of("Move this door template to a new id."), p -> promptRenameDoor(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Door", List.of("Copy this door template to a new id."), p -> promptDuplicateDoor(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Door", List.of("Permanently delete this door template."), p -> openDeleteDoorConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openDoors);
        open(player, menu);
    }

    private void openDoorComponents(Player player, String doorId, String type) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        Menu menu = menu("da:door-components:" + doorId + ":" + type, 54, "Door " + titleCase(type) + "s: " + doorId);
        int slot = 0;
        if (type.equals("marker")) {
            var markers = activeEdit == null ? template.markers() : activeEdit.markers();
            var lootBindings = activeEdit == null ? template.lootBindings() : activeEdit.lootBindings();
            for (var marker : markers) {
                button(menu, slot++, Material.REDSTONE_TORCH, marker.name(), lootMarkerLore(List.of("Type: " + marker.type(), "Position: " + marker.position()), lootBindings.get(marker.name()), "Click to configure loot.", "Right click to select in edit world."), p -> openDoorMarkerLoot(p, doorId, marker.name()), p -> selectDoorComponent(p, doorId, type, marker.name()), p -> promptRenameDoorComponent(p, doorId, type, marker.name()), p -> openDeleteDoorComponentConfirm(p, doorId, type, marker.name()));
                if (slot >= 45) {
                    break;
                }
            }
            button(menu, 45, Material.HOPPER, "Multi-edit", List.of("Configure loot for multiple markers at once."), p -> beginMultiEdit(p, MultiEditOwner.DOOR_TEMPLATE, doorId, MultiEditSlotType.MARKER));
        } else if (type.equals("feature")) {
            var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
            for (var feature : features) {
                button(menu, slot++, Material.CHEST, feature.id(), List.of("Position: " + feature.position(), "Size: " + feature.size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openDoorFeatureSlot(p, doorId, feature.id()), p -> selectDoorComponent(p, doorId, type, feature.id()), p -> promptRenameDoorComponent(p, doorId, type, feature.id()), p -> openDeleteDoorComponentConfirm(p, doorId, type, feature.id()));
                if (slot >= 45) {
                    break;
                }
            }
            button(menu, 45, Material.HOPPER, "Multi-edit", List.of("Configure multiple feature slots at once."), p -> beginMultiEdit(p, MultiEditOwner.DOOR_TEMPLATE, doorId, MultiEditSlotType.FEATURE));
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openDoor(p, doorId));
        open(player, menu);
    }

    private void openDoorFeatureSlot(Player player, String doorId, String slotId) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        AuthoringSession activeEdit = doorEditInWorkspace(player, doorId);
        List<RoomFeatureSlot> featureSlots = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        RoomFeatureSlot featureSlot = featureSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature slot " + slotId));
        Menu menu = menu("da:door-feature-slot:" + doorId + ":" + slotId, 54, "Door Feature: " + slotId);
        button(menu, 4, Material.HOPPER, "Slot " + slotId, List.of("Size: " + featureSlot.size(), "Position: " + featureSlot.position()), p -> openDoorFeatureSlot(p, doorId, slotId));
        int slot = 9;
        if (featureSlot.entries().isEmpty()) {
            button(menu, 5, Material.YELLOW_CONCRETE, "No Entries Selected", List.of("This slot always pastes nothing."), p -> openDoorFeatureSlot(p, doorId, slotId));
        }
        button(menu, slot++, Material.BARRIER, "empty", entryLore(featureSlot, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> toggleDoorFeatureEntry(p, template, featureSlot, FeatureSlotEntry.EMPTY, 1), null, p -> promptDoorFeatureWeight(p, template, featureSlot, FeatureSlotEntry.EMPTY));
        List<FeatureTemplate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.all()) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            if (!match.matched()) {
                unavailable.add(feature);
                continue;
            }
            Rotation rotation = match.rotation();
            IntVector3 rotatedSize = rotation.rotateSize(feature.size());
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), entryLore(featureSlot, feature.id(), "Size: " + feature.size(), "Paste size: " + rotatedSize, "Offset: " + FeatureMatcher.placementOffset(featureSlot.size(), rotatedSize), "Rotation: " + rotation), p -> toggleDoorFeatureEntry(p, template, featureSlot, feature.id(), 1), null, p -> promptDoorFeatureWeight(p, template, featureSlot, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableFeatureEntries(menu, slot, featureSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openDoorComponents(p, doorId, "feature"));
        open(player, menu);
    }

    private void toggleDoorFeatureEntry(Player player, DoorTemplate template, RoomFeatureSlot slot, String featureId, int defaultWeight) {
        List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            entries.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
        saveDoorFeatureSlot(player, template, slot.withEntries(entries));
    }

    private void promptDoorFeatureWeight(Player player, DoorTemplate template, RoomFeatureSlot slot, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            int weight = Integer.parseInt(value);
            List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            entries.add(new FeatureSlotEntry(featureId, weight));
            saveDoorFeatureSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveDoorFeatureSlot(Player player, DoorTemplate template, RoomFeatureSlot updatedSlot) {
        AuthoringSession activeEdit = doorEditInWorkspace(player, template.id());
        if (activeEdit != null) {
            replaceFeatureSlot(activeEdit, updatedSlot);
            player.sendMessage(Component.text("Door feature slot updated in the edit workspace. Use Save Edit to persist it."));
            openDoorFeatureSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<RoomFeatureSlot> slots = new ArrayList<>(template.featureSlots());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveDoorTemplate(new DoorTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.lootBindings(), template.gateway(), template.structureFile()));
        AuthoringSession retainedEdit = retainedDoorEdit(player, template.id());
        if (retainedEdit != null) {
            replaceFeatureSlot(retainedEdit, updatedSlot);
        }
        player.sendMessage(Component.text("Door feature slot updated."));
        openDoorFeatureSlot(player, template.id(), updatedSlot.id());
    }

    private void beginMultiEdit(Player player, MultiEditOwner owner, String ownerId, MultiEditSlotType slotType) {
        MultiEditSession session = new MultiEditSession(owner, ownerId, slotType);
        multiEdits.put(player.getUniqueId(), session);
        openMultiEditSlotSelection(player);
    }

    private void openMultiEditSlotSelection(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        Menu menu = menu("da:multi-select:" + session.ownerId + ":" + session.slotType, 54, "Multi-edit " + session.slotType.label());
        int slotIndex = 0;
        if (session.slotType == MultiEditSlotType.DOOR) {
            for (DoorSocket slot : currentDoorSlots(player, session)) {
                boolean selected = session.selectedSlotIds.contains(slot.id().toLowerCase(Locale.ROOT));
                button(menu, slotIndex++, selected ? Material.EMERALD_BLOCK : Material.IRON_DOOR, slot.id(), multiDoorSlotLore(player, slot, selected, session), p -> {
                    toggleMultiEditSlot(p, slot.id());
                    openMultiEditSlotSelection(p);
                });
                if (slotIndex >= 45) {
                    break;
                }
            }
        } else if (session.slotType == MultiEditSlotType.FEATURE) {
            for (RoomFeatureSlot slot : currentFeatureSlots(player, session)) {
                boolean selected = session.selectedSlotIds.contains(slot.id().toLowerCase(Locale.ROOT));
                button(menu, slotIndex++, selected ? Material.EMERALD_BLOCK : Material.CHEST, slot.id(), multiFeatureSlotLore(player, slot, selected, session), p -> {
                    toggleMultiEditSlot(p, slot.id());
                    openMultiEditSlotSelection(p);
                });
                if (slotIndex >= 45) {
                    break;
                }
            }
        } else {
            for (RoomMarker marker : currentMarkers(session)) {
                boolean selected = session.selectedSlotIds.contains(marker.name().toLowerCase(Locale.ROOT));
                button(menu, slotIndex++, selected ? Material.EMERALD_BLOCK : Material.REDSTONE_TORCH, marker.name(), List.of(selected ? "Selected" : "Not selected", "Type: " + marker.type(), "Position: " + marker.position()), p -> {
                    toggleMultiEditSlot(p, marker.name());
                    openMultiEditSlotSelection(p);
                });
                if (slotIndex >= 45) {
                    break;
                }
            }
        }
        if (session.selectedSlotIds.isEmpty()) {
            button(menu, 53, Material.GRAY_CONCRETE, "Continue to config", List.of("Select at least one slot first."), p -> openMultiEditSlotSelection(p));
        } else {
            button(menu, 53, Material.EMERALD_BLOCK, "Continue to config", List.of("Selected slots: " + session.selectedSlotIds.size()), this::openMultiEditConfig);
        }
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        open(player, menu);
    }

    private void openMultiEditConfig(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        if (session.selectedSlotIds.isEmpty()) {
            openMultiEditSlotSelection(player);
            return;
        }
        if (session.slotType == MultiEditSlotType.DOOR) {
            openMultiEditDoorConfig(player, session);
        } else if (session.slotType == MultiEditSlotType.FEATURE) {
            openMultiEditFeatureConfig(player, session);
        } else {
            openMultiEditMarkerConfig(player, session);
        }
    }

    private void openMultiEditMarkerConfig(Player player, MultiEditSession session) {
        Menu menu = menu("da:multi-config:" + session.ownerId + ":marker", 54, "Multi-edit Markers");
        int slot = 0;
        button(menu, slot++, Material.BARRIER, "No Loot", List.of(session.markerLootBindingConfigured && session.markerLootBindingDraft == null ? "Selected" : "Remove loot from every selected marker."), p -> {
            session.markerLootBindingDraft = null;
            session.markerLootBindingConfigured = true;
            openMultiEditMarkerConfig(p, session);
        });
        for (LootTable table : lootRegistry.all()) {
            boolean selected = session.markerLootBindingDraft != null && table.id().equalsIgnoreCase(session.markerLootBindingDraft.tableId());
            button(menu, slot++, selected ? Material.EMERALD_BLOCK : Material.CHEST, table.id(), List.of(selected ? "Selected" : "Assign this table to every selected marker."), p -> {
                LootBinding prior = session.markerLootBindingDraft;
                session.markerLootBindingDraft = prior == null ? new LootBinding(table.id()) : new LootBinding(table.id(), prior.minimumRolls(), prior.maximumRolls());
                session.markerLootBindingConfigured = true;
                openMultiEditMarkerConfig(p, session);
            });
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 45, Material.ARROW, "Back to marker selection", List.of("Keep the selected loot binding."), this::openMultiEditSlotSelection);
        if (session.markerLootBindingDraft != null) {
            button(menu, 46, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "Min Rolls: " + session.markerLootBindingDraft.minimumRolls(), List.of("Applied to every selected marker."), p -> promptMultiMarkerRoll(p, true));
            button(menu, 47, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "Max Rolls: " + session.markerLootBindingDraft.maximumRolls(), List.of("Applied to every selected marker."), p -> promptMultiMarkerRoll(p, false));
        }
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        if (session.markerLootBindingConfigured) {
            button(menu, 53, Material.EMERALD_BLOCK, "Apply to selected markers", List.of("Selected markers: " + session.selectedSlotIds.size(), session.markerLootBindingDraft == null ? "Loot: none" : "Loot: " + session.markerLootBindingDraft.tableId() + " (" + session.markerLootBindingDraft.minimumRolls() + "-" + session.markerLootBindingDraft.maximumRolls() + " rolls)"), this::applyMultiEdit);
        } else {
            button(menu, 53, Material.GRAY_CONCRETE, "Choose a loot binding", List.of("Select a loot table or No Loot first."), p -> openMultiEditMarkerConfig(p, session));
        }
        open(player, menu);
    }

    private void promptMultiMarkerRoll(Player player, boolean minimum) {
        MultiEditSession session = requireMultiEdit(player);
        LootBinding binding = session.markerLootBindingDraft;
        if (binding == null) { openMultiEditMarkerConfig(player, session); return; }
        prompts.prompt(player, "Enter a non-negative " + (minimum ? "minimum" : "maximum") + " roll count", value -> {
            try {
                int rolls = Integer.parseInt(value.trim());
                session.markerLootBindingDraft = new LootBinding(binding.tableId(), minimum ? rolls : binding.minimumRolls(), minimum ? binding.maximumRolls() : rolls);
            } catch (RuntimeException ex) { player.sendMessage(Component.text("Edit failed: " + ex.getMessage())); }
            openMultiEditMarkerConfig(player, session);
        }, () -> openMultiEditMarkerConfig(player, session));
    }

    private void openMultiEditDoorConfig(Player player, MultiEditSession session) {
        List<DoorSocket> selectedSlots = selectedDoorSlots(player, session);
        initializeMultiDoorRuleDraft(session, selectedSlots);
        Menu menu = menu("da:multi-config:" + session.ownerId + ":door", 54, "Multi-edit Doors");
        button(menu, 0, Material.NAME_TAG, "Tags: " + String.join(",", session.doorTagsDraft), List.of("Select door tags.", "Applies to every selected door."), p -> promptMultiDoorTags(p));
        button(menu, 1, session.doorConnectionRulesDraft.mustConnect() ? Material.TRIPWIRE_HOOK : Material.GRAY_CONCRETE, "Must Connect: " + session.doorConnectionRulesDraft.mustConnect(), List.of("Applies to every selected door."), p -> toggleMultiDoorMustConnect(p));
        button(menu, 2, Material.LIME_DYE, "Accept Tags: " + String.join(",", session.doorConnectionRulesDraft.allowedTags()), List.of("Select opposite-door tags.", "Applies to every selected door."), p -> promptMultiDoorAllowedTags(p));
        button(menu, 3, Material.RED_DYE, "Reject Tags: " + String.join(",", session.doorConnectionRulesDraft.deniedTags()), List.of("Select opposite-door tags.", "Applies to every selected door."), p -> promptMultiDoorDeniedTags(p));
        button(menu, 4, Material.OAK_DOOR, "Draft Door Entries", List.of("Selected slots: " + selectedSlots.size(), "Draft entries: " + session.doorDraft.size(), session.doorEntriesTouched ? "Entry changes apply to every selected slot." : "Entries remain unchanged unless edited."), p -> openMultiEditConfig(p));
        button(menu, 6, Material.LIME_BANNER, "Accept Room Tags: " + String.join(",", session.doorConnectionRulesDraft.allowedRoomTags()), List.of("Select opposite-room tags.", "Applies to every selected door."), p -> promptMultiDoorAllowedRoomTags(p));
        button(menu, 7, Material.RED_BANNER, "Reject Room Tags: " + String.join(",", session.doorConnectionRulesDraft.deniedRoomTags()), List.of("Select opposite-room tags.", "Applies to every selected door."), p -> promptMultiDoorDeniedRoomTags(p));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", multiDoorEntryLore(session, DoorSlotEntry.EMPTY, "Virtual door; leaves room blueprint unchanged."), p -> {
            toggleMultiDoorDraft(p, DoorSlotEntry.EMPTY, 1);
            openMultiEditConfig(p);
        }, null, p -> promptMultiDoorWeight(p, DoorSlotEntry.EMPTY));
        List<UnavailableCandidate> unavailable = new ArrayList<>();
        for (DoorTemplate door : doorRegistry.visible()) {
            TemplateLoadStatus<DoorTemplate> status = doorRegistry.status(door.id()).orElse(null);
            List<String> conflicts = SlotMultiEditMatcher.doorConflicts(selectedSlots, door, status);
            if (!conflicts.isEmpty()) {
                unavailable.add(new UnavailableCandidate(door.id(), conflicts, "Bounds: " + DiagnosticText.size(door.size())));
                continue;
            }
            button(menu, slot++, Material.OAK_DOOR, door.id(), multiDoorEntryLore(session, door.id(), "Bounds: " + DiagnosticText.size(door.size()), "Matches all selected slots."), p -> {
                toggleMultiDoorDraft(p, door.id(), 1);
                openMultiEditConfig(p);
            }, null, p -> promptMultiDoorWeight(p, door.id()));
            if (slot >= 45) {
                break;
            }
        }
        for (TemplateLoadStatus<DoorTemplate> status : doorRegistry.loadStatuses()) {
            if (!status.loadable()) {
                unavailable.add(new UnavailableCandidate(status.id(), status.errors(), "Template could not be loaded."));
            }
        }
        slot = addUnavailableMultiEntries(menu, slot, unavailable, "doors");
        button(menu, 45, Material.ARROW, "Back to slot selection", List.of("Keep the current draft."), this::openMultiEditSlotSelection);
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        button(menu, 53, Material.EMERALD_BLOCK, "Apply to selected slots", multiDoorApplyLore(session, selectedSlots.size()), this::applyMultiEdit);
        open(player, menu);
    }

    private void openMultiEditFeatureConfig(Player player, MultiEditSession session) {
        List<RoomFeatureSlot> selectedSlots = selectedFeatureSlots(player, session);
        Menu menu = menu("da:multi-config:" + session.ownerId + ":feature", 54, "Multi-edit Features");
        button(menu, 4, Material.HOPPER, "Draft Feature Entries", List.of("Selected slots: " + selectedSlots.size(), "Draft entries: " + session.featureDraft.size(), "Apply replaces every selected slot."), p -> openMultiEditConfig(p));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", multiFeatureEntryLore(session, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> {
            toggleMultiFeatureDraft(p, FeatureSlotEntry.EMPTY, 1);
            openMultiEditConfig(p);
        }, null, p -> promptMultiFeatureWeight(p, FeatureSlotEntry.EMPTY));
        List<UnavailableCandidate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.visible()) {
            TemplateLoadStatus<FeatureTemplate> status = featureRegistry.status(feature.id()).orElse(null);
            List<String> conflicts = new ArrayList<>(SlotMultiEditMatcher.featureConflicts(selectedSlots, feature, status));
            if (conflicts.isEmpty() && session.owner == MultiEditOwner.FEATURE_TEMPLATE
                && session.featureDraft.stream().noneMatch(entry -> entry.featureId().equalsIgnoreCase(feature.id()))) {
                FeatureTemplate owner = featureRegistry.getVisible(session.ownerId).orElseThrow();
                List<FeatureSlotEntry> draft = new ArrayList<>(session.featureDraft);
                draft.add(new FeatureSlotEntry(feature.id(), 1));
                List<RoomFeatureSlot> prospectiveSlots = currentFeatureSlots(player, session).stream()
                    .map(candidateSlot -> selected(session, candidateSlot.id()) ? candidateSlot.withEntries(draft) : candidateSlot)
                    .toList();
                FeatureTemplate prospective = new FeatureTemplate(owner.id(), owner.size(), owner.tags(), owner.markers(), prospectiveSlots, owner.lootBindings(), owner.structureFile());
                conflicts.addAll(featureRegistry.validateProspective(prospective).errors());
            }
            if (!conflicts.isEmpty()) {
                unavailable.add(new UnavailableCandidate(feature.id(), conflicts, "Size: " + DiagnosticText.size(feature.size())));
                continue;
            }
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), multiFeatureEntryLore(session, feature.id(), "Size: " + DiagnosticText.size(feature.size()), "Matches all selected slots."), p -> {
                toggleMultiFeatureDraft(p, feature.id(), 1);
                openMultiEditConfig(p);
            }, null, p -> promptMultiFeatureWeight(p, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        for (TemplateLoadStatus<FeatureTemplate> status : featureRegistry.loadStatuses()) {
            if (!status.loadable()) {
                unavailable.add(new UnavailableCandidate(status.id(), status.errors(), "Template could not be loaded."));
            }
        }
        slot = addUnavailableMultiEntries(menu, slot, unavailable, "features");
        button(menu, 45, Material.ARROW, "Back to slot selection", List.of("Keep the current draft."), this::openMultiEditSlotSelection);
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        button(menu, 53, Material.EMERALD_BLOCK, "Apply to selected slots", List.of("Replaces entries on " + selectedSlots.size() + " slot(s).", "Draft entries: " + session.featureDraft.size()), this::applyMultiEdit);
        open(player, menu);
    }

    private int addUnavailableMultiEntries(Menu menu, int slot, List<UnavailableCandidate> unavailable, String label) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These " + label + " do not match every selected slot."), p -> {});
        for (UnavailableCandidate candidate : unavailable) {
            if (slot >= 45) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("Unavailable for selected slots.");
            lore.add(candidate.summary);
            candidate.conflicts.stream().limit(4).forEach(lore::add);
            button(menu, slot++, Material.GRAY_CONCRETE, candidate.id, lore, p -> {
                p.sendMessage(Component.text(candidate.id + " conflicts:"));
                for (String conflict : candidate.conflicts) {
                    p.sendMessage(Component.text("- " + conflict));
                }
            });
        }
        return slot;
    }

    private void applyMultiEdit(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        if (session.selectedSlotIds.isEmpty()) {
            openMultiEditSlotSelection(player);
            return;
        }
        if (session.slotType == MultiEditSlotType.DOOR) {
            applyMultiDoorEdit(player, session);
        } else if (session.slotType == MultiEditSlotType.FEATURE && session.owner == MultiEditOwner.ROOM) {
            applyMultiRoomFeatureEdit(player, session);
        } else if (session.slotType == MultiEditSlotType.FEATURE && session.owner == MultiEditOwner.FEATURE_TEMPLATE) {
            applyMultiNestedFeatureEdit(player, session);
        } else if (session.slotType == MultiEditSlotType.FEATURE) {
            applyMultiDoorFeatureEdit(player, session);
        } else {
            applyMultiMarkerEdit(player, session);
        }
        multiEdits.remove(player.getUniqueId());
    }

    private void applyMultiDoorEdit(Player player, MultiEditSession session) {
        RoomTemplate template = templateRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + session.ownerId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            for (DoorSocket slot : activeEdit.doors()) {
                if (selected(session, slot.id())) {
                    activeEdit.removeDoor(slot.id());
                    activeEdit.addDoorSlot(applyMultiDoorDraft(slot, session));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door slots in the active edit session."));
            openComponents(player, template.id(), "door");
            return;
        }
        List<DoorSocket> slots = template.doors().stream()
            .map(slot -> selected(session, slot.id()) ? applyMultiDoorDraft(slot, session) : slot)
            .toList();
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), slots, template.markers(), template.featureSlots(), template.structureFile()));
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door slots."));
        openComponents(player, template.id(), "door");
    }

    private void applyMultiRoomFeatureEdit(Player player, MultiEditSession session) {
        RoomTemplate template = templateRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + session.ownerId));
        AuthoringSession activeEdit = roomEditInWorkspace(player, template.id());
        List<FeatureSlotEntry> entries = List.copyOf(session.featureDraft);
        if (activeEdit != null) {
            for (RoomFeatureSlot slot : activeEdit.featureSlots()) {
                if (selected(session, slot.id())) {
                    replaceFeatureSlot(activeEdit, slot.withEntries(entries));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " feature slots in the edit workspace. Use Save Edit to persist them."));
            openComponents(player, template.id(), "feature");
            return;
        }
        List<RoomFeatureSlot> slots = template.featureSlots().stream()
            .map(slot -> selected(session, slot.id()) ? slot.withEntries(entries) : slot)
            .toList();
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.lootBindings(), template.structureFile()));
        AuthoringSession retainedEdit = retainedRoomEdit(player, template.id());
        if (retainedEdit != null) {
            slots.stream().filter(slot -> selected(session, slot.id())).forEach(slot -> replaceFeatureSlot(retainedEdit, slot));
        }
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " feature slots."));
        openComponents(player, template.id(), "feature");
    }

    private void applyMultiDoorFeatureEdit(Player player, MultiEditSession session) {
        DoorTemplate template = doorRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + session.ownerId));
        AuthoringSession activeEdit = doorEditInWorkspace(player, template.id());
        List<FeatureSlotEntry> entries = List.copyOf(session.featureDraft);
        if (activeEdit != null) {
            for (RoomFeatureSlot slot : activeEdit.featureSlots()) {
                if (selected(session, slot.id())) {
                    replaceFeatureSlot(activeEdit, slot.withEntries(entries));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door feature slots in the edit workspace. Use Save Edit to persist them."));
            openDoorComponents(player, template.id(), "feature");
            return;
        }
        List<RoomFeatureSlot> slots = template.featureSlots().stream()
            .map(slot -> selected(session, slot.id()) ? slot.withEntries(entries) : slot)
            .toList();
        saveDoorTemplate(new DoorTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.lootBindings(), template.gateway(), template.structureFile()));
        AuthoringSession retainedEdit = retainedDoorEdit(player, template.id());
        if (retainedEdit != null) {
            slots.stream().filter(slot -> selected(session, slot.id())).forEach(slot -> replaceFeatureSlot(retainedEdit, slot));
        }
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door feature slots."));
        openDoorComponents(player, template.id(), "feature");
    }

    private void applyMultiNestedFeatureEdit(Player player, MultiEditSession session) {
        FeatureTemplate template = featureRegistry.getVisible(session.ownerId).orElseThrow();
        AuthoringSession activeEdit = authoringManager.editingFeatureSessionInWorkspace(player, template.id()).orElse(null);
        List<RoomFeatureSlot> source = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        List<RoomFeatureSlot> slots = source.stream()
            .map(slot -> selected(session, slot.id()) ? slot.withEntries(List.copyOf(session.featureDraft)) : slot)
            .toList();
        FeatureTemplate prospective = new FeatureTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.lootBindings(), template.structureFile());
        TemplateValidationResult safety = featureRegistry.validateProspective(prospective);
        if (!safety.valid()) throw new IllegalArgumentException(safety.errors().getFirst());
        if (activeEdit != null) {
            slots.stream().filter(slot -> selected(session, slot.id())).forEach(slot -> replaceFeatureSlot(activeEdit, slot));
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " nested feature slots in the edit workspace. Use Save Edit to persist them."));
        } else {
            saveFeatureTemplate(prospective);
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " nested feature slots."));
        }
        openNestedFeatureSlots(player, template.id());
    }

    private void applyMultiMarkerEdit(Player player, MultiEditSession session) {
        if (!session.markerLootBindingConfigured) {
            openMultiEditMarkerConfig(player, session);
            return;
        }
        switch (session.owner) {
            case ROOM -> {
                RoomTemplate template = templateRegistry.getVisible(session.ownerId).orElseThrow(() -> new IllegalArgumentException("Unknown room " + session.ownerId));
                Map<String, LootBinding> bindings = updatedMarkerBindings(template.lootBindings(), session);
                saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), bindings, template.structureFile()));
                authoringManager.synchronizeRoomLootBindings(template.id(), bindings);
                openComponents(player, template.id(), "marker");
            }
            case DOOR_TEMPLATE -> {
                DoorTemplate template = doorRegistry.getVisible(session.ownerId).orElseThrow(() -> new IllegalArgumentException("Unknown door " + session.ownerId));
                Map<String, LootBinding> bindings = updatedMarkerBindings(template.lootBindings(), session);
                saveDoorTemplate(new DoorTemplate(template.id(), template.size(), template.tags(), template.markers(), template.featureSlots(), bindings, template.gateway(), template.structureFile()));
                authoringManager.synchronizeDoorLootBindings(template.id(), bindings);
                openDoorComponents(player, template.id(), "marker");
            }
            case FEATURE_TEMPLATE -> {
                FeatureTemplate template = featureRegistry.getVisible(session.ownerId).orElseThrow(() -> new IllegalArgumentException("Unknown feature " + session.ownerId));
                Map<String, LootBinding> bindings = updatedMarkerBindings(template.lootBindings(), session);
                saveFeatureTemplate(new FeatureTemplate(template.id(), template.size(), template.tags(), template.markers(), template.featureSlots(), bindings, template.structureFile()));
                authoringManager.synchronizeFeatureLootBindings(template.id(), bindings);
                openFeatureMarkers(player, template.id());
            }
        }
        player.sendMessage(Component.text("Updated loot for " + session.selectedSlotIds.size() + " markers."));
    }

    private Map<String, LootBinding> updatedMarkerBindings(Map<String, LootBinding> existing, MultiEditSession session) {
        Map<String, LootBinding> bindings = new LinkedHashMap<>(existing);
        for (RoomMarker marker : currentMarkers(session)) {
            if (!selected(session, marker.name())) {
                continue;
            }
            if (session.markerLootBindingDraft == null) {
                bindings.remove(marker.name());
            } else {
                bindings.put(marker.name(), session.markerLootBindingDraft);
            }
        }
        return bindings;
    }

    private void cancelMultiEdit(Player player) {
        MultiEditSession session = multiEdits.remove(player.getUniqueId());
        if (session == null) {
            openMain(player);
            return;
        }
        if (session.slotType == MultiEditSlotType.MARKER) {
            switch (session.owner) {
                case ROOM -> openComponents(player, session.ownerId, "marker");
                case DOOR_TEMPLATE -> openDoorComponents(player, session.ownerId, "marker");
                case FEATURE_TEMPLATE -> openFeatureMarkers(player, session.ownerId);
            }
        } else if (session.owner == MultiEditOwner.ROOM) {
            openComponents(player, session.ownerId, session.slotType == MultiEditSlotType.DOOR ? "door" : "feature");
        } else if (session.owner == MultiEditOwner.DOOR_TEMPLATE) {
            openDoorComponents(player, session.ownerId, "feature");
        } else {
            openNestedFeatureSlots(player, session.ownerId);
        }
    }

    private void toggleMultiEditSlot(Player player, String slotId) {
        MultiEditSession session = requireMultiEdit(player);
        String normalized = slotId.toLowerCase(Locale.ROOT);
        if (!session.selectedSlotIds.remove(normalized)) {
            session.selectedSlotIds.add(normalized);
        }
    }

    private void toggleMultiDoorDraft(Player player, String doorId, int defaultWeight) {
        MultiEditSession session = requireMultiEdit(player);
        session.doorEntriesTouched = true;
        boolean removed = session.doorDraft.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
        if (!removed) {
            session.doorDraft.add(new DoorSlotEntry(doorId, defaultWeight));
        }
    }

    private void toggleMultiFeatureDraft(Player player, String featureId, int defaultWeight) {
        MultiEditSession session = requireMultiEdit(player);
        boolean removed = session.featureDraft.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            session.featureDraft.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
    }

    private void promptMultiDoorWeight(Player player, String doorId) {
        prompts.prompt(player, "Enter weight for " + doorId, value -> {
            MultiEditSession session = requireMultiEdit(player);
            session.doorEntriesTouched = true;
            int weight = Integer.parseInt(value);
            session.doorDraft.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
            session.doorDraft.add(new DoorSlotEntry(doorId, weight));
            openMultiEditConfig(player);
        });
    }

    private void initializeMultiDoorRuleDraft(MultiEditSession session, List<DoorSocket> selectedSlots) {
        if (session.doorRuleDraftInitialized || selectedSlots.isEmpty()) {
            return;
        }
        DoorSocket source = selectedSlots.getFirst();
        session.doorTagsDraft.addAll(source.tags());
        session.doorConnectionRulesDraft = source.connectionRules();
        session.doorRuleDraftInitialized = true;
    }

    private void promptMultiDoorTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Door Slot Tags", session.doorTagsDraft, tags -> {
            session.doorTagsDraft.clear();
            session.doorTagsDraft.addAll(tags);
            session.doorTagsTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void toggleMultiDoorMustConnect(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        DoorConnectionRules rules = session.doorConnectionRulesDraft;
        session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), !rules.mustConnect());
        session.doorConnectionRulesTouched = true;
        openMultiEditConfig(player);
    }

    private void promptMultiDoorAllowedTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Accept Door Tags", session.doorConnectionRulesDraft.allowedTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(tags, rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorDeniedTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Reject Door Tags", session.doorConnectionRulesDraft.deniedTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), tags, rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorAllowedRoomTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.ROOM, "Accept Room Tags", session.doorConnectionRulesDraft.allowedRoomTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), tags, rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorDeniedRoomTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.ROOM, "Reject Room Tags", session.doorConnectionRulesDraft.deniedRoomTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), tags, rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private DoorSocket applyMultiDoorDraft(DoorSocket slot, MultiEditSession session) {
        DoorSocket updated = slot;
        if (session.doorEntriesTouched) {
            updated = updated.withEntries(session.doorDraft);
        }
        if (session.doorTagsTouched) {
            updated = updated.withTags(session.doorTagsDraft);
        }
        if (session.doorConnectionRulesTouched) {
            updated = updated.withConnectionRules(session.doorConnectionRulesDraft);
        }
        return updated;
    }

    private List<String> multiDoorApplyLore(MultiEditSession session, int selectedCount) {
        List<String> lore = new ArrayList<>();
        lore.add("Selected slots: " + selectedCount);
        lore.add("Entries: " + (session.doorEntriesTouched ? "updated" : "unchanged"));
        lore.add("Tags: " + (session.doorTagsTouched ? "updated" : "unchanged"));
        lore.add("Connection rules: " + (session.doorConnectionRulesTouched ? "updated" : "unchanged"));
        return lore;
    }

    private void promptMultiFeatureWeight(Player player, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            MultiEditSession session = requireMultiEdit(player);
            int weight = Integer.parseInt(value);
            session.featureDraft.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            session.featureDraft.add(new FeatureSlotEntry(featureId, weight));
            openMultiEditConfig(player);
        });
    }

    private List<String> multiDoorEntryLore(MultiEditSession session, String doorId, String... extra) {
        List<String> lore = new ArrayList<>();
        session.doorDraft.stream()
            .filter(entry -> entry.doorId().equalsIgnoreCase(doorId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits shared weight.");
        return lore;
    }

    private List<String> multiFeatureEntryLore(MultiEditSession session, String featureId, String... extra) {
        List<String> lore = new ArrayList<>();
        session.featureDraft.stream()
            .filter(entry -> entry.featureId().equalsIgnoreCase(featureId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits shared weight.");
        return lore;
    }

    private List<String> multiDoorSlotLore(Player player, DoorSocket slot, boolean selected, MultiEditSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "Selected" : "Not selected");
        lore.add("Size: " + DiagnosticText.size(slot.size()));
        lore.add("Facing: " + slot.facing());
        lore.add("Current entries: " + slot.entries().size());
        if (mixedDoorEntries(slot, currentDoorSlots(player, session))) {
            lore.add("Config differs from other slots.");
        }
        return lore;
    }

    private List<String> multiFeatureSlotLore(Player player, RoomFeatureSlot slot, boolean selected, MultiEditSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "Selected" : "Not selected");
        lore.add("Size: " + DiagnosticText.size(slot.size()));
        lore.add("Current entries: " + slot.entries().size());
        if (mixedFeatureEntries(slot, currentFeatureSlots(player, session))) {
            lore.add("Config differs from other slots.");
        }
        return lore;
    }

    private boolean mixedDoorEntries(DoorSocket slot, List<DoorSocket> slots) {
        return slots.stream().anyMatch(other -> !other.id().equalsIgnoreCase(slot.id()) && !other.entries().equals(slot.entries()));
    }

    private boolean mixedFeatureEntries(RoomFeatureSlot slot, List<RoomFeatureSlot> slots) {
        return slots.stream().anyMatch(other -> !other.id().equalsIgnoreCase(slot.id()) && !other.entries().equals(slot.entries()));
    }

    private AuthoringSession roomEditInWorkspace(Player player, String roomId) {
        return authoringManager.editingSessionInWorkspace(player, roomId).orElse(null);
    }

    private AuthoringSession doorEditInWorkspace(Player player, String doorId) {
        return authoringManager.editingDoorSessionInWorkspace(player, doorId).orElse(null);
    }

    private AuthoringSession retainedRoomEdit(Player player, String roomId) {
        return authoringManager.editingSession(player, roomId).orElse(null);
    }

    private AuthoringSession retainedDoorEdit(Player player, String doorId) {
        return authoringManager.editingDoorSession(player, doorId).orElse(null);
    }

    private static void replaceFeatureSlot(AuthoringSession session, RoomFeatureSlot updatedSlot) {
        session.removeFeature(updatedSlot.id());
        session.addFeatureSlot(updatedSlot);
    }

    private List<DoorSocket> currentDoorSlots(Player player, MultiEditSession session) {
        if (session.owner != MultiEditOwner.ROOM) {
            return List.of();
        }
        if (player != null) {
            AuthoringSession activeEdit = authoringManager.editingSession(player, session.ownerId).orElse(null);
            if (activeEdit != null) {
                return activeEdit.doors();
            }
        }
        return templateRegistry.getVisible(session.ownerId).map(RoomTemplate::doors).orElse(List.of());
    }

    private List<RoomFeatureSlot> currentFeatureSlots(Player player, MultiEditSession session) {
        if (session.owner == MultiEditOwner.ROOM) {
            if (player != null && authoringManager.isInEditWorld(player)) {
                AuthoringSession activeEdit = retainedRoomEdit(player, session.ownerId);
                if (activeEdit != null) {
                    return activeEdit.featureSlots();
                }
            }
            return templateRegistry.getVisible(session.ownerId).map(RoomTemplate::featureSlots).orElse(List.of());
        }
        if (session.owner == MultiEditOwner.FEATURE_TEMPLATE) {
            if (player != null && authoringManager.isInEditWorld(player)) {
                AuthoringSession activeEdit = authoringManager.editingFeatureSession(player, session.ownerId).orElse(null);
                if (activeEdit != null) return activeEdit.featureSlots();
            }
            return featureRegistry.getVisible(session.ownerId).map(FeatureTemplate::featureSlots).orElse(List.of());
        }
        if (player != null && authoringManager.isInEditWorld(player)) {
            AuthoringSession activeEdit = retainedDoorEdit(player, session.ownerId);
            if (activeEdit != null) {
                return activeEdit.featureSlots();
            }
        }
        return doorRegistry.getVisible(session.ownerId).map(DoorTemplate::featureSlots).orElse(List.of());
    }

    private List<RoomMarker> currentMarkers(MultiEditSession session) {
        return switch (session.owner) {
            case ROOM -> templateRegistry.getVisible(session.ownerId).map(RoomTemplate::markers).orElse(List.of());
            case DOOR_TEMPLATE -> doorRegistry.getVisible(session.ownerId).map(DoorTemplate::markers).orElse(List.of());
            case FEATURE_TEMPLATE -> featureRegistry.getVisible(session.ownerId).map(FeatureTemplate::markers).orElse(List.of());
        };
    }

    private List<DoorSocket> selectedDoorSlots(Player player, MultiEditSession session) {
        return currentDoorSlots(player, session).stream().filter(slot -> selected(session, slot.id())).toList();
    }

    private List<RoomFeatureSlot> selectedFeatureSlots(Player player, MultiEditSession session) {
        return currentFeatureSlots(player, session).stream().filter(slot -> selected(session, slot.id())).toList();
    }

    private boolean selected(MultiEditSession session, String slotId) {
        return session.selectedSlotIds.contains(slotId.toLowerCase(Locale.ROOT));
    }

    private MultiEditSession requireMultiEdit(Player player) {
        MultiEditSession session = multiEdits.get(player.getUniqueId());
        if (session == null) {
            throw new IllegalArgumentException("No active multi-edit session");
        }
        return session;
    }

    private void selectDoorComponent(Player player, String doorId, String type, String id) {
        if (authoringManager.editingDoorSession(player, doorId).isEmpty()) {
            player.sendMessage(Component.text("Paste this door for editing before inspecting components."));
            openDoor(player, doorId);
            return;
        }
        var selection = authoringManager.selectComponent(player, type, id);
        player.closeInventory();
        player.sendMessage(Component.text("Selected " + type + " " + id + ": " + selection.worldBounds().describe()));
    }

    private void openDeleteDoorComponentConfirm(Player player, String doorId, String type, String id) {
        Menu menu = menu("da:delete-door-component:" + doorId + ":" + type + ":" + id, 27, "Delete Component?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Deletes " + type + " " + id), p -> {
            boolean removed;
            AuthoringSession activeEdit = authoringManager.editingDoorSession(p, doorId).orElse(null);
            if (activeEdit != null) {
                removed = type.equals("marker") ? activeEdit.removeMarker(id) : activeEdit.removeFeature(id);
            } else {
                DoorTemplate template = doorRegistry.getVisible(doorId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
                DoorTemplate updated = removeDoorTemplateComponent(template, type, id);
                removed = updated != template;
                if (removed) {
                    saveDoorTemplate(updated);
                }
            }
            p.sendMessage(Component.text(removed ? "Deleted " + type + " " + id + "." : "No matching " + type + " named " + id + "."));
            openDoorComponents(p, doorId, type);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDoorComponents(p, doorId, type));
        open(player, menu);
    }

    private void promptRenameDoorComponent(Player player, String doorId, String type, String oldId) {
        prompts.prompt(player, "Enter new " + type + " id", value -> {
            try {
                String newId = value.trim();
                boolean renamed;
                AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
                if (activeEdit != null) {
                    renamed = type.equals("marker") ? activeEdit.renameMarker(oldId, newId) : activeEdit.renameFeatureSlot(oldId, newId);
                    if (renamed) {
                        authoringManager.selectComponent(player, type, newId);
                    }
                } else {
                    DoorTemplate template = doorRegistry.getVisible(doorId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
                    DoorTemplate updated = renameDoorTemplateComponent(template, type, oldId, newId);
                    renamed = updated != template;
                    if (renamed) {
                        saveDoorTemplate(updated);
                    }
                }
                player.sendMessage(Component.text(renamed ? "Renamed " + type + " " + oldId + " to " + newId + "." : "No matching " + type + " named " + oldId + "."));
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
            }
            openDoorComponents(player, doorId, type);
        });
    }

    private DoorTemplate removeDoorTemplateComponent(DoorTemplate template, String type, String id) {
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean removed = switch (type) {
            case "marker" -> markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
            case "feature" -> features.removeIf(slot -> slot.id().equalsIgnoreCase(id));
            default -> throw new IllegalArgumentException("Unknown door component type " + type);
        };
        if (!removed) {
            return template;
        }
        Map<String, LootBinding> bindings = new LinkedHashMap<>(template.lootBindings());
        if (type.equals("marker")) bindings.keySet().removeIf(name -> name.equalsIgnoreCase(id));
        return new DoorTemplate(template.id(), template.size(), template.tags(), markers, features, bindings, template.gateway(), template.structureFile());
    }

    private DoorTemplate renameDoorTemplateComponent(DoorTemplate template, String type, String oldId, String newId) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("New id is required");
        }
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        IdentityRules.requireDoorComponentAvailable(newId, markers, features, oldId);
        boolean renamed = switch (type) {
            case "marker" -> {
                boolean found = false;
                for (int i = 0; i < markers.size(); i++) {
                    RoomMarker marker = markers.get(i);
                    if (marker.name().equalsIgnoreCase(oldId)) {
                        markers.set(i, new RoomMarker(newId, marker.type(), marker.position()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "feature" -> {
                boolean found = false;
                for (int i = 0; i < features.size(); i++) {
                    RoomFeatureSlot slot = features.get(i);
                    if (slot.id().equalsIgnoreCase(oldId)) {
                        features.set(i, new RoomFeatureSlot(newId, slot.position(), slot.size(), slot.facing(), slot.entries()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            default -> throw new IllegalArgumentException("Unknown door component type " + type);
        };
        if (!renamed) {
            return template;
        }
        Map<String, LootBinding> bindings = new LinkedHashMap<>(template.lootBindings());
        if (type.equals("marker")) {
            bindings.keySet().stream().filter(key -> key.equalsIgnoreCase(oldId)).findFirst().ifPresent(key -> {
                LootBinding binding = bindings.remove(key);
                bindings.put(newId, binding);
            });
        }
        return new DoorTemplate(template.id(), template.size(), template.tags(), markers, features, bindings, template.gateway(), template.structureFile());
    }

    private void promptRenameRoom(Player player, String roomId) {
        prompts.prompt(player, "Enter new room id", value -> {
            try {
                var renamed = assetRenameCoordinator.renameRoom(roomId, value.trim());
                authoringManager.renameActiveRoomId(player, roomId, renamed.id());
                player.sendMessage(Component.text("Renamed room " + roomId + " to " + renamed.id() + "."));
                openRoom(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openRoom(player, roomId);
            }
        });
    }

    private void promptDuplicateRoom(Player player, String roomId) {
        prompts.prompt(player, "Enter duplicate room id", value -> {
            try {
                var duplicated = templateRegistry.duplicateRoom(roomId, value.trim());
                player.sendMessage(Component.text("Duplicated room " + roomId + " to " + duplicated.id() + "."));
                openRoom(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openRoom(player, roomId);
            }
        });
    }

    private void promptRenameFeature(Player player, String featureId) {
        prompts.prompt(player, "Enter new feature id", value -> {
            try {
                var renamed = assetRenameCoordinator.renameFeature(featureId, value.trim());
                authoringManager.renameActiveFeatureId(player, featureId, renamed.id());
                player.sendMessage(Component.text("Renamed feature " + featureId + " to " + renamed.id() + "."));
                openFeature(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openFeature(player, featureId);
            }
        });
    }

    private void promptDuplicateFeature(Player player, String featureId) {
        prompts.prompt(player, "Enter duplicate feature id", value -> {
            try {
                var duplicated = featureRegistry.duplicateFeature(featureId, value.trim());
                templateRegistry.reload();
                player.sendMessage(Component.text("Duplicated feature " + featureId + " to " + duplicated.id() + "."));
                openFeature(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openFeature(player, featureId);
            }
        });
    }

    private void promptRenameDoor(Player player, String doorId) {
        prompts.prompt(player, "Enter new door id", value -> {
            try {
                var renamed = assetRenameCoordinator.renameDoor(doorId, value.trim());
                authoringManager.renameActiveDoorId(player, doorId, renamed.id());
                player.sendMessage(Component.text("Renamed door " + doorId + " to " + renamed.id() + "."));
                openDoor(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openDoor(player, doorId);
            }
        });
    }

    private void promptDuplicateDoor(Player player, String doorId) {
        prompts.prompt(player, "Enter duplicate door id", value -> {
            try {
                var duplicated = doorRegistry.duplicateDoor(doorId, value.trim());
                templateRegistry.reload();
                player.sendMessage(Component.text("Duplicated door " + doorId + " to " + duplicated.id() + "."));
                openDoor(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openDoor(player, doorId);
            }
        });
    }

    private void openDeleteRoomConfirm(Player player, String roomId) {
        Menu menu = menu("da:delete-room:" + roomId, 27, "Delete Room?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + roomId), p -> {
            try {
                templateRegistry.deleteRoom(roomId);
                assetRenameCoordinator.reloadAll();
                p.sendMessage(Component.text("Deleted room " + roomId));
                openRooms(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openRoom(p, roomId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openRoom(p, roomId));
        open(player, menu);
    }

    public void openFeatures(Player player) {
        Menu menu = menu("da:features", 54, "DungeonArchitect Features");
        int slot = 0;
        for (TemplateLoadStatus<FeatureTemplate> status : featureRegistry.loadStatuses()) {
            FeatureTemplate template = status.template();
            List<String> lore = loadStatusLore(status, template == null ? "Template could not be loaded." : "Size: " + template.size(), template == null ? "Fix the errors and reload." : "Tags: " + String.join(",", template.tags()), template == null ? "" : "Click to edit.");
            button(menu, slot++, statusValid(status) ? Material.STRUCTURE_BLOCK : Material.RED_CONCRETE, status.id(), lore, p -> openOrExplainInvalid(p, "feature", status.id(), status.errors(), template == null ? null : () -> openFeature(p, template.id())));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openFeature(Player player, String featureId) {
        FeatureTemplate template = featureRegistry.getVisible(featureId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature " + featureId));
        TemplateLoadStatus<FeatureTemplate> status = featureRegistry.status(template.id()).orElse(null);
        Menu menu = menu("da:feature-template:" + featureId, 54, "Feature: " + featureId);
        button(menu, 4, statusValid(status) ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "Size: " + template.size(), loadStatusLore(status, "Captured feature footprint."), p -> openFeature(p, featureId));
        button(menu, 12, Material.OAK_SIGN, "Tags: " + String.join(",", template.tags()), List.of("Comma separated tags."), p -> prompts.prompt(p, "Enter comma-separated tags", value -> {
            Set<String> tags = new LinkedHashSet<>();
            for (String tag : value.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.trim());
                }
            }
            saveFeatureTemplate(new FeatureTemplate(template.id(), template.size(), tags, template.markers(), template.featureSlots(), template.lootBindings(), template.structureFile()));
            p.sendMessage(Component.text("Feature tags updated."));
            openFeature(p, featureId);
        }));
        button(menu, 20, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this feature into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editFeatureSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted feature " + template.id() + " into the edit world."));
            });
        });
        button(menu, 22, authoringManager.activeFeatureId(player).filter(id -> id.equalsIgnoreCase(featureId)).isPresent() ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this feature from the edit world."), p -> {
            try {
                TemplateValidationResult result = authoringManager.saveFeature(p, featureId);
                sendValidation(p, result);
                featureRegistry.reload();
                templateRegistry.reload();
                openFeature(p, featureId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openFeature(p, featureId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Feature edit session cancelled."));
            openFeature(p, featureId);
        });
        button(menu, 28, Material.REDSTONE_TORCH, "Markers: " + template.markers().size(), template.markers().stream().map(RoomMarker::name).toList(), p -> openFeatureMarkers(p, featureId));
        button(menu, 30, Material.CHEST, "Feature Slots: " + template.featureSlots().size(), template.featureSlots().stream().map(RoomFeatureSlot::id).toList(), p -> openNestedFeatureSlots(p, featureId));
        button(menu, 36, Material.NAME_TAG, "Rename Feature", List.of("Move this feature template to a new id."), p -> promptRenameFeature(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Feature", List.of("Copy this feature template to a new id."), p -> promptDuplicateFeature(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Feature", List.of("Permanently delete this feature template."), p -> openDeleteFeatureConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openFeatures);
        open(player, menu);
    }

    private void openNestedFeatureSlots(Player player, String featureId) {
        FeatureTemplate template = featureRegistry.getVisible(featureId).orElseThrow();
        AuthoringSession activeEdit = authoringManager.editingFeatureSessionInWorkspace(player, featureId).orElse(null);
        List<RoomFeatureSlot> slots = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        Menu menu = menu("da:nested-feature-slots:" + featureId, 54, "Feature Slots: " + featureId);
        int index = 0;
        for (RoomFeatureSlot slot : slots) {
            button(menu, index++, Material.CHEST, slot.id(), List.of("Position: " + slot.position(), "Size: " + slot.size(), "Entries: " + slot.entries().size(), "Click to configure.", "Right click to select in edit world."), p -> openNestedFeatureSlot(p, featureId, slot.id()), p -> {
                try { authoringManager.highlightComponent(p, "feature", slot.id()); }
                catch (RuntimeException ex) { p.sendMessage(Component.text(ex.getMessage())); }
                openNestedFeatureSlots(p, featureId);
            });
            if (index >= 45) break;
        }
        button(menu, 45, Material.HOPPER, "Multi-edit", List.of("Configure multiple nested feature slots."), p -> beginMultiEdit(p, MultiEditOwner.FEATURE_TEMPLATE, featureId, MultiEditSlotType.FEATURE));
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openFeature(p, featureId));
        open(player, menu);
    }

    private void openNestedFeatureSlot(Player player, String ownerId, String slotId) {
        FeatureTemplate owner = featureRegistry.getVisible(ownerId).orElseThrow();
        AuthoringSession activeEdit = authoringManager.editingFeatureSessionInWorkspace(player, ownerId).orElse(null);
        List<RoomFeatureSlot> slots = activeEdit == null ? owner.featureSlots() : activeEdit.featureSlots();
        RoomFeatureSlot featureSlot = slots.stream().filter(slot -> slot.id().equalsIgnoreCase(slotId)).findFirst().orElseThrow();
        Menu menu = menu("da:nested-feature-slot:" + ownerId + ":" + slotId, 54, "Nested Feature: " + slotId);
        button(menu, 4, Material.HOPPER, "Slot " + slotId, List.of("Size: " + featureSlot.size(), "Position: " + featureSlot.position()), p -> openNestedFeatureSlot(p, ownerId, slotId));
        int index = 9;
        button(menu, index++, Material.BARRIER, "empty", entryLore(featureSlot, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> toggleNestedFeatureEntry(p, owner, featureSlot, FeatureSlotEntry.EMPTY, 1), null, p -> promptNestedFeatureWeight(p, owner, featureSlot, FeatureSlotEntry.EMPTY));
        for (FeatureTemplate candidate : featureRegistry.visible()) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, candidate);
            if (!match.matched()) {
                button(menu, index++, Material.GRAY_CONCRETE, candidate.id(), List.of("Unavailable: " + match.reason()), p -> p.sendMessage(Component.text(match.reason())));
            } else {
                RoomFeatureSlot toggled = toggled(featureSlot, candidate.id(), 1);
                List<String> safety = prospectiveFeature(owner, slots, toggled).errors();
                boolean removal = featureSlot.entries().stream().anyMatch(entry -> entry.featureId().equalsIgnoreCase(candidate.id()));
                if (!removal && !safety.isEmpty()) {
                    button(menu, index++, Material.GRAY_CONCRETE, candidate.id(), List.of("Unsafe nesting:", safety.getFirst()), p -> p.sendMessage(Component.text(safety.getFirst())));
                } else {
                    Rotation rotation = match.rotation();
                    button(menu, index++, Material.STRUCTURE_BLOCK, candidate.id(), entryLore(featureSlot, candidate.id(), "Size: " + candidate.size(), "Rotation: " + rotation), p -> toggleNestedFeatureEntry(p, owner, featureSlot, candidate.id(), 1), null, p -> promptNestedFeatureWeight(p, owner, featureSlot, candidate.id()));
                }
            }
            if (index >= 45) break;
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openNestedFeatureSlots(p, ownerId));
        open(player, menu);
    }

    private RoomFeatureSlot toggled(RoomFeatureSlot slot, String featureId, int defaultWeight) {
        List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) entries.add(new FeatureSlotEntry(featureId, defaultWeight));
        return slot.withEntries(entries);
    }

    private TemplateValidationResult prospectiveFeature(FeatureTemplate owner, List<RoomFeatureSlot> sourceSlots, RoomFeatureSlot updatedSlot) {
        List<RoomFeatureSlot> slots = new ArrayList<>(sourceSlots);
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        return featureRegistry.validateProspective(new FeatureTemplate(owner.id(), owner.size(), owner.tags(), owner.markers(), slots, owner.lootBindings(), owner.structureFile()));
    }

    private void toggleNestedFeatureEntry(Player player, FeatureTemplate owner, RoomFeatureSlot slot, String featureId, int defaultWeight) {
        saveNestedFeatureSlot(player, owner, toggled(slot, featureId, defaultWeight));
    }

    private void promptNestedFeatureWeight(Player player, FeatureTemplate owner, RoomFeatureSlot slot, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            int weight = Integer.parseInt(value);
            List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            entries.add(new FeatureSlotEntry(featureId, weight));
            saveNestedFeatureSlot(player, owner, slot.withEntries(entries));
        });
    }

    private void saveNestedFeatureSlot(Player player, FeatureTemplate owner, RoomFeatureSlot updatedSlot) {
        AuthoringSession activeEdit = authoringManager.editingFeatureSessionInWorkspace(player, owner.id()).orElse(null);
        List<RoomFeatureSlot> source = activeEdit == null ? owner.featureSlots() : activeEdit.featureSlots();
        TemplateValidationResult safety = prospectiveFeature(owner, source, updatedSlot);
        if (!safety.valid()) throw new IllegalArgumentException(safety.errors().getFirst());
        if (activeEdit != null) {
            replaceFeatureSlot(activeEdit, updatedSlot);
            player.sendMessage(Component.text("Nested feature slot updated in the edit workspace. Use Save Edit to persist it."));
        } else {
            List<RoomFeatureSlot> slots = new ArrayList<>(source);
            slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
            saveFeatureTemplate(new FeatureTemplate(owner.id(), owner.size(), owner.tags(), owner.markers(), slots, owner.lootBindings(), owner.structureFile()));
            authoringManager.editingFeatureSession(player, owner.id()).ifPresent(session -> replaceFeatureSlot(session, updatedSlot));
        }
        openNestedFeatureSlot(player, owner.id(), updatedSlot.id());
    }

    private void openDoorMarkerLoot(Player player, String doorId, String marker) {
        DoorTemplate door = doorRegistry.getVisible(doorId).orElseThrow();
        openLootBinding(player, "da:door-loot:" + doorId + ":" + marker, marker, door.lootBindings(), bindings -> {
            saveDoorTemplate(new DoorTemplate(door.id(), door.size(), door.tags(), door.markers(), door.featureSlots(), bindings, door.gateway(), door.structureFile()));
            authoringManager.synchronizeDoorLootBindings(door.id(), bindings);
        },
            () -> openDoorComponents(player, doorId, "marker"));
    }

    private void openFeatureMarkers(Player player, String featureId) {
        FeatureTemplate feature = featureRegistry.getVisible(featureId).orElseThrow();
        Menu menu = menu("da:feature-markers:" + featureId, 54, "Feature Markers: " + featureId);
        int slot = 0;
        for (RoomMarker marker : feature.markers()) {
            button(menu, slot++, Material.REDSTONE_TORCH, marker.name(), lootMarkerLore(List.of("Position: " + marker.position()), feature.lootBindings().get(marker.name()), "Click to configure loot.", "Shift-right to delete."), p -> openFeatureMarkerLoot(p, featureId, marker.name()), null, null, p -> openDeleteFeatureMarkerConfirm(p, featureId, marker.name()));
            if (slot >= 45) break;
        }
        button(menu, 45, Material.HOPPER, "Multi-edit", List.of("Configure loot for multiple markers at once."), p -> beginMultiEdit(p, MultiEditOwner.FEATURE_TEMPLATE, featureId, MultiEditSlotType.MARKER));
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openFeature(p, featureId)); open(player, menu);
    }

    private List<String> lootMarkerLore(List<String> base, LootBinding binding, String... controls) {
        List<String> lore = new ArrayList<>(base);
        lore.add(binding == null ? "Loot: none" : "Loot: " + binding.tableId() + " | Rolls: " + binding.minimumRolls() + "-" + binding.maximumRolls());
        lore.addAll(List.of(controls));
        return lore;
    }

    private void openDeleteFeatureMarkerConfirm(Player player, String featureId, String markerId) {
        Menu menu = menu("da:delete-feature-marker:" + featureId + ":" + markerId, 27, "Delete Marker?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Deletes marker " + markerId + " and its loot binding."), p -> {
            boolean removed;
            if (authoringManager.activeFeatureId(p).filter(id -> id.equalsIgnoreCase(featureId)).isPresent()) {
                removed = authoringManager.removeComponent(p, "marker", markerId);
            } else {
                FeatureTemplate template = featureRegistry.getVisible(featureId).orElseThrow(() -> new IllegalArgumentException("Unknown feature " + featureId));
                List<RoomMarker> markers = new ArrayList<>(template.markers());
                removed = markers.removeIf(marker -> marker.name().equalsIgnoreCase(markerId));
                if (removed) {
                    Map<String, LootBinding> bindings = new LinkedHashMap<>(template.lootBindings());
                    bindings.keySet().removeIf(name -> name.equalsIgnoreCase(markerId));
                    saveFeatureTemplate(new FeatureTemplate(template.id(), template.size(), template.tags(), markers, template.featureSlots(), bindings, template.structureFile()));
                }
            }
            p.sendMessage(Component.text(removed ? "Deleted marker " + markerId + "." : "No matching marker named " + markerId + "."));
            openFeatureMarkers(p, featureId);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openFeatureMarkers(p, featureId));
        open(player, menu);
    }

    private void openFeatureMarkerLoot(Player player, String featureId, String marker) {
        FeatureTemplate feature = featureRegistry.getVisible(featureId).orElseThrow();
        openLootBinding(player, "da:feature-loot:" + featureId + ":" + marker, marker, feature.lootBindings(), bindings -> {
            saveFeatureTemplate(new FeatureTemplate(feature.id(), feature.size(), feature.tags(), feature.markers(), feature.featureSlots(), bindings, feature.structureFile()));
            authoringManager.synchronizeFeatureLootBindings(feature.id(), bindings);
        },
            () -> openFeatureMarkers(player, featureId));
    }

    private void openLootBinding(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back) {
        if (existing.containsKey(marker)) openLootBindingSettings(player, id, marker, existing, save, back);
        else openLootBindingPicker(player, id, marker, existing, save, back, 0);
    }

    private void openLootBindingPicker(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back, int page) {
        List<LootTable> tables = lootRegistry.all().stream().sorted(java.util.Comparator.comparing(LootTable::id)).toList();
        int pages = LootEditorInteractionRules.pageCount(tables.size());
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = menu(id + ":picker:" + current, 54, LootEditorInteractionRules.pageTitle("Loot: " + marker, current, pages));
        int start = current * 45;
        for (int slot = 0; slot < 45 && start + slot < tables.size(); slot++) {
            LootTable table = tables.get(start + slot);
            LootBinding prior = existing.get(marker);
            boolean ready = lootRegistry.usable(table.id());
            List<String> tableLore = ready ? List.of("Assign this pool and configure its rolls.") : List.of("Unavailable: this pool has no valid reachable item.", "Open the pool and repair its listed entries first.");
            button(menu, slot, ready ? Material.CHEST : Material.RED_CONCRETE, table.id(), tableLore, p -> {
                if (!ready) { p.sendMessage(Component.text("Cannot assign " + table.id() + " until it has a valid reachable item.")); return; }
                LootBinding binding = prior == null ? new LootBinding(table.id()) : new LootBinding(table.id(), prior.minimumRolls(), prior.maximumRolls());
                Map<String, LootBinding> bindings = new LinkedHashMap<>(existing); bindings.put(marker, binding);
                try {
                    save.accept(bindings);
                    openLootBindingSettings(p, id, marker, bindings, save, back);
                } catch (RuntimeException ex) {
                    p.sendMessage(Component.text("Loot binding was not saved: " + ex.getMessage() + ". Previous settings are unchanged."));
                    openLootBindingPicker(p, id, marker, existing, save, back, current);
                }
            });
        }
        if (tables.isEmpty()) menu.inventory.setItem(22, GuiItems.item(Material.KNOWLEDGE_BOOK, "No Ready Loot Pools", List.of("Create a loot table and add at least one reachable item first.")));
        if (LootEditorInteractionRules.hasPreviousPage(current)) button(menu, 45, Material.ARROW, "Previous Page", List.of(), p -> openLootBindingPicker(p, id, marker, existing, save, back, current - 1));
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> back.run());
        if (LootEditorInteractionRules.hasNextPage(current, pages)) button(menu, 53, Material.ARROW, "Next Page", List.of(), p -> openLootBindingPicker(p, id, marker, existing, save, back, current + 1));
        open(player, menu);
    }

    private void openLootBindingSettings(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back) {
        LootBinding binding = existing.get(marker);
        if (binding == null) { openLootBindingPicker(player, id, marker, existing, save, back, 0); return; }
        Menu menu = menu(id + ":settings", 27, "Loot Binding: " + marker);
        menu.inventory.setItem(4, GuiItems.item(Material.CHEST, binding.tableId(), List.of("This marker rolls this reusable pool.", "Roll range: " + binding.minimumRolls() + "-" + binding.maximumRolls())));
        button(menu, 10, Material.ENDER_CHEST, "Change Table", List.of("Current roll settings will be preserved."), p -> openLootBindingPicker(p, id, marker, existing, save, back, 0));
        button(menu, 12, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "Min Rolls: " + binding.minimumRolls(), List.of("Click to set the minimum roll count."), p -> promptBindingRoll(p, id, marker, existing, save, back, true));
        button(menu, 14, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "Max Rolls: " + binding.maximumRolls(), List.of("Click to set the maximum roll count."), p -> promptBindingRoll(p, id, marker, existing, save, back, false));
        button(menu, 20, Material.BARRIER, "No Loot", List.of("Remove this marker's loot binding."), p -> openRemoveLootBindingConfirm(p, id, marker, existing, save, back));
        button(menu, 24, Material.ARROW, "Back", List.of("All completed changes are saved."), p -> back.run());
        open(player, menu);
    }

    private void promptBindingRoll(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back, boolean minimum) {
        LootBinding binding = existing.get(marker);
        prompts.prompt(player, "Enter a non-negative " + (minimum ? "minimum" : "maximum") + " roll count", value -> {
            try {
                int rolls = Integer.parseInt(value.trim());
                int min = minimum ? rolls : binding.minimumRolls();
                int max = minimum ? binding.maximumRolls() : rolls;
                updateLootBinding(player, id, marker, existing, save, back, new LootBinding(binding.tableId(), min, max));
            } catch (RuntimeException ex) { player.sendMessage(Component.text("Edit failed: " + ex.getMessage())); openLootBindingSettings(player, id, marker, existing, save, back); }
        }, () -> openLootBindingSettings(player, id, marker, existing, save, back));
    }

    private void updateLootBinding(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back, LootBinding binding) {
        Map<String, LootBinding> bindings = new LinkedHashMap<>(existing); bindings.put(marker, binding);
        try {
            save.accept(bindings);
            openLootBindingSettings(player, id, marker, bindings, save, back);
        } catch (RuntimeException ex) {
            player.sendMessage(Component.text("Loot binding was not saved: " + ex.getMessage() + ". Previous settings are unchanged."));
            openLootBindingSettings(player, id, marker, existing, save, back);
        }
    }

    private void openRemoveLootBindingConfirm(Player player, String id, String marker, Map<String, LootBinding> existing, Consumer<Map<String, LootBinding>> save, Runnable back) {
        Menu menu = menu(id + ":remove", 27, "Remove Loot Binding?");
        button(menu, 11, Material.RED_CONCRETE, "Remove Loot", List.of("This marker will no longer generate loot."), p -> {
            Map<String, LootBinding> bindings = new LinkedHashMap<>(existing); bindings.remove(marker);
            try { save.accept(bindings); back.run(); }
            catch (RuntimeException ex) { p.sendMessage(Component.text("Loot binding was not removed: " + ex.getMessage() + ". Previous settings are unchanged.")); openLootBindingSettings(p, id, marker, existing, save, back); }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openLootBindingSettings(p, id, marker, existing, save, back));
        open(player, menu);
    }

    private void openDeleteFeatureConfirm(Player player, String featureId) {
        Menu menu = menu("da:delete-feature:" + featureId, 27, "Delete Feature?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + featureId), p -> {
            try {
                assetRenameCoordinator.deleteFeature(featureId);
                p.sendMessage(Component.text("Deleted feature " + featureId));
                openFeatures(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openFeature(p, featureId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openFeature(p, featureId));
        open(player, menu);
    }

    private void openDeleteDoorConfirm(Player player, String doorId) {
        Menu menu = menu("da:delete-door:" + doorId, 27, "Delete Door?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + doorId), p -> {
            try {
                doorRegistry.deleteDoor(doorId);
                assetRenameCoordinator.reloadAll();
                p.sendMessage(Component.text("Deleted door " + doorId));
                openDoors(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openDoor(p, doorId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDoor(p, doorId));
        open(player, menu);
    }

    public void openConfig(Player player) {
        Menu menu = menu("da:config", 54, "DungeonArchitect Config");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        int slot = fillConfig(menu, config, new File(plugin.getDataFolder(), "config.yml"), "", 0);
        button(menu, Math.min(slot, 45), Material.CHEST, "Features", List.of("Open captured feature templates."), this::openFeatures);
        button(menu, 49, Material.EMERALD, "Reload", List.of("Reload config, rooms, and features."), p -> {
            reloadContent();
            p.sendMessage(Component.text("DungeonArchitect reloaded."));
            openConfig(p);
        });
        button(menu, 53, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openDungeons(Player player) {
        Menu menu = menu("da:dungeons", 54, "Active Dungeons");
        int slot = 0;
        DungeonInstance current = dungeonManager.getDungeon(player).orElse(null);
        if (current != null) {
            addDungeon(menu, slot++, current, true);
        }
        for (DungeonInstance instance : dungeonManager.instances()) {
            if (current != null && current.id().equals(instance.id())) {
                continue;
            }
            addDungeon(menu, slot++, instance, false);
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    private void addDungeon(Menu menu, int slot, DungeonInstance instance, boolean current) {
        String alias = "#" + dungeonManager.alias(instance).orElse(0);
        button(menu, slot, current ? Material.ENDER_EYE : Material.ENDER_PEARL, alias + " " + instance.id(), List.of("State: " + instance.state(), "Rooms: " + instance.rooms().size(), "Seed: " + instance.seed(), "Click to manage."), p -> openDungeon(p, instance));
    }

    public void openDungeon(Player player, DungeonInstance instance) {
        Menu menu = menu("da:dungeon:" + instance.id(), 27, "Dungeon " + dungeonManager.alias(instance).map(i -> "#" + i).orElse(""));
        button(menu, 10, Material.OAK_DOOR, "Exit", List.of("Teleport yourself out of this dungeon."), p -> {
            dungeonManager.exitDungeon(p);
            p.sendMessage(Component.text("Exited dungeon."));
            openDungeons(p);
        });
        button(menu, 12, Material.COMPASS, "Teleport Room", List.of("Enter room index in chat."), p -> prompts.prompt(p, "Enter room index", value -> teleportToRoom(p, instance, Integer.parseInt(value))));
        button(menu, 14, Material.RED_CONCRETE, "Destroy", List.of("Click to confirm destroy."), p -> openDestroyConfirm(p, instance));
        button(menu, 16, Material.PAPER, "Debug", List.of("Send instance summary to chat."), p -> {
            p.sendMessage(Component.text(instance.id() + " state=" + instance.state() + " rooms=" + instance.rooms().size() + " seed=" + instance.seed()));
            openDungeon(p, instance);
        });
        button(menu, 22, Material.ARROW, "Back", List.of(), this::openDungeons);
        open(player, menu);
    }

    private void openDestroyConfirm(Player player, DungeonInstance instance) {
        Menu menu = menu("da:confirm:" + instance.id(), 27, "Destroy Dungeon?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Destroy", List.of(instance.id().toString()), p -> {
            dungeonManager.destroyDungeon(instance.id());
            p.sendMessage(Component.text("Destroyed dungeon."));
            openDungeons(p);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDungeon(p, instance));
        open(player, menu);
    }

    private int fillConfig(Menu menu, YamlConfiguration yaml, File file, String path, int slot) {
        ConfigurationSection section = path.isBlank() ? yaml : yaml.getConfigurationSection(path);
        if (section == null) {
            return slot;
        }
        for (String key : section.getKeys(false)) {
            String fullPath = path.isBlank() ? key : path + "." + key;
            if (yaml.isConfigurationSection(fullPath)) {
                slot = fillConfig(menu, yaml, file, fullPath, slot);
                continue;
            }
            Object value = yaml.get(fullPath);
            int target = slot++;
            button(menu, target, Material.COMPARATOR, fullPath + ": " + value, List.of("Click to edit."), p -> prompts.prompt(p, "Enter value for " + fullPath, input -> {
                yaml.set(fullPath, parseScalar(input));
                saveYaml(yaml, file);
                reloadContent();
                p.sendMessage(Component.text(fullPath + " updated."));
                openConfig(p);
            }));
            if (slot >= 45) {
                break;
            }
        }
        return slot;
    }

    private void teleportToRoom(Player player, DungeonInstance instance, int index) {
        if (index < 0 || index >= instance.rooms().size()) {
            throw new IllegalArgumentException("Room index out of range");
        }
        var room = instance.rooms().get(index);
        var world = Bukkit.getWorld(instance.worldName());
        if (world == null) {
            throw new IllegalArgumentException("Dungeon world is not loaded");
        }
        var origin = room.node().transform().origin();
        player.teleport(new org.bukkit.Location(world, origin.x() + 0.5, origin.y() + 1, origin.z() + 0.5));
    }

    private void saveRoom(RoomTemplate template) {
        Path roomDir = template.structureFile().getParent();
        try {
            RoomTemplateIO.save(template, roomDir);
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void saveFeatureTemplate(FeatureTemplate template) {
        Path featureDir = template.structureFile().getParent();
        try {
            TemplateValidationResult prospective = featureRegistry.validateProspective(template);
            if (!prospective.valid()) throw new IllegalArgumentException(prospective.errors().getFirst());
            FeatureTemplateIO.save(template, featureDir);
            featureRegistry.reload();
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void saveDoorTemplate(DoorTemplate template) {
        Path doorDir = template.structureFile().getParent();
        try {
            DoorTemplateIO.save(template, doorDir);
            doorRegistry.reload();
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Object parseScalar(String input) {
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(input);
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return input;
        }
    }

    private IntVector3 parseVector(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected x,y,z");
        }
        return new IntVector3(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }

    private <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E value : type.getEnumConstants()) {
            names.add(value.name());
        }
        return names;
    }

    private RoomCategory nextCategory(RoomCategory current) {
        RoomCategory[] values = RoomCategory.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private String titleCase(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private void sendValidation(Player player, TemplateValidationResult result) {
        for (String repair : result.repairs()) {
            player.sendMessage(Component.text("Repair: " + repair));
        }
        if (result.valid() && result.warnings().isEmpty()) {
            player.sendMessage(Component.text("Validation OK."));
            return;
        }
        if (!result.errors().isEmpty()) {
            player.sendMessage(Component.text("Validation errors:"));
        }
        for (String error : result.errors()) {
            player.sendMessage(Component.text("- " + error));
        }
        for (String warning : result.warnings()) {
            player.sendMessage(Component.text("Warning: " + warning));
        }
    }

    private boolean statusValid(TemplateLoadStatus<?> status) {
        return status == null || status.valid();
    }

    private void openOrExplainInvalid(Player player, String type, String id, List<String> errors, Runnable open) {
        if (open != null) {
            open.run();
            return;
        }
        player.sendMessage(Component.text("Cannot open " + type + " " + id + ": " + String.join("; ", errors)));
    }

    private List<String> loadStatusLore(TemplateLoadStatus<?> status, String... base) {
        List<String> lore = new ArrayList<>(List.of(base));
        if (status == null) {
            return lore;
        }
        if (!status.repairs().isEmpty()) {
            lore.add("Repairs: " + status.repairs().size());
        }
        if (!status.errors().isEmpty()) {
            lore.add("Invalid: " + status.errors().size() + " issue(s)");
            status.errors().stream().limit(2).forEach(error -> lore.add("- " + error));
            lore.add("Click to inspect. Use /da diagnose for full details.");
        }
        return DiagnosticText.lore(lore);
    }

    private List<String> diagnosticSummaryLore() {
        TemplateValidationResult result = TemplateDiagnostics.analyze(templateRegistry, featureRegistry, doorRegistry, lootRegistry);
        if (result.valid() && result.warnings().isEmpty()) {
            return List.of("No known template issues.");
        }
        return List.of("Errors: " + result.errors().size(), "Warnings: " + result.warnings().size(), "Click for full report.");
    }

    private List<String> diagnosticLore(TemplateDiagnostic diagnostic) {
        List<String> lore = new ArrayList<>();
        lore.add(diagnostic.message());
        if (diagnostic.localPosition() != null) {
            lore.add("Position: " + DiagnosticText.position(diagnostic.localPosition()));
        }
        if (diagnostic.suggestion() != null) {
            lore.add("Fix: " + diagnostic.suggestion());
        }
        return DiagnosticText.lore(lore);
    }

    private String targetName(TemplateDiagnostic diagnostic) {
        if (diagnostic.templateId() == null) {
            return "template";
        }
        if (diagnostic.componentId() == null) {
            return diagnostic.templateId();
        }
        return diagnostic.templateId() + "/" + diagnostic.componentId();
    }

    private String message(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private void saveYaml(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Menu menu(String id, int size, String title) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(id), size, Component.text(title));
        return new Menu(inventory, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action) {
        button(menu, slot, material, name, lore, action, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction) {
        button(menu, slot, material, name, lore, action, rightClickAction, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction, MenuAction shiftLeftAction) {
        button(menu, slot, material, name, lore, action, rightClickAction, shiftLeftAction, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction, MenuAction shiftLeftAction, MenuAction shiftRightAction) {
        ItemStack item = GuiItems.item(material, name, lore);
        menu.inventory.setItem(slot, item);
        menu.actions.put(slot, action);
        if (rightClickAction != null) {
            menu.rightClickActions.put(slot, rightClickAction);
        }
        if (shiftLeftAction != null) {
            menu.shiftLeftActions.put(slot, shiftLeftAction);
        }
        if (shiftRightAction != null) {
            menu.shiftRightActions.put(slot, shiftRightAction);
        }
    }

    private void open(Player player, Menu menu) {
        actions.put(player.getUniqueId(), new PlayerMenuActions(menu.actions, menu.rightClickActions, menu.shiftLeftActions, menu.shiftRightActions));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof MenuHolder holder && holder.id().startsWith("da:loot:")) {
            handleLootEditorClick(event, player);
            return;
        }
        event.setCancelled(true);
        PlayerMenuActions playerActions = actions.getOrDefault(player.getUniqueId(), new PlayerMenuActions(Map.of(), Map.of(), Map.of(), Map.of()));
        MenuAction action = null;
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            action = playerActions.shiftRightActions().get(event.getRawSlot());
        }
        if (action == null && event.getClick() == ClickType.SHIFT_LEFT) {
            action = playerActions.shiftLeftActions().get(event.getRawSlot());
        }
        if (action == null) {
            action = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT
                ? playerActions.rightClickActions().get(event.getRawSlot())
                : playerActions.actions().get(event.getRawSlot());
        }
        if (action == null) {
            action = playerActions.actions().get(event.getRawSlot());
        }
        if (action != null) {
            action.click(player);
        }
    }

    private void handleLootEditorClick(InventoryClickEvent event, Player player) {
        LootEditSession session = lootEdits.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory()) { event.setCancelled(true); return; }
        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (raw >= 45 && raw < top.getSize()) {
            event.setCancelled(true);
            PlayerMenuActions playerActions = actions.get(player.getUniqueId());
            if (playerActions != null) {
                MenuAction action = playerActions.actions().get(raw);
                if (action != null) action.click(player);
            }
            return;
        }
        if (raw >= top.getSize()) {
            LootEditorInteractionRules.Intent intent = LootEditorInteractionRules.classify(
                LootEditorInteractionRules.Region.PLAYER, event.getClick(), !isAir(event.getCurrentItem()));
            if (intent == LootEditorInteractionRules.Intent.ADD_TEMPLATE) {
                event.setCancelled(true);
                queueLootTemplateAdd(player, session, event.getCurrentItem());
            } else if (intent == LootEditorInteractionRules.Intent.RESYNC) {
                event.setCancelled(true);
                player.updateInventory();
            }
            return;
        }
        if (raw < 0 || raw >= 45) return;

        event.setCancelled(true);
        DraftLootEntry draft = session.pageEntry(raw);
        LootEditorInteractionRules.Intent intent = LootEditorInteractionRules.classify(
            LootEditorInteractionRules.Region.EDITOR, event.getClick(), draft != null);
        switch (intent) {
            case CONFIGURE -> queueLootTransaction(player, session, () -> {
                suppressLootClose.add(player.getUniqueId());
                openLootEntryConfigNow(player, session, draft.id());
            });
            case REMOVE_ENTRY -> queueRemoveLootEntry(player, session, draft);
            default -> player.updateInventory();
        }
    }

    private void queueLootTransaction(Player player, LootEditSession session, Runnable transaction) {
        if (session.closed) { player.updateInventory(); return; }
        session.transactions.addLast(transaction);
        if (session.transactionPending) return;
        session.transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> drainLootTransactions(player, session));
    }

    private void drainLootTransactions(Player player, LootEditSession session) {
        try {
            while (!session.transactions.isEmpty()) {
                if (!player.isOnline() || session.closed || lootEdits.get(player.getUniqueId()) != session) break;
                Runnable transaction = session.transactions.removeFirst();
                LootTransactionSnapshot snapshot = new LootTransactionSnapshot(session.snapshot(), session.page, session.nextDraftId);
                try {
                    transaction.run();
                } catch (RuntimeException ex) {
                    session.restore(snapshot.drafts(), snapshot.page(), snapshot.nextDraftId());
                    session.transactions.clear();
                    plugin.getLogger().warning("Loot editor transaction failed for " + player.getName() + ": " + ex.getMessage());
                    player.sendMessage(Component.text("Loot edit failed safely; the previous table settings are unchanged."));
                    break;
                }
            }
        } finally {
            session.transactions.clear();
            session.transactionPending = false;
            if (!session.closed && lootEdits.get(player.getUniqueId()) == session && player.getOpenInventory().getTopInventory() == session.inventory) {
                refreshLootEditor(player, session);
            }
            else if (player.isOnline()) player.updateInventory();
        }
    }

    private void queueLootTemplateAdd(Player player, LootEditSession session, ItemStack source) {
        if (isAir(source)) return;
        ItemStack template = LootEditorTemplates.normalize(source);
        queueLootTransaction(player, session, () -> {
            LootEntry entry = new LootEntry(template, 1, 1, 1, 1);
            List<LootPoolEntry> entries = new ArrayList<>(session.values());
            entries.add(entry);
            if (!saveLootCandidate(player, new LootTable(session.id, entries))) return;
            DraftLootEntry added = session.add(entry);
            session.sort();
            session.page = session.pageOf(added.id());
        });
    }

    private void queueRemoveLootEntry(Player player, LootEditSession session, DraftLootEntry draft) {
        if (draft == null) { player.updateInventory(); return; }
        queueLootTransaction(player, session, () -> {
            if (session.entry(draft.id()) == null) return;
            List<LootPoolEntry> entries = session.entries().stream()
                .filter(candidate -> !candidate.id().equals(draft.id()))
                .map(DraftLootEntry::value)
                .toList();
            if (saveLootCandidate(player, new LootTable(session.id, entries))) {
                session.remove(draft.id());
                session.sort();
            }
        });
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder holder) || !holder.id().startsWith("da:loot:")) return;
        if (event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < 45)) return;
        event.setCancelled(true);
        player.updateInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            UUID playerId = event.getPlayer().getUniqueId();
            if (isLootSessionMenu(event.getInventory())) {
                LootEditSession session = lootEdits.get(playerId);
                if (suppressLootClose.remove(playerId)) {
                    return;
                }
                if (session != null) {
                    scheduleLootClose((Player) event.getPlayer(), session);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)) {
                    actions.remove(playerId);
                }
            });
        }
    }

    private void scheduleLootClose(Player player, LootEditSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.closed || lootEdits.get(player.getUniqueId()) != session) return;
            if (session.transactionPending) { scheduleLootClose(player, session); return; }
            if (isLootSessionMenu(player.getOpenInventory().getTopInventory())) return;
            closeLootEditorSession(player, session);
            player.updateInventory();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        suppressLootClose.remove(playerId);
        LootEditSession session = lootEdits.get(playerId);
        if (session != null) closeLootEditorSession(event.getPlayer(), session);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        for (Map.Entry<UUID, LootEditSession> entry : new ArrayList<>(lootEdits.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) closeLootEditorSession(player, entry.getValue());
            else lootLeases.release(entry.getValue().id, entry.getValue().ownerId);
        }
    }

    private void prepareForReload() {
        for (Map.Entry<UUID, LootEditSession> entry : new ArrayList<>(lootEdits.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                lootLeases.release(entry.getValue().id, entry.getValue().ownerId);
                continue;
            }
            closeLootEditorSession(player, entry.getValue());
            player.closeInventory();
            player.sendMessage(Component.text("Loot editor closed for reload. All completed changes were already saved."));
        }
        lootEdits.clear();
        lootMultiEdits.clear();
        lootLeases.clear();
        suppressLootClose.clear();
    }

    public void reloadContent() {
        prepareForReload();
        reloadAll.run();
    }

    private record Menu(Inventory inventory, Map<Integer, MenuAction> actions, Map<Integer, MenuAction> rightClickActions, Map<Integer, MenuAction> shiftLeftActions, Map<Integer, MenuAction> shiftRightActions) {
    }

    private record PlayerMenuActions(Map<Integer, MenuAction> actions, Map<Integer, MenuAction> rightClickActions, Map<Integer, MenuAction> shiftLeftActions, Map<Integer, MenuAction> shiftRightActions) {
    }

    private enum MultiEditOwner {
        ROOM,
        DOOR_TEMPLATE,
        FEATURE_TEMPLATE
    }

    private enum LootEntryField {
        WEIGHT("Enter a positive weight"),
        MINIMUM_AMOUNT("Enter a minimum count (at least 1 and no more than max)"),
        MAXIMUM_AMOUNT("Enter a maximum count (at least min)"),
        MAXIMUM_PER_CONTAINER("Enter max rolls per container (0 for unlimited)");

        private final String prompt;

        LootEntryField(String prompt) {
            this.prompt = prompt;
        }

        private String prompt() {
            return prompt;
        }
    }

    private enum MultiEditSlotType {
        DOOR("Door Slots"),
        FEATURE("Feature Slots"),
        MARKER("Markers");

        private final String label;

        MultiEditSlotType(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private static final class MultiEditSession {
        private final MultiEditOwner owner;
        private final String ownerId;
        private final MultiEditSlotType slotType;
        private final Set<String> selectedSlotIds = new LinkedHashSet<>();
        private final List<DoorSlotEntry> doorDraft = new ArrayList<>();
        private final Set<String> doorTagsDraft = new LinkedHashSet<>();
        private final List<FeatureSlotEntry> featureDraft = new ArrayList<>();
        private DoorConnectionRules doorConnectionRulesDraft = DoorConnectionRules.DEFAULT;
        private boolean doorRuleDraftInitialized;
        private boolean doorEntriesTouched;
        private boolean doorTagsTouched;
        private boolean doorConnectionRulesTouched;
        private LootBinding markerLootBindingDraft;
        private boolean markerLootBindingConfigured;

        private MultiEditSession(MultiEditOwner owner, String ownerId, MultiEditSlotType slotType) {
            this.owner = owner;
            this.ownerId = ownerId;
            this.slotType = slotType;
        }
    }

    private static final class LootEditSession {
        private final String id;
        private final String sessionId = UUID.randomUUID().toString();
        private final UUID ownerId;
        private final String ownerName;
        private boolean showWeights;
        private int page;
        private long nextDraftId;
        private boolean transactionPending;
        private boolean closed;
        private Inventory inventory;
        private String inventoryTitle;
        private final Deque<Runnable> transactions = new ArrayDeque<>();
        private final List<DraftLootEntry> drafts = new ArrayList<>();

        private LootEditSession(LootTable table, UUID ownerId, String ownerName) {
            id = table.id();
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            table.entries().forEach(this::add);
            sort();
        }

        private DraftLootEntry add(LootPoolEntry value) {
            DraftLootEntry draft = new DraftLootEntry(Long.toString(nextDraftId++), value);
            drafts.add(draft);
            return draft;
        }

        private List<DraftLootEntry> entries() { return List.copyOf(drafts); }
        private List<LootPoolEntry> values() { return drafts.stream().map(DraftLootEntry::value).toList(); }
        private DraftLootEntry entry(String draftId) {
            if (draftId == null) return null;
            return drafts.stream().filter(draft -> draft.id().equals(draftId)).findFirst().orElse(null);
        }

        private List<DraftLootEntry> pageEntries() {
            int start = Math.min(page * 45, drafts.size());
            return drafts.subList(start, Math.min(start + 45, drafts.size()));
        }

        private DraftLootEntry pageEntry(int slot) {
            if (slot < 0 || slot >= 45) return null;
            int index = page * 45 + slot;
            return index < drafts.size() ? drafts.get(index) : null;
        }

        private int pageOf(String draftId) {
            for (int index = 0; index < drafts.size(); index++) {
                if (drafts.get(index).id().equals(draftId)) return LootEditorInteractionRules.pageOf(index);
            }
            return Math.min(page, pageCount() - 1);
        }

        private boolean remove(String draftId) {
            return drafts.removeIf(draft -> draft.id().equals(draftId));
        }

        private void replace(String draftId, LootPoolEntry value) {
            for (int index = 0; index < drafts.size(); index++) {
                if (drafts.get(index).id().equals(draftId)) {
                    drafts.set(index, new DraftLootEntry(draftId, value));
                    return;
                }
            }
        }

        private List<DraftLootEntry> snapshot() { return new ArrayList<>(drafts); }

        private void restore(List<DraftLootEntry> snapshot, int restoredPage, long restoredNextDraftId) {
            drafts.clear();
            drafts.addAll(snapshot);
            page = restoredPage;
            nextDraftId = restoredNextDraftId;
        }

        private void sort() {
            drafts.sort(java.util.Comparator.comparingInt((DraftLootEntry draft) -> draft.value().weight()).reversed());
            page = Math.min(page, pageCount() - 1);
        }

        private LootTable tableReplacing(String draftId, LootPoolEntry replacement) {
            return tableReplacing(Map.of(draftId, replacement));
        }

        private LootTable tableReplacing(Map<String, ? extends LootPoolEntry> replacements) {
            List<LootPoolEntry> values = drafts.stream()
                .map(draft -> replacements.containsKey(draft.id()) ? replacements.get(draft.id()) : draft.value())
                .toList();
            return new LootTable(id, values);
        }

        private int pageCount() { return LootEditorInteractionRules.pageCount(drafts.size()); }
    }

    private record DraftLootEntry(String id, LootPoolEntry value) { }

    private record LootTransactionSnapshot(List<DraftLootEntry> drafts, int page, long nextDraftId) { }

    private static final class LootMultiEditSession {
        private final LootEditSession editor;
        private final Set<String> selectedIds = new LinkedHashSet<>();
        private int page;
        private int weight;
        private int minimumAmount;
        private int maximumAmount;
        private int maximumPerContainer;
        private boolean initialized;
        private boolean tableMode;

        private LootMultiEditSession(LootEditSession editor) {
            this.editor = editor;
        }

        private void initializeDraft() {
            if (initialized) return;
            LootPoolEntry entry = firstSelectedEntry();
            if (entry == null) throw new IllegalStateException("Select at least one loot entry");
            weight = entry.weight();
            maximumPerContainer = entry.maximumPerContainer();
            tableMode = entry instanceof LootTableEntry;
            if (entry instanceof LootEntry item) {
                minimumAmount = item.minimumAmount();
                maximumAmount = item.maximumAmount();
            }
            initialized = true;
        }

        private LootPoolEntry firstSelectedEntry() {
            for (DraftLootEntry draft : editor.entries()) {
                if (selectedIds.contains(draft.id())) return draft.value();
            }
            return null;
        }

        private boolean accepts(LootPoolEntry candidate) {
            LootPoolEntry first = firstSelectedEntry();
            return first == null || (first instanceof LootTableEntry) == (candidate instanceof LootTableEntry);
        }
    }

    private static final class TagSelection {
        private final TagDomain domain;
        private final String title;
        private final Set<String> selected = new LinkedHashSet<>();
        private final Consumer<Set<String>> onSave;
        private final Consumer<Player> onCancel;
        private String filter = "";
        private int page;

        private TagSelection(TagDomain domain, String title, Set<String> selected, Consumer<Set<String>> onSave, Consumer<Player> onCancel) {
            this.domain = domain;
            this.title = title;
            this.selected.addAll(selected);
            this.onSave = onSave;
            this.onCancel = onCancel;
        }
    }

    private record UnavailableCandidate(String id, List<String> conflicts, String summary) {
        private UnavailableCandidate {
            conflicts = List.copyOf(conflicts);
        }
    }
}
