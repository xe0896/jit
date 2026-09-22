package objects;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import entities.*;

// A snapshot pointer of the root tree, we use the hash so avoid
// duplication as well as save memory
public class Commit extends JitObject {
    private static final String TYPE = "commit";

    public byte[] treeHash; // Points to the root tree
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
        DataOutputStream outPrim = new DataOutputStream(out);
        
        out.write(author.name().getBytes(JitObject.utf));
        out.write(0);
        out.write(author.email().getBytes(JitObject.utf));
        out.write(0);
        out.write(committer.name().getBytes(JitObject.utf));
        out.write(0);
        out.write(committer.email().getBytes(JitObject.utf));
        out.write(0);
        out.write(message.getBytes(JitObject.utf));
        out.write(0);
        outPrim.writeLong(author.time().getEpochSecond());
        outPrim.writeLong(committer.time().getEpochSecond());
        out.write(treeHash);
        outPrim.writeInt(parentHashes.size());
        out.write(0);

        for(int i = 0; i < parentHashes.size(); i++) {
            byte[] parentHash = parentHashes.get(i);   
            out.write(parentHash);
        }

        return out.toByteArray();
    }

    public static Commit parseContent(byte[] content) {
        // <a_name>0<a_email>0<c_name>0<c_email>0<message>0<a_time><c_time><treeHash>[length]0<list of parent hashes>
        // use +20 bytes to read the next hash

        // Text (ASCII/UTF-8) - null byte doesn't appear in practice safe delimiter
        // Binary (long, Instant, int) - any byte value can appear, including 0x00

        Deque<byte[]> list = JitObject.split(content, (byte)0, 0, 5);

        byte[] _aname = list.poll();
        byte[] _aemail = list.poll();
        byte[] _cname = list.poll();
        byte[] _cemail = list.poll();
        byte[] _message = list.poll();

        byte[] timeHashLength = list.poll();
        byte[] _parentHashes = list.poll();

        byte[] _authorTime = Arrays.copyOfRange(timeHashLength, 0, Long.BYTES);
        byte[] _committerTime = Arrays.copyOfRange(timeHashLength, Long.BYTES, 2*Long.BYTES);

        String authorName = new String(_aname, JitObject.utf);
        String authorEmail = new String(_aemail, JitObject.utf);
        String committerName = new String(_cname, JitObject.utf);
        String committerEmail = new String(_cemail, JitObject.utf);
        String message = new String(_message, JitObject.utf);

        Instant authorTime = Instant.ofEpochSecond(ByteBuffer.wrap(_authorTime).getLong());
        Instant committerTime = Instant.ofEpochSecond(ByteBuffer.wrap(_committerTime).getLong());

        Author author = new Author(authorName, authorEmail, authorTime);
        Committer committer = new Committer(committerName, committerEmail, committerTime);

        byte[] treeHash = Arrays.copyOfRange(timeHashLength, 2*Long.BYTES, 2*Long.BYTES + JitObject.HASH_LENGTH);
        byte[] _length = Arrays.copyOfRange(timeHashLength, 2*Long.BYTES + JitObject.HASH_LENGTH, timeHashLength.length);

        int length = ByteBuffer.wrap(_length).getInt();

        int parentIdx = 0;
        List<byte[]> parentHashes = new ArrayList<>();

        for(int i = 0; i < length; i++) {            
            byte[] parentHash = Arrays.copyOfRange(_parentHashes, parentIdx, parentIdx + JitObject.HASH_LENGTH);
            parentHashes.add(parentHash);
            parentIdx += JitObject.HASH_LENGTH;
        }

        return new Commit(treeHash, parentHashes, committer, author, message);
    }
}
