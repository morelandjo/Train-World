package com.vodmordia.trainworld.datagen;

import com.vodmordia.trainworld.TrainWorld;
import com.vodmordia.trainworld.registry.TrainWorldConfiguredFeatures;
import com.vodmordia.trainworld.registry.TrainWorldPlacedFeatures;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation for Train World worldgen features.
 */
@EventBusSubscriber(modid = TrainWorld.MODID)
public class TrainWorldDatagen {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var packOutput = generator.getPackOutput();
        var lookupProvider = event.getLookupProvider();
        var existingFileHelper = event.getExistingFileHelper();

        // Add worldgen provider
        generator.addProvider(
            event.includeServer(),
            new DatapackBuiltinEntriesProvider(
                packOutput,
                lookupProvider,
                new RegistrySetBuilder()
                    .add(Registries.CONFIGURED_FEATURE, TrainWorldConfiguredFeatures::bootstrap)
                    .add(Registries.PLACED_FEATURE, TrainWorldPlacedFeatures::bootstrap),
                Set.of(TrainWorld.MODID)
            )
        );
    }
}
