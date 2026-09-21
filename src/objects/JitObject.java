package objects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public abstract class JitObject {
    public static final Charset ascii = StandardCharsets.US_ASCII;
    public static final Charset utf = StandardCharsets.UTF_8;
    public static final int HASH_LENGTH = 20;
    
    abstract String type();
    abstract byte[] serialiseContent() throws IOException;

    /** 
     * Creates the envelope of the content as well as the type
     * @return byte[]
     * @throws IOException
     */
    public byte[] serialise() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] content = serialiseContent();
        // We do not want to store the integer representation of the content length, but rather
        // the ASCII representation so we first convert it to a String then convert it into
        byte[] length = String.valueOf(content.length).getBytes(ascii);

        // "<type> <length>0<content>"
        out.write(type().getBytes(ascii));
        out.write(' ');
        out.write(length);
        out.write(0);
        out.write(content);

        return out.toByteArray();
    }

    /** 
     * Given an array of bytes that has some delimiters, split the bytes given the delimiter
     * and also where to start from as well as how many, limit-- is done as the amount of
     * delimiters n would suggest the limit would be n - 1
     * @param source
     * @param delimiter
     * @param from
     * @param limit
     * @return Deque<byte[]>
     */
    public static Deque<byte[]> split(byte[] source, byte delimiter, int from, int limit) {
        Deque<byte[]> list = new ArrayDeque<>();
        limit--; // Temporary
        int prev = from;

        for(int i = from; i < source.length && limit != 0; i++) {
            // Whenever we find the delimiter we take that byte subarray
            // and append to the list and decrement limit until we have
            // reached the limit or we cannot go further into the source

            if(source[i] == delimiter) {
                list.offer(Arrays.copyOfRange(source, prev, i));
                if(limit > 0) limit--;
                prev = i + 1;
            }
        }

        if(limit != 0 && prev < source.length) {
            list.offer(Arrays.copyOfRange(source, prev, source.length));
        }

        return list;
    }

    /** 
     * Inverse of serialise; serialise would of declared the type first so we use that to extract it
     * and depending on the type we call the appropriate parse function
     * @param envelope
     * @return JitObject
     */
    public static JitObject deserialise(byte[] envelope) {
        int i = 0;
        int whitespace = 0;
        for(;i < envelope.length; i++) {
            byte b = envelope[i];
            if(b == ' ') whitespace = i;
            if(b == 0) break;
        }
        byte[] type = Arrays.copyOfRange(envelope, 0, whitespace);
        String typeStr = new String(type, StandardCharsets.US_ASCII);

        byte[] content = Arrays.copyOfRange(envelope, i+1, envelope.length);
        
        switch(typeStr) {
            case "blob" -> {return Bloob.parseContent(content);}
            case "tree" -> {return Tree.parseContent(content);}
            case "commit" -> {return Commit.parseContent(content);}
            case "tag" -> {return Tag.parseContent(content);}
            default -> {return null;}
        }
    }

    /** 
     * Calls the serialise function for the object and creates a hash via SHA-1
     * @return byte[]
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public byte[] hash() throws IOException, NoSuchAlgorithmException {
        // The hash will be applied to the envelope, not the content itself as
        // two blobs could have the same hash if we was to just feed the content

        byte[] serialised = serialise();
        MessageDigest md = MessageDigest.getInstance("SHA-1");

        byte[] hash = md.digest(serialised);

        return hash;   
    }
}