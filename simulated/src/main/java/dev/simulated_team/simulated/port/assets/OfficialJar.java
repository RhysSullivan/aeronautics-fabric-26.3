package dev.simulated_team.simulated.port.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PORT-26.3: the official Create Aeronautics release jar, the only source of the mods' art, models, sounds and other
 * {@code assets/}. Our jars carry no file from upstream {@code assets/} (All Rights Reserved); the game reads them
 * from this jar, unmodified, and {@link AssetOverlay} derives the few files 26.3 needs in a different form.
 *
 * <p>The jar is a NeoForge bundle: the three mods' jars sit inside it under {@code META-INF/jarjar/}. They are
 * unpacked once, byte for byte, next to it and read in place. The official jar is never put on the mod path, so none
 * of its code loads.
 */
public final class OfficialJar {
    public static final String FILE_NAME = "create-aeronautics-bundled-1.21.1-1.3.0.jar";
    public static final String URL = "https://cdn.modrinth.com/data/oWaK0Q19/versions/w7zlLnea/" + FILE_NAME;
    public static final String SHA512 = "2abba2e166a0ec8d42ab06108b63070d61f985420ecca8739c5b2300561b31486b69b3ad13310b0c459edb9edebeffb55a4cdf4ce493805833d32f5bde9ce778";
    public static final long SIZE = 33120013L;
    /** Nested jars, in the order their files are searched. */
    public static final List<String> NESTED = List.of(
            "META-INF/jarjar/dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.0.jar",
            "META-INF/jarjar/dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.0.jar",
            "META-INF/jarjar/dev.ryanhcode.offroad.offroad-neoforge-1.21.1-1.3.0.jar");

    private OfficialJar() {}

    /** {@code <game dir>/aeronautics-assets}, or {@code -Daeronautics.assets.dir}. */
    public static Path directory(final Path gameDir) {
        final String override = System.getProperty("aeronautics.assets.dir");
        return override != null ? Path.of(override) : gameDir.resolve("aeronautics-assets");
    }

    /**
     * The nested jars, opened. Fetches the official jar from Modrinth first if it is missing (unless
     * {@code -Daeronautics.assets.noDownload=true}), and checks its SHA-512 before anything is read from it.
     */
    public static List<ZipFile> open(final Path dir) throws IOException {
        Files.createDirectories(dir);
        final Path jar = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(jar)) {
            if (Boolean.getBoolean("aeronautics.assets.noDownload")) throw missing(jar, null);
            try {
                download(jar);
            } catch (final IOException e) {
                throw missing(jar, e);
            }
        }
        final Path stamp = dir.resolve("unpacked.sha512");
        final boolean unpacked = Files.isRegularFile(stamp) && Files.readString(stamp).trim().equals(SHA512)
                && NESTED.stream().allMatch(n -> Files.isRegularFile(dir.resolve(fileName(n))));
        if (!unpacked) {
            final String actual = sha512(jar);
            if (!actual.equals(SHA512)) {
                throw new IOException("The official Create Aeronautics jar at " + jar + " is not the expected file "
                        + "(SHA-512 " + actual + ", expected " + SHA512 + "). Delete it and start the game again, or put "
                        + "the file from " + URL + " there.");
            }
            try (ZipFile outer = new ZipFile(jar.toFile())) {
                for (final String n : NESTED) {
                    final ZipEntry e = outer.getEntry(n);
                    if (e == null) throw new IOException(jar + " has no " + n);
                    final Path tmp = dir.resolve(fileName(n) + ".tmp");
                    try (InputStream in = outer.getInputStream(e)) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(tmp, dir.resolve(fileName(n)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
            Files.writeString(stamp, SHA512);
        }
        final List<ZipFile> out = new ArrayList<>();
        for (final String n : NESTED) out.add(new ZipFile(dir.resolve(fileName(n)).toFile()));
        return out;
    }

    private static String fileName(final String nested) {
        return nested.substring(nested.lastIndexOf('/') + 1);
    }

    private static IOException missing(final Path jar, final Exception cause) {
        return new IOException("Create Aeronautics' art and models come from the official release jar, which is not at "
                + jar + (cause != null ? " and could not be downloaded (" + cause + ")" : "") + ". Download " + URL
                + " and put it at that path (see INSTALL.md).", cause);
    }

    private static void download(final Path jar) throws IOException {
        final Path tmp = jar.resolveSibling(FILE_NAME + ".part");
        final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        final HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "simulated-26.3-port (asset fetch)").build();
        try {
            final HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " from " + URL);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (Files.size(tmp) != SIZE || !sha512(tmp).equals(SHA512)) {
            Files.deleteIfExists(tmp);
            throw new IOException("downloaded file does not match the expected SHA-512");
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String sha512(final Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            final MessageDigest md = MessageDigest.getInstance("SHA-512");
            final byte[] buf = new byte[1 << 16];
            for (int r; (r = in.read(buf)) > 0; ) md.update(buf, 0, r);
            return HexFormat.of().formatHex(md.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
