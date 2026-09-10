import java.io.ByteArrayOutputStream;
import java.io.IOException;

public record TreeEntry(byte[] hash, int mode, String name) {
    public void writeTo(ByteArrayOutputStream out) throws IOException {
        out.write(Integer.toOctalString(mode).getBytes(GitObject.ascii));
        out.write(' ');
        out.write(name.getBytes(GitObject.ascii));
        out.write(0);
        out.write(hash);
    }
}