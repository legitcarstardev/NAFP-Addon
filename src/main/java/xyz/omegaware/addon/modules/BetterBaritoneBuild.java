package xyz.omegaware.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectEndEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.utils.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

class EventRegistry {
    public static final EventRegistry INSTANCE = new EventRegistry();

    public static class Event {
        public enum EventType {
            Resume,
            PathToPos,
            InteractWithBlock,
            FetchItems
        }
        public EventType type;
        public boolean bWaitOnPath;
        public Runnable callback;

        public Event(EventType type, boolean bWaitOnPath, Runnable callback) {
            this.type = type;
            this.bWaitOnPath = bWaitOnPath;
            this.callback = callback;
        }
    } private final List<Event> eventQueue = new ArrayList<>();

    public void clear() {
        eventQueue.clear();
    }

    public void push(Event event) {
        eventQueue.add(event);
    }

    private void remove(Event event) {
        eventQueue.remove(event);
    }

    public boolean isEmpty() {
        return eventQueue.isEmpty();
    }

    public List<Event> getAll() {
        return new ArrayList<>(eventQueue);
    }

    public Event next() {
        if (eventQueue.isEmpty()) return null;
        Event event = eventQueue.getFirst();
        remove(event);
        return event;
    }

    public boolean eventExists(Event.EventType type) {
        return eventQueue.stream().anyMatch(event -> event.type == type);
    }
}

class StorageRegistry {
    public static final StorageRegistry INSTANCE = new StorageRegistry();

    public static class Storage {
        public BlockPos blockPos;
        public List<ItemStack> inventory;

        public Storage() {
            this.blockPos = BlockPos.ZERO;
            this.inventory = new ArrayList<>();
        }

        public Storage(BlockPos blockPos, List<ItemStack> inventory) {
            this.blockPos = blockPos;
            this.inventory = inventory;
        }

        public boolean hasItem(Item item) {
            for (ItemStack stack : inventory) {
                if (stack.getItem().equals(item)) {
                    return true;
                }
            }
            return false;
        }
    } private final List<Storage> storages = new ArrayList<>();

    public void clear() {
        storages.clear();
    }

    public void add(Storage storage) {
        storages.add(storage);
    }

    public List<Storage> getAll() {
        return new ArrayList<>(storages);
    }

