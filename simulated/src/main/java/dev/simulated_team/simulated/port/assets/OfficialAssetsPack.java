package dev.simulated_team.simulated.port.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PORT-26.3: a required client resource pack that reads Create Aeronautics' assets from the official release jar
 * ({@link OfficialJar}), unmodified, with {@link AssetOverlay}'s 26.3 forms over them. Sits just above vanilla, under
 * every other pack, so resource packs still override it.
 */
public final class OfficialAssetsPack implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID = "aeronautics_official_assets";
    private static final PackLocationInfo LOCATION = new PackLocationInfo(ID, Component.literal("Create Aeronautics (official assets)"), PackSource.BUILT_IN, Optional.empty());
    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);

    private static @Nullable OfficialAssetsPack instance;

    private final List<ZipFile> zips;
    /** path (assets/...) to the release jars holding it, in search order. */
    private final Map<String, List<ZipFile>> index = new LinkedHashMap<>();
    private final AssetOverlay overlay;
    /** every path served: release files plus generated ones. */
    private final TreeSet<String> paths = new TreeSet<>();

    private OfficialAssetsPack(final List<ZipFile> zips) {
        this.zips = zips;
        for (final ZipFile z : zips) {
            z.stream().filter(e -> !e.isDirectory() && e.getName().startsWith("assets/"))
                    .forEach(e -> index.computeIfAbsent(e.getName(), k -> new ArrayList<>(1)).add(z));
        }
        overlay = new AssetOverlay(this::officialBytes, index.keySet(), OfficialAssetsPack::updateStructure);
        paths.addAll(index.keySet());
        paths.addAll(overlay.generatedPaths());
    }

    /** The repository source registered for client resources (see {@code PackRepositoryMixin}). */
    public static RepositorySource source() {
        return out -> {
            final OfficialAssetsPack pack = get();
            out.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
                @Override
                public PackResources openMetadata(final PackLocationInfo location) {
                    return pack;
                }

                @Override
                public Stream<PackResources> openResources(final PackLocationInfo location, final Pack.Metadata metadata) {
                    return Stream.of(pack);
                }
            }, new Pack.Metadata(Component.literal("Art, models and sounds from the official release jar"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of()), SELECTION));
        };
    }

    private static synchronized OfficialAssetsPack get() {
        if (instance == null) {
            try {
                final var dir = OfficialJar.directory(FabricLoader.getInstance().getGameDir());
                instance = new OfficialAssetsPack(OfficialJar.open(dir));
                LOGGER.info("Create Aeronautics assets: {} files from the official release jar in {}, {} derived for 26.3", instance.index.size(), dir, instance.overlay.generatedPaths().size());
            } catch (final IOException e) {
                throw new UncheckedIOException(e.getMessage(), e);
            }
        }
        return instance;
    }

    /** An official file as it is in the release; the atlas files the mods both declare are merged, as packs merge them. */
    private byte @Nullable [] officialBytes(final String path) {
        final List<ZipFile> in = index.get(path);
        if (in == null) return null;
        try {
            if (in.size() == 1 || !path.startsWith("assets/minecraft/atlases/")) return read(in.getFirst(), path);
            final JsonObject merged = new JsonObject();
            final JsonArray sources = new JsonArray();
            for (final ZipFile z : in) {
                final JsonObject o = JsonParser.parseString(new String(read(z, path), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("sources")) sources.addAll(o.getAsJsonArray("sources"));
            }
            merged.add("sources", sources);
            return merged.toString().getBytes(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(final ZipFile z, final String path) throws IOException {
        final ZipEntry e = z.getEntry(path);
        try (InputStream in = z.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    private static byte[] updateStructure(final byte[] gz) throws IOException {
        final CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(gz), NbtAccounter.unlimitedHeap());
        final int from = NbtUtils.getDataVersion(tag, 500);
        final int to = SharedConstants.getCurrentVersion().dataVersion().version();
        if (from >= to) return gz;
        final CompoundTag updated = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, from);
        renameModStates(updated);
        NbtUtils.addCurrentDataVersion(updated);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(gz.length * 2);
        NbtIo.writeCompressed(updated, out);
        return out.toByteArray();
    }

    /**
     * Block states the mods renamed between the 1.21.1 release and the 1.21.11 code this port is built on (vanilla's
     * data fixer knows nothing of them): the stirling engine is the red portable engine, symmetric sails lie on an
     * axis instead of facing up or down, and the altitude sensor has no powered state.
     */
    private static void renameModStates(final CompoundTag structure) {
        final var palette = structure.getListOrEmpty("palette");
        for (int i = 0; i < palette.size(); i++) {
            final CompoundTag state = palette.getCompoundOrEmpty(i);
            final String name = state.getStringOr("Name", "");
            final CompoundTag props = state.getCompoundOrEmpty("Properties");
            if (name.equals("simulated:stirling_engine")) state.putString("Name", "simulated:red_portable_engine");
            if (name.endsWith("_symmetric_sail") && name.startsWith("simulated:") && props.contains("facing")) {
                final String f = props.getStringOr("facing", "up");
                props.remove("facing");
                props.putString("axis", f.equals("up") || f.equals("down") ? "y" : f.equals("east") || f.equals("west") ? "x" : "z");
                state.put("Properties", props);
            }
            if (name.equals("simulated:altitude_sensor") && props.contains("powered")) {
                props.remove("powered");
                state.put("Properties", props);
            }
        }
    }

    private @Nullable IoSupplier<InputStream> supplier(final String path) {
        if (!paths.contains(path)) return null;
        return () -> {
            byte[] b = overlay.read(path);
            if (b == null) b = officialBytes(path);
            if (b == null) throw new IOException("missing " + path);
            return new ByteArrayInputStream(b);
        };
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(final PackType type, final Identifier location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        return supplier("assets/" + location.getNamespace() + "/" + location.getPath());
    }

    @Override
    public void listResources(final PackType type, final String namespace, final String directory, final ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        final String prefix = "assets/" + namespace + "/" + directory + "/";
        for (final String p : paths.subSet(prefix, prefix + Character.MAX_VALUE)) {
            final Identifier id = Identifier.tryBuild(namespace, p.substring(("assets/" + namespace + "/").length()));
            if (id != null) output.accept(id, supplier(p));
        }
    }

    @Override
    public Set<String> getNamespaces(final PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Set.of();
        final Set<String> ns = new HashSet<>();
        for (final String p : paths) ns.add(p.substring(7, p.indexOf('/', 7)));
        return ns;
    }

    @Override
    public PackLocationInfo location() {
        return LOCATION;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(final String... path) {
        return null;
    }

    @Override
    public <T> @Nullable T getMetadataSection(final MetadataSectionType<T> type) {
        return null;
    }

    @Override
    public void close() {
        // shared for the game's lifetime: the zips stay open
    }
}
