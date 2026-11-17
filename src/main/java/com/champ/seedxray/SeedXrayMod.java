package com.champ.seedxray;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SeedXrayMod implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of("champ-seed-xray", "main"));
    private static KeyBinding openKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        SeedXrayConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.champ-seed-xray.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.champ-seed-xray.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static KeyBinding openKey() {
        return openKey;
    }

    public static KeyBinding toggleKey() {
        return toggleKey;
    }

    private void tick(MinecraftClient client) {
        while (openKey.wasPressed()) client.setScreen(new SeedXrayScreen());
        while (toggleKey.wasPressed()) {
            SeedXrayController.applySeed(client);
            SeedXrayController.toggle(client);
        }
        SeedXrayController.tick(client);
    }
}
