package components;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    // A map of entries that provided a full path, points to an index entry which specifies
    // the mode which is the type so a tree or blob in most casesand the hash of the entry which
    // is a SHA-1 hash and not the content hash which would point to the actual contents
    // stored in the objects/ folder
    public Map<String, TreeEntry> entries;

    public static final Path INDEX_PATH = Path.of(".jit/index");

    public Index(ObjectStore objectStore) {
        this.objectStore = objectStore;
        this.entries = new HashMap<>();
    }

    /** 
     * Adds the provided path into the index map by reading the bytes stored in the file
     * and storing it in objects/ which must be a blob. The index file is read then
     * adds the TreeEntry then writes, not that efficient
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
        read();
        entries.put(path, new TreeEntry(path, hash, 0100644));
        write();
    }

    /** 
     * Remove the file from the index by first taking in the entries stored already in the index file
     * then removes it from the map, then writes the map back
     * @param path
     */
    public void remove(String path) throws IOException {
        read();
        entries.remove(path);
        write();
    }

    /** 
     * Given an already populated entries map, writes the contents into the index file, writing a list
     * would automatically add '\n' which is used to delimit when we want to read()
     * @throws IOException
     */
    public void write() throws IOException {
        List<String> lines = new ArrayList<>();

        // For each String -> TreeEntry
        for (var entry : entries.entrySet()) {
            String path = entry.getKey();
            TreeEntry idx = entry.getValue();
            // Makes the hash human read-able, in terms of output being nice as the hash() stored here
            // is going to be SHA-1 which can contain anything such as '\n' and have messy outputs
            String hex = HexFormat.of().formatHex(idx.hash());

            // Adds an entry and relies on '\n' delimit from the list
            String res = idx.mode() + " " + hex + " " + path;
            lines.add(res);
        }

        Files.write(INDEX_PATH, lines);
    }

    /** 
     * Reads the entries inside the current index file, and populates the entry map
     * @throws IOException
     */
    public void read() throws IOException {
        // <mode> <hex> <path>\n
        // A line was delimited via '\n' by write()
        for (String line : Files.readAllLines(INDEX_PATH)) {
            // Splits each line into 3 parts to grab the full TreeEntry
            String[] parts = line.split(" ", 3);
            String _mode = parts[0];
            String _hex = parts[1];
            String path = parts[2];

            // Parse the hex representation of SHA-1 back to SHA-1
            int mode = Integer.parseInt(_mode);
            byte[] hash = HexFormat.of().parseHex(_hex);

            TreeEntry idx = new TreeEntry(path, hash, mode);
            entries.put(path, idx);
        }
    }

    /** 
     * Builds the tree hierarchy represented by the current index
     * 
     * @return byte[] content hash of the root, should point to a tree
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public byte[] buildTree() throws IOException, NoSuchAlgorithmException {
        Map<String, List<TreeEntry>> map = new HashMap<>();
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
                List<TreeEntry> list = map.get("");
                list.add(entries.get(path));
            } else {
                String dir = path.substring(0, idx);
                if (!map.containsKey(dir))
                    map.put(dir, new ArrayList<>());
                List<TreeEntry> list = map.get(dir);
                list.add(entries.get(path));
            }
        }

        // Consider a repository that only has a folder in the root so src/ and no file
        // meaning there would be no instance of "" and therefore the root would be null
        // a tree still exists though since 
        byte[] root = null;

        List<String> dirs = new ArrayList<>(map.keySet());

        dirs.sort(Comparator.comparingInt((String dir) -> dir.isEmpty() ? 0 : dir.split("/").length).reversed());

        for (String path : dirs) {
            // src/lib/
            // src/
            List<TreeEntry> list = map.get(path);

            Tree tree = Tree.of(list);
            byte[] hash = objectStore.store(tree);

            if (path.isEmpty()) {
                root = hash;
                continue;
            }

            int idx = path.lastIndexOf("/");

            String parent = (idx == -1) ? "" : path.substring(0, idx);
            String name = (idx == -1) ? path : path.substring(idx + 1);

            if (!map.containsKey(parent))
                map.put(path, new ArrayList<>());
            List<TreeEntry> parentList = map.get(parent);

            parentList.add(new TreeEntry(name, hash, 040000));
        }
        return root;
    }
}
