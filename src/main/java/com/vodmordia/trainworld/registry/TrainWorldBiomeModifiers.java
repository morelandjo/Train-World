package com.vodmordia.trainworld.registry;

import com.mojang.serialization.MapCodec;
import com.vodmordia.trainworld.TrainWorld;
import com.vodmordia.trainworld.worldgen.TrainWorldBiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers biome modifiers for Train World.
 */
public class TrainWorldBiomeModifiers {

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, TrainWorld.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<TrainWorldBiomeModifier>> TRAIN_WORLD_MODIFIER =
            BIOME_MODIFIER_SERIALIZERS.register("train_world", () -> TrainWorldBiomeModifier.CODEC);
}
