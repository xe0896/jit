import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final String INIT = "init";
    private static final String ADD = "add";
    private static final String STATUS = "status";
    
    public static final Path JIT = Path.of(".jit");

    public static void main(String[] args) throws IOException {
        if(args.length == 0) {System.out.println("?"); return;}
        switch(args[0]) {
            case INIT -> init();
            case ADD -> add();
            case STATUS -> status();
            default -> System.out.printf("Command '%s' not implemented or does not exist\n", args[0]);
        }
    }

    public static void add() {

    }

    public static void status() {

    }

    public static void init() throws IOException {
        // Assuming it is a sensible user, not handling errors with regarding to corrupted .jit structure
        try {
            Files.createDirectory(JIT);
        } catch (FileAlreadyExistsException e) {
            Path cur = Path.of("").toAbsolutePath();
            System.out.printf(".jit already exists in %s\n", cur.toString());
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Files.createDirectory(JIT.resolve("objects"));
        Files.createDirectory(JIT.resolve("refs"));
        Files.createFile(JIT.resolve("index"));
        Files.createFile(JIT.resolve("HEAD"));
    }
}
