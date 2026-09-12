import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Deque;

public class Tag extends GitObject {
    private static final String TYPE = "tag";

    public byte[] targetHash;
    Tagger tagger;
    Instant taggerTime;
    String message;

    public Tag(byte[] targetHash, Tagger tagger, Instant taggerTime, String message) {
        this.targetHash = targetHash;
        this.tagger = tagger;
        this.taggerTime = taggerTime;
        this.message = message;
    }

    @Override 
    public String type() {
        return TYPE;
    }

    @Override 
    public byte[] serialiseContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream outPrim = new DataOutputStream(out)

        out.write(tagger.targetType().getBytes(GitObject.ascii));
        out.write(0);
        out.write(tagger.tagName().getBytes(GitObject.ascii));
        out.write(0);
        out.write(tagger.taggerName().getBytes(GitObject.ascii));
        out.write(0);
        out.write(tagger.taggerEmail().getBytes(GitObject.ascii));
        out.write(0);
        out.write(message.getBytes(GitObject.ascii));
        outPrim.writeLong(taggerTime.getEpochSecond());
        out.write(targetHash);

        return out.toByteArray();
    }
    
    public static Tag parseContent(byte[] content) {
        // <targetType>0<tagName>0<taggerName>0<taggerEmail>0<message>0<time><targetHash>
        Deque<byte[]> queue = GitObject.split(content, (byte)0, 0, 5);

        byte[] _targetType = queue.poll();
        byte[] _tagName = queue.poll();
        byte[] _taggerName = queue.poll();
        byte[] _taggerEmail = queue.poll();
        byte[] _message = queue.poll();
        
        String targetType = new String(_targetType, GitObject.ascii);
        String tagName = new String(_tagName, GitObject.utf);
        String taggerName = new String(_taggerName, GitObject.utf);
        String taggerEmail = new String(_taggerEmail, GitObject.utf);
        String message = new String(_message, GitObject.utf);

        byte[] timeAndHash = queue.poll();

        byte[] _time = Arrays.copyOfRange(timeAndHash, 0, Long.BYTES);
        byte[] targetHash = Arrays.copyOfRange(timeAndHash, Long.BYTES, Long.BYTES + GitObject.HASH_LENGTH);

        Instant time = Instant.ofEpochSecond(ByteBuffer.wrap(_time).getLong());

        Tagger tagger = new Tagger(targetType, tagName, taggerName, taggerEmail);

        return new Tag(targetHash, tagger, time, message);
    }
}
