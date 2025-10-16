package com.vodmordia.trainworld.registry;

import com.vodmordia.trainworld.TrainWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Registers configured features (features with configurations) for Train World.
 */
public class TrainWorldConfiguredFeatures {

    /**
     * Track network configured feature.
     */
    public static final ResourceKey<ConfiguredFeature<?, ?>> TRACK_NETWORK_CONFIGURED =
            createKey("track_network_configured");

    /**
     * Creates a resource key for a configured feature.
     */
    private static ResourceKey<ConfiguredFeature<?, ?>> createKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(TrainWorld.MODID, name));
    }

    /**
     * Bootstraps (registers) all configured features.
     */
    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        // Register track network configured feature
        register(context,
                TRACK_NETWORK_CONFIGURED,
                TrainWorldFeatures.TRACK_NETWORK.get(),
                NoneFeatureConfiguration.INSTANCE
        );
    }

    /**
     * Helper method to register a configured feature.
     */
    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(
            BootstrapContext<ConfiguredFeature<?, ?>> context,
            ResourceKey<ConfiguredFeature<?, ?>> key,
            F feature,
            FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
