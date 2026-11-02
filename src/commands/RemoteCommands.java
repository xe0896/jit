package commands;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static commands.BranchCommands.branch;
import static components.Repository.JIT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import commands.RepositoryCommands.ConfigValue;

public class RemoteCommands {
    public static void pull() {

    }

    public static void push(String[] args) {
        String remote = args[0];
        String branch = args[1];

        Path config = JIT.resolve("config");

        if (!Files.exists(config)) {
            EXIT_FAILURE("Config file does not exist (corruption)");
        }

    }

    public static void writeConfig(Map<String, List<ConfigValue>> map) {
        Path config = JIT.resolve("config");
        if (!Files.exists(config))
            EXIT_FAILURE("No config file found (corrupted)");

        PrintStream out = System.out;

        for (var entry : map.entrySet()) {
            String key = entry.getKey();
            List<ConfigValue> values = entry.getValue();

            out.print(String.format("[%s]\n", key));
            out.print("     ");

            for (ConfigValue cv : values) {
                out.print(String.format("%s = %s\n", cv.reference(), cv.value()));
                out.print("     \n\n");
            }
        }
    }

    public static Map<String, List<ConfigValue>> readConfig() throws IOException {
        Map<String, List<ConfigValue>> res = new HashMap<>();
        Path pathConfig = JIT.resolve("config");
        if (!Files.exists(pathConfig))
            EXIT_FAILURE("No config file found (corrupted)");
        String config = Files.readString(pathConfig);

        BufferedReader reader = new BufferedReader(new StringReader(config));

        return res;
    }

    public static void remote(String[] args) {
        if (args.length == 1) {
            // Print out the origin for this branch
        } else if (args.length == 3) {
            // Only add is supported in the form: jit remote add origin <URL>
            // "add", "remote" and the "URL" if it ain't 3 then it is a failure
            Path config = JIT.resolve("config");

            if (!Files.exists(config)) {
                EXIT_FAILURE("Config file does not exist (corruption)");
            }

        } else {
            EXIT_FAILURE("jit remote add <remote> <URL> is only supported");
        }
    }
}
