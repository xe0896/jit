public class Tag extends GitObject {
    private static final String TYPE = "tag";

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
