import java.io.ByteArrayOutputStream;
import java.io.IOException;

public record TreeEntry(byte[] hash, int mode, String name) {
    /** 
     * The hash here could either be another tree or a blob, the mode
     * also can be applied to trees like blobs
     * @param out
     * @throws IOException
     */
    public void writeTo(ByteArrayOutputStream out) throws IOException {
        out.write(Integer.toOctalString(mode).getBytes(GitObject.ascii));
        out.write(' ');
        out.write(name.getBytes(GitObject.ascii));
        out.write(0);
        out.write(hash);
    }
}