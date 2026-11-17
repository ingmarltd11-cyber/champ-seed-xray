package com.champ.seedxray;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper.Impl;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.ScatteredOreFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

public final class OreColorUtil {
    public static final class Ore {
        public final int generationStep;
        public final int featureIndex;
        public final String name;
        public final IntProvider count;
        public final HeightProvider height;
        public final HeightContext heightContext;
        public final float rarityChance;
        public final float discardOnAirChance;
        public final int veinSize;
        public final List<OreFeatureConfig.Target> targets;
        public final Color color;
        public final boolean scattered;

        private Ore(PlacedFeature feature, int generationStep, int featureIndex, String name, Color color) {
            this.generationStep = generationStep;
            this.featureIndex = featureIndex;
            this.name = name;
            this.color = color;
            IntProvider count = ConstantIntProvider.create(1);
            HeightProvider height = null;
            float rarityChance = 1.0F;
            for (PlacementModifier modifier : feature.placementModifiers()) {
                if (modifier instanceof CountPlacementModifier placement) {
                    count = placement.count;
                } else if (modifier instanceof HeightRangePlacementModifier placement) {
                    height = placement.height;
                } else if (modifier instanceof RarityFilterPlacementModifier placement) {
                    rarityChance = placement.chance;
                }
            }
            OreFeatureConfig config = (OreFeatureConfig) feature.feature().value().config();
            this.count = count;
            this.height = height;
            this.heightContext = OreColorUtil.heightContext;
            this.rarityChance = rarityChance <= 0.0F ? 1.0F : rarityChance;
            this.discardOnAirChance = config.discardOnAirChance;
            this.veinSize = config.size;
            this.targets = List.copyOf(config.targets);
            this.scattered = feature.feature().value().feature() instanceof ScatteredOreFeature;
        }

    }

    private static final int OVERWORLD_STEP = 6;
    private static final int NETHER_STEP = 7;
    private static volatile HeightContext heightContext;

    private OreColorUtil() {
    }

    public static Map<RegistryKey<Biome>, List<Ore>> forDimension(String dimensionPath) {
        WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
        Impl<PlacedFeature> placedFeatures = lookup.getOrThrow(RegistryKeys.PLACED_FEATURE);
        Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions = lookup
            .getOrThrow(RegistryKeys.WORLD_PRESET)
            .getOrThrow(WorldPresets.DEFAULT)
            .value()
            .createDimensionsRegistryHolder()
            .dimensions();
        DimensionOptions dimension = switch (dimensionPath == null ? "overworld" : dimensionPath) {
            case "the_nether" -> dimensions.get(DimensionOptions.NETHER);
            case "the_end" -> dimensions.get(DimensionOptions.END);
            default -> dimensions.get(DimensionOptions.OVERWORLD);
        };
        if (dimension == null) return Map.of();
        heightContext = new HeightContext(dimension.chunkGenerator(), HeightLimitView.create(-64, 384));
        List<RegistryEntry<Biome>> biomes = dimension.chunkGenerator().getBiomeSource().getBiomes().stream().toList();
        List<IndexedFeatures> indexed = PlacedFeatureIndexer.collectIndexedFeatures(
            biomes,
            biome -> biome.value().getGenerationSettings().getFeatures(),
            true
        );
        Map<PlacedFeature, Ore> byFeature = new HashMap<>();
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_COAL_LOWER, OVERWORLD_STEP, "Coal", new Color(15, 15, 15));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_COAL_UPPER, OVERWORLD_STEP, "Coal", new Color(15, 15, 15));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_IRON_MIDDLE, OVERWORLD_STEP, "Iron", new Color(255, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_IRON_SMALL, OVERWORLD_STEP, "Iron", new Color(255, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_IRON_UPPER, OVERWORLD_STEP, "Iron", new Color(255, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_GOLD, OVERWORLD_STEP, "Gold", new Color(255, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_GOLD_LOWER, OVERWORLD_STEP, "Gold", new Color(255, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_GOLD_EXTRA, OVERWORLD_STEP, "Gold", new Color(255, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_GOLD_NETHER, NETHER_STEP, "Gold", new Color(255, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_GOLD_DELTAS, NETHER_STEP, "Gold", new Color(255, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_REDSTONE, OVERWORLD_STEP, "Redstone", new Color(255, 0, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_REDSTONE_LOWER, OVERWORLD_STEP, "Redstone", new Color(255, 0, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_DIAMOND, OVERWORLD_STEP, "Diamond", new Color(0, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_DIAMOND_BURIED, OVERWORLD_STEP, "Diamond", new Color(0, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_DIAMOND_LARGE, OVERWORLD_STEP, "Diamond", new Color(0, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_DIAMOND_MEDIUM, OVERWORLD_STEP, "Diamond", new Color(0, 255, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_LAPIS, OVERWORLD_STEP, "Lapis", new Color(0, 0, 200));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_LAPIS_BURIED, OVERWORLD_STEP, "Lapis", new Color(0, 0, 200));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_COPPER, OVERWORLD_STEP, "Copper", new Color(255, 120, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_COPPER_LARGE, OVERWORLD_STEP, "Copper", new Color(255, 120, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_EMERALD, OVERWORLD_STEP, "Emerald", new Color(0, 255, 0));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_QUARTZ_NETHER, NETHER_STEP, "Quartz", new Color(200, 200, 200));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_QUARTZ_DELTAS, NETHER_STEP, "Quartz", new Color(200, 200, 200));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_DEBRIS_SMALL, NETHER_STEP, "Ancient Debris", new Color(200, 30, 255));
        add(byFeature, placedFeatures, indexed, OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE, NETHER_STEP, "Ancient Debris", new Color(200, 30, 255));
        Map<RegistryKey<Biome>, List<Ore>> result = new HashMap<>();
        for (RegistryEntry<Biome> biome : biomes) {
            RegistryKey<Biome> key = biome.getKey().orElse(null);
            if (key == null) continue;
            List<Ore> ores = biome.value()
                .getGenerationSettings()
                .getFeatures()
                .stream()
                .flatMap(RegistryEntryList::stream)
                .map(RegistryEntry::value)
                .filter(byFeature::containsKey)
                .map(byFeature::get)
                .toList();
            result.put(key, ores);
        }
        return result;
    }

    private static void add(
        Map<PlacedFeature, Ore> byFeature,
        Impl<PlacedFeature> placedFeatures,
        List<IndexedFeatures> indexed,
        RegistryKey<PlacedFeature> key,
        int generationStep,
        String name,
        Color color
    ) {
        PlacedFeature feature = placedFeatures.getOrThrow(key).value();
        int featureIndex = indexed.get(generationStep).indexMapping().applyAsInt(feature);
        byFeature.put(feature, new Ore(feature, generationStep, featureIndex, name, color));
    }
}
