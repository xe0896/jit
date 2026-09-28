package components;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import objects.Bloob;
import objects.JitObject;
import objects.Tree;
import objects.Tree.TreeEntry;

public class Index {
    private ObjectStore objectStore;
    public Map<String, IndexEntry> entries;

    public static final Path INDEX_PATH = Path.of(".jit/index");

    public Index(ObjectStore objectStore) {
        this.objectStore = objectStore;
        this.entries = new HashMap<>();
    }

    /** 
     * The idea behind the index is when we edit a file/blob then we need to
     * jit add the file so that we can commit later on, 
     * @param path
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void add(String path) throws IOException, NoSuchAlgorithmException, NoSuchFileException {
        // A given path would be like src/main.c
        byte[] bytes = Files.readAllBytes(Path.of(path));
        Bloob blob = Bloob.of(bytes);
        // When we commit the files would already be in objects/ due to this line below
        byte[] hash = objectStore.store(blob);
        entries.put(path, new IndexEntry(path, hash, 0100644));
    }

    /** 
     * @param path
     */
    public void remove(String path) {
        entries.remove(path);
    }

    /** 
     * After the user has done git add to all the relevant files, then
     * this would be called when we commit which takes all the added files
     * and write this into the .jit/index
     * @throws IOException
     */
    public void write() throws IOException {
        List<String> lines = new ArrayList<>();

        for (var entry : entries.entrySet()) {
            String path = entry.getKey();
            IndexEntry idx = entry.getValue();
            String hex = HexFormat.of().formatHex(idx.hash());
            String res = idx.mode() + " " + hex + " " + path;

            lines.add(res);
        }

        Files.write(INDEX_PATH, lines);
    }

    /** 
     * @throws IOException
     */
    public void read() throws IOException {
        // <mode> <hex> <path>\n

        for (String line : Files.readAllLines(INDEX_PATH)) {
            String[] parts = line.split(" ", 3);
            String _mode = parts[0];
            String _hex = parts[1];
            String path = parts[2];

            int mode = Integer.parseInt(_mode);
            byte[] hash = HexFormat.of().parseHex(_hex);

            IndexEntry idx = new IndexEntry(path, hash, mode);
            entries.put(path, idx);
        }
    }

    /** 
     * Builds the tree hierarchy represented by the current index. 
     * 
     * @return byte[] hash of the root tree reprsenting the staged snapshot
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public byte[] buildTree() throws IOException, NoSuchAlgorithmException {
        Map<String, List<IndexEntry>> map = new HashMap<>();
        for (String path : entries.keySet()) {
            // For each path so src/main.c
            int idx = path.lastIndexOf("/");
            // Obtain idx of last "/" so then we can get the folder of this
            // such as src/main.c, and take the idx "/" and go up to not including
            // so substring(0, idx)
            if (idx == -1) {
                // idx == -1 suggests this is a root file, make a special case of
                // "" to hold root files
                if (!map.containsKey(""))
                    map.put("", new ArrayList<>());
                List<IndexEntry> list = map.get("");
                list.add(entries.get(path));
            } else {
                String dir = path.substring(0, idx);
                if (!map.containsKey(dir))
                    map.put(dir, new ArrayList<>());
                List<IndexEntry> list = map.get(dir);
                list.add(entries.get(path));
            }
        }

        byte[] root = null;

        // Populate the objects/ folder with the trees, the blobs are already there due to git add 
        for (var inst : map.entrySet()) {
            String dir = inst.getKey();
            List<IndexEntry> list = inst.getValue();
            List<TreeEntry> treeEntries = new ArrayList<>();

            for (IndexEntry entry : list) {
                treeEntries.add(new TreeEntry(entry.hash(), entry.mode(), entry.path()));
            }

            Tree tree = Tree.of(treeEntries);

            byte[] hash = objectStore.store(tree);
            if (dir.equals(""))
                root = hash;
        }

        return root;
    }

    public record IndexEntry(String path, byte[] hash, int mode) {
    }
}
