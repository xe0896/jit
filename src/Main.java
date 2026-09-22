import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import objects.Tree;
import objects.Commit;
import objects.JitObject;
import components.Index;
import components.ObjectStore;

public class Main {
    private static final String INIT = "init";
    private static final String ADD = "add";
    private static final String STATUS = "status";
    
    public static final Path JIT = Path.of(".jit");

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
        if(args.length == 0) {System.out.println("?"); return;}
        switch(args[0]) {
            case INIT -> init();
            case ADD -> {
                if(args.length == 1) {
                    System.out.println("jit add expects atleast one file name");
                } else {
                    add(Arrays.copyOfRange(args, 1, args.length));
                }
            }
            case STATUS -> status();
            default -> System.out.printf("Command '%s' not implemented or does not exist\n", args[0]);
        }
    }

    public static void add(String[] paths) throws IOException, InterruptedException, NoSuchAlgorithmException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        for(String path : paths) {
            try {
                index.add(path);   
            } catch (NoSuchFileException e) {
                System.out.printf("path '%s' does not exist\n", path);
                return;
            }
        }

        System.out.println(index.entries);
    }

    public static void status() throws IOException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        // status has 3 jobs:
        // HEAD vs index: what has been staged and is ready to be committed
        // index vs working directory: what has changed but hasn't been staged
        // last case is working directory files not being staged at all

        byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
        String stringHead = new String(HEAD, JitObject.utf);

        if(stringHead.substring(0, 3).equals("ref")) {
            // ref: refs/heads/master
            Path path = Path.of(stringHead.substring(5));
            // path=refs/heads/master

            // Reads the hash stored in the master file that was pointed to
            // by HEAD then uses that hash to go into objects and find
            // the object stored there, it would be a commit as this master
            // file points to the commit currently on, then we go to the 
            // commit treehash via commit.treeHash which then would be a tree
            // so an actual hierarchy of files/folders
            JitObject object = objStore.load(Files.readAllBytes(path)).get();
            Commit commit = (Commit) object;
            byte[] treeHash = commit.treeHash;

            Tree tree = (Tree) objStore.load(treeHash).get();
            Map<String, byte[]> treeMap = Tree.flattenTree(tree);
            index.read();
            Map<String, IndexEntry> indexMap = index.entries;


        } else {
            // hash
        }
    }

    public static void init() throws IOException {
        // Assuming it is a sensible user, not handling errors with regarding to corrupted .jit structure
        try {
            Files.createDirectory(JIT);
        } catch (FileAlreadyExistsException e) {
            Path cur = Path.of("").toAbsolutePath();
            System.out.printf(".jit already exists in %s\n", cur.toString());
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Files.createDirectory(JIT.resolve("objects"));
        Files.createDirectory(JIT.resolve("refs"));
        Files.createFile(JIT.resolve("index"));
        Files.createFile(JIT.resolve("HEAD"));

        Files.writeString(JIT.resolve("HEAD"), "ref: refs/heads/master");

        Files.createDirectory(JIT.resolve("refs").resolve("heads"));
    }
}