    public Storage indexStorage(AbstractContainerMenu menu, BlockPos blockPos) {
        if (menu == null || blockPos == null) return null;

        int max = 27; // Default size for most chests, shulker boxes, etc.
        if (menu.getType() == MenuType.GENERIC_9x6) max = 27 * 2;

        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                inventory.add(stack);
            }
        }

        return new Storage(blockPos, inventory);
    }

    public Storage find(BlockPos blockPos) {
        for (Storage storage : storages) {
            if (storage.blockPos.equals(blockPos)) {
                return storage;
            }
        }
        return null;
    }

    public Storage findItem(Item item) {
        for (Storage storage : storages) {
            if (storage.hasItem(item)) {
                return storage;
            }
        }
        return null;
    }

    public void findItemAndPath(Item item) {
        Storage storage = findItem(item);
        if (storage != null) {
            Logger.info("%s Navigating to storage containing:%s %s", ChatFormatting.GREEN, ChatFormatting.WHITE, item.getName(new ItemStack(item)).getString());
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.PathToPos, true, () -> OmegawareAddons.BETTER_BARITONE_BUILD.pathToPos(storage.blockPos)));
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.InteractWithBlock, true, () -> {
                if (mc.player == null || mc.gameMode == null) {
                    Logger.error("Player or interaction manager is null!");
                    return;
                }
                mc.setScreen(null); // Close any open screens to ensure that we can interact with the storage block

                Vec3 hitPos = Vec3.atCenterOf(storage.blockPos);
                BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, storage.blockPos, false);

                InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit); // Attempt to interact with the block
                if (OmegawareAddons.BETTER_BARITONE_BUILD.debugMode.get()) {
                    Logger.info("Attempted interact with block at %s, result: %s", storage.blockPos, result.consumesAction());
                }

                if (result.consumesAction()) // If the interaction was successful, we can then make the player swing their hand
                    mc.player.swing(InteractionHand.MAIN_HAND);
            }));
        }
    }

    public void update() {
        storages.removeIf(storage -> {
            if (storage == null || storage.blockPos == null) return true;
            assert Minecraft.getInstance().level != null;
            if (!Minecraft.getInstance().level.hasChunkAt(storage.blockPos)) return false;

            if (storage.inventory == null || storage.inventory.isEmpty()) {
                return true;
            }

            return Minecraft.getInstance().level.getBlockState(storage.blockPos).isAir() || Minecraft.getInstance().level.getBlockEntity(storage.blockPos) == null;
        });
    }

    public void updateStorage(BlockPos blockPos, List<ItemStack> inventory) {
        Storage storage = find(blockPos);
        if (storage != null) {
            storage.inventory = inventory;
        }
    }

    void save() {
        update();

        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();

            JsonArray linkedStoragesArray = new JsonArray();
            for (Storage storage : storages) {
                JsonObject storageJson = new JsonObject();
                storageJson.addProperty("blockPos", storage.blockPos.asLong());

                JsonObject inventoryJson = new JsonObject();
                for (ItemStack stack : storage.inventory) {
                    if (!stack.isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        JsonObject itemData = new JsonObject();
                        itemData.addProperty("count", stack.getCount());
                        inventoryJson.add(itemId, itemData);
                    }
                }
                storageJson.add("inventory", inventoryJson);
                linkedStoragesArray.add(storageJson);
            }

            payload.add("linked_storages", linkedStoragesArray);


            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to load Linked Storages to {}", configFile.toPath());
        }
    }

    void load() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");
        if (!configFile.exists()) {
            //noinspection LoggingSimilarMessage
            OmegawareAddons.LOG.warn("Config file \"{}\" not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = new GsonBuilder().setPrettyPrinting().create().fromJson(content, JsonObject.class);
            if (payload.has("linked_storages")) {
                JsonArray linkedStoragesArray = payload.getAsJsonArray("linked_storages");
                storages.clear();

                for (int i = 0; i < linkedStoragesArray.size(); i++) {
                    JsonObject storageJson = linkedStoragesArray.get(i).getAsJsonObject();
                    Storage storage = new Storage();

                    if (storageJson.has("blockPos")) {
                        storage.blockPos = BlockPos.of(storageJson.get("blockPos").getAsLong());
                    }

                    if (storageJson.has("inventory")) {
                        JsonObject inventoryJson = storageJson.getAsJsonObject("inventory");
                        storage.inventory = new ArrayList<>();

                        for (String itemId : inventoryJson.keySet()) {
                            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
                            if (item != null) {
                                JsonObject itemData = inventoryJson.getAsJsonObject(itemId);
                                int count = itemData.get("count").getAsInt();
                                storage.inventory.add(new ItemStack(item, count));
                            }
                        }
                    }

                    storages.add(storage);
                }
            }

        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load Linked Storages from {}: {}", configFile.toPath(), e.getMessage());
        }
    }
}

class Home {
    public static final Home INSTANCE = new Home();
    private static BlockPos pos;

    public void setHome(BlockPos home) {
        pos = home;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isSet() {
        return pos != null;
    }

    void save() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();
            payload.addProperty("home", pos.asLong());
            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to save Home to {}", configFile.toPath());
        }
    }

    void load() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");
        if (!configFile.exists()) {
            //noinspection LoggingSimilarMessage
            OmegawareAddons.LOG.warn("Config file \"{}\" not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = new GsonBuilder().setPrettyPrinting().create().fromJson(content, JsonObject.class);
            if (payload.has("home")) {
                pos = BlockPos.of(payload.get("home").getAsLong());
            }
        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load Home from {}: {}", configFile.toPath(), e.getMessage());
        }
    }
}

