package com.vodmordia.trainworld.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.slf4j.Logger;

/**
 * Biome modifier that adds train track generation to biomes.
 * This is configured via JSON in data/trainworld/neoforge/biome_modifier/
 */
public record TrainWorldBiomeModifier(
    HolderSet<Biome> biomes,
    Holder<PlacedFeature> feature,
    GenerationStep.Decoration step
) implements BiomeModifier {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<TrainWorldBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(TrainWorldBiomeModifier::biomes),
            PlacedFeature.CODEC.fieldOf("feature").forGetter(TrainWorldBiomeModifier::feature),
            GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(TrainWorldBiomeModifier::step)
        ).apply(instance, TrainWorldBiomeModifier::new)
    );

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // Check if this biome is in our biome set
        if (!biomes.contains(biome)) {
            return;
        }

        // Add our placed feature during the ADD phase
        if (phase == Phase.ADD) {
            builder.getGenerationSettings().addFeature(step, feature);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}

