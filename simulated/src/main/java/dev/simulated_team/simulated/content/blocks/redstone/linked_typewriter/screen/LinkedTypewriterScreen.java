package dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen;

import com.mojang.math.Axis;
import com.zurrtum.create.client.catnip.gui.element.GuiGameElement;
import com.zurrtum.create.client.catnip.gui.element.ScreenElement;
import com.zurrtum.create.client.foundation.gui.AllIcons;
import com.zurrtum.create.client.foundation.gui.menu.AbstractSimiContainerScreen;
import com.zurrtum.create.client.foundation.gui.widget.IconButton;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.widgets.ConfirmationWidgetBase;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.widgets.KeyWidget;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimGUITextures;
import dev.simulated_team.simulated.index.SimIcons;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.network.packets.linked_typewriter.TypewriterDisconnectUser;
import dev.simulated_team.simulated.network.packets.linked_typewriter.TypewriterKeySavePacket;
import dev.simulated_team.simulated.util.SimColors;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LinkedTypewriterScreen extends AbstractSimiContainerScreen<LinkedTypewriterMenuCommon> {
    public final @Nullable LinkedTypewriterBlockEntity clientBe;
    public final EntryModifierScreen modifier;

    private final LinkedTypewriterEntries newEntries = new LinkedTypewriterEntries();
    private final KeyEditorScreen keyEditor;
    private final List<KeyRow> rows = new ArrayList<>();

    private SimGUITextures background;
    private IconButton confirmButton;
    private IconButton editBindingsButton;
    private ConfirmationWidgetBase resetButton;
    private GuiGameElement.GuiPartialRenderBuilder typewriterPreview;
    private List<Rect2i> extraAreas = List.of();

    public LinkedTypewriterScreen(final LinkedTypewriterMenuCommon menu, final Inventory inventory,
                                  final Component title) {
        // PORT-26.3: Create Fly takes the window size in the constructor.
        super(menu, inventory, title, SimGUITextures.LINKED_TYPEWRITER_MAIN.width, SimGUITextures.LINKED_TYPEWRITER_MAIN.height);
        this.clientBe = menu.contentHolder;
        if (this.clientBe != null) {
            this.newEntries.addAll(this.clientBe.getTypewriterEntries().getKeyMap());
        }
        this.keyEditor = new KeyEditorScreen(this);
        this.modifier = new EntryModifierScreen(this);
    }

    @Override
    protected void init() {
        this.background = SimGUITextures.LINKED_TYPEWRITER_MAIN;
        super.init();

        this.rows.clear();
        this.createRows();
        this.modifier.init();
        if (this.typewriterPreview != null) {
            this.typewriterPreview.clear();
        }
        if (this.clientBe != null) {
            this.typewriterPreview = GuiGameElement
                    .of(this.isTypewriterPowered()
                            ? SimPartialModels.LINKED_TYPEWRITER_GUI_POWERED
                            : SimPartialModels.LINKED_TYPEWRITER_GUI)
                    // Partial scale is measured in 16-pixel units. The
                    // original preview used a 40-pixel model scale.
                    .scale(2.5f)
                    .padding(16)
                    .transform((pose, partialTick) -> {
                        // The original preview renders a WEST-facing block.
                        // Minecraft bakes that blockstate rotation as +90
                        // degrees in pose space; combined with the 63-degree
                        // camera yaw, this north-facing partial needs 153.
                        // The 16 pixels of padding make a 56-pixel target
                        // around the 40-pixel model, whose center is 28 / 40.
                        pose.translate(0.7, 0.7, 0);
                        pose.rotate(Axis.XP.rotationDegrees(-22));
                        pose.rotate(Axis.YP.rotationDegrees(153));
                        pose.scale(1, -1, 1);
                        pose.translate(-0.5, -0.5, -0.5);
                    });
        }

        this.resetButton = new ConfirmationWidgetBase(
                this.leftPos + 8,
                this.topPos + this.background.height - 24,
                AllIcons.I_TRASH
        ).withMessage(SimLang.translate("linked_typewriter.confirm_delete_all").component())
                .withCallback(() -> this.sendNewKeys(true));
        this.confirmButton = new IconButton(
                this.leftPos + this.background.width - 33,
                this.topPos + this.background.height - 24,
                AllIcons.I_CONFIRM
        ).withCallback(this::onClose);
        this.editBindingsButton = new IconButton(
                this.leftPos + this.background.width - 62,
                this.topPos + this.background.height - 24,
                SimIcons.HAMBURGER
        ).withCallback(() -> this.switchScreen(true));

        this.addMainWidgets();
        this.extraAreas = List.of(new Rect2i(
                this.leftPos + this.background.width - 30,
                this.topPos + this.background.height - 30,
                94,
                94
        ));
    }

    private void addMainWidgets() {
        for (final KeyRow row : this.rows) {
            for (final KeyWidget key : row) {
                this.addTypewriterWidget(key);
            }
        }
        this.addTypewriterWidget(this.resetButton);
        this.addTypewriterWidget(this.confirmButton);
        this.addTypewriterWidget(this.editBindingsButton);
    }

    public void switchScreen(final boolean editor) {
        this.clearWidgets();
        if (editor) {
            this.keyEditor.startEditing();
            return;
        }

        this.keyEditor.endEditing();
        this.resetButton.cancelConfirmation();
        this.setMainWidgetsActive(true);
        this.addMainWidgets();
    }

    private void setMainWidgetsActive(final boolean active) {
        for (final KeyRow row : this.rows) {
            for (final KeyWidget key : row) {
                key.setKeyboardActive(active);
                key.visible = active;
            }
        }
        this.resetButton.active = active;
        this.resetButton.visible = active;
        this.confirmButton.active = active;
        this.confirmButton.visible = active;
        this.editBindingsButton.active = active;
        this.editBindingsButton.visible = active;
    }

    // PORT-26.3: container screens draw their background in extractBackground (as Create Fly's filter screens do).
    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
                                  final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (this.keyEditor.isActive()) {
            this.keyEditor.renderBackground(graphics, partialTick);
        } else {
            this.background.render(graphics, this.leftPos, this.topPos);
            graphics.text(
                    this.font,
                    this.title,
                    this.leftPos + (this.background.width - this.font.width(this.title)) / 2,
                    this.topPos + 4,
                    SimColors.TITLE_DARK_RED,
                    false
            );
            if (!this.modifier.isModifying()) {
                this.renderTypewriter(graphics);
            }
        }

        this.modifier.renderBackground(graphics);
    }

    private void renderTypewriter(final GuiGraphicsExtractor graphics) {
        if (this.typewriterPreview == null) {
            return;
        }

        this.typewriterPreview.partial(this.isTypewriterPowered()
                ? SimPartialModels.LINKED_TYPEWRITER_GUI_POWERED
                : SimPartialModels.LINKED_TYPEWRITER_GUI);

        // This full partial includes the typewriter's keys while retaining the
        // block preview's orientation and center anchor.
        this.typewriterPreview
                .at(
                        this.leftPos + this.background.width,
                        this.topPos + this.background.height - 44
                )
                .render(graphics);
    }

    private boolean isTypewriterPowered() {
        return this.clientBe != null
                && (this.clientBe.powered
                || this.clientBe.getBlockState().getValue(LinkedTypewriterBlock.POWERED));
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY,
                                 final double horizontalAmount, final double verticalAmount) {
        if (this.keyEditor.isActive() && !this.modifier.isModifying()) {
            this.keyEditor.shiftEntries(verticalAmount > 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent click, final boolean doubled) {
        if (this.resetButton != null
                && this.resetButton.confirmation
                && !this.resetButton.isMouseOver(click.x(), click.y())) {
            this.resetButton.cancelConfirmation();
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void onClose() {
        if (this.modifier.getPendingEntry() != null) {
            this.modifier.getPendingEntry().finishModifications();
        }
        this.sendNewKeys(false);

        LinkedTypewriterInteractionHandler.setMode(LinkedTypewriterInteractionHandler.Mode.IDLE);
        LinkedTypewriterInteractionHandler.associateTypewriter(null);
        if (this.clientBe != null) {
            VeilPacketManager.server().sendPacket(
                    new TypewriterDisconnectUser(this.clientBe.getBlockPos()));
        }
        if (this.typewriterPreview != null) {
            this.typewriterPreview.clear();
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.typewriterPreview != null) {
            this.typewriterPreview.clear();
        }
        super.removed();
    }

    public void sendNewKeys(final boolean clearServer) {
        if (clearServer) {
            this.newEntries.clearAll();
        }
        if (this.clientBe != null) {
            VeilPacketManager.server().sendPacket(new TypewriterKeySavePacket(
                    this.newEntries,
                    this.clientBe.getBlockPos(),
                    clearServer
            ));
        }
    }

    public LinkedTypewriterEntries getNewEntries() {
        return this.newEntries;
    }

    public int getTopPos() {
        return this.topPos;
    }

    public int getLeftPos() {
        return this.leftPos;
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> T addTypewriterWidget(
            final T widget) {
        return this.addRenderableWidget(widget);
    }

    public void removeTypewriterWidget(final GuiEventListener widget) {
        this.removeWidget(widget);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return this.keyEditor.isActive() || this.modifier.isModifying()
                ? List.of()
                : this.extraAreas;
    }

    private void createRows() {
        final int x = this.leftPos + 8;
        final int y = this.topPos + 21;
        final KeyRow first = new KeyRow(x, y);
        final KeyRow second = new KeyRow(x, y + 14);
        final KeyRow third = new KeyRow(x, y + 28);
        final KeyRow fourth = new KeyRow(x, y + 42);
        final KeyRow fifth = new KeyRow(x, y + 56);
        final int key = 14;

        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_GRAVE, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_1, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_2, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_3, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_4, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_5, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_6, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_7, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_8, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_9, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_0, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_MINUS, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_EQUALS, null);
        first.addKey(key + 12, com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE, null);
        first.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_DELETE, null);

        second.addKey(key + 6, com.mojang.blaze3d.platform.InputConstants.KEY_TAB, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_Q, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_W, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_E, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_R, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_T, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_Y, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_U, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_I, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_O, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_P, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_LBRACKET, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_RBRACKET, null);
        second.addKey(key + 6, com.mojang.blaze3d.platform.InputConstants.KEY_BACKSLASH, null);
        second.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP, null);

        third.addKey(key + 12, com.mojang.blaze3d.platform.InputConstants.KEY_CAPSLOCK, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_A, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_S, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_D, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_F, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_G, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_H, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_J, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_K, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_L, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_SEMICOLON, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_APOSTROPHE, null);
        third.addKey(key + 14, com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, null);
        third.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN, null);

        fourth.addKey(key + 18, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_Z, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_X, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_C, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_V, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_B, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_N, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_M, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_COMMA, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_PERIOD, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_SLASH, null);
        fourth.addKey(key + 8, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT, null);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_UP, SimIcons.KEY_ARROW_UP);
        fourth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_END, null);

        fifth.addKey(key + 4, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL, null);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_LGUI, null);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_LALT, null);
        fifth.addKey(key + 74, com.mojang.blaze3d.platform.InputConstants.KEY_SPACE, null);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_RALT, null);
        fifth.addKey(key, 101 /* PORT-26.3: SDL_SCANCODE_APPLICATION (no InputConstants name) */, null);
        fifth.addKey(key + 4, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL, null);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_LEFT, SimIcons.KEY_ARROW_LEFT);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_DOWN, SimIcons.KEY_ARROW_DOWN);
        fifth.addKey(key, com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT, SimIcons.KEY_ARROW_RIGHT);

        this.rows.add(first);
        this.rows.add(second);
        this.rows.add(third);
        this.rows.add(fourth);
        this.rows.add(fifth);
    }

    private class KeyRow extends ArrayList<KeyWidget> {
        private final int x;
        private final int y;
        private int width;

        private KeyRow(final int x, final int y) {
            this.x = x;
            this.y = y;
        }

        private void addKey(final int width, final int keyCode, @Nullable final ScreenElement icon) {
            final KeyWidget widget = new KeyWidget(
                    this.x + this.width,
                    this.y,
                    width,
                    keyCode,
                    icon,
                    LinkedTypewriterScreen.this
            );
            widget.withCallback(() -> {
                LinkedTypewriterScreen.this.setMainWidgetsActive(false);
                LinkedTypewriterScreen.this.modifier
                        .startModifying(
                                LinkedTypewriterScreen.this.newEntries.getEntry(keyCode),
                                newEntry -> {
                                    LinkedTypewriterScreen.this.setMainWidgetsActive(true);
                                    if (newEntry != null) {
                                        LinkedTypewriterScreen.this.newEntries.setKey(
                                                newEntry.glfwKeyCode, newEntry);
                                    }
                                }
                        )
                        .keyCode(keyCode);
            });
            this.add(widget);
            this.width += width;
        }
    }
}
