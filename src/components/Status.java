package components;

public sealed interface Status {
    record Added(String path) implements Status {
    }

    record Modified(String path) implements Status {
    }

    record Deleted(String path) implements Status {
    }

    record Untracked(String path) implements Status {
    }
}