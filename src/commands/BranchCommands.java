package commands;

import components.Index;
import components.ObjectStore;
import components.Status;
import entities.HeadInfo;
import java.io.IOException;
import java.io.PrintStream;
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
    /**
    * List the branches that the repository has, alongside an indicator of a star (*) and green highlight
    * on the branch that is currently being worked on
    *
    * @throws IOException
    */
    public static void branch() throws IOException {
        // Grabs the current branch so that when we iterate over our list of branches
        // we can compare it and make it highlighted
        ObjectStore objStore = new ObjectStore();
        Path branches = JIT.resolve("refs/heads");

        String curBranch = curBranch(objStore);

        PrintStream out = System.out;

        try (var stream = Files.list(branches)) {
            for (Path p : stream.toList()) {
                String fileName = p.getFileName().toString();
                if (fileName.equals(curBranch)) {
                    out.print(" * ");
                    out.print(GREEN);
                    out.print(curBranch);
                    out.print(RESET);
                } else {
                    out.print("   ");
                    out.print(fileName);
                }
                out.print("\n");
            }
        }
    }

    public static String curBranch(ObjectStore objStore) throws IOException {
        HeadInfo head = resolveHead(objStore);
        if (head.branch().isEmpty())
            throw new UnsupportedOperationException("Undetatched heads are going to get a revamp");

        String curBranch = head.branch().get();

        return curBranch;
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

    public static Path branchCheck(String branch) throws IOException {
        String stored = Files.readString(JIT.resolve("HEAD"));

        int idx = stored.lastIndexOf("/");

        String storedBranch = stored.substring(idx + 1, stored.length());

        if (branch.equals(storedBranch))
            EXIT_FAILURE(String.format("Branch '%s' is already toggled", branch));

        Path branches = JIT.resolve("refs/heads");

        if (!Files.exists(branches.resolve(branch)))
            EXIT_FAILURE(String.format("Branch '%s' does not exist", branch));

        return branches;
    }

    /**
    * Given a 'treeHash' populate the current working directory with the contents, ensuring it is
    * efficient as we compare with each file and see if it is different if there was a match
    * meaning no changes, else we need to replace it and also create folders that may also have new files
    * @param treeHash
    */
    public static void populate(byte[] treeHash) {

    }

    public static void switchBranch(String branch) throws IOException {
        // Checks to see if the branch that is being switched to
        // exists as well as if it is already toggled
        Path branches = branchCheck(branch);

        ObjectStore objStore = new ObjectStore();

        // Goes through each branch to find the hash of the branch that it wants to go to
        // by going through the content address chain and also calls 'populate' to get the
        // current branch and replace its contents with the switched branch
        try (var stream = Files.list(branches)) {
            for (Path p : stream.toList()) {
                String pathName = p.getFileName().toString();
                if (branch.equals(pathName)) {
                    String hex = Files.readString(p);
                    byte[] hash = HexFormat.of().parseHex(hex);

                    Optional<JitObject> object = objStore.load(hash);
                    if (object.isEmpty())
                        EXIT_FAILURE("Hash stored in the branch file does not exist, possible corruption");

                    Commit commit = (Commit) object.get();
                    byte[] commitTreeHash = commit.treeHash;

                    // Given a tree hash populate the working directory with it, but ensure we only replace
                    // stuff that is new, if it already exists then skip it
                    // If the working directory doesn't reflect the index then there are some unstaged changes
                    List<Status> staged = new ArrayList<>();
                    List<Status> unstaged = new ArrayList<>();
                    List<Status> untracked = new ArrayList<>();

                    changes(staged, unstaged, untracked); // Grab the staged, unstaged and untracked changes

                    if (unstaged.size() > 0)
                        EXIT_FAILURE("You have unstaged changes");

                    // Given this tree hash and references to other trees as well as blobs
                    // populate the working directory
                    populate(commitTreeHash);

                    System.out.println(String.format("Switch to branch '%s'", branch));
                    Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/" + branch);
                }
            }
        }
    }

    public static void gotoCommit(byte[] commitHash) throws IOException {

    }
}
