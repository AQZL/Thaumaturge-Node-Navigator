package dev.thaumaturge.nodenavigator.client;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.client.render.aspect.AspectTagRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

public final class NodeDisplay {
    private static final String DIMENSION_PREFIX = "screen.thaumaturge_node_navigator.dimension.";
    private static final Identifier UNKNOWN_TEXTURE =
            Identifier.fromNamespaceAndPath("thaumaturge", "textures/aspects/_unknown.png");

    private NodeDisplay() {}

    public static Component aspectName(String id) {
        Identifier identifier = aspectIdentifier(id);
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
        Identifier identifier = id == null ? null : Identifier.tryParse(id);
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

    public static void drawAspect(GuiGraphicsExtractor graphics, String id, int x, int y, int size, boolean bright) {
        if (size <= 0) {
            return;
        }
        Identifier identifier = aspectIdentifier(id);
        Holder<IAspect> aspect = identifier == null ? null : resolveAspect(identifier);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) x, (float) y);
            float scale = (float) size / AspectTagRenderer.TAG_SIZE;
            graphics.pose().scale(scale, scale);
            if (aspect != null) {
                AspectTagRenderer.render(graphics, 0, 0, aspect, 0.0F, 0, bright ? 1.0F : 0.30F, false);
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, UNKNOWN_TEXTURE, 0, 0, 0.0F, 0.0F,
                        AspectTagRenderer.TAG_SIZE, AspectTagRenderer.TAG_SIZE,
                        AspectTagRenderer.TEXTURE_SIZE, AspectTagRenderer.TEXTURE_SIZE,
                        AspectTagRenderer.TEXTURE_SIZE, AspectTagRenderer.TEXTURE_SIZE,
                        bright ? 0xFFFFFFFF : 0x4DFFFFFF);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static @Nullable Identifier aspectIdentifier(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return Identifier.tryParse(id.indexOf(':') < 0 ? "thaumaturge:" + id : id);
    }

    private static @Nullable Holder<IAspect> resolveAspect(Identifier id) {
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
