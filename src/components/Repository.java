package components;

import components.ObjectStore;
import entities.HeadInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;
import objects.Commit;
import objects.JitObject;

import static commands.BranchCommands.branch;
import static commands.CommitCommands.commit;

public class Repository {
    public static final Path JIT = Path.of(".jit");

    /**
     * Returns the commit hash of the HEAD file, HEAD may either be attached so with a ref or detatched with a straight hash
     * @return byte[]
     */
    public static HeadInfo resolveHead(ObjectStore objStore) throws IOException {
        // Detatched HEAD, this means that the HEAD would store a hash rather than a ref, meaning the person must of
        // switched to a previous commit and the HEAD would store that hash commit in the file rather then a ref of the
        // main/master branch, this is because someone must of checkouted to a specific commit hash which makes the HEAD
        // point to that hash rather than the main branch via ref

        byte[] HEAD = Files.readAllBytes(JIT.resolve("HEAD"));
        String stringHead = new String(HEAD, JitObject.utf);

        // Branch executes given that it sees refs/heads/master which means that we are at the
        // tip of a branch, the branch may not point to anything meaning an initial commit hasn't been
        // processed, in that case a branch name still exists as all defaults point to 'master', there
        // is just no commit object to refer to for the HEAD to construct for comparisons sake
        if (stringHead.substring(0, 3).equals("ref")) {
            // ref: refs/heads/master
            String refsPath = stringHead.substring(5);
            Path _path = JIT.resolve(Path.of(refsPath));

            int idx = stringHead.lastIndexOf("/");
            String branch = stringHead.substring(idx + 1);

            // path=refs/heads/master

            // The file being empty means that this is the first commit, meaning there is a branch
            // due to default 'master' but no commit to refer to so we say its empty
            if (!Files.exists(_path)) {
                return new HeadInfo(Optional.of(branch), Optional.empty());
            }

            // Reads the hash stored in the master file that was pointed to
            // by HEAD then uses that hash to go into objects and find
            // the object stored there, it would be a commit as this master
            // file points to the commit currently on, then we go to the
            // commit treehash via commit.treeHash which then would be a tree
            // so an actual hierarchy of files/folders

            byte[] hash = HexFormat.of().parseHex(Files.readString(_path).trim());
            JitObject object = objStore.load(hash).get();
            Commit commit = (Commit) object;
            return new HeadInfo(Optional.of(branch), Optional.of(commit));
        }

        // A HEAD that does not have a ref: means it a detatched HEAD and points to a commit
        // rather than the tip of a branch, meaning there is no branch but there is still a commit
        // as the HEAD would store the straight hash for it
        JitObject object = objStore.load(HEAD).get();
        Commit commit = (Commit) object;
        return new HeadInfo(Optional.empty(), Optional.of(commit));
    }
}
