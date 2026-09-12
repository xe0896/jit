import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;

public class Refs {
    public void writeRef(String refPath, byte[] hash) throws IOException {
        Path dir = Paths.get(".git", refPath);
        Files.createDirectories(dir);

        String hex = HexFormat.of().formatHex(hash);

        Files.writeString(dir, hex);

    }

    public byte[] readRef(String refPath) {

    }
}
