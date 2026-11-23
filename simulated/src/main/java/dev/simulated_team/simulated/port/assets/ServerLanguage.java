package dev.simulated_team.simulated.port.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PORT-26.3: a dedicated server resolves translations itself (console, text the server flattens). The mods' English
 * strings live in their {@code assets/} lang files, which our jars no longer carry, so on a dedicated server the
 * default language is wrapped to also answer from the official release jar's {@code en_us.json} files (with the
 * same three merging-glue keys {@link AssetOverlay} adds). Without the jar the server still runs; untranslated keys
 * show as keys in its console.
 */
public final class ServerLanguage {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerLanguage() {}

    public static void install() {
        final Map<String, String> extra = new HashMap<>();
        try {
            for (final ZipFile z : OfficialJar.open(OfficialJar.directory(FabricLoader.getInstance().getGameDir()))) {
                for (final String ns : List.of("simulated", "aeronautics", "offroad")) {
                    final ZipEntry e = z.getEntry("assets/" + ns + "/lang/en_us.json");
                    if (e == null) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        final JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                        for (final Map.Entry<String, JsonElement> kv : o.entrySet()) extra.putIfAbsent(kv.getKey(), kv.getValue().getAsString());
                    }
                }
                z.close();
            }
        } catch (final Exception e) {
            LOGGER.warn("Create Aeronautics: the official release jar is unavailable ({}); the server console shows translation keys for these mods", e.getMessage());
            return;
        }
        final String generic = extra.get("simulated.merging_glue.invalid_directions");
        if (generic != null) for (final String k : List.of("down_down", "horizontal_vertical", "up_up")) extra.putIfAbsent("simulated.merging_glue.invalid_directions." + k, generic);
        final Language base = Language.getInstance();
        Language.inject(new Language() {
            @Override
            public String getOrDefault(final String key, final String fallback) {
                return base.has(key) ? base.getOrDefault(key, fallback) : extra.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(final String key) {
                return base.has(key) || extra.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return base.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(final FormattedText text) {
                return base.getVisualOrder(text);
            }
        });
        LOGGER.info("Create Aeronautics: {} server translations from the official release jar", extra.size());
    }
}
