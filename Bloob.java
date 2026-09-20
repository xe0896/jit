// Actual file content like a main.c
public class Bloob extends GitObject {
    private static final String TYPE = "blob";
    private final byte[] content;

    private Bloob(byte[] content) {
        this.content = content;
    }

    @Override 
    public String type() {
        return TYPE;
    }

    @Override 
    public byte[] serialiseContent() {
        return content;
    }

    public static Bloob parseContent(byte[] payload) {
        return new Bloob(payload);
    }
}
