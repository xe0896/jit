import java.time.Instant;

public class Tag extends GitObject {
    private static final String TYPE = "tag";

    public byte[] targetHash;
    String targetType;
    String tagName;
    String taggerName;
    String taggerEmail;
    Instant taggerTime;
    String message;

    @Override 
    public String type() {
        return TYPE;
    }

    @Override 
    public byte[] serialiseContent() {
        return null;
    }
    
    public static Tag parseContent(byte[] content) {
        return null;
    }
}
