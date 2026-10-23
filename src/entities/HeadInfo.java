package entities;

import java.util.Optional;

import objects.Commit;

public record HeadInfo(Optional<String> branch, Optional<Commit> commit) {
}
