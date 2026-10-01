package ru.monolith.arsenal.compat.valkyrienskies;

import com.electronwill.nightconfig.core.CommentedConfig;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.valkyrienskies.mod.common.config.VSConfigUpdater;
import ru.monolith.arsenal.MonolithArsenal;

/**
 * Patch for the Valkyrien Skies 1.21.11 port (2.4.205+0d0017dd8a): its Fabric initializer registers the VS TOML
 * configs through {@code fuzs.forgeconfigapiport.fabric.api.neoforge.v4}, which no Forge Config API Port build for
 * Minecraft 1.21.9+ contains (they ship {@code api.v5}). VS catches the resulting NoClassDefFoundError and runs on
 * defaults. This repeats exactly the same registration (same specs, file names and load handler) through the v5 API.
 * It does nothing if the v4 API exists, so a fixed VS build is not registered twice.
 */
public final class ValkyrienSkiesConfigBridge {
    private static final String MOD_ID = "valkyrienskies";
    private static final String LEGACY_API = "fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry";
    private static boolean applied;

    private ValkyrienSkiesConfigBridge() {
    }

    public static void apply() {
        if (applied) {
            return;
        }
        applied = true;
        try {
            Class.forName(LEGACY_API, false, ValkyrienSkiesConfigBridge.class.getClassLoader());
            MonolithArsenal.LOGGER.info("Forge Config API Port v4 API present; Valkyrien Skies registers its own configs");
            return;
        } catch (ClassNotFoundException expected) {
            // The case this bridge exists for.
        }

        // Listen first: STARTUP configs are loaded inside register(), so a listener added afterwards misses them.
        ModConfigEvents.loading(MOD_ID).register(ValkyrienSkiesConfigBridge::applyLoadedConfig);
        ModConfigEvents.reloading(MOD_ID).register(ValkyrienSkiesConfigBridge::applyLoadedConfig);
        VSConfigUpdater updater = VSConfigUpdater.INSTANCE;
        ConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.STARTUP, (IConfigSpec) updater.getCORE_SERVER_SPEC(), "valkyrienskies-core-server.toml");
        ConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.SERVER, (IConfigSpec) updater.getSERVER_SPEC(), "valkyrienskies-server.toml");
        ConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.COMMON, (IConfigSpec) updater.getCOMMON_SPEC(), "valkyrienskies-common.toml");
        ConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.CLIENT, (IConfigSpec) updater.getCLIENT_SPEC(), "valkyrienskies-client.toml");
        MonolithArsenal.LOGGER.info("Valkyrien Skies TOML configs registered through Forge Config API Port v5 (compatibility bridge)");
    }

    /** Same as VS's own handler: push the loaded TOML values into the VS config objects. */
    private static void applyLoadedConfig(ModConfig config) {
        if (!(config.getSpec() instanceof ModConfigSpec spec)) {
            return;
        }
        var loaded = config.getLoadedConfig();
        if (loaded == null) {
            return;
        }
        CommentedConfig values = loaded.config();
        if (values == null) {
            return;
        }
        VSConfigUpdater.INSTANCE.applyFromConfigLoad(spec, key -> values.get(key));
        MonolithArsenal.LOGGER.info("Applied Valkyrien Skies config {}", config.getFileName());
    }
}
