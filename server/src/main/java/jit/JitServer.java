package jit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;

import git.HeadRequest;
import git.HeadResponse;
import git.PushChunk;
import git.PushResponse;
import git.PusherServiceGrpc;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

public class JitServer {
   private static final ObjectMapper mapper = new ObjectMapper();

   private static Server server;
   // Connection details come from the environment (see server/.env.example) so
   // no credentials live in the repo
   private static final String url = requireEnv("DB_URL");
   private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
   private static final String DB_PASSWORD = requireEnv("DB_PASSWORD");

   private static String requireEnv(String name) {
      String value = System.getenv(name);
      if (value == null || value.isBlank()) {
         throw new IllegalStateException("Missing required environment variable: " + name);
      }
      return value;
   }

   public static void main(String[] args) throws IOException {
      int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
      server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
            .executor(executor)
            .build()
            .start();
      System.out.println("Server started, listening on " + port);

      try (Connection initConn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
         initialise(initConn);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static void initialise(Connection conn) throws Exception {
      try (InputStream in = JitServer.class
            .getResourceAsStream("/schema.sql")) {

         if (in == null) {
            throw new FileNotFoundException("schema.sql not found");
         }

         String sql = new String(
               in.readAllBytes(),
               StandardCharsets.UTF_8);

         try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
         }
      }
   }

   static class PusherImpl extends PusherServiceGrpc.PusherServiceImplBase {
      static String getBranchQuery = "SELECT * FROM branches WHERE name = ?";

      public static Optional<String> getCurrentCommitHash(String branch) {
         try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD)) {
            PreparedStatement branchQueryPs = conn.prepareStatement(getBranchQuery);
            branchQueryPs.setString(1, branch);

            try (ResultSet rs = branchQueryPs.executeQuery()) {
               // rs.next() returns false if a SELECT query of rows is empty
               if (!rs.next()) {
                  // Empty, this is the first branch so we can convey this to the client by
                  // returning ""
                  return Optional.empty();
               } else {
                  // Contains a branch, we return the tip to let them know how many
                  // commits we need to stay ahead
                  String currentTip = rs.getString("tip_hash");
                  return Optional.of(currentTip);
               }
            }

         } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
         }

         return Optional.empty();
      }

      @Override
      public void currentCommit(HeadRequest req, StreamObserver<HeadResponse> responseObserver) {
         String branch = req.getBranch();
         HeadResponse headResponse;

         Optional<String> currentTip = getCurrentCommitHash(branch);
         if (!currentTip.isEmpty()) {
            headResponse = HeadResponse.newBuilder().setCommitHash(currentTip.get()).build();
         } else {
            headResponse = HeadResponse.newBuilder().setCommitHash("").build();
         }

         responseObserver.onNext(headResponse);
         responseObserver.onCompleted();
      }

      // Returns how to handle operations that is started by the client calling
      // 'pusher'
      // gRPC would be handling all of the correct calls and also the cleanup in
      // onCompleted
      @Override
      public StreamObserver<PushChunk> pusher(StreamObserver<PushResponse> responseObserver) {
         return new StreamObserver<PushChunk>() {
            @Override
            public void onNext(PushChunk chunk) {
               // Receive each chunk from the client
            }

            @Override
            public void onError(Throwable t) {
               // Client stream failed
            }

            @Override
            public void onCompleted() {
               // Client finished sending

               PushResponse response = PushResponse.newBuilder()
                     .setSuccess(true)
                     .build();

               responseObserver.onNext(response);
               responseObserver.onCompleted();
            }
         };
      }
   }
}
