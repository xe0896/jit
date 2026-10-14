import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Stream;

import objects.Tree;
import objects.Tree.TreeEntry;
import objects.Bloob;
import objects.Commit;
import objects.JitObject;
import components.Index;
import components.ObjectStore;
import components.Status;
import entities.Author;
import entities.Committer;

public class Main {
    private static final String INIT = "init";
    private static final String ADD = "add";
    private static final String STATUS = "status";
    private static final String PULL = "pull";
    private static final String COMMIT = "commit";
    private static final String LOG = "log";
    private static final String SWITCH = "switch";

    private static final List<String> ignores = List.of(".git", ".jit", ".DS_Store");

    public static final Path JIT = Path.of(".jit");

    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";
    public static final String ORANGE = "\u001B[38;5;214m";

    record HeadInfo(Optional<String> branch, Optional<Commit> commit) {
    }

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
                default -> EXIT_FAILURE(String.format("Command '%s' not implemented or does not exist", args[0]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Commit createCommit(String message, byte[] treeHash, List<byte[]> parentHashes) {
        // Creating a commit is done by given the tree hash we have the contents from that
        // but we also need to have a list of the previous commits relative to this one
        // we can do that by 

        Author author = new Author("zain", "zainahmed@gmail.com", Instant.now());
        Committer committer = new Committer("zain", "zainahmed@gmail.com", Instant.now());

        return Commit.of(treeHash, parentHashes, committer, author, message);
    }

    public static void createBranch(String branch) throws IOException {
        Path HEAD = JIT.resolve("HEAD");
        Files.writeString(HEAD, "ref: refs/heads/" + branch);
    }

    public static void gotoCommit(byte[] commitHash) throws IOException {

    }

    public static void switchBranch(String branch) throws IOException {
        Path HEAD = JIT.resolve("HEAD");

        String stored = Files.readString(HEAD);

        int idx = stored.lastIndexOf("/");

        String storedBranch = stored.substring(idx + 1, stored.length());

        if (branch.equals(storedBranch))
            EXIT_FAILURE(String.format("Branch '%s' is already toggled", branch));

        Path branches = JIT.resolve("refs/heads");

        if (!Files.exists(JIT.resolve(Path.of("refs/heads").resolve(branch))))
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

                    treeHashPrint(commitTreeHash);

                    // Given this tree hash and references to other trees as well as blobs
                    // populate the working directory

                    System.out.println(String.format("Switch to branch '%s'", branch));
                    Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/" + branch);
                }
            }
        }
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

