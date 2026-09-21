package components;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.Optional;

public class Refs {
    // Refs are human readable names that point at commit hashes, making
    // hashes not being unreadable ".jit/refs/heads/main contains "a3f5c9e8b2..."
    // they are basically pointers with names, a name like main would point
    // to refs/heads/main to get the hash rather than be given a hash
    
    // HEAD is a special ref and points to the ref that the user is currently on
    // ref: refs/heads/main
    public void writeRef(String refPath, byte[] hash) throws IOException {
        Path path = Paths.get(".jit", refPath);
        Files.createDirectories(path);

        String hex = HexFormat.of().formatHex(hash);

        Files.writeString(path, hex);
    }

    public Optional<byte[]> readRef(String refPath) throws IOException {
        Path path = Paths.get(".jit", refPath);

        if(!Files.exists(path)) return Optional.empty();
    
        return Optional.of(Files.readAllBytes(path));
    }
}
