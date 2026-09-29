package components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import objects.JitObject;

public class ObjectStore {
    public static final Path OBJECT_PATH = Path.of(".jit/objects");

    public byte[] store(JitObject object) throws NoSuchAlgorithmException, IOException {
        byte[] hash = object.hash();
        byte[] content = object.serialise();

        String hex = HexFormat.of().formatHex(hash);

        String dirName = hex.substring(0, 2);

        Path dir = OBJECT_PATH.resolve(dirName);

        Files.createDirectories(dir);
        Path file = dir.resolve(hex.substring(2));

        Files.write(file, content);

        return hash;
    }

    public Optional<JitObject> load(byte[] hash) throws IOException {
        String hex = HexFormat.of().formatHex(hash);

        String dirName = hex.substring(0, 2);
        String fileName = hex.substring(2);

        // ab/eo234j2io4j2o4j23, ab is the dirName the file is the fileName
        Path path = OBJECT_PATH.resolve(dirName).resolve(fileName);

        if (!Files.exists(path))
            return Optional.empty();

        JitObject object = JitObject.deserialise(Files.readAllBytes(path));

        return Optional.of(object);
    }
}