    public static void Switch(String[] args) throws IOException {
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
            EXIT_FAILURE("jit commit only allows 2 arguments");
        }
    }

    public static void commit(String message) throws IOException, NoSuchAlgorithmException {
        System.out.println(message);
        // When we commit that would make the HEAD point to this new commit, 
        // we need to take a snapshot of the current index
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        index.read(); // Read what is the current index file, what has been added

        // Tree root of the indexed files
        byte[] root = index.buildTree();

        HeadInfo head = resolveHead(objStore);
        Optional<String> _branch = head.branch();
        Optional<Commit> _prevCommit = head.commit();

        byte[] commitHash;

        if (_prevCommit.isEmpty()) {
            // An empty commit means this is the first commit, so there is no 
            // commit list we have to construct for this commit, this means
            // that we create the list of previous hashes to be empty

            List<byte[]> parentHashes = new ArrayList<>();

            Commit commit = createCommit(message, root, parentHashes);
            commitHash = objStore.store(commit);
        } else {
            // A commit exists so we have to deserialise it and get the list it has
            // and then append that commit to it then use that as the list for this commit
            Commit prevCommit = _prevCommit.get();
            byte[] prevCommitHash = prevCommit.hash();

            // Grab the previous commits previous hashes, then add the previous commit to the list
            // as this would be the previous hash list for the current commit
            List<byte[]> parentHashes = prevCommit.parentHashes;
            parentHashes.add(prevCommitHash);

            Commit commit = createCommit(message, root, parentHashes);
            commitHash = objStore.store(commit);
        }

        if (_branch.isEmpty()) {
            // Detatched HEAD, store in the HEAD file directly the commit hash
            Files.writeString(JIT.resolve("HEAD"), HexFormat.of().formatHex(commitHash));
        } else {
            // Undetatched, meaning it is pointing to the tip of a branch stored
            // in head, so store the current hash to where it is pointing to
            byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
            String stringHead = new String(HEAD, JitObject.utf);
            String refsPath = stringHead.substring(5);
            Path _path = JIT.resolve(Path.of(refsPath));
            // May be the first commit, meaning the branch file does not exist ('master' in 'refs/heads/master')
            if (!Files.exists(_path))
                Files.createFile(_path);

            Files.writeString(_path, HexFormat.of().formatHex(commitHash));
        }
    }

    public static void pull() {

    }

    public static void log() throws IOException, NoSuchAlgorithmException {
        ObjectStore objStore = new ObjectStore();
        HeadInfo head = resolveHead(objStore);
        if (head.commit().isEmpty()) {
            System.out.println("No commit has happened yet");
            return;
        }

        StringBuilder sb = new StringBuilder();

        Commit commit = head.commit().get();

        logPrinter(sb, commit);

        List<byte[]> parentHashes = commit.parentHashes;

        for (int i = parentHashes.size() - 1; i >= 0; i--) {
            byte[] b = parentHashes.get(i);
            Commit otherCommit = (Commit) objStore.load(b).get();
            logPrinter(sb, otherCommit);
        }

        System.out.println(sb);
    }

    public static void EXIT_FAILURE(String message) {
        System.out.println(message);
        System.exit(1);
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

    public static Map<String, byte[]> getWorkingPaths() throws IOException {
        Map<String, byte[]> currentPaths = new HashMap<>();
        Deque<Path> stack = new ArrayDeque<>();

        stack.push(Path.of(""));

        while (!stack.isEmpty()) {
            Path cur = stack.pop();
            if (ignores.contains(cur.getFileName().toString()))
                continue;
            if (Files.isDirectory(cur)) {
                Files.list(cur).forEach(path -> {
                    stack.push(path);
                });
            } else {
                currentPaths.put(cur.toString(), Files.readAllBytes(cur));
            }
        }

        return currentPaths;
    }

    /** 
     * Returns the commit hash of the HEAD file, HEAD may either be attached so with a ref or detatched with a straight hash
     * @return byte[]
     */
    public static HeadInfo resolveHead(ObjectStore objStore) throws IOException {
        // Detatched HEAD, this means that the HEAD would store a hash rather than a ref, meaning the person must of
        // switched to a previous commit and the HEAD would store that hash commit in the file rather then a ref of the
        // main/master branch, this is because someone must of checkouted to a specific commit hash which makes the HEAD
        // point to that hash rather than the main branch via ref

        byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
        String stringHead = new String(HEAD, JitObject.utf);

        // Branch executes given that it sees refs/heads/master which means that we are at the
        // tip of a branch, the branch may not point to anything meaning an initial commit hasn't been
        // processed, in that case a branch name still exists as all defaults point to 'master', there 
        // is just no commit object to refer to for the HEAD to construct for comparisons sake
        if (stringHead.substring(0, 3).equals("ref")) {
            // ref: refs/heads/master
            String refsPath = stringHead.substring(5);
            Path _path = JIT.resolve(Path.of(refsPath));

            int idx = stringHead.lastIndexOf("/");
            String branch = stringHead.substring(idx + 1);

            // path=refs/heads/master

            // The file being empty means that this is the first commit, meaning there is a branch
            // due to default 'master' but no commit to refer to so we say its empty
            if (!Files.exists(_path)) {
                return new HeadInfo(Optional.of(branch), Optional.empty());
            }

            // Reads the hash stored in the master file that was pointed to
            // by HEAD then uses that hash to go into objects and find
            // the object stored there, it would be a commit as this master
            // file points to the commit currently on, then we go to the 
            // commit treehash via commit.treeHash which then would be a tree
            // so an actual hierarchy of files/folders

            byte[] hash = HexFormat.of().parseHex(Files.readString(_path).trim());
            JitObject object = objStore.load(hash).get();
            Commit commit = (Commit) object;
            return new HeadInfo(Optional.of(branch), Optional.of(commit));
        }

        // A HEAD that does not have a ref: means it a detatched HEAD and points to a commit
        // rather than the tip of a branch, meaning there is no branch but there is still a commit
        // as the HEAD would store the straight hash for it
        JitObject object = objStore.load(HEAD).get();
        Commit commit = (Commit) object;
        return new HeadInfo(Optional.empty(), Optional.of(commit));
    }

    public static void status() throws IOException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        // status has 3 jobs:
        // HEAD vs index: what has been staged and is ready to be committed
        // index vs working directory: what has changed but hasn't been staged
        // last case is working directory files not being staged at all
        HeadInfo head = resolveHead(objStore);

        Optional<Commit> commit = head.commit();

        Map<String, byte[]> treeMap = (!commit.isEmpty())
                ? Tree.flattenTree((Tree) objStore.load(commit.get().treeHash).get())
                : new HashMap<>();

        Map<String, byte[]> workingMap = getWorkingPaths();
        index.read();

        // index.entries is used to get Map<String, byte[]> where byte[] is a SHA-1 hash of the
        // object that matches what is stored in the objects/. The hash won't contian the raw
        // file contents since it has other stuff such as the type, working directory takes the
        // hash file contents so we need to map this back and since this is gonna be a blob
        // then we can just read the full contents pointed to by object/

        Map<String, byte[]> indexMap = new HashMap<>();

        for (var entry : index.entries.entrySet()) {
            String path = entry.getKey();
            TreeEntry e = entry.getValue();

            JitObject object = objStore.load(e.hash()).get();
            Bloob blob = (Bloob) object;
            indexMap.put(path, blob.serialiseContent());
        }

        // Map<String, TreeEntry> indexMap = index.entries;

        List<Status> staged = new ArrayList<>();
        List<Status> unstaged = new ArrayList<>();
        List<Status> untracked = new ArrayList<>();

        // Staged changes: compares head with index, (added, modified, deleted)
        // Unstaged changes: compares index with working, (modified, deleted)
        // Untracked changes: files that are in working but not in index (new files)

        Set<String> headAndIndex = new HashSet<>(treeMap.keySet());
        headAndIndex.addAll(indexMap.keySet());

        Set<String> indexAndWorking = new HashSet<>(indexMap.keySet());
        indexAndWorking.addAll(workingMap.keySet());

        // Comparing head with index to get the staged changes
        for (String path : headAndIndex) {
            byte[] headHash = treeMap.get(path);
            byte[] indexHash = indexMap.get(path);

            if (headHash == null) {
                // Head hash may be null as the file may have been created to be git added since this "path"
                // can only be in here if it is in head or index, given its not in head it must be in index
                // so this is a new file that has been git added
                staged.add(new Status.Added(path));
            } else if (indexHash == null) {
                // Index hash may be null as we may of done git rm file.txt which makes the file get deleted
                // as well as remove it from the index
                staged.add(new Status.Deleted(path));
            } else if (!Arrays.equals(headHash, indexHash)) {
                // Modified as if the arrays are not equal then that must mean some change must of occured
                // then someone done git add to reflect that in the index
                staged.add(new Status.Modified(path));
            }
            // An index hash having the same hash means nothing has changed, we can ignore this as the person
            // likely recently commited and hasn't changed a file yet
        }

        for (String path : indexAndWorking) {
            byte[] indexHash = indexMap.get(path);
            byte[] workingHash = workingMap.get(path);

            if (indexHash == null) {
                // Index hash may be null as this is a new file that has been created but hasn't been git added yet
                untracked.add(new Status.Untracked(path));
            } else if (workingHash == null) {
                // Working hash may be null as the file may of been deleted but not reflected in index yet since
                // they may of done rm file.txt instead of git rm file.txt
                unstaged.add(new Status.Deleted(path));
            } else if (!Arrays.equals(indexHash, workingHash)) {
                unstaged.add(new Status.Modified(path));
            }
        }

        Optional<String> branch = head.branch();

        String message = statusPrint(branch, staged, unstaged, untracked);
        System.out.println(message);
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

    public static void statusPrinter(StringBuilder sb, String ANSI, String text, String path) {
        sb.append("      ");
        sb.append(ANSI);
        sb.append(text);
        sb.append("  ");
        sb.append(path);
        sb.append(RESET);
        sb.append("\n");
    }

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
                        statusPrinter(stage, GREEN, "added: ", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(stage, GREEN, "deleted: ", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(stage, GREEN, "modified", a.path());
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
                        statusPrinter(unstage, RED, "untracked: ", a.path());
                    }
                    case Status.Deleted a -> {
                        statusPrinter(unstage, RED, "deleted: ", a.path());
                    }
                    case Status.Modified a -> {
                        statusPrinter(unstage, RED, "modified: ", a.path());
                    }
                    default -> EXIT_FAILURE("Status incorrect enum");
                }
            }
        }
        if (!untracked.isEmpty()) {
            untrack.append("Untracked files:\n");
            for (Status status : untracked) {
                Status.Untracked a = (Status.Untracked) status;
                statusPrinter(untrack, RED, "untracked: ", a.path());
            }
        }

        return bra.toString() + stage.toString() + unstage.toString() + untrack.toString();

    }

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

        Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/master");

        Files.createDirectory(JIT.resolve("refs").resolve("heads"));
    }
}
