package cli;

import components.ObjectStore;
import components.Status;
import entities.Author;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
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
import static components.Repository.JIT;

public class ConsoleOutput {
    // ANSI color codes, helpful for outputting for stuff like 'jit status'
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";
    public static final String ORANGE = "\u001B[38;5;214m";

    public static void statusPrint(Optional<String> branch, Optional<Commit> commit, List<Status> staged, List<Status> unstaged,
            List<Status> untracked) throws IOException {

        String commitHash = !commit.isEmpty() ? HexFormat.of().formatHex(commit.get().treeHash) : "";

        PrintStream out = System.out;

        if (branch.isEmpty()) {
            out.print("No commits yet, therefore no branch\n");
        } else {
            out.print(String.format("On branch %s (%s)\n", branch.get(), commitHash));
        }

        if (!staged.isEmpty()) {
            out.print("Changes to be committed:\n");
            for (Status status : staged) {
                switch (status) {
                    case Status.Added a -> {
                        statusPrinter(out, GREEN, "added:", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(out, GREEN, "deleted:", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(out, GREEN, "modified:", a.path());
                    }
                    default -> EXIT_FAILURE("Status incorrect enum");
                }
            }
        }
        if (!unstaged.isEmpty()) {
            out.print("Changes not staged for commit:\n");
            for (Status status : unstaged) {
                switch (status) {
                    case Status.Untracked a -> {
                        statusPrinter(out, RED, "untracked:", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(out, RED, "deleted:", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(out, RED, "modified:", a.path());
                    }
                    default -> EXIT_FAILURE("Status incorrect enum");
                }
            }
        }
        if (!untracked.isEmpty()) {
            out.print("Untracked files:\n");
            for (Status status : untracked) {
                Status.Untracked a = (Status.Untracked) status;
                statusPrinter(out, RED, "untracked:", a.path());
            }
        }
    }

    public static void statusPrinter(PrintStream out, String ANSI, String text, String path) {
        String whitespace = " ";
        out.print("      ");
        out.print(ANSI);
        out.print(text);
        out.print(whitespace.repeat(11 - text.length()));
        out.print(path);
        out.print(RESET);
        out.print("\n");
    }

    public static void logPrinter(PrintStream out, Commit commit) throws NoSuchAlgorithmException, IOException {
        out.print(ORANGE);
        out.print("commit ");
        out.print(HexFormat.of().formatHex(commit.hash()));
        out.print(RESET);
        out.print('\n');
        out.print("Author: ");
        out.print(commit.author.name());
        out.print(" <");
        out.print(commit.author.email());
        out.print('>');
        out.print('\n');
        out.print("Date:   ");
        out.print(DateTimeFormatter
                .ofPattern("dd MMM yyyy, HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(commit.author.time()));
        out.print("\n\n");
        out.print("     " + commit.message);
        out.print("\n\n");
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
                        System.out.println("Directory: " + entry.path());
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
