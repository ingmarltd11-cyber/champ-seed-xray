package com.champ.seedxray;

import java.awt.Color;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.ChunkRandom.RandomProvider;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.OreFeatureConfig.Target;

public final class SeedXrayController {
    private static final Pattern LABELED_SEED = Pattern.compile("(?i)seed\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final int MAX_PARTICLES = 320;
    private static final int MAX_TRACERS = 24;

    private static final Map<ChunkKey, Map<OreColorUtil.Ore, Set<Vec3d>>> cache = new ConcurrentHashMap<>();
    private static long seed;
    private static String lastSeedText = "";
    private static boolean active;
    private static boolean validSeed;
    private static Map<RegistryKey<Biome>, List<OreColorUtil.Ore>> oreIndex;
    private static RegistryKey<World> dimension;
    private static ClientWorld world;
    private static int simulatedRadius = -1;
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int particleTick;
    private static int oreCount;
    private static String status = "Enter a seed";

    private SeedXrayController() {
    }

    public static boolean isActive() {
        return active;
    }

    public static String status() {
        return status;
    }

    public static void toggle(MinecraftClient client) {
        if (active) {
            disable(client, "Stopped");
            return;
        }
        if (client.player == null || client.world == null) {
            status = "Join a world first";
            return;
        }
        if (!applySeed(client)) return;
        active = true;
        SeedXrayConfig.active = true;
        SeedXrayConfig.save();
        resetCache();
        status = "Scanning";
        if (client.player != null) client.player.sendMessage(Text.literal("Seed Xray enabled"), true);
    }

    public static boolean applySeed(MinecraftClient client) {
        Long parsed = parseSeed(SeedXrayConfig.seed);
        if (parsed == null) {
            validSeed = false;
            active = false;
            status = "Invalid seed";
            return false;
        }
        seed = parsed;
        lastSeedText = Long.toString(parsed);
        SeedXrayConfig.seed = lastSeedText;
        validSeed = true;
        resetCache();
        status = "Seed ready";
        if (client.player != null) client.player.sendMessage(Text.literal("Seed Xray: seed " + seed), true);
        return true;
    }

    public static void tick(MinecraftClient client) {
        if (!active) return;
        if (!validSeed || client.player == null || client.world == null) {
            disable(client, "World unloaded");
            return;
        }
        if (client.world != world || !client.world.getRegistryKey().equals(dimension)) {
            resetCache();
            world = client.world;
            dimension = world.getRegistryKey();
            oreIndex = OreColorUtil.forDimension(dimension.getValue().getPath());
            if (oreIndex.isEmpty()) {
                disable(client, "No ores for this dimension");
                return;
            }
        }

        ChunkPos center = client.player.getChunkPos();
        if (center.x != lastChunkX || center.z != lastChunkZ) {
            lastChunkX = center.x;
            lastChunkZ = center.z;
            refresh(center);
        }
        if (simulatedRadius != SeedXrayConfig.radius) refresh(center);
        if (++particleTick >= 10) {
            particleTick = 0;
            spawnParticles(client);
        }
        int chunks = 0;
        int ores = 0;
        for (Map<OreColorUtil.Ore, Set<Vec3d>> value : cache.values()) {
            chunks++;
            for (Set<Vec3d> positions : value.values()) ores += positions.size();
        }
        oreCount = ores;
        status = "Seed " + seed + " | " + dimension.getValue().getPath() + " | " + chunks + " chunks | " + ores + " ores";
    }

    public static void disable(MinecraftClient client, String reason) {
        active = false;
        SeedXrayConfig.active = false;
        status = reason;
        resetCache();
        SeedXrayConfig.save();
        if (client.player != null) client.player.sendMessage(Text.literal("Seed Xray: " + reason), true);
    }

    private static void resetCache() {
        cache.clear();
        oreIndex = null;
        world = null;
        dimension = null;
        simulatedRadius = -1;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        oreCount = 0;
    }

    private static void refresh(ChunkPos center) {
        if (world == null || oreIndex == null) return;
        simulatedRadius = SeedXrayConfig.radius;
        for (int dx = -simulatedRadius; dx <= simulatedRadius; dx++) {
            for (int dz = -simulatedRadius; dz <= simulatedRadius; dz++) {
                Chunk chunk = world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk != null) simulate(chunk);
            }
        }
    }

