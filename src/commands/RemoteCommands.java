package commands;

import static cli.ConsoleOutput.EXIT_FAILURE;
import static cli.ConsoleOutput.EXIT_SUCCESS;
import static commands.BranchCommands.curBranch;
import static components.Repository.JIT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


import components.ObjectStore;

public class RemoteCommands {
    private static final String ADD = "add";
    private static final String REPLACE = "replace";

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

    public static void writeConfig(Map<String, Map<String, String>> map) throws IOException {
        Path config = JIT.resolve("config");
        if (!Files.exists(config))
            EXIT_FAILURE("No config file found (corrupted)");

        try (BufferedWriter writer = Files.newBufferedWriter(config)) {
            for (var entry : map.entrySet()) {
                String key = entry.getKey();
                Map<String, String> values = entry.getValue();

                writer.write(String.format("[%s]\n", key));

                for(var e : values.entrySet()) {
                    String reference = e.getKey();
                    String value = e.getValue();
                    writer.write(String.format("	%s = %s\n", reference, value));
                }

                writer.write("\n");
            }
        }
    }

    public static void configInit(String firstBranch) throws IOException {
        String key = String.format("branch \"%s\"", firstBranch);
        Map<String, String> init = Map.of("remote", "origin");
        Map<String, Map<String, String>> map = Map.of(key, init);
        writeConfig(map);
    }

    public static Map<String, Map<String, String>> readConfig(Path path) throws IOException {
        Map<String, Map<String, String>> res = new HashMap<>();
        if (!Files.exists(path))
            EXIT_FAILURE("No config file found (corrupted)");

        String config = Files.readString(path);
        BufferedReader reader = new BufferedReader(new StringReader(config));

        String line;
        while ((line = reader.readLine()) != null) {
            if(!line.isBlank()) {
                String key = line.substring(1, line.length() - 1);
                Map<String, String> configMap = new HashMap<>();
                while(!(line = reader.readLine().trim()).isEmpty()) {
                    int idx = line.indexOf("=");
                    String reference = line.substring(0, idx - 1);
                    String value = line.substring(idx + 2, line.length());

                    configMap.put(reference, value);
                }
                res.put(key, configMap);
            }
        }

        // Given the string, we need to parse this into a map; in order to do this we can say that
        // the first line would be the start of an instance of a K,V pair in the map.

        return res;
    }

    public static Optional<String> getRemote(Map<String, Map<String, String>> config, String curBranch) throws IOException {
        String key = String.format("branch \"%s\"", curBranch);

        Map<String, String> map = config.get(key);

        if(map == null)
            EXIT_FAILURE(String.format("'%s' does not have a branch instance in config (corruption)", curBranch));

        String remote = map.get("remote");
        return remote == null ? Optional.empty() : Optional.of(remote);
    }

    public static Optional<String> remoteUrl(Map<String, Map<String, String>> config, String remote) {
        String key = String.format("remote \"%s\"", remote);

        Map<String, String> map = config.get(key);

        if(map == null)
            return Optional.empty();

        String url = map.get("url");

        if(url == null) EXIT_FAILURE(String.format("Missing url for remote `%s`", remote));

        return Optional.of(url);
    }

    public static void remote(String[] args) throws IOException {
        ObjectStore objStore = new ObjectStore();
        PrintStream out = System.out;
        if (args.length == 0) {
            // Print out the origin for this branch
            Map<String, Map<String, String>> config = readConfig(JIT.resolve("config"));
            if(config == null) {
                out.println("No config values are in the config file, must be a new project");
            } else {
                String curBranch = curBranch(objStore);
                Optional<String> remote = getRemote(config, curBranch);
                if(remote.isEmpty()) {
                    EXIT_SUCCESS(String.format("Add a new remote for branch %s", curBranch));
                } else {
                    String remoteIdentifier = remote.get();
                    Optional<String> url = remoteUrl(config, remoteIdentifier);
                    if(url.isEmpty()) {
                        EXIT_SUCCESS(String.format("%s (no URL has been assigned)", remoteIdentifier));
                    } else {
                        EXIT_SUCCESS(String.format("%s (%s)", remoteIdentifier, url.get()));
                    }
                }
            }

        } else if (args.length == 3) {
            String cmd = args[0];
            Path config = JIT.resolve("config");
            if (!Files.exists(config)) EXIT_FAILURE("Config file does not exist (corruption)");

            switch(cmd) {
                case ADD -> {
                    Map<String, Map<String, String>> configMap = readConfig(config);
                    String curBranch = curBranch(objStore);

                    Optional<String> remote = getRemote(configMap, curBranch);
                    if(remote.isEmpty()) EXIT_FAILURE(String.format("No remote exists for branch '%s'", curBranch));

                    if(!remote.get().equals(args[1])) EXIT_FAILURE(String.format("Remote %s does not belong to branch %s, the remote found was %s", args[1], curBranch, remote.get()));

                    Optional<String> url = remoteUrl(configMap, remote.get());
                    if(!url.isEmpty()) EXIT_FAILURE(String.format("URL already exists for branch %s and it is: \"%s\"", remote.get(), url.get()));

                    String key = String.format("remote \"%s\"", args[1]);
                    Map<String, String> map = configMap.get(key);

                    if(map == null) map = new HashMap<>();

                    map.put("url", args[2]);
                    configMap.put(key, map);
                    writeConfig(configMap);
                }

                case REPLACE -> {
                    Map<String, Map<String, String>> configMap = readConfig(config);
                    String curBranch = curBranch(objStore);

                    Optional<String> remote = getRemote(configMap, curBranch);
                    if(remote.isEmpty()) EXIT_FAILURE(String.format("No remote exists for branch '%s'", curBranch));

                    if(!remote.get().equals(args[1])) EXIT_FAILURE(String.format("Remote %s does not belong to branch %s, the remote found was %s", args[1], curBranch, remote.get()));

                    Optional<String> url = remoteUrl(configMap, remote.get());
                    if(url.isEmpty()) EXIT_FAILURE(String.format("Nothing to replace for branch '%s' as it is already empty, use 'jit remote add %s'", curBranch, curBranch));

                    String key = String.format("remote \"%s\"", args[1]);
                    Map<String, String> map = configMap.get(key);

                    if(map == null) map = new HashMap<>();

                    map.put("url", args[2]);
                    configMap.put(key, map);
                    writeConfig(configMap);
                }

                default -> {
                    EXIT_FAILURE("jit remote does not recognise command: " + cmd);
                }
            }
        } else {
            EXIT_FAILURE("jit remote only supports 'replace', 'add");
        }
    }
}