class FetchRegistry {
    public static final FetchRegistry INSTANCE = new FetchRegistry();

    public static class Material {
        public Item item;
        public int stacks;

        public Material(Item item, int stacks) {
            this.item = item;
            this.stacks = stacks;
        }
    } private final List<Material> fetchList = new ArrayList<>();

    public void clear() {
        fetchList.clear();
    }

    public void add(Material material) {
        fetchList.add(material);
    }

    public List<Material> get() {
        return new ArrayList<>(fetchList);
    }

    public Material find(Item item) {
        for (Material material : fetchList) {
            if (material.item.equals(item)) {
                return material;
            }
        }
        return null;
    }

    public boolean hasItem(Item item) {
        return find(item) != null;
    }

    public Material updateMaterial(Material material, int newStacks) {
        Material existingMaterial = find(material.item);
        if (existingMaterial != null) {
            existingMaterial.stacks = newStacks;
            return existingMaterial;
        }
        return null;
    }

    public void update() {
        fetchList.removeIf(material -> material == null || material.item == null || material.stacks <= 0);
    }

    public boolean isEmpty() {
        return fetchList.isEmpty();
    }
}

public class BetterBaritoneBuild extends Module {
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "better-baritone-build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgRepeat = this.settings.createGroup("Repeat Command");

    private final Setting<Boolean> storageLinkMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-link-mode")
        .description("If enabled, all storage blocks you interact with will be linked to this module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreY = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-ignore-y")
        .description("If enabled, the Y coordinate will be ignored when navigating to a block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> homeIfStuck = sgGeneral.add(new BoolSetting.Builder()
        .name("home-if-stuck")
        .description("If enabled, Baritone will return set home point if it gets stuck while building.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> returnToBuildSpot = sgGeneral.add(new BoolSetting.Builder()
        .name("return-to-build-spot")
        .description("If enabled, after fetching materials the module will path back to where it ran out before resuming the build, instead of just resuming from wherever the storage run left you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preventMining = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-mining")
        .description("If enabled, Baritone will not break/mine any blocks while this module is active - it will only place blocks into empty (air) space and leave existing blocks alone, even ones that don't match the schematic.")
        .defaultValue(false)
        .onChanged(enabled -> {
            if (isActive()) setAllowBreak(!enabled);
        })
        .build()
    );

    private final Setting<List<Block>> ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignore-blocks")
        .description("Blocks in this list won't be required for the build - Baritone will just leave those spots empty instead of pausing the whole build when a linked storage doesn't have them.")
        .onChanged(blocks -> {
            if (isActive()) applyIgnoredBlocks();
        })
        .build()
    );

