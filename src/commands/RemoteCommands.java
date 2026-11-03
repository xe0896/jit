package commands;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static components.Repository.JIT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static void writeConfig(Map<String, List<ConfigValue>> map) throws IOException {
        Path config = JIT.resolve("config");
        if (!Files.exists(config))
            EXIT_FAILURE("No config file found (corrupted)");

        try (BufferedWriter writer = Files.newBufferedWriter(config)) {
            for (var entry : map.entrySet()) {
                String key = entry.getKey();
                List<ConfigValue> values = entry.getValue();

                writer.write(String.format("[%s]\n", key));

                for (ConfigValue cv : values) {
                    writer.write(String.format("	%s = %s\n", cv.reference(), cv.value()));
                }

                writer.write("\n");
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

        String line;
        while ((line = reader.readLine()) != null) {
            if(!line.isBlank()) {
                String key = line.substring(1, line.length() - 1);
                List<ConfigValue> configList = new ArrayList<>();
                while(!(line = reader.readLine().trim()).isEmpty()) {
                    int idx = line.indexOf("=");
                    String reference = line.substring(0, idx - 1);
                    String value = line.substring(idx + 2, line.length());

                    configList.add(new ConfigValue(reference, value));
                }
                res.put(key, configList);
            }
        }

        // Given the string, we need to parse this into a map; in order to do this we can say that
        // the first line would be the start of an instance of a K,V pair in the map.

        return res;
    }

    record ConfigValue(String reference, String value) {
    }

    public static void remote(String[] args) throws IOException {
        List<ConfigValue> a = List.of(new ConfigValue("a", "b"), new ConfigValue("c", "d"), new ConfigValue("e", "f"));
        List<ConfigValue> b = List.of(new ConfigValue("g", "h"), new ConfigValue("i", "j"), new ConfigValue("k", "l"));
        List<ConfigValue> c = List.of(new ConfigValue("m", "n"), new ConfigValue("o", "p"), new ConfigValue("q", "r"));
        Map<String, List<ConfigValue>> configs = Map.of("A", a, "B", b, "C", c);

        writeConfig(configs);
        Map<String, List<ConfigValue>> res = readConfig();
        writeConfig(res);
        Map<String, List<ConfigValue>> res1 = readConfig();
        writeConfig(res1);

        /*
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
        */
    }
}
