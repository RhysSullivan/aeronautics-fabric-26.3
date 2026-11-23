package dev.simulated_team.simulated.port.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * PORT-26.3: the files Minecraft 26.3 needs in a different form from the official 1.21.1 release, derived here from
 * the official files when they are read. Nothing in this class is a copy of an upstream asset: it holds resource
 * names and the rules that turn the official files into their 26.3 form.
 *
 * <p>What it does, and why (each rule is checked against the assets of the 26.3 build that was flown, see
 * {@code scripts/handout/check_assets.py}):
 * <ul>
 *   <li>item model definitions ({@code assets/NS/items/NAME.json}, new in 1.21.4): one per top-level item model;
 *       the physics staff and plunger launcher use this port's special item models;</li>
 *   <li>a {@code particle} texture on models that had none (26.3 no longer falls back to the first texture);</li>
 *   <li>blockstate and model fixes the 1.21.11 Fabric sources carry over 1.21.1 (analog transmission rotation,
 *       swivel bearing base plate faces, symmetric sail item parent);</li>
 *   <li>the 26.3 equipment asset for the aviator's goggles, using the official armour texture;</li>
 *   <li>the powered linked typewriter item model, from the official item model and powered textures;</li>
 *   <li>atlas sources for sprites this port draws itself (staff and launcher items, diagram entity);</li>
 *   <li>three merging glue messages the Fabric code uses, pointed at the official generic message;</li>
 *   <li>Ponder structures, data-fixed from the release's data version to the running game's;</li>
 *   <li>the laser shaders as 26.3 core shaders, built from the official Veil shader bodies.</li>
 * </ul>
 */
public final class AssetOverlay {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> NAMESPACES = List.of("simulated", "aeronautics", "offroad");
    private static final Set<String> SPECIAL_ITEMS = Set.of("simulated:creative_physics_staff", "simulated:plunger_launcher");

    /** Models given an explicit particle texture: the first texture they declare. */
    private static final List<String> PARTICLE = List.of(
            "aeronautics/models/block/adjustable_burner/redstone_indicator.json",
            "aeronautics/models/block/andesite_propeller/item.json",
            "aeronautics/models/block/gyroscopic_propeller_bearing/item.json",
            "aeronautics/models/block/gyroscopic_propeller_bearing/metal_bearing_plate.json",
            "aeronautics/models/block/gyroscopic_propeller_bearing/piston_head.json",
            "aeronautics/models/block/gyroscopic_propeller_bearing/piston_pole.json",
            "aeronautics/models/block/mounted_potato_cannon/partials/barrel.json",
            "aeronautics/models/block/mounted_potato_cannon/partials/bellow.json",
            "aeronautics/models/block/mounted_potato_cannon/partials/cog.json",
            "offroad/models/block/rockcutting_wheel/item.json",
            "offroad/models/block/rockcutting_wheel/wheel.json",
            "simulated/models/block/directional_gearshift/barrel.json",
            "simulated/models/block/directional_gearshift/barrel_shaft.json",
            "simulated/models/block/directional_gearshift/horizontal/left.json",
            "simulated/models/block/directional_gearshift/vertical/left.json",
            "simulated/models/block/navigation_table/nav_table_pointer.json",
            "simulated/models/block/navigation_table/redstone_indicator.json",
            "simulated/models/block/portable_engine/exhaust_outlet_left.json",
            "simulated/models/block/portable_engine/exhaust_outlet_right.json",
            "simulated/models/block/portable_engine/exhaust_pipe_left.json",
            "simulated/models/block/portable_engine/exhaust_pipe_right.json",
            "simulated/models/block/portable_engine/hatch_bottom.json",
            "simulated/models/block/portable_engine/hatch_top.json",
            "simulated/models/block/portable_engine/heated/exhaust_outlet_left.json",
            "simulated/models/block/portable_engine/heated/exhaust_outlet_right.json",
            "simulated/models/block/portable_engine/heated/exhaust_pipe_left.json",
            "simulated/models/block/portable_engine/heated/exhaust_pipe_right.json",
            "simulated/models/block/portable_engine/heated/hatch_bottom.json",
            "simulated/models/block/portable_engine/heated/hatch_top.json",
            "simulated/models/block/portable_engine/heated/mouth.json",
            "simulated/models/block/portable_engine/mouth.json",
            "simulated/models/block/portable_engine/superheated/exhaust_outlet_left.json",
            "simulated/models/block/portable_engine/superheated/exhaust_outlet_right.json",
            "simulated/models/block/portable_engine/superheated/exhaust_pipe_left.json",
            "simulated/models/block/portable_engine/superheated/exhaust_pipe_right.json",
            "simulated/models/block/portable_engine/superheated/hatch_bottom.json",
            "simulated/models/block/portable_engine/superheated/hatch_top.json",
            "simulated/models/block/portable_engine/superheated/mouth.json",
            "simulated/models/block/rope/knot.json",
            "simulated/models/block/rope/rope.json",
            "simulated/models/block/rope_winch/shaft.json",
            "simulated/models/block/swivel_bearing/ironcog.json",
            "simulated/models/block/swivel_bearing/item.json",
            "simulated/models/item/plunger_launcher/plunger_tether.json",
            "simulated/models/item/plunger_launcher/tether_spool.json");

