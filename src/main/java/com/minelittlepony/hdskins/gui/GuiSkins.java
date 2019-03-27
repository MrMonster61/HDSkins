package com.minelittlepony.hdskins.gui;

import com.google.common.base.Splitter;
import com.minelittlepony.common.client.gui.Button;
import com.minelittlepony.common.client.gui.GameGui;
import com.minelittlepony.common.client.gui.IGuiAction;
import com.minelittlepony.common.client.gui.IconicButton;
import com.minelittlepony.common.client.gui.IconicToggle;
import com.minelittlepony.common.client.gui.Label;
import com.minelittlepony.common.client.gui.Style;
import com.minelittlepony.hdskins.HDSkins;
import com.minelittlepony.hdskins.SkinChooser;
import com.minelittlepony.hdskins.SkinUploader;
import com.minelittlepony.hdskins.SkinUploader.ISkinUploadHandler;
import com.minelittlepony.hdskins.VanillaModels;
import com.minelittlepony.hdskins.net.SkinServer;
import com.minelittlepony.hdskins.upload.FileDrop;
import com.minelittlepony.hdskins.util.CallableFutures;
import com.minelittlepony.hdskins.util.Edge;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderSkybox;
import net.minecraft.client.renderer.RenderSkyboxCube;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.util.InputMappings;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.file.Path;
import java.util.List;

import static net.minecraft.client.renderer.GlStateManager.*;

public class GuiSkins extends GameGui implements ISkinUploadHandler, FileDrop.IDropCallback {

    private int updateCounter = 0;
    private float lastPartialTick;

    private Button btnBrowse;
    private FeatureButton btnUpload;
    private FeatureButton btnDownload;
    private FeatureButton btnClear;

    private FeatureSwitch btnModeSteve;
    private FeatureSwitch btnModeAlex;

    private FeatureSwitch btnModeSkin;
    private FeatureSwitch btnModeElytra;

    protected EntityPlayerModel localPlayer;
    protected EntityPlayerModel remotePlayer;

    private DoubleBuffer doubleBuffer;

    private float msgFadeOpacity = 0;

    private double lastMouseX = 0;

    private boolean jumpState = false;
    private boolean sneakState = false;

    protected final SkinUploader uploader;
    protected final SkinChooser chooser;

    private final RenderSkybox panorama = new RenderSkybox(new RenderSkyboxCube(getBackground()));

    private final FileDrop dropper = FileDrop.newDropEvent(this);

    private final Edge ctrlKey = new Edge(this::ctrlToggled) {
        @Override
        protected boolean nextState() {
            return GuiScreen.isCtrlKeyDown();
        }
    };
    private final Edge jumpKey = new Edge(this::jumpToggled) {
        @Override
        protected boolean nextState() {
            return InputMappings.isKeyDown(GLFW.GLFW_KEY_SPACE);
        }
    };
    private final Edge sneakKey = new Edge(this::sneakToggled) {
        @Override
        protected boolean nextState() {
            return GuiScreen.isShiftKeyDown();
        }
    };

    public GuiSkins(List<SkinServer> servers) {
        mc = Minecraft.getInstance();
        GameProfile profile = mc.getSession().getProfile();

        localPlayer = getModel(profile);
        remotePlayer = getModel(profile);

        RenderManager rm = mc.getRenderManager();
        rm.textureManager = mc.getTextureManager();
        rm.options = mc.gameSettings;
        rm.renderViewEntity = localPlayer;

        uploader = new SkinUploader(servers, localPlayer, remotePlayer, this);
        chooser = new SkinChooser(uploader);
    }

    protected ResourceLocation getBackground() {
        return new ResourceLocation(HDSkins.MOD_ID, "textures/cubemaps/cubemap0_%d.png");
    }

    protected EntityPlayerModel getModel(GameProfile profile) {
        return new EntityPlayerModel(profile);
    }

    @Override
    public void tick() {

        if (!(InputMappings.isKeyDown(GLFW.GLFW_KEY_LEFT) || InputMappings.isKeyDown(GLFW.GLFW_KEY_RIGHT))) {
            updateCounter++;
        }

        uploader.update();

        updateButtons();
    }

