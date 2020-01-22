package com.minelittlepony.hdskins.skins;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import com.google.common.collect.Sets;
import com.minelittlepony.hdskins.client.HDSkins;
import com.minelittlepony.hdskins.skins.SkinType;
import com.minelittlepony.hdskins.util.IndentedToStringStyle;
import com.minelittlepony.hdskins.util.net.MoreHttpResponses;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.util.UUIDTypeAdapter;

import net.minecraft.client.MinecraftClient;

@ServerType("mojang")
public class YggdrasilSkinServer implements SkinServer {

    static final SkinServer INSTANCE = new YggdrasilSkinServer();

    private static final Set<Feature> FEATURES = Sets.newHashSet(
            Feature.UPLOAD_USER_SKIN,
            Feature.DOWNLOAD_USER_SKIN,
            Feature.DELETE_USER_SKIN,
            Feature.MODEL_VARIANTS,
            Feature.MODEL_TYPES);

    private transient final String address = "https://api.mojang.com";

    private transient final boolean requireSecure = true;

    @Override
    public boolean supportsFeature(Feature feature) {
        return FEATURES.contains(feature);
    }

    @Override
    public TexturePayload loadProfileData(GameProfile profile) throws IOException {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = new HashMap<>();

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftSessionService session = client.getSessionService();

        try {
            textures.putAll(session.getTextures(profile, requireSecure));
        } catch (InsecureTextureException ignored) {
        }

        if (textures.isEmpty()) {
            profile.getProperties().clear();
            if (profile.getId().equals(client.getSession().getProfile().getId())) {
                profile.getProperties().putAll(client.getSessionProperties());
                textures.putAll(session.getTextures(profile, false));
            } else {
                session.fillProfileProperties(profile, requireSecure);

                try {
                    textures.putAll(session.getTextures(profile, requireSecure));
                } catch (InsecureTextureException var6) {
                }
            }
        }

        return new TexturePayload(profile, textures.entrySet().stream().collect(Collectors.toMap(
                entry -> SkinType.forVanilla(entry.getKey()),
                Map.Entry::getValue
        )));
    }

    @Override
    public void performSkinUpload(SkinUpload upload) throws IOException, AuthenticationException {
        switch (upload.getSchemaAction()) {
            case "none":
                send(appendHeaders(upload, RequestBuilder.delete()));
                break;
            default:
                send(prepareUpload(upload, RequestBuilder.put()));
        }
    }

    private RequestBuilder prepareUpload(SkinUpload upload, RequestBuilder request) throws IOException {
        request = appendHeaders(upload, request);
        switch (upload.getSchemaAction()) {
            case "file":
                final File file = new File(upload.getImage());

                MultipartEntityBuilder b = MultipartEntityBuilder.create()
                        .addBinaryBody("file", file, ContentType.create("image/png"), file.getName());

                mapMetadata(upload.getMetadata()).forEach(b::addTextBody);

                return request.setEntity(b.build());
            case "http":
            case "https":
                return request
                        .addParameter("file", upload.getImage().toString())
                        .addParameters(MoreHttpResponses.mapAsParameters(mapMetadata(upload.getMetadata())));
            default:
                throw new IOException("Unsupported URI scheme: " + upload.getSchemaAction());
        }
    }

    private RequestBuilder appendHeaders(SkinUpload upload, RequestBuilder request) {
        return request
                .setUri(URI.create(String.format("%s/user/profile/%s/%s", address,
                        UUIDTypeAdapter.fromUUID(upload.getSession().getProfile().getId()),
                        upload.getType().name().toLowerCase(Locale.US))))
                .addHeader("authorization", "Bearer " + upload.getSession().getAccessToken());
    }

    private Map<String, String> mapMetadata(Map<String, String> metadata) {
        return metadata.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> {
                    String value = entry.getValue();
                    if ("model".contentEquals(entry.getKey()) && "default".contentEquals(value)) {
                        return "classic";
                    }
                    return value;
                })
        );
    }

    private void send(RequestBuilder request) throws IOException {
        try (MoreHttpResponses response = MoreHttpResponses.execute(HDSkins.httpClient, request.build())) {
            if (!response.ok()) {
                throw new IOException(response.json(ErrorResponse.class, "Server error wasn't in json: {}").toString());
            }
        }
    }

    @Override
    public String toString() {
        return new IndentedToStringStyle.Builder(this)
                .append("address", address)
                .append("secured", requireSecure)
                .toString();
    }

    class ErrorResponse {
        String error;
        String errorMessage;

        @Override
        public String toString() {
            return String.format("%s: %s", error, errorMessage);
        }
    }
}