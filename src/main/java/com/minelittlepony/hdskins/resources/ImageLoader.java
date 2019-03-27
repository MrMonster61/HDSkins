package com.minelittlepony.hdskins.resources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.util.ResourceLocation;

import com.minelittlepony.hdskins.resources.texture.ImageBufferDownloadHD;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public class ImageLoader implements Supplier<ResourceLocation> {

    private static Minecraft mc = Minecraft.getInstance();

    private final ResourceLocation original;

    public ImageLoader(ResourceLocation loc) {
        this.original = loc;
    }

    @Override
    @Nullable
    public ResourceLocation get() {
        NativeImage image = getImage(original);
        final NativeImage updated = new ImageBufferDownloadHD().parseUserSkin(image);
        if (updated == null) {
            return null;
        }
        if (updated == image) {
            // don't load a new image
            return this.original;
        }
        return addTaskAndGet(() -> loadSkin(updated));
    }

    private static <V> V addTaskAndGet(Callable<V> callable) {
        try {
            return mc.addScheduledTask(callable).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static NativeImage getImage(ResourceLocation res) {

        try (InputStream in = mc.getResourceManager().getResource(res).getInputStream()) {
            return NativeImage.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private ResourceLocation loadSkin(NativeImage image) {

        ResourceLocation conv = new ResourceLocation(original.getNamespace() + "-converted", original.getPath());
        boolean success = mc.getTextureManager().loadTexture(conv, new DynamicTexture(image));
        return success ? conv : null;
    }

}