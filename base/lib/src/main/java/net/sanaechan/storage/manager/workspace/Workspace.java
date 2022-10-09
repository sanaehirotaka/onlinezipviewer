package net.sanaechan.storage.manager.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lombok.Data;
import net.sanaechan.storage.manager.storage.BucketConfig;

@Data
public class Workspace {

	private static final Map<String, Workspace> map = new HashMap<>();

    private long timestamp;

    private String lastLogin;

    private String password;

    private String location;

    private List<BucketConfig> buckets;

    private String opener;

    private Map<String, String> typeOpener;

    public void update() {
        this.timestamp = this.timestamp ^ (this.timestamp << 13);
        this.timestamp = this.timestamp ^ (this.timestamp >> 17);
        this.timestamp = this.timestamp ^ (this.timestamp << 5);
    }

    public static Workspace get(String key) {
        return map.get(key);
    }

    public static String set(Workspace current) {
    	SecureRandom rng = new SecureRandom();
    	byte[] bytes = new byte[32];
    	rng.nextBytes(bytes);
    	String key = Base64.getUrlEncoder().encodeToString(bytes).replaceAll("=", "");
    	map.clear();
    	map.put(key, current);
    	return key;
    }

    public static Path tempDirEncrypted(Workspace workspace) throws IOException {
        Path dir = Path.of(workspace.getLocation(), "temp", "enc");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    public static Path tempDirDecrypted(Workspace workspace) throws IOException {
        Path dir = Path.of(workspace.getLocation(), "temp", "dec");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    public static Path tempFileEncrypted(Workspace workspace) throws IOException {
        return tempFileEncrypted(workspace, "");
    }

    public static Path tempFileDecrypted(Workspace workspace) throws IOException {
        return tempFileDecrypted(workspace, "");
    }

    public static Path tempFileEncrypted(Workspace workspace, String suffix) throws IOException {
        return Files.createTempFile(tempDirEncrypted(workspace), Long.toString(System.nanoTime()), suffix);
    }

    public static Path tempFileDecrypted(Workspace workspace, String suffix) throws IOException {
        return Files.createTempFile(tempDirDecrypted(workspace), Long.toString(System.nanoTime()), suffix);
    }

    public static void cleanTempDir(Workspace workspace) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDirDecrypted(workspace))) {
            for (Iterator<Path> ite = stream.iterator(); ite.hasNext();) {
                shread(ite.next());
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDirEncrypted(workspace))) {
            for (Iterator<Path> ite = stream.iterator(); ite.hasNext();) {
                shreadHeader(ite.next());
            }
        }
    }

    public static void shreadHeader(Path... paths) throws IOException {
        Random rnd = new Random();
        byte[] baseBuffer = new byte[1024 * 16];
        ByteBuffer buffer = ByteBuffer.wrap(baseBuffer);

        for (Path path : paths) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                shred(channel, rnd, baseBuffer, buffer, true);
            }
            Files.deleteIfExists(path);
        }
    }

    public static void shread(Path... paths) throws IOException {
        Random rnd = new Random();
        byte[] baseBuffer = new byte[1024 * 16];
        ByteBuffer buffer = ByteBuffer.wrap(baseBuffer);

        for (Path path : paths) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                shred(channel, rnd, baseBuffer, buffer, false);
            }
            Files.deleteIfExists(path);
        }
    }

    private static void shred(FileChannel channel, Random rnd, byte[] baseBuffer, ByteBuffer buffer, boolean headOnly)
            throws IOException {
        long size = headOnly ? Math.min(buffer.capacity(), channel.size()) : channel.size();
        long position;
        while (size > (position = channel.position())) {
            long remaining = size - position;
            int len = (int) Math.min(buffer.capacity(), remaining);
            rnd.nextBytes(baseBuffer);
            buffer.rewind();
            buffer.limit(len);
            channel.write(buffer);
        }
    }
}
