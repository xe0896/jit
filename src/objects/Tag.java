package objects;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Deque;

import entities.Tagger;

public class Tag extends JitObject {
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

    /** 
     * @return String
     */
    @Override 
    public String type() {
        return TYPE;
    }

    /** 
     * @return byte[]
     * @throws IOException
     */
    @Override 
    public byte[] serialiseContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream outPrim = new DataOutputStream(out);

        out.write(tagger.targetType().getBytes(JitObject.ascii));
        out.write(0);
        out.write(tagger.tagName().getBytes(JitObject.ascii));
        out.write(0);
        out.write(tagger.taggerName().getBytes(JitObject.ascii));
        out.write(0);
        out.write(tagger.taggerEmail().getBytes(JitObject.ascii));
        out.write(0);
        out.write(message.getBytes(JitObject.ascii));
        outPrim.writeLong(taggerTime.getEpochSecond());
        out.write(targetHash);

        return out.toByteArray();
    }
    
    /** 
     * @param content
     * @return Tag
     */
    public static Tag parseContent(byte[] content) {
        // <targetType>0<tagName>0<taggerName>0<taggerEmail>0<message>0<time><targetHash>
        Deque<byte[]> queue = JitObject.split(content, (byte)0, 0, 5);

        byte[] _targetType = queue.poll();
        byte[] _tagName = queue.poll();
        byte[] _taggerName = queue.poll();
        byte[] _taggerEmail = queue.poll();
        byte[] _message = queue.poll();
        
        String targetType = new String(_targetType, JitObject.ascii);
        String tagName = new String(_tagName, JitObject.utf);
        String taggerName = new String(_taggerName, JitObject.utf);
        String taggerEmail = new String(_taggerEmail, JitObject.utf);
        String message = new String(_message, JitObject.utf);

        byte[] timeAndHash = queue.poll();

        byte[] _time = Arrays.copyOfRange(timeAndHash, 0, Long.BYTES);
        byte[] targetHash = Arrays.copyOfRange(timeAndHash, Long.BYTES, Long.BYTES + JitObject.HASH_LENGTH);

        Instant time = Instant.ofEpochSecond(ByteBuffer.wrap(_time).getLong());

        Tagger tagger = new Tagger(targetType, tagName, taggerName, taggerEmail);

        return new Tag(targetHash, tagger, time, message);
    }
}