    @Override
    public void initGui() {
        dropper.subscribe();

        addButton(new Label(width / 2, 10, "hdskins.manager", 0xffffff, true));
        addButton(new Label(34, 34, "hdskins.local", 0xffffff));
        addButton(new Label(width / 2 + 34, 34, "hdskins.net", 0xffffff));

        addButton(btnBrowse = new Button(width / 2 - 150, height - 27, 90, 20, "hdskins.options.browse", sender -> {
            chooser.openBrowsePNG(format("hdskins.open.title"));
        })).setEnabled(!mc.mainWindow.isFullscreen());

        addButton(btnUpload = new FeatureButton(width / 2 - 24, height / 2 - 20, 48, 20, "hdskins.options.chevy", sender -> {
            if (uploader.canUpload()) {
                punchServer("hdskins.upload");
            }
        })).setEnabled(uploader.canUpload())
            .setTooltip("hdskins.options.chevy.title");

        addButton(btnDownload = new FeatureButton(width / 2 - 24, height / 2 + 20, 48, 20, "hdskins.options.download", sender -> {
            if (uploader.canClear()) {
                chooser.openSavePNG(format("hdskins.save.title"), mc.getSession().getUsername());
            }
        })).setEnabled(uploader.canClear())
            .setTooltip("hdskins.options.download.title");

        addButton(btnClear = new FeatureButton(width / 2 + 60, height - 27, 90, 20, "hdskins.options.clear", sender ->{
            if (uploader.canClear()) {
                punchServer("hdskins.request");
            }
        })).setEnabled(uploader.canClear());

        addButton(btnBrowse = new Button(width / 2 - 150, height - 27, 90, 20, "hdskins.options.browse", sender ->
                    chooser.openBrowsePNG(format("hdskins.open.title"))))
                .setEnabled(!mc.mainWindow.isFullscreen());

        addButton(new Button(width / 2 - 50, height - 25, 100, 20, "hdskins.options.close", sender ->
                    mc.displayGuiScreen(new GuiMainMenu())));

        addButton(btnModeSteve = new FeatureSwitch(width - 25, 32, sender -> switchSkinMode(VanillaModels.DEFAULT)))
                .setIcon(new ItemStack(Items.LEATHER_LEGGINGS), 0x3c5dcb)
                .setEnabled(VanillaModels.isSlim(uploader.getMetadataField("model")))
                .setTooltip("hdskins.mode.steve")
                .setTooltipOffset(0, 10);

        addButton(btnModeAlex = new FeatureSwitch(width - 25, 51, sender -> switchSkinMode(VanillaModels.SLIM)))
                .setIcon(new ItemStack(Items.LEATHER_LEGGINGS), 0xfff500)
                .setEnabled(VanillaModels.isFat(uploader.getMetadataField("model")))
                .setTooltip("hdskins.mode.alex")
                .setTooltipOffset(0, 10);

        addButton(btnModeSkin = new FeatureSwitch(width - 25, 75, sender -> uploader.setSkinType(Type.SKIN)))
                .setIcon(new ItemStack(Items.LEATHER_CHESTPLATE))
                .setEnabled(uploader.getSkinType() == Type.ELYTRA)
                .setTooltip(format("hdskins.mode." + Type.SKIN.name().toLowerCase()))
                .setTooltipOffset(0, 10);

        addButton(btnModeElytra = new FeatureSwitch(width - 25, 94, sender -> uploader.setSkinType(Type.ELYTRA)))
                .setIcon(new ItemStack(Items.ELYTRA))
                .setEnabled(uploader.getSkinType() == Type.SKIN)
                .setTooltip(format("hdskins.mode." + Type.ELYTRA.name().toLowerCase()))
                .setTooltipOffset(0, 10);

        addButton(new IconicToggle(width - 25, 118, 3, sender -> {
            playSound(SoundEvents.BLOCK_BREWING_STAND_BREW);

            boolean sleep = sender.getValue() == 1;
            boolean ride = sender.getValue() == 2;
            localPlayer.setSleeping(sleep);
            remotePlayer.setSleeping(sleep);

            localPlayer.setRiding(ride);
            remotePlayer.setRiding(ride);
        }))
                .setValue(localPlayer.isPlayerSleeping() ? 1 : 0)
                .setStyle(new Style().setIcon(new ItemStack(Items.IRON_BOOTS, 1)).setTooltip("hdskins.mode.stand"), 0)
                .setStyle(new Style().setIcon(new ItemStack(Items.CLOCK, 1)).setTooltip("hdskins.mode.sleep"), 1)
                .setStyle(new Style().setIcon(new ItemStack(Items.OAK_BOAT, 1)).setTooltip("hdskins.mode.ride"), 2)
                .setTooltipOffset(0, 10);

        addButton(new Button(width - 25, height - 65, 20, 20, "?", sender -> {
            uploader.cycleGateway();
            playSound(SoundEvents.ENTITY_VILLAGER_YES);
            sender.setTooltip(uploader.getGateway());
        }))
                .setTooltip(uploader.getGateway())
                .setTooltipOffset(0, 10);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        try {
            uploader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        HDSkins.getInstance().clearSkinCache();

        dropper.cancel();
    }

    @Override
    public void onDrop(List<Path> paths) {
        paths.stream().findFirst().ifPresent(path -> {
            chooser.selectFile(path);
            updateButtons();
        });
    }

    @Override
    public void onSkinTypeChanged(Type newType) {
        playSound(SoundEvents.BLOCK_BREWING_STAND_BREW);

        btnModeSkin.enabled = newType == Type.ELYTRA;
        btnModeElytra.enabled = newType == Type.SKIN;
    }

    protected void switchSkinMode(String model) {
        playSound(SoundEvents.BLOCK_BREWING_STAND_BREW);

        boolean thinArmType = VanillaModels.isSlim(model);

        btnModeSteve.enabled = thinArmType;
        btnModeAlex.enabled = !thinArmType;

        uploader.setMetadataField("model", model);
        localPlayer.setPreviewThinArms(thinArmType);
        remotePlayer.setPreviewThinArms(thinArmType);
    }

    protected boolean canTakeEvents() {
        return !chooser.pickingInProgress() && uploader.tryClearStatus() && msgFadeOpacity == 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;

        if (canTakeEvents() && super.mouseClicked(mouseX, mouseY, button)) {
            int bottom = height - 40;
            int mid = width / 2;

            if ((mouseX > 30 && mouseX < mid - 30 || mouseX > mid + 30 && mouseX < width - 30) && mouseY > 30 && mouseY < bottom) {
                localPlayer.swingArm(EnumHand.MAIN_HAND);
                remotePlayer.swingArm(EnumHand.MAIN_HAND);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double changeX, double changeY) {
        lastMouseX = mouseX;

        if (canTakeEvents() && super.mouseDragged(mouseX, mouseY, button, changeX, changeY)) {
            updateCounter -= (lastMouseX - mouseX);

            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char keyChar, int keyCode) {
        if (canTakeEvents()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                updateCounter -= 5;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                updateCounter += 5;
            }

            if (!chooser.pickingInProgress() && !uploader.uploadInProgress()) {
                return super.charTyped(keyChar, keyCode);
            }
        }

        return false;
    }

    private void jumpToggled(boolean jumping) {
        if (jumping && ctrlKey.getState()) {
            jumpState = !jumpState;
        }

        jumping |= jumpState;

        localPlayer.setJumping(jumping);
        remotePlayer.setJumping(jumping);
    }

    private void sneakToggled(boolean sneaking) {
        if (sneaking && ctrlKey.getState()) {
            sneakState = !sneakState;
        }

        sneaking |= sneakState;

        localPlayer.setSneaking(sneaking);
        remotePlayer.setSneaking(sneaking);
    }

    private void ctrlToggled(boolean ctrl) {
        if (ctrl) {
            if (sneakKey.getState()) {
                sneakState = !sneakState;
            }

            if (jumpKey.getState()) {
                jumpState = !jumpState;
            }
        }
    }

    @Override
    protected void drawContents(int mouseX, int mouseY, float partialTick) {
        ctrlKey.update();
        jumpKey.update();
        sneakKey.update();

        panorama.render(partialTick);

        float deltaTime = updateCounter + partialTick - lastPartialTick;
        lastPartialTick = updateCounter + partialTick;

        int bottom = height - 40;
        int mid = width / 2;
        int horizon = height / 2 + height / 5;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        drawRect(30, 30, mid - 30, bottom, Integer.MIN_VALUE);
        drawRect(mid + 30, 30, width - 30, bottom, Integer.MIN_VALUE);

        drawGradientRect(30, horizon, mid - 30, bottom, 0x80FFFFFF, 0xffffff);
        drawGradientRect(mid + 30, horizon, width - 30, bottom, 0x80FFFFFF, 0xffffff);

        super.drawContents(mouseX, mouseY, partialTick);

        enableClipping(bottom);

        float yPos = height * 0.75F;
        float xPos1 = width / 4F;
        float xPos2 = width * 0.75F;
        float scale = height / 4F;

        renderPlayerModel(localPlayer, xPos1, yPos, scale, horizon - mouseY, mouseX, partialTick);
        renderPlayerModel(remotePlayer, xPos2, yPos, scale, horizon - mouseY, mouseX, partialTick);

        disableClipping();

        if (chooser.getStatus() != null && !uploader.canUpload()) {
            drawRect(40, height / 2 - 12, width / 2 - 40, height / 2 + 12, 0xB0000000);
            drawCenteredString(fontRenderer, format(chooser.getStatus()), (int) xPos1, height / 2 - 4, 0xffffff);
        }

        if (uploader.downloadInProgress() || uploader.isThrottled() || uploader.isOffline()) {

            int lineHeight = uploader.isThrottled() ? 18 : 12;

            drawRect((int) (xPos2 - width / 4 + 40), height / 2 - lineHeight, width - 40, height / 2 + lineHeight, 0xB0000000);

            if (uploader.isThrottled()) {
                drawCenteredString(fontRenderer, format(SkinUploader.ERR_MOJANG), (int) xPos2, height / 2 - 10, 0xff5555);
                drawCenteredString(fontRenderer, format(SkinUploader.ERR_WAIT, uploader.getRetries()), (int) xPos2, height / 2 + 2, 0xff5555);
            } else if (uploader.isOffline()) {
                drawCenteredString(fontRenderer, format(SkinUploader.ERR_OFFLINE), (int) xPos2, height / 2 - 4, 0xff5555);
            } else {
                drawCenteredString(fontRenderer, format(SkinUploader.STATUS_FETCH), (int) xPos2, height / 2 - 4, 0xffffff);
            }
        }

        boolean uploadInProgress = uploader.uploadInProgress();
        boolean showError = uploader.hasStatus();

        if (uploadInProgress || showError || msgFadeOpacity > 0) {
            if (!uploadInProgress && !showError) {
                msgFadeOpacity -= deltaTime / 10;
            } else if (msgFadeOpacity < 1) {
                msgFadeOpacity += deltaTime / 10;
            }

            msgFadeOpacity = MathHelper.clamp(msgFadeOpacity, 0, 1);
        }

        if (msgFadeOpacity > 0) {
            int opacity = (Math.min(180, (int) (msgFadeOpacity * 180)) & 255) << 24;

            drawRect(0, 0, width, height, opacity);

            String errorMsg = format(uploader.getStatusMessage());

            if (uploadInProgress) {
                drawCenteredString(fontRenderer, errorMsg, width / 2, height / 2, 0xffffff);
            } else if (showError) {
                int blockHeight = (height - fontRenderer.getWordWrappedHeight(errorMsg, width - 10)) / 2;

                drawCenteredString(fontRenderer, format("hdskins.failed"), width / 2, blockHeight - fontRenderer.FONT_HEIGHT * 2, 0xffff55);
                fontRenderer.drawSplitString(errorMsg, 5, blockHeight, width - 10, 0xff5555);
            }
        }

        depthMask(true);
        enableDepthTest();
    }

    private void renderPlayerModel(EntityPlayerModel thePlayer, float xPosition, float yPosition, float scale, float mouseY, float mouseX, float partialTick) {
        mc.getTextureManager().bindTexture(thePlayer.getLocal(Type.SKIN).getTexture());

        enableColorMaterial();
        pushMatrix();
        translatef(xPosition, yPosition, 300);

        scalef(scale, scale, scale);
        rotatef(-15, 1, 0, 0);

        RenderHelper.enableStandardItemLighting();

        float rot = ((updateCounter + partialTick) * 2.5F) % 360;

        rotatef(rot, 0, 1, 0);

        float lookFactor = (float) Math.sin((rot * (Math.PI / 180)) + 45);
        float lookX = (float) Math.atan((xPosition - mouseX) / 20) * 30;

        thePlayer.rotationYawHead = lookX * lookFactor;
        thePlayer.rotationPitch = (float) Math.atan(mouseY / 40) * -20;

        mc.getRenderManager().renderEntity(thePlayer, 0, 0, 0, 0, 1, false);

        popMatrix();
        RenderHelper.disableStandardItemLighting();
        disableColorMaterial();
    }

    /*
     *       /   |
     *     1/    |o      Q = t + q
     *     /q    |       x = xPosition - mouseX
     *     *-----*       sin(q) = o             cos(q) = x        tan(q) = o/x
     *   --|--x------------------------------------
     *     |
     *      mouseX
     */

    private void enableClipping(int yBottom) {
        GL11.glPopAttrib();

        if (doubleBuffer == null) {
            doubleBuffer = BufferUtils.createByteBuffer(32).asDoubleBuffer();
        }

        doubleBuffer.clear();
        doubleBuffer.put(0).put(1).put(0).put(-30).flip();

        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, doubleBuffer);
        doubleBuffer.clear();
        doubleBuffer.put(0).put(-1).put(0).put(yBottom).flip();

        GL11.glClipPlane(GL11.GL_CLIP_PLANE1, doubleBuffer);
        GL11.glEnable(GL11.GL_CLIP_PLANE0);
        GL11.glEnable(GL11.GL_CLIP_PLANE1);
    }

    private void disableClipping() {
        GL11.glDisable(GL11.GL_CLIP_PLANE1);
        GL11.glDisable(GL11.GL_CLIP_PLANE0);

        disableDepthTest();
        enableBlend();
        depthMask(false);
    }

    private void punchServer(String uploadMsg) {
        uploader.uploadSkin(uploadMsg).handle(CallableFutures.callback(this::updateButtons));

        updateButtons();
    }

    private void updateButtons() {
        btnClear.enabled = uploader.canClear();
        btnUpload.enabled = uploader.canUpload() && uploader.supportsFeature(Feature.UPLOAD_USER_SKIN);
        btnDownload.enabled = uploader.canClear() && !chooser.pickingInProgress();
        btnBrowse.enabled = !chooser.pickingInProgress();

        boolean types = !uploader.supportsFeature(Feature.MODEL_TYPES);
        boolean variants = !uploader.supportsFeature(Feature.MODEL_VARIANTS);

        btnModeSkin.setLocked(types);
        btnModeElytra.setLocked(types);

        btnModeSteve.setLocked(variants);
        btnModeAlex.setLocked(variants);

        btnClear.setLocked(!uploader.supportsFeature(Feature.DELETE_USER_SKIN));
        btnUpload.setLocked(!uploader.supportsFeature(Feature.UPLOAD_USER_SKIN));
        btnDownload.setLocked(!uploader.supportsFeature(Feature.DOWNLOAD_USER_SKIN));
    }

    protected class FeatureButton extends Button {
        private List<String> disabledTooltip = Splitter.onPattern("\r?\n|\\\\n").splitToList(format("hdskins.warning.disabled.description"));

        protected boolean locked;

        public FeatureButton(int x, int y, int width, int height, String label, IGuiAction<? extends Button> callback) {
            super(x, y, width, height, label, callback);
        }

        @Override
        protected List<String> getTooltip() {
            if (locked) {
                return disabledTooltip;
            }
            return super.getTooltip();
        }

        @Override
        public Button setTooltip(String tooltip) {
            disabledTooltip = Splitter.onPattern("\r?\n|\\\\n").splitToList(
                    format("hdskins.warning.disabled.title",
                    format(tooltip),
                    format("hdskins.warning.disabled.description")));
            return super.setTooltip(tooltip);
        }

        public void setLocked(boolean lock) {
            locked = lock;
            enabled &= !lock;
        }
    }

    protected class FeatureSwitch extends IconicButton {
        private List<String> disabledTooltip = null;

        protected boolean locked;

        public FeatureSwitch(int x, int y, IGuiAction<? extends IconicButton> callback) {
            super(x, y, callback);
        }

        @Override
        protected List<String> getTooltip() {
            if (locked) {
                return disabledTooltip;
            }
            return super.getTooltip();
        }

        @Override
        public Button setTooltip(String tooltip) {
            disabledTooltip = Splitter.onPattern("\r?\n|\\\\n").splitToList(
                    format("hdskins.warning.disabled.title",
                    format(tooltip),
                    format("hdskins.warning.disabled.description")));
            return super.setTooltip(tooltip);
        }

        public void setLocked(boolean lock) {
            locked = lock;
            enabled &= !lock;
        }
    }
}
