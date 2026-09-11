import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
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
        // <mode>' '<name>0<hash>
        // initial split would be:
        // list[0] = <mode>
        // list[1] = <name>0<hash>...

        List<TreeEntry> e = new ArrayList<>();
        int cursor = 0;
        byte[] source = payload;

        while(cursor < payload.length) {
            Deque<byte[]> list1 = GitObject.split(source, (byte)' ', 0, 1);
            byte[] _mode = list1.poll();
            Deque<byte[]> list2 = GitObject.split(list1.poll(), (byte)0, 0, 1);
            byte[] _name = list2.poll();
            byte[] hash = Arrays.copyOfRange(list2.poll(), 0, HASH_LENGTH);

            int mode = Integer.parseInt(new String(_mode, GitObject.ascii), 8);
            String name = new String(_name, GitObject.utf);

            int jump = _mode.length + 1 + _name.length + 1 + HASH_LENGTH;
            cursor += jump;
            source = Arrays.copyOfRange(source, jump, source.length);

            e.add(new TreeEntry(hash, mode, name));
        }

        return new Tree(e);
    }
}
