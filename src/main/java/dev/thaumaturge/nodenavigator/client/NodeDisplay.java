package dev.thaumaturge.nodenavigator.client;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.client.render.aspect.AspectTagRenderer;
import com.leclowndu93150.thaumaturge.client.render.GuiBlend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import javax.annotation.Nullable;

public final class NodeDisplay {
    private static final String DIMENSION_PREFIX = "screen.thaumaturge_node_navigator.dimension.";
    private static final ResourceLocation UNKNOWN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/aspects/_unknown.png");

    private NodeDisplay() {}

    public static Component aspectName(String id) {
        ResourceLocation identifier = aspectResourceLocation(id);
        if (identifier == null) {
            return Component.translatableWithFallback("tc.aspect.unknown", "Unknown aspect");
        }
        Holder<IAspect> aspect = resolveAspect(identifier);
        if (aspect != null) {
            String key = "aspect." + identifier.getNamespace() + "." + aspect.value().tag();
            if (I18n.exists(key)) {
                return AspectComponents.trueName(aspect);
            }
            return Component.translatableWithFallback(key, humanize(aspect.value().tag()));
        }
        String key = "aspect." + identifier.getNamespace() + "." + identifier.getPath();
        return Component.translatableWithFallback(key, humanize(identifier.getPath()));
    }

    public static Component dimensionName(String id) {
        ResourceLocation identifier = id == null ? null : ResourceLocation.tryParse(id);
        if (identifier == null) {
            return Component.translatableWithFallback(DIMENSION_PREFIX + "unknown", "Unknown dimension");
        }
        if (identifier.getNamespace().equals("minecraft")) {
            switch (identifier.getPath()) {
                case "overworld":
                    return Component.translatableWithFallback(DIMENSION_PREFIX + "overworld", "Overworld");
                case "the_nether":
                    return Component.translatableWithFallback(DIMENSION_PREFIX + "nether", "The Nether");
                case "the_end":
                    return Component.translatableWithFallback(DIMENSION_PREFIX + "end", "The End");
                default:
                    break;
            }
        }
        String path = identifier.getPath();
        String base = "dimension." + identifier.getNamespace() + "." + path;
        String dotted = "dimension." + identifier.getNamespace() + "." + path.replace('/', '.');
        for (String key : new String[] {base, base + ".name", dotted, dotted + ".name"}) {
            if (I18n.exists(key)) {
                return Component.translatable(key);
            }
        }
        if (identifier.getNamespace().equals("thaumaturge") && path.equals("outer_lands")) {
            return Component.translatable(DIMENSION_PREFIX + "outer_lands");
        }
        return Component.translatableWithFallback(DIMENSION_PREFIX + "fallback", "%1$s (%2$s)",
                humanize(path), identifier.getNamespace());
    }

    public static void drawAspect(GuiGraphics graphics, String id, int x, int y, int size, boolean bright) {
        if (size <= 0) {
            return;
        }
        ResourceLocation identifier = aspectResourceLocation(id);
        Holder<IAspect> aspect = identifier == null ? null : resolveAspect(identifier);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate((float) x, (float) y, 0.0F);
            float scale = (float) size / AspectTagRenderer.TAG_SIZE;
            graphics.pose().scale(scale, scale, 1.0F);
            if (aspect != null) {
                AspectTagRenderer.render(graphics, 0, 0, aspect, 0.0F, 0, bright ? 1.0F : 0.30F, false);
            } else {
                GuiBlend.blitTinted(graphics, UNKNOWN_TEXTURE, 0, 0,
                        AspectTagRenderer.TAG_SIZE, AspectTagRenderer.TAG_SIZE,
                        0.0F, 0.0F,
                        AspectTagRenderer.TEXTURE_SIZE, AspectTagRenderer.TEXTURE_SIZE,
                        AspectTagRenderer.TEXTURE_SIZE, AspectTagRenderer.TEXTURE_SIZE,
                        bright ? 0xFFFFFFFF : 0x4DFFFFFF);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static @Nullable ResourceLocation aspectResourceLocation(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(id.indexOf(':') < 0 ? "thaumaturge:" + id : id);
    }

    private static @Nullable Holder<IAspect> resolveAspect(ResourceLocation id) {
        return Aspects.resolve(Minecraft.getInstance().level, ResourceKey.create(IAspect.REGISTRY_KEY, id));
    }

    private static String humanize(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split("[_/.-]+")) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }
}
