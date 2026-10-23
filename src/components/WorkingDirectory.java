package components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static commands.RemoteCommands.push;

public class WorkingDirectory {
    // List of things to ignore in the root of a jit directory
    private static final List<String> ignores = List.of(".git", ".jit", ".DS_Store");

    public static Map<String, byte[]> getWorkingPaths() throws IOException {
        Map<String, byte[]> currentPaths = new HashMap<>();
        Deque<Path> stack = new ArrayDeque<>();

        stack.push(Path.of(""));

        while (!stack.isEmpty()) {
            Path cur = stack.pop();
            if (ignores.contains(cur.getFileName().toString()))
                continue;
            if (Files.isDirectory(cur)) {
                Files.list(cur).forEach(path -> {
                    stack.push(path);
                });
            } else {
                currentPaths.put(cur.toString(), Files.readAllBytes(cur));
            }
        }

        return currentPaths;
    }
}
