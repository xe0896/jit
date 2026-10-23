package commands;

import components.Index;
import components.ObjectStore;
import components.Status;
import entities.HeadInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import objects.Commit;
import objects.JitObject;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static cli.ConsoleOutput.GREEN;
import static cli.ConsoleOutput.RESET;
import static cli.ConsoleOutput.treeHashPrint;
import static commands.CommitCommands.commit;
import static components.Repository.JIT;
import static components.Repository.resolveHead;
import static components.StatusScanner.changes;

public class BranchCommands {
    // List the brnaches in this repository
    public static void branch() throws IOException {
        ObjectStore objStore = new ObjectStore();
        Path branches = JIT.resolve("refs/heads");
        HeadInfo head = resolveHead(objStore);

        if (head.branch().isEmpty())
            throw new UnsupportedOperationException("Undetatched heads are going to get a revamp");

        String curBranch = head.branch().get();

        StringBuilder sb = new StringBuilder();

        try (var stream = Files.list(branches)) {
            for (Path p : stream.toList()) {
                String fileName = p.getFileName().toString();
                if (fileName.equals(curBranch)) {
                    sb.append(" * ");
                    sb.append(GREEN);
                    sb.append(curBranch);
                    sb.append(RESET);
                } else {
                    sb.append("   ");
                    sb.append(fileName);
                }
                sb.append("\n");
            }
        }

        System.out.println(sb);
    }

    public static void Switch(String[] args) throws IOException, NoSuchAlgorithmException {
        // For creating a new branch we can re-use the index that we are diverging from
        if (args.length == 1) {
            // Switching to an existing branch
            switchBranch(args[0]);
        } else if (args.length == 2) {
            // Either creating and going to that new branch or is going
            // to a detatched state by going to a particular commit
            if (args[0].equals("-c")) {
                createBranch(args[1]);
            } else {
                // Must be going to a detatched state
                gotoCommit(args[0].getBytes(StandardCharsets.UTF_8));
            }
        } else {
            EXIT_FAILURE("jit switch only allows 2 arguments");
        }
    }

    public static void createBranch(String branch) throws IOException, NoSuchAlgorithmException {
        ObjectStore objStore = new ObjectStore();
        HeadInfo head = resolveHead(objStore);

        String curBranch = head.branch()
                .orElseThrow(() -> new UnsupportedOperationException("Not considering no branch cases"));

        Commit curCommit = head.commit()
                .orElseThrow(() -> new UnsupportedOperationException("Not considering no commit cases"));

        Path newBranchLocation = JIT.resolve(Path.of("refs/heads/" + branch));
        if (Files.exists(newBranchLocation))
            throw new IllegalArgumentException("File already exists");

        Files.createFile(newBranchLocation);

        byte[] curCommitBytes = curCommit.hash();

        String hexHash = HexFormat.of().formatHex(curCommitBytes);

        Files.writeString(newBranchLocation, hexHash);
        Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/" + branch);

        System.out.println(String.format("Switching from branch '%s' to branch '%s'", curBranch, branch));
    }

    public static void switchBranch(String branch) throws IOException {
        Path HEAD = JIT.resolve("HEAD");

        String stored = Files.readString(HEAD);

        int idx = stored.lastIndexOf("/");

        String storedBranch = stored.substring(idx + 1, stored.length());

        if (branch.equals(storedBranch))
            EXIT_FAILURE(String.format("Branch '%s' is already toggled", branch));

        Path branches = JIT.resolve("refs/heads");

        if (!Files.exists(branches.resolve(branch)))
            EXIT_FAILURE(String.format("Branch '%s' does not exist", branch));

        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        try (var stream = Files.list(branches)) {
            for (Path p : stream.toList()) {
                String pathName = p.getFileName().toString();
                if (branch.equals(pathName)) {
                    System.out.println(p);
                    String hex = Files.readString(p);
                    System.out.println(hex);
                    byte[] hash = HexFormat.of().parseHex(hex);

                    Optional<JitObject> object = objStore.load(hash);
                    if (object.isEmpty())
                        EXIT_FAILURE("Hash stored in the branch file does not exist, possible corruption");

                    Commit commit = (Commit) object.get();
                    byte[] commitTreeHash = commit.treeHash;

                    if (populate(commitTreeHash)) {
                        EXIT_FAILURE("You have unstaged changes");
                    }

                    treeHashPrint(commitTreeHash);

                    // Given this tree hash and references to other trees as well as blobs
                    // populate the working directory

                    System.out.println(String.format("Switch to branch '%s'", branch));
                    Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/" + branch);
                }
            }
        }
    }

    public static void gotoCommit(byte[] commitHash) throws IOException {

    }

    // Given a tree hash populate the working directory with it, but ensure we only replace
    // stuff that is new, if it already exists then skip it
    public static boolean populate(byte[] treeHash) throws IOException {
        // If the working directory doesn't reflect the index then there are some unstaged changes
        List<Status> staged = new ArrayList<>();
        List<Status> unstaged = new ArrayList<>();
        List<Status> untracked = new ArrayList<>();

        changes(staged, unstaged, untracked); // Grab the staged, unstaged and untracked changes

        return unstaged.size() > 0;
    }
}
