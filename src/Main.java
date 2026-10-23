import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static commands.BranchCommands.Switch;
import static commands.BranchCommands.branch;
import static commands.CommitCommands.commit;
import static commands.CommitCommands.log;
import static commands.RemoteCommands.pull;
import static commands.RemoteCommands.push;
import static commands.RepositoryCommands.add;
import static commands.RepositoryCommands.init;
import static commands.StatusCommands.status;
import static components.Repository.JIT;

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

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        if (args.length == 0)
            EXIT_FAILURE("Provide more arguments");

        if (args[0].equals(INIT)) {
            init();
            return;
        }

        if (!Files.exists(JIT))
            EXIT_FAILURE("fatal: not a jit repository (do jit init)");

        try {
            switch (args[0]) {
                case INIT -> init();
                case ADD -> {
                    if (args.length == 1) {
                        EXIT_FAILURE("jit add expects atleast one file name");
                    } else {
                        add(Arrays.copyOfRange(args, 1, args.length));
                    }
                }
                case STATUS -> status();
                case PULL -> pull();
                case COMMIT -> {
                    if (args.length != 3)
                        EXIT_FAILURE("jit status expects atleast 2 arguments (jit status <mode> 'message')");

                    if (!args[1].equals("-m"))
                        EXIT_FAILURE("Only mode allowed is '-m'");

                    commit(args[2]);
                }
                case SWITCH -> Switch(Arrays.copyOfRange(args, 1, args.length));
                case LOG -> log();
                case BRANCH -> branch();
                case PUSH -> push(Arrays.copyOfRange(args, 1, args.length));
                default -> EXIT_FAILURE(String.format("Command '%s' not implemented or does not exist", args[0]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
