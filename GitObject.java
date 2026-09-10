import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.util.Arrays;

public abstract class GitObject {
    public static final Charset ascii = StandardCharsets.US_ASCII;
    public static final int HASH_LENGTH = 20;

    abstract String type();
    abstract byte[] serialiseContent() throws IOException;

    // Creates the envelope of the content as well as the type
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

    public static GitObject deserialise(byte[] envelope) {
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
            case "blob" -> {return Blob.parseContent(content);}
            case "tree" -> {return Tree.parseContent(content);}
            case "commit" -> {return Commit.parseContent(content);}
            case "tag" -> {return Tag.parseContent(content);}
            default -> {return null;}
        }
    }

    public byte[] hash() throws IOException, NoSuchAlgorithmException {
        // The hash will be applied to the envelope, not the content itself as
        // two blobs could have the same hash if we was to just feed the content

        byte[] serialised = serialise();
        MessageDigest md = MessageDigest.getInstance("SHA-1");

        byte[] hash = md.digest(serialised);

        return hash;   
    }
}