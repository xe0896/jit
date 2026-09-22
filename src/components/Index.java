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

import objects.JitObject;

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
        byte[] hash = objectStore.store(bytes);
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

        for(var entry : entries.entrySet()) {
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

        for(String line : Files.readAllLines(INDEX_PATH)) {
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
        for(String path : entries.keySet()) {
            // For each path so src/main.c
            int idx = path.lastIndexOf("/");
            if(idx == -1) {
                if(!map.containsKey("")) map.put("", new ArrayList<>());
                List<IndexEntry> list = map.get("");
                list.add(entries.get(path));
            } else {
                String dir = path.substring(0, idx);
                if(!map.containsKey(dir)) map.put(dir, new ArrayList<>());
                List<IndexEntry> list = map.get(dir);
                list.add(entries.get(path));
            }
        }

        byte[] root = null;

        for(var inst : map.entrySet()) {
            String dir = inst.getKey();
            List<IndexEntry> list = inst.getValue();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for(IndexEntry entry : list) {
                
                String path = entry.path();

                out.write(dir.getBytes(JitObject.ascii));
                out.write(' ');

                int idx = path.lastIndexOf("/");
                String name = path.substring(idx + 1, path.length());

                out.write(name.getBytes(JitObject.ascii));
                out.write(0);
                out.write(entry.hash());
            }
            byte[] byteArray = out.toByteArray();
            objectStore.store(byteArray);
            if(dir == "") root = byteArray;
        }

        return root;
    }

    public record IndexEntry(String path, byte[] hash, int mode) {}
}
