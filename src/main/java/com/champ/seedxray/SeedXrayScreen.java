package com.champ.seedxray;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SeedXrayScreen extends Screen {
    private static final int ACCENT = 0xFF35D6C8;
    private static final int TEXT = 0xFFF2F0FF;
    private static final int MUTED = 0xFFAAA4C0;
    private static final int PANEL = 0xFF101B20;
    private static final int BORDER = 0xFF24424A;
    private static final List<String> ORES = List.of(
        "Diamond", "Gold", "Iron", "Redstone", "Lapis", "Emerald", "Coal", "Copper", "Quartz", "Ancient Debris");

    private ButtonWidget activeButton;
    private ButtonWidget tracerButton;
    private ButtonWidget deepslateButton;
    private TextFieldWidget seedField;
    private final ButtonWidget[] oreButtons = new ButtonWidget[ORES.size()];

    public SeedXrayScreen() {
        super(Text.literal("Champ Seed Xray"));
    }

    @Override
    protected void init() {
        int panelW = Math.min(415, width - 12);
        int panelH = 224;
        int x = width / 2 - panelW / 2;
        int y = (height - panelH) / 2;

        seedField = addDrawableChild(new TextFieldWidget(this.textRenderer, x + 20, y + 32, panelW - 40, 18,
            Text.literal("World seed")));
        seedField.setMaxLength(32);
        seedField.setText(SeedXrayConfig.seed);
        seedField.setChangedListener(value -> SeedXrayConfig.seed = value);

        activeButton = addDrawableChild(ButtonWidget.builder(Text.literal("Seed Xray: OFF"), button -> {
            if (!SeedXrayController.isActive()) SeedXrayController.applySeed(client);
            SeedXrayController.toggle(client);
            refresh();
        }).dimensions(x + 20, y + 53, panelW - 40, 18).build());

        int half = (panelW - 46) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply seed"), button -> {
            SeedXrayController.applySeed(client);
            refresh();
        }).dimensions(x + 20, y + 75, half, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> {
            SeedXrayConfig.seed = "";
            seedField.setText("");
            SeedXrayController.disable(client, "Cleared");
            refresh();
        }).dimensions(x + 26 + half, y + 75, half, 16).build());

        int gap = 4;
        int oreW = (panelW - 40 - gap * 4) / 5;
        int oreY = y + 112;
        for (int i = 0; i < ORES.size(); i++) {
            String ore = ORES.get(i);
            int col = i % 5;
            int row = i / 5;
            oreButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal(label(ore)), button -> {
                if (SeedXrayConfig.selected.contains(ore)) SeedXrayConfig.selected.remove(ore);
                else SeedXrayConfig.selected.add(ore);
                refresh();
            }).dimensions(x + 20 + col * (oreW + gap), oreY + row * 18, oreW, 16).build());
        }

        addDrawableChild(new RadiusSlider(x + 20, y + 150, panelW - 40, 16));
        tracerButton = addDrawableChild(ButtonWidget.builder(Text.literal("Tracers"), button -> {
            SeedXrayConfig.tracers = !SeedXrayConfig.tracers;
            refresh();
        }).dimensions(x + 20, y + 170, half, 16).build());
        deepslateButton = addDrawableChild(ButtonWidget.builder(Text.literal("Deepslate only"), button -> {
            SeedXrayConfig.deepslateOnly = !SeedXrayConfig.deepslateOnly;
            refresh();
        }).dimensions(x + 26 + half, y + 170, half, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(x + 20, y + 192, panelW - 40, 18).build());
        refresh();
    }

    private void refresh() {
        activeButton.setMessage(Text.literal(SeedXrayController.isActive() ? "Seed Xray: ON" : "Seed Xray: OFF"));
        tracerButton.setMessage(Text.literal(SeedXrayConfig.tracers ? "[x] Tracers" : "[ ] Tracers"));
        deepslateButton.setMessage(Text.literal(SeedXrayConfig.deepslateOnly ? "[x] Deepslate" : "[ ] Deepslate"));
        for (int i = 0; i < ORES.size(); i++) {
            String ore = ORES.get(i);
            boolean selected = SeedXrayConfig.selected.contains(ore);
            oreButtons[i].setMessage(Text.literal((selected ? "[x] " : "[ ] ") + label(ore)));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        int panelW = Math.min(415, width - 12);
        int panelH = 224;
        int x = width / 2 - panelW / 2;
        int y = (height - panelH) / 2;
        context.fill(x, y, x + panelW, y + panelH, PANEL);
        context.fill(x, y, x + panelW, y + 2, ACCENT);
        context.fill(x, y + panelH - 1, x + panelW, y + panelH, BORDER);
        context.drawText(this.textRenderer, this.title, x + 20, y + 8, TEXT, false);
        context.drawText(this.textRenderer, Text.literal("Seed-based ore simulation - F8 opens, F9 toggles"),
            x + 20, y + 19, MUTED, false);
        context.drawText(this.textRenderer, Text.literal(shorten(SeedXrayController.status(), 58)),
            x + 20, y + 96, MUTED, false);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private static String label(String ore) {
        return ore.equals("Ancient Debris") ? "Debris" : ore;
    }

    private static String shorten(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xA6000000);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        SeedXrayConfig.seed = seedField.getText();
        SeedXrayConfig.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class RadiusSlider extends SliderWidget {
        private RadiusSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (SeedXrayConfig.radius - 1) / 7.0);
            updateMessage();
        }

        private int current() {
            return 1 + (int) Math.round(value * 7);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Scan radius: " + current() + " chunks"));
        }

        @Override
        protected void applyValue() {
            SeedXrayConfig.radius = current();
        }
    }
}
