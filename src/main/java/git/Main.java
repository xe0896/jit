package git;

import static git.cli.ConsoleOutput.EXIT_FAILURE;
import static git.commands.BranchCommands.Switch;
import static git.commands.BranchCommands.branch;
import static git.commands.CommitCommands.commit;
import static git.commands.CommitCommands.log;
import static git.commands.RemoteCommands.pull;
import static git.commands.RemoteCommands.push;
import static git.commands.RemoteCommands.remote;
import static git.commands.RepositoryCommands.add;
import static git.commands.RepositoryCommands.init;
import static git.commands.StatusCommands.status;
import static git.components.Repository.JIT;
import static git.components.Repository.resolveHead;

import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import git.components.ObjectStore;
import git.entities.HeadInfo;
import git.objects.Commit;

public class Main {
   // Supported operations
   private static final String INIT = "init";
   private static final String ADD = "add";
   private static final String STATUS = "status";
   private static final String PULL = "pull";
   private static final String COMMIT = "commit";
   private static final String LOG = "log";
   private static final String SWITCH = "switch";
   private static final String BRANCH = "branch";
   private static final String PUSH = "push";
   private static final String REMOTE = "remote";
   private static final String DUMMY = "dummy";

   public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
      if (args.length == 0)
         throw EXIT_FAILURE("Provide more arguments");

      if (args[0].equals(INIT)) {
         init();
         return;
      }

      if (!Files.exists(JIT))
         throw EXIT_FAILURE("fatal: not a jit repository (do jit init)");

      try {
         switch (args[0]) {
            case DUMMY -> {
               ObjectStore objStore = new ObjectStore();
               HeadInfo head = resolveHead(objStore);
               byte[] hash = head.commit().get().treeHash;
               Commit.commitFiles(hash);
            }
            case INIT -> init();
            case ADD -> {
               if (args.length == 1) {
                  throw EXIT_FAILURE("jit add expects atleast one file name");
               } else {
                  add(Arrays.copyOfRange(args, 1, args.length));
               }
            }
            case STATUS -> status();
            case PULL -> pull();
            case COMMIT -> {
               if (args.length != 3)
                  throw EXIT_FAILURE("jit status expects atleast 2 arguments (jit status <mode> 'message')");

               if (!args[1].equals("-m"))
                  throw EXIT_FAILURE("Only mode allowed is '-m'");

               commit(args[2]);
            }
            case SWITCH -> Switch(Arrays.copyOfRange(args, 1, args.length));
            case LOG -> log();
            case BRANCH -> branch();
            case PUSH -> {
               if (args.length > 3)
                  throw EXIT_FAILURE("jit push currently only supports 'jit push' and 'jit push <remote> <branch>");

               if (args.length == 1) {
                  push();
               } else if (args.length == 3) {
                  push(Arrays.copyOfRange(args, 1, args.length));
               }
            }
            case REMOTE -> remote(Arrays.copyOfRange(args, 1, args.length));
            default -> throw EXIT_FAILURE(String.format("Command '%s' not implemented or does not exist", args[0]));
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}
