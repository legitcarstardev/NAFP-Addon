package xyz.omegaware.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import xyz.omegaware.addon.modules.BetterBaritoneBuild;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.io.File;

public class OmegawareAddons extends MeteorAddon {
    public static final String MOD_ID = "omegaware-addons";
    public static ModMetadata MOD_META;
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("OmegaWare");
    public static final HudGroup HUD_GROUP = new HudGroup("OmegaWare");

    public static BetterBaritoneBuild BETTER_BARITONE_BUILD = null;

    public OmegawareAddons() {
        name = "NAFP-Addon";
        authors = new String[] { "LegitCarStar" };
        color.set(204, 255, 102, 255); // #CCFF66
    }

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "omegaware"), key), Utils.getFileWorldName()), filename);
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing OmegaWare Addons");

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        if (BaritoneUtils.IS_AVAILABLE) {
            BETTER_BARITONE_BUILD = new BetterBaritoneBuild();
            Modules.get().add(BETTER_BARITONE_BUILD);
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "xyz.omegaware.addon";
    }

    @Override
    public String getWebsite() {
        return "https://github.com/Omega172/OmegaWare-Addons";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Omega172", "OmegaWare-Addons", "1.21.4", null);
    }

    @Override
    public String getCommit() {
        String commit = MOD_META.getCustomValue(MOD_ID + ":commit").getAsString();
        return commit.isEmpty() ? null : commit;
    }
}
