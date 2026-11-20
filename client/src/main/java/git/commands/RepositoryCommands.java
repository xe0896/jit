package git.commands;

import static git.cli.ConsoleOutput.EXIT_FAILURE;
import static git.cli.ConsoleOutput.EXIT_SUCCESS;
import static git.commands.RemoteCommands.configInit;
import static git.components.Repository.JIT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Stream;

import git.components.Index;
import git.components.ObjectStore;

public class RepositoryCommands {
   private static final String startingBranch = "master";

   public static void init() throws IOException {
      // Assuming it is a sensible user, not handling errors with regarding to
      // corrupted .jit structure
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
         Files.createDirectory(JIT);
         EXIT_SUCCESS("Re-initialised ./jit");
      }

      Files.createDirectory(JIT.resolve("objects"));
      Files.createDirectory(JIT.resolve("refs"));
      Files.createFile(JIT.resolve("index"));
      Files.createFile(JIT.resolve("HEAD"));
      Files.createFile(JIT.resolve("config"));

      configInit(startingBranch);

      Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/" + startingBranch);

      Files.createDirectory(JIT.resolve("refs").resolve("heads"));
   }

   public static void add(String[] paths) throws IOException, InterruptedException, NoSuchAlgorithmException {
      ObjectStore objStore = new ObjectStore();
      Index index = new Index(objStore);

      for (String path : paths) {
         try {
            index.add(path);
         } catch (NoSuchFileException e) {
            throw EXIT_FAILURE(String.format("path '%s' does not exist\n", path));
         }
      }

   }
}
