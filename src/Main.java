import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import objects.Tree;
import objects.Tree.TreeEntry;
import objects.Bloob;
import objects.Commit;
import objects.JitObject;
import components.Index;
import components.ObjectStore;
import components.Status;

public class Main {
    private static final String INIT = "init";
    private static final String ADD = "add";
    private static final String STATUS = "status";
    private static final String PULL = "pull";
    private static final String COMMIT = "commit";

    private static final List<String> ignores = List.of(".git", ".jit", ".DS_Store");

    public static final Path JIT = Path.of(".jit");

    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String RESET = "\u001B[0m";

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        if (args.length == 0)
            EXIT_FAILURE("Provide more arguments");

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
                default -> EXIT_FAILURE(String.format("Command '%s' not implemented or does not exist", args[0]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void commit(String message) throws IOException {
        // When we commit that would make the HEAD point to this new commit, we need to take a snapshot
        // of the current index
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        index.read();

    }

    public static void pull() {

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

    record HeadInfo(Optional<String> branch, Commit commit) {
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
    public static Optional<HeadInfo> resolveHead(ObjectStore objStore) throws IOException {
        // Detatched HEAD, this means that the HEAD would store a hash rather than a ref, meaning the person must of
        // switched to a previous commit and the HEAD would store that hash commit in the file rather then a ref of the
        // main/master branch, this is because someone must of checkouted to a specific commit hash which makes the HEAD
        // point to that hash rather than the main branch via ref

        byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
        String stringHead = new String(HEAD, JitObject.utf);

        if (stringHead.substring(0, 3).equals("ref")) {
            // ref: refs/heads/master
            String branch = stringHead.substring(5);
            Path _path = Path.of(branch);

            int idx = stringHead.lastIndexOf("/");
            System.out.println(stringHead.substring(idx + 1));

            // path=refs/heads/master

            if (!Files.exists(_path))
                return Optional.empty();

            // Reads the hash stored in the master file that was pointed to
            // by HEAD then uses that hash to go into objects and find
            // the object stored there, it would be a commit as this master
            // file points to the commit currently on, then we go to the 
            // commit treehash via commit.treeHash which then would be a tree
            // so an actual hierarchy of files/folders
            JitObject object = objStore.load(Files.readAllBytes(_path)).get();
            Commit commit = (Commit) object;
            return Optional.of(new HeadInfo(Optional.of(branch), commit));
        }

        JitObject object = objStore.load(HEAD).get();
        Commit commit = (Commit) object;
        return Optional.of(new HeadInfo(Optional.empty(), commit));
    }

    public static void status() throws IOException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        // status has 3 jobs:
        // HEAD vs index: what has been staged and is ready to be committed
        // index vs working directory: what has changed but hasn't been staged
        // last case is working directory files not being staged at all
        Optional<HeadInfo> commit = resolveHead(objStore);

        Map<String, byte[]> treeMap = (!commit.isEmpty())
                ? Tree.flattenTree((Tree) objStore.load(commit.get().commit().treeHash).get())
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
                unstaged.add(new Status.Untracked(path));
            } else if (workingHash == null) {
                // Working hash may be null as the file may of been deleted but not reflected in index yet since
                // they may of done rm file.txt instead of git rm file.txt
                unstaged.add(new Status.Deleted(path));
            } else if (!Arrays.equals(indexHash, workingHash)) {
                unstaged.add(new Status.Modified(path));
            }
        }

        // A branch name may exist or not, handles if there was nothing in the HEAD so its the first commit
        // so we assign it to be empty, else it would go to commit.get().branch() which still be empty 
        Optional<String> branch = (commit.isEmpty()) ? Optional.empty() : commit.get().branch();
        statusPrint(branch, staged, unstaged);
    }

    public static void statusPrint(Optional<String> branch, List<Status> staged, List<Status> unstaged) {
        for (Status status : staged) {
            switch (status) {
                case Status.Added a -> System.out.printf("Added '%s' (staged)\n", a.path());
                case Status.Deleted a -> System.out.printf("Deleted '%s' (staged)\n", a.path());
                case Status.Modified a -> System.out.printf("Modified '%s' (staged)\n", a.path());
                default -> EXIT_FAILURE("Status incorrect enum");
            }
        }

        for (Status status : unstaged) {
            switch (status) {
                case Status.Untracked a -> System.out.printf("Untracked '%s'\n", a.path());
                case Status.Deleted a -> System.out.printf("Deleted '%s'\n", a.path());
                case Status.Modified a -> System.out.printf("Modified '%s'\n", a.path());
                default -> EXIT_FAILURE("Status incorrect enum");
            }
        }

    }

    public static void init() throws IOException {
        // Assuming it is a sensible user, not handling errors with regarding to corrupted .jit structure
        try {
            Files.createDirectory(JIT);
        } catch (FileAlreadyExistsException e) {
            Path cur = Path.of("").toAbsolutePath();
            EXIT_FAILURE(String.format(".jit already exists in %s\n", cur.toString()));
        }

        Files.createDirectory(JIT.resolve("objects"));
        Files.createDirectory(JIT.resolve("refs"));
        Files.createFile(JIT.resolve("index"));
        Files.createFile(JIT.resolve("HEAD"));

        Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/master");

        Files.createDirectory(JIT.resolve("refs").resolve("heads"));
    }
}