    private final Setting<Integer> homeIfStuckTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("home-if-stuck-timeout")
        .description("The timeout in seconds before Baritone returns to the home point if it gets stuck.")
        .defaultValue(15)
        .min(1)
        .sliderRange(5, 120)
        .visible(homeIfStuck::get)
        .build()
    );

    private final Setting<Boolean> highlightLinkedStorages = sgRender.add(new BoolSetting.Builder()
        .name("highlight-linked-storages")
        .description("If enabled, linked storages will be highlighted with a box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> invertHighlight = sgRender.add(new BoolSetting.Builder()
        .name("invert-highlight")
        .description("If enabled, the highlight will be inverted (i.e. highlighted blocks will not be highlighted).")
        .defaultValue(false)
        .visible(highlightLinkedStorages::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this::isActive)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the rendering.")
            .defaultValue(new SettingColor(0, 255, 255, 40))
            .visible(() -> shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the rendering.")
            .defaultValue(new SettingColor(0, 255, 255, 255))
            .visible(() -> shapeMode.get().lines())
            .build()
    );

    private final Setting<Boolean> disconnectOnDone = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-done")
        .description("If enabled, the module will disconnect you from the server when it is done.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disconnectOnError = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-error")
        .description("If enabled, the module will disconnect you from the server when it encounters an error.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> extraStacks = sgGeneral.add(new IntSetting.Builder()
        .name("extra-stacks")
        .description("The number of extra stacks to fetch from the linked storage.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    public final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, the module will print debug information to the console.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> repeatCommandEnabled = sgRepeat.add(new BoolSetting.Builder()
        .name("repeat-command")
        .description("If enabled, periodically re-sends a Baritone command on an interval - useful for commands that need to be re-issued from time to time (e.g. re-running a build).")
        .defaultValue(false)
        .onChanged(enabled -> {
            if (isActive()) lastRepeatTime = System.currentTimeMillis();
        })
        .build()
    );

    private final Setting<String> repeatCommandText = sgRepeat.add(new StringSetting.Builder()
        .name("repeat-command-text")
        .description("The Baritone command to repeat, without the leading '#'. Example: build myschematic")
        .defaultValue("")
        .visible(repeatCommandEnabled::get)
        .build()
    );

    private final Setting<Double> repeatInterval = sgRepeat.add(new DoubleSetting.Builder()
        .name("repeat-interval-minutes")
        .description("How often to repeat the command, in minutes. Fractions are allowed, e.g. 0.5 for every 30 seconds.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.5, 30)
        .decimalPlaces(2)
        .visible(repeatCommandEnabled::get)
        .build()
    );

    // Globals
    IBaritone baritone = null;
    private static String buildCommand = "";
    private static EventRegistry.Event currentEvent = null;
    private BlockPos lastBlockInteractPos = null;
    private BlockPos materialShortagePos = null; // where the player was standing when Baritone first reported missing materials
    private Boolean savedAllowBreak = null; // Baritone's original allowBreak / buildIgnoreExisting values, while
    private Boolean savedBuildIgnoreExisting = null; // prevent-mining has overridden them
    private final List<Block> appliedIgnoredBlocks = new ArrayList<>(); // blocks we've personally added to Baritone's okIfAir list
    private long lastRepeatTime = 0;

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            Logger.error("Baritone is not available!");
            toggle();
            return;
        }

        baritone = BaritoneAPI.getProvider().getBaritoneForMinecraft(Minecraft.getInstance());

        currentEvent = null;
        EventRegistry.INSTANCE.clear();
        FetchRegistry.INSTANCE.clear();
        materialShortagePos = null;

        StorageRegistry.INSTANCE.load();
        Home.INSTANCE.load();

        if (preventMining.get()) setAllowBreak(false);
        applyIgnoredBlocks();
        lastRepeatTime = System.currentTimeMillis();
    }

    @Override
    public void onDeactivate() {
        currentEvent = null;
        EventRegistry.INSTANCE.clear();
        FetchRegistry.INSTANCE.clear();

        StorageRegistry.INSTANCE.save();
        Home.INSTANCE.save();

        setAllowBreak(true); // Restore Baritone's original allowBreak value, if we changed it
        clearIgnoredBlocks();
    }

    /**
     * Enables or disables Baritone's ability to break/mine blocks. Disables both the general
     * pathing/mining permission (allowBreak) and the build command's own wrong-block-removal logic
     * (buildIgnoreExisting treats any existing non-air block as "correct" so it's never targeted for
     * removal) - the build process does not consult allowBreak at all, so both are needed for a build
     * to truly place-only. Remembers the previous values so they can be restored when this module
     * deactivates or the prevent-mining setting is turned back off.
     */
    private void setAllowBreak(boolean allow) {
        if (!allow) {
            if (savedAllowBreak == null) {
                savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
                savedBuildIgnoreExisting = BaritoneAPI.getSettings().buildIgnoreExisting.value;
            }
            BaritoneAPI.getSettings().allowBreak.value = false;
            BaritoneAPI.getSettings().buildIgnoreExisting.value = true;
        } else if (savedAllowBreak != null) {
            BaritoneAPI.getSettings().allowBreak.value = savedAllowBreak;
            BaritoneAPI.getSettings().buildIgnoreExisting.value = savedBuildIgnoreExisting;
            savedAllowBreak = null;
            savedBuildIgnoreExisting = null;
        }
    }

    /**
     * Syncs the ignore-blocks setting into Baritone's own okIfAir setting: any position in the schematic
     * that's still air and calls for one of these blocks is then treated by Baritone as already correct,
     * so it's never added to the "needs materials" list in the first place and the build just skips it -
     * no pause, no missing-materials chat spam, no restart needed. Only removes/re-adds the blocks this
     * module itself added, so it won't clobber anything already in okIfAir from elsewhere.
     */
    private void applyIgnoredBlocks() {
        List<Block> okIfAir = BaritoneAPI.getSettings().okIfAir.value;
        okIfAir.removeAll(appliedIgnoredBlocks);
        appliedIgnoredBlocks.clear();

        for (Block block : ignoredBlocks.get()) {
            if (block != null && !okIfAir.contains(block)) {
                okIfAir.add(block);
                appliedIgnoredBlocks.add(block);
            }
        }
    }

    private void clearIgnoredBlocks() {
        BaritoneAPI.getSettings().okIfAir.value.removeAll(appliedIgnoredBlocks);
        appliedIgnoredBlocks.clear();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();

        WButton printBtn = theme.button("Print Linked Storages");
        printBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            StorageRegistry.INSTANCE.getAll().forEach(storage -> {
                if (storage.blockPos == null) return;
                sb.append(String.format("X=%s, Y=%s, Z=%s\n", storage.blockPos.getX(), storage.blockPos.getY(), storage.blockPos.getZ()));
            });

            Logger.info("Linked Storages:\n%s", sb.toString());
        };
        hList.add(printBtn);

        WButton clearBtn = theme.button("Clear Linked Storages");
        clearBtn.action = () -> {
            StorageRegistry.INSTANCE.clear();
            Logger.info("Linked Storages cleared!");
        };
        hList.add(clearBtn);

        WButton setHomeBtn = theme.button("Set Home");
        setHomeBtn.action = () -> {
            if (mc.player == null || mc.level == null) return;

            Home.INSTANCE.setHome(mc.player.blockPosition());
            Home.INSTANCE.save();

            BlockPos home = Home.INSTANCE.getPos();
            Logger.info("%sHome point set to:%s X=%s, Y=%s, Z=%s", ChatFormatting.GREEN, ChatFormatting.WHITE, home.getX(), home.getY(), home.getZ());
        };
        hList.add(setHomeBtn);

        WHorizontalList hlist2 = list.add(theme.horizontalList()).expandX().widget();
        WButton printFetchListBtn = theme.button("Print Fetch List");
        printFetchListBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            FetchRegistry.INSTANCE.get().forEach(material -> {
                sb.append(String.format("Item: %s, Stacks: %d\n", material.item.getName(new ItemStack(material.item)).getString(), material.stacks));
            });

            Logger.info("Fetch List:\n%s", sb.toString());
        };
        hlist2.add(printFetchListBtn);
        WButton clearFetchListBtn = theme.button("Clear Fetch List");
        clearFetchListBtn.action = () -> {
            FetchRegistry.INSTANCE.clear();
            Logger.info("Fetch List cleared!");
        };
        hlist2.add(clearFetchListBtn);

        WButton printEventQueueBtn = theme.button("Print Event Queue");
        printEventQueueBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            EventRegistry.INSTANCE.getAll().forEach(event -> {
                sb.append(String.format("Event Type: %s, Wait on Path: %s\n", event.type, event.bWaitOnPath));
            });

            Logger.info("Event Queue:\n%s", sb.toString());
        };
        hlist2.add(printEventQueueBtn);

        WButton clearEventQueueBtn = theme.button("Clear Event Queue");
        clearEventQueueBtn.action = () -> {
            EventRegistry.INSTANCE.clear();
            Logger.info("Event Queue cleared!");
        };
        hlist2.add(clearEventQueueBtn);

        return list;
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) {
        if (!isActive()) return;

        StorageRegistry.INSTANCE.load();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!isActive()) return;

        if (repeatCommandEnabled.get() && !repeatCommandText.get().isBlank()) {
            long intervalMs = (long) (repeatInterval.get() * 60_000.0);
            long now = System.currentTimeMillis();
            if (now - lastRepeatTime >= intervalMs) {
                lastRepeatTime = now;
                if (debugMode.get()) {
                    Logger.info("Repeating command: %s%s", ChatFormatting.WHITE, repeatCommandText.get());
                }
                baritone.getCommandManager().execute(repeatCommandText.get());
            }
        }

        if (!EventRegistry.INSTANCE.isEmpty() && currentEvent == null) {
            currentEvent = EventRegistry.INSTANCE.next();
        }

        if (currentEvent != null) {
            if (debugMode.get()) {
                Logger.info("Executing event: %s", currentEvent.type.toString());
            }

            if (currentEvent.bWaitOnPath && baritone.getPathingBehavior().hasPath() || baritone.getPathingBehavior().isPathing()) {
                // Wait for Baritone to finish pathing
                return;
            }

            currentEvent.callback.run();
            currentEvent = null;
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // Home Shit and anti-stuck shit
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.level == null || !highlightLinkedStorages.get()) return;

        if (!invertHighlight.get()) {
            StorageRegistry.INSTANCE.getAll().forEach(storage -> {
                if (storage.blockPos == null || !mc.level.hasChunkAt(storage.blockPos)) return;

                event.renderer.box(storage.blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            });
        } else {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
                if (!(blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity))
                    continue;

                BlockPos pos = blockEntity.getBlockPos();
                if (!mc.level.hasChunkAt(pos) || StorageRegistry.INSTANCE.find(pos) != null) return;

                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.level == null) return;

        BlockEntity blockEntity = mc.level.getBlockEntity(event.result.getBlockPos());
        if (!(blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity)) {
            if (debugMode.get()) Logger.error("Block entity is not a valid storage type!");
            lastBlockInteractPos = null;
            return;
        }

        lastBlockInteractPos = event.result.getBlockPos();

        if (debugMode.get()) {
            Logger.info("Interacted with block at %s", lastBlockInteractPos);
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String msg = event.getMessage().getString();
        if (msg == null || msg.isEmpty()) return;
        msg = msg.toLowerCase().trim();

        if (!msg.contains("[baritone]") || msg.contains("omegaware")) return;
        int index = msg.indexOf("[baritone]");

        msg = msg.substring(index+10).trim(); // Remove the "[Baritone]" part
        // 10x block{minecraft:black_concrete}[axis=x] 86x block{minecraft:red_concrete} 1x block{minecraft:birch_log}[axis=y]
        if (msg.matches("\\d+x block\\{minecraft:[a-z_]+}.*")) {
            String[] parts = msg.split(" ");

            String blockCount = parts[0].replace("x", "").trim();
            int count = Integer.parseInt(blockCount);
            int stacks = (int) Math.ceil(count / 64.0);

            String blockMessage = parts[1];
            String blockName = blockMessage.substring(blockMessage.indexOf(':') + 1);

            int endIndex = blockName.indexOf('}');
            if (endIndex != -1) {
                blockName = blockName.substring(0, endIndex);
            }

            Identifier identifier = Identifier.parse(blockName);
            Item item = BuiltInRegistries.ITEM.getValue(identifier);

            if (item == null) {
                if (debugMode.get()) Logger.error("Item not found: %s%s", ChatFormatting.WHITE, blockName);
                return;
            }

            if (FetchRegistry.INSTANCE.hasItem(item)) return;

            // Remember where the player was standing the first time materials ran out during this build,
            // so we can send them back here (before continuing) once the fetch trip is done.
            if (materialShortagePos == null && mc.player != null) {
                materialShortagePos = mc.player.blockPosition();
                if (debugMode.get()) {
                    Logger.info("Materials ran out near %s, will return here after fetching", materialShortagePos);
                }
            }

            StorageRegistry.Storage storage = StorageRegistry.INSTANCE.findItem(item);
            if (storage == null) {
                Logger.error("No linked storage contains the item: %s%s", ChatFormatting.WHITE, item.getName(new ItemStack(item)).getString());

                if (disconnectOnError.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    if (autoReconnect.isActive()) {
                        autoReconnect.toggle();
                    }

                    String prefix = Logger.PREFIX.getString();
                    MutableComponent text = Component.literal(String.format("%s%s%s%s %s", ChatFormatting.GRAY, ChatFormatting.BLUE, prefix.substring(0, prefix.length() - 1), ChatFormatting.GRAY, ChatFormatting.RED) + String.format("No linked storage contains the item: %s\n", item.getName(new ItemStack(item)).getString()));

                    disconnectOnError.set(false); // Disable the setting to prevent infinite disconnects

                    ClientPacketListener networkHandler = mc.getConnection();
                    if (networkHandler != null) {
                        networkHandler.getConnection().disconnect(text);
                    }
                }
                return;
            }

            FetchRegistry.INSTANCE.add(new FetchRegistry.Material(item, stacks + extraStacks.get()));
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.FetchItems, true, () -> StorageRegistry.INSTANCE.findItemAndPath(item)));
            return;
        }

        if (msg.contains("done building")) {
            if (debugMode.get()) {
                Logger.info("Baritone has finished building!");
            }

            if (disconnectOnDone.get()) {
                AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                if (autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }

                String prefix = Logger.PREFIX.getString();
                MutableComponent text = Component.literal(String.format("%s%s%s%s %s", ChatFormatting.GRAY, ChatFormatting.BLUE, prefix.substring(0, prefix.length() - 1), ChatFormatting.GRAY, ChatFormatting.RED) + "Baritone has finished building!");

                ClientPacketListener networkHandler = mc.getConnection();
                if (networkHandler != null) {
                    networkHandler.getConnection().disconnect(text);
                }
            }
            return;
        }

        msg = msg.substring(2).trim(); // Remove the "> " part
        if (msg.startsWith("build") || msg.startsWith("litematica")) {
            buildCommand = msg;
            materialShortagePos = null;
            if (debugMode.get()) {
                Logger.info("Build command captured: %s%s", ChatFormatting.WHITE, buildCommand);
            }
            return;
        }

        if (msg.startsWith("stop") || msg.startsWith("cancel")) {
            buildCommand = "";
            currentEvent = null;
            EventRegistry.INSTANCE.clear();
            FetchRegistry.INSTANCE.clear();
            materialShortagePos = null;

            Logger.info("Stop received.");
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive() || mc.player == null || mc.level == null || mc.screen == null || lastBlockInteractPos == null) return;

        if (StorageRegistry.INSTANCE.find(lastBlockInteractPos) != null) {
            StorageRegistry.Storage storage = StorageRegistry.INSTANCE.indexStorage(mc.player.containerMenu, lastBlockInteractPos);
            StorageRegistry.INSTANCE.updateStorage(lastBlockInteractPos, storage.inventory);
            StorageRegistry.INSTANCE.update();
            StorageRegistry.INSTANCE.save();
        }

        if (!FetchRegistry.INSTANCE.isEmpty()) {
            if (!EventRegistry.INSTANCE.eventExists(EventRegistry.Event.EventType.FetchItems)) {
                EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.FetchItems, true, () -> {
                    // The queued callback runs later, by which point the fetch list may have already
                    // been drained by the forEach loop below (or another event) - guard against that
                    // instead of blindly calling getFirst() on a list that's now empty.
                    if (FetchRegistry.INSTANCE.isEmpty()) return;
                    StorageRegistry.INSTANCE.findItemAndPath(FetchRegistry.INSTANCE.get().getFirst().item);
                }));
            }

            StorageRegistry.Storage interactionStorage = StorageRegistry.INSTANCE.find(lastBlockInteractPos);
            if (interactionStorage == null) return;

            FetchRegistry.INSTANCE.get().forEach(material -> {
                if (material.item == null || material.stacks <= 0) return;
                if (!interactionStorage.hasItem(material.item)) return;
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler == null) return;

                MeteorExecutor.execute(() -> {
                    boolean initial = true;
                    int count = 0;

                    int max = 27; // Default size for most chests, shulker boxes, etc.
                    if (handler.getType() == MenuType.GENERIC_9x6) max = 27 * 2;

                    for (int i = 0; i < max; i++) {
                        if (!handler.getSlot(i).hasItem()) continue;

                        int sleep;
                        if (initial) {
                            sleep = 50;
                            initial = false;
                        } else sleep = 70;
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            OmegawareAddons.LOG.error("Interrupted while sleeping in item fetch: {}", e.getMessage());
                        }

                        // Exit if user closes screen or exit world
                        if (mc.screen == null || !Utils.canUpdate()) break;

                        Item item = handler.getSlot(i).getItem().getItem();
                        if (item != material.item) continue;

                        count++;
                        InvUtils.shiftClick().slotId(i);

                        if (count >= material.stacks) {
                            break;
                        }
                    }

                    FetchRegistry.Material updatedMaterial = FetchRegistry.INSTANCE.updateMaterial(material, material.stacks - count);
                    if (debugMode.get()) {
                        Logger.info("Fetched %d stacks of %s%s, %d stacks remaining", count, ChatFormatting.WHITE, material.item.getName(new ItemStack(material.item)).getString(), updatedMaterial != null ? updatedMaterial.stacks : 0);
                    }

                    FetchRegistry.INSTANCE.update();

                    if (FetchRegistry.INSTANCE.isEmpty()) {
                        baritone.getPathingBehavior().cancelEverything();

                        if (returnToBuildSpot.get() && materialShortagePos != null) {
                            BlockPos returnPos = materialShortagePos;
                            materialShortagePos = null; // consumed - next shortage will capture a fresh spot

                            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.PathToPos, true, () -> {
                                if (debugMode.get()) Logger.info("Returning to where materials ran out: %s before resuming build", returnPos);
                                pathToPos(returnPos);
                            }));
                        } else {
                            materialShortagePos = null;
                        }

                        EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.Resume, false, () -> baritone.getCommandManager().execute(buildCommand)));
                    }
                });
            });
        }

        if (!storageLinkMode.get() || StorageRegistry.INSTANCE.find(lastBlockInteractPos) != null) return;

        StorageRegistry.Storage storage = StorageRegistry.INSTANCE.indexStorage(mc.player.containerMenu, lastBlockInteractPos);
        if (storage == null) {
            if (debugMode.get()) Logger.error("Storage is null for block at %s", lastBlockInteractPos);
            return;
        }


        if (storage.inventory.isEmpty()) {
            if (debugMode.get()) Logger.warn("Storage at %s is empty!", lastBlockInteractPos);
            return;
        }

        if (debugMode.get()) {
            Logger.info("Indexed storage at %s with %d items", lastBlockInteractPos, storage.inventory.size());
        }

        StorageRegistry.INSTANCE.add(storage);
        StorageRegistry.INSTANCE.save();

        Logger.info("Storage at %s has been linked!", lastBlockInteractPos);
    }

    public void pathToPos(BlockPos blockPos) {
        if (mc.player == null || mc.level == null) return;

        if (debugMode.get()) Logger.info("%sNavigating to:%s X=%s, Y=%s, Z=%s", ChatFormatting.GREEN, ChatFormatting.WHITE, blockPos.getX(), blockPos.getY(), blockPos.getZ());


        if (!ignoreY.get()) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
        } else baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos.atY(mc.player.blockPosition().getY())));
    }
}
