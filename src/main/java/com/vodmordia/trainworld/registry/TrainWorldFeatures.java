package com.vodmordia.trainworld.registry;

import com.vodmordia.trainworld.TrainWorld;
import com.vodmordia.trainworld.worldgen.TrackFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom features for Train World.
 */
public class TrainWorldFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, TrainWorld.MODID);

    /**
     * The main track network generation feature.
     */
    public static final DeferredHolder<Feature<?>, TrackFeature> TRACK_NETWORK =
            FEATURES.register("track_network",
                    () -> new TrackFeature(NoneFeatureConfiguration.CODEC));
}
