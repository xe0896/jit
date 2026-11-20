package git.commands;

import static git.cli.ConsoleOutput.EXIT_FAILURE;
import static git.cli.ConsoleOutput.EXIT_SUCCESS;
import static git.commands.BranchCommands.curBranch;
import static git.components.Repository.JIT;
import static git.components.Repository.resolveHead;

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

import git.HeadRequest;
import git.HeadResponse;
import git.PusherServiceGrpc;
import git.components.ObjectStore;
import git.entities.HeadInfo;
import git.objects.Commit;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class RemoteCommands {
   private static final String ADD = "add";
   private static final String REPLACE = "replace";
   private static PusherServiceGrpc.PusherServiceStub pushStub;

   public static void pull() {

   }

   public static void push() throws IOException {
      ObjectStore objStore = new ObjectStore();
      String curBranch = curBranch(objStore);

      Map<String, Map<String, String>> config = readConfig(JIT.resolve("config"));

      Optional<String> remote = getRemote(config, curBranch);
      if (remote.isEmpty())
         throw EXIT_FAILURE("Remote does not exist");

      Optional<String> url = remoteUrl(config, remote.get());

      if (url.isEmpty())
         throw EXIT_FAILURE("URL does not exist");

      HeadInfo head = resolveHead(objStore);
      if (head.commit().isEmpty())
         throw EXIT_FAILURE("Commit does not exist");

      // Assumes that the HEAD is not detatched
      pusher(url.get(), head.branch().get(), head.commit().get());
   }

   // Given a valid URL and the commit we want to push, we would remotely add the
   // commit to save it to some database
   public static void pusher(String url, String branch, Commit commit) {
      ManagedChannel serverChannel = Grpc.newChannelBuilder(url, InsecureChannelCredentials.create()).build();
      pushStub = PusherServiceGrpc.newStub(serverChannel);
      HeadRequest headRequest = HeadRequest.newBuilder().setBranch(branch).build();

      // Stub used is asynchronous, this request is unary so no async but still have
      // to follow the structure of it being async, onNext gives us what we need
      // instantly
      pushStub.currentCommit(headRequest, new StreamObserver<HeadResponse>() {
         private static String commitHash;

         @Override
         public void onNext(HeadResponse response) {
            commitHash = response.getCommitHash();
            if (commitHash.isBlank()) {
               // Has no commit hash for this branch, the next step is to
               // send the full history of the commit hash
            } else {
               // Compare with the current and see if it needs an updated one
               // with all the commit hash range between old and current

            }
         }

         @Override
         public void onError(Throwable t) {
            t.printStackTrace();
         }

         @Override
         public void onCompleted() {
            System.out.println("Received the commit hash: " + commitHash);
         }
      });
   }

   public static void push(String[] args) throws IOException {
      String remote = args[0];
      String branch = args[1];

      Path configPath = JIT.resolve("config");

      if (!Files.exists(configPath))
         throw EXIT_FAILURE("Config file does not exist (corruption)");

      Map<String, Map<String, String>> config = readConfig(JIT.resolve("config"));
      System.out.println("Checking remote: " + remote);
      Optional<String> url = remoteUrl(config, remote);

      if (!url.isEmpty())
         System.out.println(url.get());
   }

   public static void writeConfig(Map<String, Map<String, String>> map) throws IOException {
      Path config = JIT.resolve("config");
      if (!Files.exists(config))
         throw EXIT_FAILURE("No config file found (corrupted)");

      try (BufferedWriter writer = Files.newBufferedWriter(config)) {
         for (var entry : map.entrySet()) {
            String key = entry.getKey();
            Map<String, String> values = entry.getValue();

            writer.write(String.format("[%s]\n", key));

            for (var e : values.entrySet()) {
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
         throw EXIT_FAILURE("No config file found (corrupted)");

      String config = Files.readString(path);
      BufferedReader reader = new BufferedReader(new StringReader(config));

      String line;
      while ((line = reader.readLine()) != null) {
         if (!line.isBlank()) {
            String key = line.substring(1, line.length() - 1);
            Map<String, String> configMap = new HashMap<>();
            while (!(line = reader.readLine().trim()).isEmpty()) {
               int idx = line.indexOf("=");
               String reference = line.substring(0, idx - 1);
               String value = line.substring(idx + 2, line.length());

               configMap.put(reference, value);
            }
            res.put(key, configMap);
         }
      }

      // Given the string, we need to parse this into a map; in order to do this we
      // can say that
      // the first line would be the start of an instance of a K,V pair in the map.

      return res;
   }

   public static Optional<String> getRemote(Map<String, Map<String, String>> config, String curBranch)
         throws IOException {
      String key = String.format("branch \"%s\"", curBranch);

      Map<String, String> map = config.get(key);

      if (map == null)
         throw EXIT_FAILURE(String.format("'%s' does not have a branch instance in config (corruption)", curBranch));

      String remote = map.get("remote");
      return remote == null ? Optional.empty() : Optional.of(remote);
   }

   public static Optional<String> remoteUrl(Map<String, Map<String, String>> config, String remote) {
      String key = String.format("remote \"%s\"", remote);

      Map<String, String> map = config.get(key);

      if (map == null)
         return Optional.empty();

      String url = map.get("url");

      if (url == null)
         throw EXIT_FAILURE(String.format("Missing url for remote `%s`", remote));

      return Optional.of(url);
   }

   public static void remote(String[] args) throws IOException {
      ObjectStore objStore = new ObjectStore();
      PrintStream out = System.out;
      if (args.length == 0) {
         // Print out the origin for this branch
         Map<String, Map<String, String>> config = readConfig(JIT.resolve("config"));
         if (config == null) {
            out.println("No config values are in the config file, must be a new project");
         } else {
            String curBranch = curBranch(objStore);
            Optional<String> remote = getRemote(config, curBranch);
            if (remote.isEmpty()) {
               EXIT_SUCCESS(String.format("Add a new remote for branch %s", curBranch));
            } else {
               String remoteIdentifier = remote.get();
               Optional<String> url = remoteUrl(config, remoteIdentifier);
               if (url.isEmpty()) {
                  EXIT_SUCCESS(String.format("%s (no URL has been assigned)", remoteIdentifier));
               } else {
                  EXIT_SUCCESS(String.format("%s (%s)", remoteIdentifier, url.get()));
               }
            }
         }

      } else if (args.length == 3) {
         String cmd = args[0];
         Path config = JIT.resolve("config");
         if (!Files.exists(config))
            throw EXIT_FAILURE("Config file does not exist (corruption)");

         switch (cmd) {
            case ADD -> {
               Map<String, Map<String, String>> configMap = readConfig(config);
               String curBranch = curBranch(objStore);

               Optional<String> remote = getRemote(configMap, curBranch);
               if (remote.isEmpty())
                  throw EXIT_FAILURE(String.format("No remote exists for branch '%s'", curBranch));

               if (!remote.get().equals(args[1]))
                  throw EXIT_FAILURE(String.format("Remote %s does not belong to branch %s, the remote found was %s",
                        args[1], curBranch, remote.get()));

               Optional<String> url = remoteUrl(configMap, remote.get());
               if (!url.isEmpty())
                  throw EXIT_FAILURE(
                        String.format("URL already exists for branch %s and it is: \"%s\"", remote.get(), url.get()));

               String key = String.format("remote \"%s\"", args[1]);
               Map<String, String> map = configMap.get(key);

               if (map == null)
                  map = new HashMap<>();

               map.put("url", args[2]);
               configMap.put(key, map);
               writeConfig(configMap);
            }

            case REPLACE -> {
               Map<String, Map<String, String>> configMap = readConfig(config);
               String curBranch = curBranch(objStore);

               Optional<String> remote = getRemote(configMap, curBranch);
               if (remote.isEmpty())
                  throw EXIT_FAILURE(String.format("No remote exists for branch '%s'", curBranch));

               if (!remote.get().equals(args[1]))
                  throw EXIT_FAILURE(String.format("Remote %s does not belong to branch %s, the remote found was %s",
                        args[1], curBranch, remote.get()));

               Optional<String> url = remoteUrl(configMap, remote.get());
               if (url.isEmpty())
                  throw EXIT_FAILURE(String.format(
                        "Nothing to replace for branch '%s' as it is already empty, use 'jit remote add %s'", curBranch,
                        curBranch));

               String key = String.format("remote \"%s\"", args[1]);
               Map<String, String> map = configMap.get(key);

               if (map == null)
                  map = new HashMap<>();

               map.put("url", args[2]);
               configMap.put(key, map);
               writeConfig(configMap);
            }

            default -> {
               throw EXIT_FAILURE("jit remote does not recognise command: " + cmd);
            }
         }
      } else {
         throw EXIT_FAILURE("jit remote only supports 'replace', 'add'");
      }
   }
}
