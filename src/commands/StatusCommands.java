package commands;

import components.Status;
import entities.HeadInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cli.ConsoleOutput.statusPrint;
import static commands.BranchCommands.branch;
import static components.StatusScanner.changes;

public class StatusCommands {
    public static void status() throws IOException {
        List<Status> staged = new ArrayList<>();
        List<Status> unstaged = new ArrayList<>();
        List<Status> untracked = new ArrayList<>();

        HeadInfo head = changes(staged, unstaged, untracked);
        Optional<String> branch = head.branch();

        String message = statusPrint(branch, staged, unstaged, untracked);
        System.out.println(message);
    }
}
