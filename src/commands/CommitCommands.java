package commands;

import components.Index;
import components.ObjectStore;
import entities.Author;
import entities.Committer;
import entities.HeadInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import objects.Commit;
import objects.JitObject;
import objects.Tree;

import static cli.ConsoleOutput.logPrinter;
import static commands.BranchCommands.branch;
import static commands.RepositoryCommands.add;
import static components.Repository.JIT;
import static components.Repository.resolveHead;

public class CommitCommands {
    public static void commit(String message) throws IOException, NoSuchAlgorithmException {
        System.out.println(message);
        // When we commit that would make the HEAD point to this new commit,
        // we need to take a snapshot of the current index
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        index.read(); // Read what is the current index file, what has been added

        // Tree root of the indexed files
        byte[] root = index.buildTree();

        HeadInfo head = resolveHead(objStore);
        Optional<String> _branch = head.branch();
        Optional<Commit> _prevCommit = head.commit();

        byte[] commitHash;

        if (_prevCommit.isEmpty()) {
            // An empty commit means this is the first commit, so there is no
            // commit list we have to construct for this commit, this means
            // that we create the list of previous hashes to be empty

            List<byte[]> parentHashes = new ArrayList<>();

            Commit commit = createCommit(message, root, parentHashes);
            commitHash = objStore.store(commit);
        } else {
            // A commit exists so we have to deserialise it and get the list it has
            // and then append that commit to it then use that as the list for this commit
            Commit prevCommit = _prevCommit.get();
            byte[] prevCommitHash = prevCommit.hash();

            // Grab the previous commits previous hashes, then add the previous commit to the list
            // as this would be the previous hash list for the current commit
            List<byte[]> parentHashes = prevCommit.parentHashes;
            parentHashes.add(prevCommitHash);

            Commit commit = createCommit(message, root, parentHashes);
            commitHash = objStore.store(commit);
        }

        if (_branch.isEmpty()) {
            // Detatched HEAD, store in the HEAD file directly the commit hash
            Files.writeString(JIT.resolve("HEAD"), HexFormat.of().formatHex(commitHash));
        } else {
            // Undetatched, meaning it is pointing to the tip of a branch stored
            // in head, so store the current hash to where it is pointing to
            byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
            String stringHead = new String(HEAD, JitObject.utf);
            String refsPath = stringHead.substring(5);
            Path _path = JIT.resolve(Path.of(refsPath));
            // May be the first commit, meaning the branch file does not exist ('master' in 'refs/heads/master')
            if (!Files.exists(_path))
                Files.createFile(_path);

            Files.writeString(_path, HexFormat.of().formatHex(commitHash));
        }
    }

    public static Commit createCommit(String message, byte[] treeHash, List<byte[]> parentHashes) {
        // Creating a commit is done by given the tree hash we have the contents from that
        // but we also need to have a list of the previous commits relative to this one
        // we can do that by

        Author author = new Author("zain", "zainahmed@gmail.com", Instant.now());
        Committer committer = new Committer("zain", "zainahmed@gmail.com", Instant.now());

        return Commit.of(treeHash, parentHashes, committer, author, message);
    }

    public static void log() throws IOException, NoSuchAlgorithmException {
        ObjectStore objStore = new ObjectStore();
        HeadInfo head = resolveHead(objStore);
        if (head.commit().isEmpty()) {
            System.out.println("No commit has happened yet");
            return;
        }

        StringBuilder sb = new StringBuilder();

        Commit commit = head.commit().get();

        logPrinter(sb, commit);

        List<byte[]> parentHashes = commit.parentHashes;

        for (int i = parentHashes.size() - 1; i >= 0; i--) {
            byte[] b = parentHashes.get(i);
            Commit otherCommit = (Commit) objStore.load(b).get();
            logPrinter(sb, otherCommit);
        }

        System.out.println(sb);
    }
}