    /** Converts a Ponder structure (gzipped NBT) from the release's data version to the running game's. */
    public interface StructureUpdater {
        byte[] update(byte[] gzippedNbt) throws Exception;
    }

    private final Function<String, byte[]> official;
    private final StructureUpdater structures;
    /** Generated files: path (under {@code assets/}) to a builder. */
    private final Map<String, java.util.function.Supplier<byte[]>> generated = new TreeMap<>();
    /** Official files rewritten on read: path to a rewrite. */
    private final Map<String, UnaryOperator<byte[]>> rewritten = new LinkedHashMap<>();

    /**
     * @param official reads an official file by its path in the release jars ({@code assets/...}), null if absent
     * @param officialPaths every file path in the release jars
     */
    public AssetOverlay(final Function<String, byte[]> official, final Collection<String> officialPaths, final StructureUpdater structures) {
        this.official = official;
        this.structures = structures;
        for (final String p : officialPaths) {
            final String[] s = p.split("/");
            // assets/NS/models/item/NAME.json -> assets/NS/items/NAME.json
            if (s.length == 5 && s[0].equals("assets") && NAMESPACES.contains(s[1]) && s[2].equals("models") && s[3].equals("item") && s[4].endsWith(".json")) {
                final String id = s[1] + ":" + s[4].substring(0, s[4].length() - 5);
                if (!SPECIAL_ITEMS.contains(id)) generated.put("assets/" + s[1] + "/items/" + s[4], () -> itemDefinition("minecraft:model", s[1] + ":item/" + id.substring(id.indexOf(':') + 1)));
            }
            if (s.length > 3 && s[0].equals("assets") && NAMESPACES.contains(s[1]) && s[2].equals("ponder") && p.endsWith(".nbt")) rewritten.put(p, this::updateStructure);
        }
        for (final String id : SPECIAL_ITEMS) {
            final String ns = id.substring(0, id.indexOf(':')), name = id.substring(id.indexOf(':') + 1);
            generated.put("assets/" + ns + "/items/" + name + ".json", () -> itemDefinition(ns + ":model/" + name, null));
        }
        for (final String p : PARTICLE) rewritten.put("assets/" + p, AssetOverlay::addParticle);

        rewritten.put("assets/simulated/blockstates/analog_transmission.json", b -> json(b, o -> {
            // the 1.21.11 block faces the other way along z: no extra half turn for axis=z
            for (final Map.Entry<String, JsonElement> v : o.getAsJsonObject("variants").entrySet())
                if (v.getKey().contains("axis=z")) v.getValue().getAsJsonObject().remove("y");
        }));
        for (final String m : List.of("block", "block_assembled")) {
            rewritten.put("assets/simulated/models/block/swivel_bearing/" + m + ".json", b -> json(b, o -> {
                // the base plate's inner faces sit inside the bearing; 1.21.11 drops them (they z-fight on 26.3)
                for (final JsonElement e : o.getAsJsonArray("elements")) {
                    final JsonObject el = e.getAsJsonObject();
                    final JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
                    if (from.get(1).getAsDouble() != 0 || to.get(1).getAsDouble() != 1) continue;
                    final double x0 = from.get(0).getAsDouble(), x1 = to.get(0).getAsDouble(), z0 = from.get(2).getAsDouble(), z1 = to.get(2).getAsDouble();
                    final JsonObject faces = el.getAsJsonObject("faces");
                    if (x0 == 0 && x1 == 16 && (z0 == 12 && z1 == 16 || z0 == 0 && z1 == 4)) faces.remove("up");
                    if (z0 == 4 && z1 == 12 && (x0 == 0 && x1 == 4 || x0 == 12 && x1 == 16)) { faces.remove("up"); faces.remove("north"); faces.remove("south"); }
                }
            }));
        }
        rewritten.put("assets/simulated/models/item/white_symmetric_sail.json", b -> json(b, o -> o.addProperty("parent", "simulated:block/symmetric_sail/block")));

        // aviator's goggles: 26.3 equipment asset, drawn with the official armour layer texture
        generated.put("assets/aeronautics/equipment/aviators_goggles.json", () -> utf8("{\"layers\":{\"humanoid\":[{\"texture\":\"aeronautics:aviators_goggles\"}]}}"));
        generated.put("assets/aeronautics/textures/entity/equipment/humanoid/aviators_goggles.png", () -> official.apply("assets/aeronautics/textures/models/armor/aviators_goggles_layer_1.png"));
        // powered linked typewriter item: the official item model with the powered textures
        generated.put("assets/simulated/models/block/linked_typewriter/item_powered.json", () -> utf8("{\"parent\":\"simulated:block/linked_typewriter/item\",\"textures\":{"
                + "\"1\":\"simulated:block/linked_typewriter/linked_typewriter_powered\",\"redstone_antenna\":\"create:block/redstone_antenna_powered\"}}"));
        // atlases: sprites this port draws outside block models
        rewritten.put("assets/minecraft/atlases/blocks.json", b -> json(b, o -> {
            final JsonArray src = o.getAsJsonArray("sources");
            for (final String r : List.of("simulated:item/plungerlauncher", "simulated:item/zapper_staff", "simulated:entity/diagram_large", "simulated:entity/diagram_medium", "simulated:entity/diagram_small")) {
                final JsonObject s = new JsonObject(); s.addProperty("type", "single"); s.addProperty("resource", r); src.add(s);
            }
        }));
        generated.put("assets/minecraft/atlases/items.json", () -> utf8("{\"sources\":[{\"type\":\"filter\",\"pattern\":{\"namespace\":\"simulated\",\"path\":\"item/(plungerlauncher|zapper_staff)\"}}]}"));
        // merging glue: the Fabric code names the failing pair of faces; use the official generic message for all three
        for (final String lang : List.of("en_us", "en_ud")) {
            rewritten.put("assets/simulated/lang/" + lang + ".json", b -> json(b, o -> {
                final JsonElement generic = o.get("simulated.merging_glue.invalid_directions");
                if (generic == null) return;
                for (final String k : List.of("down_down", "horizontal_vertical", "up_up"))
                    if (!o.has("simulated.merging_glue.invalid_directions." + k)) o.add("simulated.merging_glue.invalid_directions." + k, generic);
            }));
        }
        // laser: 26.3 core shaders around the official shader bodies
        generated.put("assets/simulated/shaders/core/laser.vsh", () -> LaserShaders.vertex(official.apply("assets/simulated/pinwheel/shaders/program/laser/laser.vsh")));
        generated.put("assets/simulated/shaders/core/laser.fsh", () -> LaserShaders.fragment(official.apply("assets/simulated/pinwheel/shaders/program/laser/laser.fsh")));
        generated.put("assets/simulated/shaders/core/diagram_outline.fsh", () -> DiagramShaders.fragment(official.apply("assets/simulated/pinwheel/shaders/program/contraption_diagram/outline_diagram.fsh")));
        generated.put("assets/aeronautics/shaders/core/burner_flame.vsh", () -> BurnerFlameShaders.vertex(official.apply("assets/aeronautics/pinwheel/shaders/program/burner_flame.vsh")));
        generated.put("assets/aeronautics/shaders/core/burner_flame.fsh", () -> BurnerFlameShaders.fragment(official.apply("assets/aeronautics/pinwheel/shaders/program/burner_flame.fsh"),
                official.apply("assets/aeronautics/pinwheel/shaders/include/util.glsl")));
    }

