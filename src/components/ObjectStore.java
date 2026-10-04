package components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import objects.JitObject;

public class ObjectStore {
    public static final Path OBJECT_PATH = Path.of(".jit/objects");

    /** 
     * Stores jit objects into objects/ with the prefix identifiers that is able to be
     * loaded using its SHA-1 hash to provide it to a hex encoder
     * @param object 
     * @return byte[] SHA-1 hash of the object
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public byte[] store(JitObject object) throws NoSuchAlgorithmException, IOException {
        byte[] hash = object.hash();
        byte[] content = object.serialise();

        // Converts the SHA-1 hash to its hex format to limit it to 20 characters
        // and make it not contain characters that disallow it to be a folder/file name
        String hex = HexFormat.of().formatHex(hash);

        // Take the first 2 letters of hash as the folder
        String dirName = hex.substring(0, 2);

        Path dir = OBJECT_PATH.resolve(dirName);
        Files.createDirectories(dir);

        // File is the characters after the 2 letters of the hex hash
        Path file = dir.resolve(hex.substring(2));

        // Write the content (prefix identifier)
        Files.write(file, content);

        return hash;
    }

    /** 
     * @param hash
     * @return Optional<JitObject>
     * @throws IOException
     */
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
