package com.champ.seedxray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class SeedXrayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static boolean active;
    public static String seed = "";
    public static int radius = 5;
    public static boolean tracers;
    public static boolean deepslateOnly;
    public static Set<String> selected = new LinkedHashSet<>(List.of(
        "Diamond", "Gold", "Iron", "Redstone", "Lapis", "Emerald", "Coal", "Copper", "Quartz", "Ancient Debris"));

    private SeedXrayConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("champ-seed-xray.json");
    }

    public static void load() {
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) return;
            Data data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
            if (data == null) return;
            active = data.active;
            seed = data.seed == null ? "" : data.seed;
            radius = Math.max(1, Math.min(8, data.radius));
            tracers = data.tracers;
            deepslateOnly = data.deepslateOnly;
            if (data.selected != null && !data.selected.isEmpty()) selected = new LinkedHashSet<>(data.selected);
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new Data()), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static final class Data {
        boolean active = SeedXrayConfig.active;
        String seed = SeedXrayConfig.seed;
        int radius = SeedXrayConfig.radius;
        boolean tracers = SeedXrayConfig.tracers;
        boolean deepslateOnly = SeedXrayConfig.deepslateOnly;
        Set<String> selected = SeedXrayConfig.selected;
    }
}
