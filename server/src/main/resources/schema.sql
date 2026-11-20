DROP TABLE IF EXISTS commits;

CREATE TABLE branches (
    repository_id UUID NOT NULL,
    name TEXT NOT NULL,
    tip_hash TEXT NOT NULL,
    PRIMARY KEY (repository_id, name)
);

CREATE TABLE commits (
    hash TEXT PRIMARY KEY,
    parent_hash TEXT REFERENCES commits(hash)
);
