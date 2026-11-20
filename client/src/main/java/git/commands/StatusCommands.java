package git.commands;

import static git.cli.ConsoleOutput.statusPrint;
import static git.components.StatusScanner.changes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import git.components.Status;
import git.entities.HeadInfo;

public class StatusCommands {
   public static void status() throws IOException {
      List<Status> staged = new ArrayList<>();
      List<Status> unstaged = new ArrayList<>();
      List<Status> untracked = new ArrayList<>();

      HeadInfo head = changes(staged, unstaged, untracked);

      statusPrint(head.branch(), head.commit(), staged, unstaged, untracked);
   }
}
