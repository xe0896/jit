package cli;

import components.ObjectStore;
import components.Status;
import entities.Author;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import objects.Commit;
import objects.JitObject;
import objects.Tree;
import objects.Tree.TreeEntry;

import static commands.BranchCommands.branch;
import static commands.CommitCommands.commit;
import static commands.RemoteCommands.push;
import static commands.StatusCommands.status;

public class ConsoleOutput {
    // ANSI color codes, helpful for outputting for stuff like 'jit status'
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";
    public static final String ORANGE = "\u001B[38;5;214m";

    public static String statusPrint(Optional<String> branch, List<Status> staged, List<Status> unstaged,
            List<Status> untracked) {

        StringBuilder bra = new StringBuilder();

        if (branch.isEmpty()) {
            bra.append("No commits yet, therefore no branch\n");
        } else {
            bra.append(String.format("On branch %s\n", branch.get()));
        }

        StringBuilder stage = new StringBuilder();
        StringBuilder unstage = new StringBuilder();
        StringBuilder untrack = new StringBuilder();

        if (!staged.isEmpty()) {
            stage.append("Changes to be committed:\n");
            for (Status status : staged) {
                switch (status) {
                    case Status.Added a -> {
                        statusPrinter(stage, GREEN, "added:", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(stage, GREEN, "deleted:", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(stage, GREEN, "modified:", a.path());
                    }
                    default -> EXIT_FAILURE("Status incorrect enum");
                }
            }
        }
        if (!unstaged.isEmpty()) {
            unstage.append("Changes not staged for commit:\n");
            for (Status status : unstaged) {
                switch (status) {
                    case Status.Untracked a -> {
                        statusPrinter(unstage, RED, "untracked:", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(unstage, RED, "deleted:", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(unstage, RED, "modified:", a.path());
                    }
                    default -> EXIT_FAILURE("Status incorrect enum");
                }
            }
        }
        if (!untracked.isEmpty()) {
            untrack.append("Untracked files:\n");
            for (Status status : untracked) {
                Status.Untracked a = (Status.Untracked) status;
                statusPrinter(untrack, RED, "untracked:", a.path());
            }
        }

        return bra.toString() + stage.toString() + unstage.toString() + untrack.toString();

    }

    public static void statusPrinter(StringBuilder sb, String ANSI, String text, String path) {
        String whitespace = " ";
        sb.append("      ");
        sb.append(ANSI);
        sb.append(text);
        sb.append(whitespace.repeat(11 - text.length()));
        sb.append(path);
        sb.append(RESET);
        sb.append("\n");
    }

    public static void logPrinter(StringBuilder sb, Commit commit) throws NoSuchAlgorithmException, IOException {
        sb.append(ORANGE);
        sb.append("commit ");
        sb.append(HexFormat.of().formatHex(commit.hash()));
        sb.append(RESET);
        sb.append('\n');
        sb.append("Author: ");
        sb.append(commit.author.name());
        sb.append(" <");
        sb.append(commit.author.email());
        sb.append('>');
        sb.append('\n');
        sb.append("Date:   ");
        sb.append(DateTimeFormatter
                .ofPattern("dd MMM yyyy, HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(commit.author.time()));
        sb.append("\n\n");
        sb.append("     " + commit.message);
        sb.append("\n\n");
    }

    public static void treeHashPrint(byte[] hash) throws IOException {
        ObjectStore objStore = new ObjectStore();

        Optional<JitObject> _obj = objStore.load(hash);

        if (_obj.isEmpty())
            EXIT_FAILURE("Expected a tree hash stored in objects/ for the provided hash to print");

        JitObject obj = _obj.get();

        if (!(obj instanceof Tree tree)) {
            // Compiler does not know that EXIT_FAILURE is a [[noreturn]] function
            EXIT_FAILURE("Provided initial hash must be a 'tree' hash");

        } else {
            Deque<Tree> stack = new ArrayDeque<>();
            stack.push(tree);

            while (!stack.isEmpty()) {
                Tree cur = stack.pop();

                List<TreeEntry> list = cur.entries;

                for (TreeEntry entry : list) {
                    if (entry.mode() != 040000) {
                        // Directory
                        System.out.println(entry.path());
                    } else {
                        // File
                        byte[] dirHash = entry.hash();
                        Optional<JitObject> _x = objStore.load(dirHash);
                        if (_x.isEmpty())
                            EXIT_FAILURE("Expected a tree hash stored in objects/ for the provided hash to print");

                        JitObject x = _x.get();

                        if (!(x instanceof Tree parentTree)) {
                            // Compiler does not know that EXIT_FAILURE is a [[noreturn]] function
                            EXIT_FAILURE("Provided hash must be a 'tree' hash");
                        } else {
                            stack.push(parentTree);
                        }
                    }
                }
            }
        }
    }

    public static void EXIT_FAILURE(String message) {
        System.out.println(message);
        System.exit(1);
    }
}