    private static void simulate(Chunk chunk) {
        if (chunk == null || world == null || oreIndex == null) return;
        ChunkPos chunkPos = chunk.getPos();
        ChunkKey key = new ChunkKey(dimension, chunkPos.toLong());
        if (cache.containsKey(key)) return;
        Map<OreColorUtil.Ore, Set<Vec3d>> result = new LinkedHashMap<>();
        Set<OreColorUtil.Ore> candidates = candidates(chunkPos);
        if (!candidates.isEmpty()) {
            ChunkRandom random = new ChunkRandom(RandomProvider.XOROSHIRO.create(0L));
            int originX = chunkPos.x << 4;
            int originZ = chunkPos.z << 4;
            long populationSeed = random.setPopulationSeed(seed, originX, originZ);
            for (OreColorUtil.Ore ore : candidates) {
                random.setDecoratorSeed(populationSeed, ore.featureIndex, ore.generationStep);
                int count = ore.count.get(random);
                Set<Vec3d> positions = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    if (ore.rarityChance > 1.0F && random.nextFloat() >= 1.0F / ore.rarityChance) continue;
                    int x = random.nextInt(16) + originX;
                    int z = random.nextInt(16) + originZ;
                    if (ore.height == null) continue;
                    int y = ore.height.get(random, ore.heightContext);
                    BlockPos origin = new BlockPos(x, y, z);
                    if (!appliesToBiome(ore, biomeAt(origin))) continue;
                    positions.addAll(ore.scattered
                        ? scatteredVein(ore, random, origin)
                        : normalVein(ore, random, origin));
                }
                if (!positions.isEmpty()) result.put(ore, positions);
            }
        }
        cache.put(key, result);
    }

    private static Set<OreColorUtil.Ore> candidates(ChunkPos center) {
        if (oreIndex == null || world == null) return Collections.emptySet();
        Set<OreColorUtil.Ore> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ChunkPos pos : ChunkPos.stream(center, 1).toList()) {
            Chunk chunk = world.getChunk(pos.x, pos.z, ChunkStatus.BIOMES, false);
            if (chunk == null) continue;
            for (ChunkSection section : chunk.getSectionArray()) {
                section.getBiomeContainer().forEachValue(value -> {
                    RegistryKey<Biome> key = ((RegistryEntry<Biome>) value).getKey().orElse(null);
                    if (key != null) result.addAll(oreIndex.getOrDefault(key, List.of()));
                });
            }
        }
        return result;
    }

    private static RegistryKey<Biome> biomeAt(BlockPos pos) {
        if (world == null) return null;
        RegistryEntry<Biome> biome = world.getBiome(pos);
        return biome == null ? null : biome.getKey().orElse(null);
    }

    private static boolean appliesToBiome(OreColorUtil.Ore ore, RegistryKey<Biome> biome) {
        if (biome == null || oreIndex == null) return false;
        List<OreColorUtil.Ore> ores = oreIndex.get(biome);
        return ores != null && ores.contains(ore);
    }

    private static Set<Vec3d> normalVein(OreColorUtil.Ore ore, ChunkRandom random, BlockPos origin) {
        Set<Vec3d> result = new HashSet<>();
        int size = ore.veinSize;
        if (size <= 0) return result;
        float angle = random.nextFloat() * (float) Math.PI;
        float radius = size / 8.0F;
        int padding = MathHelper.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x1 = origin.getX() + Math.sin(angle) * radius;
        double x2 = origin.getX() - Math.sin(angle) * radius;
        double z1 = origin.getZ() + Math.cos(angle) * radius;
        double z2 = origin.getZ() - Math.cos(angle) * radius;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        double y2 = origin.getY() + random.nextInt(3) - 2;
        int minX = origin.getX() - MathHelper.ceil(radius) - padding;
        int minY = origin.getY() - 2 - padding;
        int minZ = origin.getZ() - MathHelper.ceil(radius) - padding;
        int width = 2 * (MathHelper.ceil(radius) + padding);
        int height = 2 * (2 + padding);
        if (minY > world.getTopY(Heightmap.Type.MOTION_BLOCKING, minX, minZ)) return result;
        double[] spheres = new double[size * 4];
        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            double centerX = MathHelper.lerp(progress, x1, x2);
            double centerY = MathHelper.lerp(progress, y1, y2);
            double centerZ = MathHelper.lerp(progress, z1, z2);
            double jitter = random.nextDouble() * size / 16.0;
            double sphereRadius = ((MathHelper.sin((float) Math.PI * progress) + 1.0F) * jitter + 1.0) / 2.0;
            spheres[i * 4] = centerX;
            spheres[i * 4 + 1] = centerY;
            spheres[i * 4 + 2] = centerZ;
            spheres[i * 4 + 3] = sphereRadius;
        }
        for (int i = 0; i < size - 1; i++) {
            if (spheres[i * 4 + 3] <= 0.0) continue;
            for (int j = i + 1; j < size; j++) {
                if (spheres[j * 4 + 3] <= 0.0) continue;
                double dx = spheres[i * 4] - spheres[j * 4];
                double dy = spheres[i * 4 + 1] - spheres[j * 4 + 1];
                double dz = spheres[i * 4 + 2] - spheres[j * 4 + 2];
                double dr = spheres[i * 4 + 3] - spheres[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) spheres[j * 4 + 3] = -1.0;
                    else spheres[i * 4 + 3] = -1.0;
                }
            }
        }
        BitSet seen = new BitSet(width * height * width);
        Mutable mutable = new Mutable();
        int top = world.getTopYInclusive();
        int bottom = world.getBottomY();
        for (int i = 0; i < size; i++) {
            double sphereRadius = spheres[i * 4 + 3];
            if (sphereRadius < 0.0) continue;
            double centerX = spheres[i * 4];
            double centerY = spheres[i * 4 + 1];
            double centerZ = spheres[i * 4 + 2];
            int xStart = Math.max(MathHelper.floor(centerX - sphereRadius), minX);
            int yStart = Math.max(MathHelper.floor(centerY - sphereRadius), minY);
            int zStart = Math.max(MathHelper.floor(centerZ - sphereRadius), minZ);
            int xEnd = Math.max(MathHelper.floor(centerX + sphereRadius), xStart);
            int yEnd = Math.max(MathHelper.floor(centerY + sphereRadius), yStart);
            int zEnd = Math.max(MathHelper.floor(centerZ + sphereRadius), zStart);
            for (int x = xStart; x <= xEnd; x++) {
                double nx = (x + 0.5 - centerX) / sphereRadius;
                if (nx * nx >= 1.0) continue;
                for (int y = yStart; y <= yEnd; y++) {
                    double ny = (y + 0.5 - centerY) / sphereRadius;
                    if (nx * nx + ny * ny >= 1.0) continue;
                    for (int z = zStart; z <= zEnd; z++) {
                        double nz = (z + 0.5 - centerZ) / sphereRadius;
                        if (nx * nx + ny * ny + nz * nz >= 1.0) continue;
                        int index = x - minX + (y - minY) * width + (z - minZ) * width * height;
                        if (seen.get(index)) continue;
                        seen.set(index);
                        mutable.set(x, y, z);
                        if (y < bottom || y > top) continue;
                        if (matchesTarget(ore, mutable, random)) result.add(new Vec3d(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private static Set<Vec3d> scatteredVein(OreColorUtil.Ore ore, ChunkRandom random, BlockPos origin) {
        Set<Vec3d> result = new HashSet<>();
        int count = random.nextInt(ore.veinSize + 1);
        for (int i = 0; i < count; i++) {
            int radius = Math.min(i, 7);
            int x = offset(random, radius) + origin.getX();
            int y = offset(random, radius) + origin.getY();
            int z = offset(random, radius) + origin.getZ();
            BlockPos pos = new BlockPos(x, y, z);
            if (matchesTarget(ore, pos, random)) result.add(new Vec3d(x, y, z));
        }
        return result;
    }

    private static int offset(ChunkRandom random, int radius) {
        return Math.round((random.nextFloat() - random.nextFloat()) * radius);
    }

    private static boolean matchesTarget(OreColorUtil.Ore ore, BlockPos pos, ChunkRandom random) {
        if (world == null) return false;
        BlockState state = world.getBlockState(pos);
        for (Target target : ore.targets) {
            if (!state.isOf(target.state.getBlock()) && !target.target.test(state, random)) continue;
            if (shouldKeep(random, ore.discardOnAirChance) || !hasAirNeighbor(pos)) return true;
        }
        return false;
    }

    private static boolean shouldKeep(ChunkRandom random, float chance) {
        if (chance <= 0.0F) return true;
        if (chance >= 1.0F) return false;
        return random.nextFloat() >= chance;
    }

    private static boolean hasAirNeighbor(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (world.getBlockState(pos.offset(direction)).isAir()) return true;
        }
        return false;
    }

    private static void spawnParticles(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Vec3d eye = client.player.getEyePos();
        int spawned = 0;
        int tracers = 0;
        for (Map.Entry<ChunkKey, Map<OreColorUtil.Ore, Set<Vec3d>>> chunk : cache.entrySet()) {
            if (!chunk.getKey().dimension.equals(dimension)) continue;
            for (Map.Entry<OreColorUtil.Ore, Set<Vec3d>> entry : chunk.getValue().entrySet()) {
                OreColorUtil.Ore ore = entry.getKey();
                if (!SeedXrayConfig.selected.contains(ore.name)) continue;
                for (Vec3d pos : entry.getValue()) {
                    if (SeedXrayConfig.deepslateOnly && pos.y >= 0.0) continue;
                    double distance = eye.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                    if (distance > 64.0 * 64.0) continue;
                    if (spawned++ >= MAX_PARTICLES) return;
                    int rgb = ore.color.getRGB() & 0xFFFFFF;
                    client.world.addParticleClient(new DustParticleEffect(rgb, 1.0F),
                        pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 0.0, 0.0, 0.0);
                    if (SeedXrayConfig.tracers && tracers++ < MAX_TRACERS) {
                        Vec3d center = new Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                        for (int step = 1; step <= 5; step++) {
                            double t = step / 6.0;
                            client.world.addParticleClient(new DustParticleEffect(rgb, 0.7F),
                                eye.x + (center.x - eye.x) * t,
                                eye.y + (center.y - eye.y) * t,
                                eye.z + (center.z - eye.z) * t,
                                0.0, 0.0, 0.0);
                        }
                    }
                }
            }
        }
    }

    private static Long parseSeed(String text) {
        if (text == null) return null;
        Matcher labeled = LABELED_SEED.matcher(text);
        if (labeled.find()) return parseLong(labeled.group(1));
        Matcher any = NUMBER.matcher(text);
        while (any.find()) {
            Long value = parseLong(any.group());
            if (value != null) return value;
        }
        return null;
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ChunkKey(RegistryKey<World> dimension, long packed) {
    }
}
