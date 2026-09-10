import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Tree extends GitObject {
    private static final String TYPE = "tree";
    private final List<TreeEntry> entries;

    private Tree(List<TreeEntry> entries) {
        this.entries = entries;
    }

    @Override 
    public String type() {
        return TYPE;
    }

    @Override 
    public byte[] serialiseContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for(TreeEntry entry : entries) {
            entry.writeTo(out);
        }
        return out.toByteArray();
    }

    public static Tree parseContent(byte[] payload) {
        int cursor = 0;
        List<TreeEntry> e = new ArrayList<>();
        while(cursor < payload.length) {
            int whitespace = 0;
            int i = cursor;
            for(;i < payload.length; i++) {
                if(payload[i] == ' ') whitespace = i;
                if(payload[i] == 0) break;
            }

            byte[] _mode = Arrays.copyOfRange(payload, cursor, whitespace);
            byte[] _name = Arrays.copyOfRange(payload, whitespace + 1, i);
            byte[] hash = Arrays.copyOfRange(payload, i+1, GitObject.HASH_LENGTH);

            int mode = Integer.parseInt(new String(_mode, GitObject.ascii));
            String name = new String(_name, GitObject.ascii);

            e.add(new TreeEntry(hash, mode, name));
            cursor += i + GitObject.HASH_LENGTH + 1;  
        }

        return new Tree(e);
    }
}
