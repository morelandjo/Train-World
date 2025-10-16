package com.vodmordia.trainworld.registry;

import com.vodmordia.trainworld.TrainWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;

import java.util.List;

/**
 * Registers placed features (features with placement rules) for Train World.
 */
public class TrainWorldPlacedFeatures {

    /**
     * Track network placed feature - placed in every chunk during SURFACE_STRUCTURES step.
     */
    public static final ResourceKey<PlacedFeature> TRACK_NETWORK_PLACED = createKey("track_network_placed");

    /**
     * Creates a resource key for a placed feature.
     */
    private static ResourceKey<PlacedFeature> createKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(TrainWorld.MODID, name));
    }

    /**
     * Bootstraps (registers) all placed features.
     */
    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        // Register track network placed feature
        register(context,
                TRACK_NETWORK_PLACED,
                configuredFeatures.getOrThrow(TrainWorldConfiguredFeatures.TRACK_NETWORK_CONFIGURED),
                List.of(
                        // Place in every chunk (no rarity filter)
                        // The feature itself handles density through Perlin noise
                        HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                        BiomeFilter.biome()
                )
        );
    }

    /**
     * Helper method to register a placed feature.
     */
    private static void register(BootstrapContext<PlacedFeature> context,
                                  ResourceKey<PlacedFeature> key,
                                  Holder<ConfiguredFeature<?, ?>> feature,
                                  List<PlacementModifier> placement) {
        context.register(key, new PlacedFeature(feature, placement));
    }
}