    /** Paths this overlay adds that the release does not have. */
    public Set<String> generatedPaths() {
        return generated.keySet();
    }

    /** The 26.3 form of a file, or null when the official file is used as it is (or there is none). */
    public byte[] read(final String path) {
        final java.util.function.Supplier<byte[]> g = generated.get(path);
        if (g != null) return g.get();
        final UnaryOperator<byte[]> r = rewritten.get(path);
        if (r == null) return null;
        final byte[] b = official.apply(path);
        return b == null ? null : r.apply(b);
    }

    private byte[] updateStructure(final byte[] gz) {
        try {
            return structures.update(gz);
        } catch (final Exception e) {
            throw new IllegalStateException("could not update a Ponder structure", e);
        }
    }

    private static byte[] itemDefinition(final String type, final String model) {
        final JsonObject m = new JsonObject();
        m.addProperty("type", type);
        if (model != null) m.addProperty("model", model);
        final JsonObject o = new JsonObject();
        o.add("model", m);
        return utf8(GSON.toJson(o));
    }

    private static byte[] addParticle(final byte[] b) {
        return json(b, o -> {
            final JsonObject t = o.getAsJsonObject("textures");
            if (t != null && !t.has("particle") && !t.isEmpty()) t.addProperty("particle", "#" + t.keySet().iterator().next());
        });
    }

    private static byte[] json(final byte[] b, final java.util.function.Consumer<JsonObject> edit) {
        final JsonObject o = JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        edit.accept(o);
        return utf8(GSON.toJson(o));
    }

    private static byte[] utf8(final String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
