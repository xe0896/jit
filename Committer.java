import java.time.Instant;

public record Committer(String name, String email, Instant time) {}
