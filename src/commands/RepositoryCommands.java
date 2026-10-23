package commands;

import components.Index;
import components.ObjectStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Stream;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static components.Repository.JIT;

public class RepositoryCommands {
    public static void init() throws IOException {
        // Assuming it is a sensible user, not handling errors with regarding to corrupted .jit structure
        try {
            Files.createDirectory(JIT);
        } catch (FileAlreadyExistsException e) {
            try (Stream<Path> paths = Files.walk(JIT)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException a) {
                                throw new UncheckedIOException(a);
                            }
                        });
            }
            System.out.println("Re-initialised ./jit");
            Files.createDirectory(JIT);
        }

        Files.createDirectory(JIT.resolve("objects"));
        Files.createDirectory(JIT.resolve("refs"));
        Files.createFile(JIT.resolve("index"));
        Files.createFile(JIT.resolve("HEAD"));
        Files.createFile(JIT.resolve("config"));

        Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/master");

        Files.createDirectory(JIT.resolve("refs").resolve("heads"));
    }

    public static void add(String[] paths) throws IOException, InterruptedException, NoSuchAlgorithmException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        for (String path : paths) {
            try {
                index.add(path);
            } catch (NoSuchFileException e) {
                EXIT_FAILURE(String.format("path '%s' does not exist\n", path));
            }
        }

    }
}
