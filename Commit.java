import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class Commit extends GitObject {
    private static final String TYPE = "commit";

    private byte[] treeHash; // Points to the root tree
    private List<byte[]> parentHashes; // Zero or more parents
    private final static int DELIMITERS = 5;

    private Author author;
    private Committer committer;

    private String message;

    private Commit(byte[] treeHash, List<byte[]> parentHashes, Committer committer, Author author, String message) {
        this.treeHash = treeHash;
        this.parentHashes = parentHashes;
        this.author = author;
        this.committer = committer;
        this.message = message;
    }

    @Override 
    public String type() {
        return TYPE;
    }

    @Override 
    public byte[] serialiseContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        out.write(author.name().getBytes());


        return out.toByteArray();
    }

    public static Commit parseContent(byte[] content) {
        // <a_name>0<a_email>0<c_name>0<c_email>0<message>0<a_time><c_time><treeHash>[length]<list of parent hashes>
        // use +20 bytes to read the next hash
        
        Deque<Integer> queue = new ArrayDeque<>();

        int count = DELIMITERS;

        for(int i = 0; i < content.length && count > 0; i++) {
            if(content[i] == 0) {queue.offer(i); count--;}
        }

        int a = queue.poll();
        int b = queue.poll();
        int c = queue.poll();
        int d = queue.poll();
        int e = queue.poll();
        
        String authorName = new String(Arrays.copyOfRange(content, 0, a), GitObject.ascii);
        String authorEmail = new String(Arrays.copyOfRange(content, a+1, b), GitObject.ascii);
        
        String committerName = new String(Arrays.copyOfRange(content, b+1, c), GitObject.ascii);
        String committerEmail = new String(Arrays.copyOfRange(content, c+1, d), GitObject.ascii);

        String message = new String(Arrays.copyOfRange(content, d+1, e), GitObject.ascii);

        int timeIdx = e + 1; // beginning index for the first time slot

        String authorTimeStr = new String(Arrays.copyOfRange(content, timeIdx, timeIdx + Long.BYTES), GitObject.ascii);
        String committerTimeStr = new String(Arrays.copyOfRange(content, timeIdx + 1 + Long.BYTES, timeIdx + 1 + 2*Long.BYTES), GitObject.ascii);

        Instant authorTime = Instant.ofEpochSecond(Long.parseLong(authorTimeStr));
        Instant committerTime = Instant.ofEpochSecond(Long.parseLong(committerTimeStr));

        Author author = new Author(authorName, authorEmail, authorTime);
        Committer committer = new Committer(committerName, committerEmail, committerTime);
        
        int hashIdx = (timeIdx + 1 + 2*Long.BYTES) + 1;

        byte[] treeHash = Arrays.copyOfRange(content, hashIdx, hashIdx + GitObject.HASH_LENGTH);

        int lengthIdx = hashIdx + GitObject.HASH_LENGTH + 1;

        int size = Integer.parseInt(new String(Arrays.copyOfRange(content, lengthIdx, Integer.BYTES)));

        int parentIdx = lengthIdx + Integer.BYTES;

        List<byte[]> parentHashes = new ArrayList<>();

        for(int i = 0; i < size; i++) {
            byte[] parentHash = Arrays.copyOfRange(content, parentIdx, parentIdx + GitObject.HASH_LENGTH);
            parentHashes.add(parentHash);
            parentIdx += GitObject.HASH_LENGTH;
        }

        return new Commit(treeHash, parentHashes, committer, author, message);
    }
}
