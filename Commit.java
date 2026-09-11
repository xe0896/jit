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
        
        out.write(author.name().getBytes(GitObject.utf));
        out.write(0);
        out.write(author.email().getBytes(GitObject.utf));
        out.write(0);
        out.write(committer.name().getBytes(GitObject.utf));
        out.write(0);
        out.write(committer.email().getBytes(GitObject.utf));
        out.write(0);
        out.write(message.getBytes(GitObject.utf));
        out.write(0);
        out.write(Long.toString(author.time().getEpochSecond()).getBytes(GitObject.ascii));
        out.write(Long.toString(committer.time().getEpochSecond()).getBytes(GitObject.ascii));
        out.write(treeHash);
        out.write(Integer.toString(parentHashes.size()).getBytes(GitObject.ascii));

        for(byte[] parentHash : parentHashes) {
            out.write(Integer.toString(parentHash.length).getBytes(GitObject.ascii));
            out.write(parentHash);
        }

        return out.toByteArray();
    }

    public static Commit parseContent(byte[] content) {
        // <a_name>0<a_email>0<c_name>0<c_email>0<message>0<a_time><c_time><treeHash>[length]<list of parent hashes>
        // use +20 bytes to read the next hash

        Deque<byte[]> list = GitObject.split(content, (byte)0, 0, 5);

        byte[] _aname = list.poll();
        byte[] _aemail = list.poll();
        byte[] _cname = list.poll();
        byte[] _cemail = list.poll();
        byte[] _message = list.poll();

        String authorName = new String(_aname, GitObject.utf);
        String authorEmail = new String(_aemail, GitObject.utf);
        String committerName = new String(_cname, GitObject.utf);
        String committerEmail = new String(_cemail, GitObject.utf);
        String message = new String(_message, GitObject.utf);

        byte[] rest = list.poll();
        
        String authorTimeStr = new String(Arrays.copyOfRange(rest, 0, Long.BYTES), GitObject.utf);
        String committerTimeStr = new String(Arrays.copyOfRange(rest, Long.BYTES, 2*Long.BYTES), GitObject.utf);

        Instant authorTime = Instant.ofEpochSecond(Long.parseLong(authorTimeStr));
        Instant committerTime = Instant.ofEpochSecond(Long.parseLong(committerTimeStr));

        Author author = new Author(authorName, authorEmail, authorTime);
        Committer committer = new Committer(committerName, committerEmail, committerTime);

        int hashIdx = 2*Long.BYTES;

        byte[] treeHash = Arrays.copyOfRange(rest, hashIdx, hashIdx + GitObject.HASH_LENGTH);

        int lengthIdx = hashIdx + GitObject.HASH_LENGTH;

        int size = Integer.parseInt(new String(Arrays.copyOfRange(rest, lengthIdx, lengthIdx + Integer.BYTES)));

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
