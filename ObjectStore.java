import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public class ObjectStore {
    public byte[] store(byte[] content) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        
        byte[] hash = md.digest(content);

        String hex = HexFormat.of().formatHex(hash);

        String dirName = hex.substring(0, 2);

        Path dir = Paths.get(".git/objects", dirName);
        
        Files.createDirectories(dir);
        Path file = dir.resolve(hex.substring(2));

        Files.write(file, content);

        return hash;
    }

    public Optional<byte[]> load(byte[] hash) {
        String hex = HexFormat.of().formatHex(hash);

        String dirName = hex.substring(0, 2);
        
        Path path = Paths.get(".git/objects", dirName, hex.substring(2));

        if(!Files.exists(path)) return Optional.empty();

        return Optional.of(Files.readAllBytes(path))
    }
}
