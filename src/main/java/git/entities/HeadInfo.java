package git.entities;

import java.util.Optional;

import git.objects.Commit;

public record HeadInfo(Optional<String> branch, Optional<Commit> commit) {
}
