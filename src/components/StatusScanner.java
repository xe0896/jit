package components;

import components.Index;
import components.ObjectStore;
import components.Status;
import entities.HeadInfo;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import objects.Bloob;
import objects.Commit;
import objects.JitObject;
import objects.Tree;
import objects.Tree.TreeEntry;

import static commands.CommitCommands.commit;
import static commands.RepositoryCommands.add;
import static commands.StatusCommands.status;
import static components.Repository.resolveHead;
import static components.WorkingDirectory.getWorkingPaths;

public class StatusScanner {
    public static HeadInfo changes(List<Status> staged, List<Status> unstaged, List<Status> untracked)
            throws IOException {
        ObjectStore objStore = new ObjectStore();
        Index index = new Index(objStore);

        // status has 3 jobs:
        // HEAD vs index: what has been staged and is ready to be committed
        // index vs working directory: what has changed but hasn't been staged
        // last case is working directory files not being staged at all
        HeadInfo head = resolveHead(objStore);

        Optional<Commit> commit = head.commit();

        Map<String, byte[]> treeMap = (!commit.isEmpty())
                ? Tree.flattenTree((Tree) objStore.load(commit.get().treeHash).get())
                : new HashMap<>();

        Map<String, byte[]> workingMap = getWorkingPaths();
        index.read();

        // index.entries is used to get Map<String, byte[]> where byte[] is a SHA-1 hash of the
        // object that matches what is stored in the objects/. The hash won't contian the raw
        // file contents since it has other stuff such as the type, working directory takes the
        // hash file contents so we need to map this back and since this is gonna be a blob
        // then we can just read the full contents pointed to by object/

        Map<String, byte[]> indexMap = new HashMap<>();

        for (var entry : index.entries.entrySet()) {
            String path = entry.getKey();
            TreeEntry e = entry.getValue();

            JitObject object = objStore.load(e.hash()).get();
            Bloob blob = (Bloob) object;
            indexMap.put(path, blob.serialiseContent());
        }

        // Map<String, TreeEntry> indexMap = index.entries;

        // Staged changes: compares head with index, (added, modified, deleted)
        // Unstaged changes: compares index with working, (modified, deleted)
        // Untracked changes: files that are in working but not in index (new files)

        Set<String> headAndIndex = new HashSet<>(treeMap.keySet());
        headAndIndex.addAll(indexMap.keySet());

        Set<String> indexAndWorking = new HashSet<>(indexMap.keySet());
        indexAndWorking.addAll(workingMap.keySet());

        // Comparing head with index to get the staged changes
        for (String path : headAndIndex) {
            byte[] headHash = treeMap.get(path);
            byte[] indexHash = indexMap.get(path);

            if (headHash == null) {
                // Head hash may be null as the file may have been created to be git added since this "path"
                // can only be in here if it is in head or index, given its not in head it must be in index
                // so this is a new file that has been git added
                staged.add(new Status.Added(path));
            } else if (indexHash == null) {
                // Index hash may be null as we may of done git rm file.txt which makes the file get deleted
                // as well as remove it from the index
                staged.add(new Status.Deleted(path));
            } else if (!Arrays.equals(headHash, indexHash)) {
                // Modified as if the arrays are not equal then that must mean some change must of occured
                // then someone done git add to reflect that in the index
                staged.add(new Status.Modified(path));
            }
            // An index hash having the same hash means nothing has changed, we can ignore this as the person
            // likely recently commited and hasn't changed a file yet
        }

        for (String path : indexAndWorking) {
            byte[] indexHash = indexMap.get(path);
            byte[] workingHash = workingMap.get(path);

            if (indexHash == null) {
                // Index hash may be null as this is a new file that has been created but hasn't been git added yet
                untracked.add(new Status.Untracked(path));
            } else if (workingHash == null) {
                // Working hash may be null as the file may of been deleted but not reflected in index yet since
                // they may of done rm file.txt instead of git rm file.txt
                unstaged.add(new Status.Deleted(path));
            } else if (!Arrays.equals(indexHash, workingHash)) {
                unstaged.add(new Status.Modified(path));
            }
        }

        return head;
    }
}
