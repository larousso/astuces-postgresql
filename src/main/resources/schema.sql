
create table if not exists show(
    "tconst" varchar(100) primary key,
    "titleType" text,
    "primaryTitle" text,
    "originalTitle" text,
    "isAdult"	bool,
    "startYear" numeric,
    "endYear" numeric,
    "runtimeMinutes" numeric,
    "genres" text[]
);

create table if not exists people(
    "nconst" varchar(100) primary key,
    "primaryName" text,
    "birthYear" numeric,
    "deathYear" numeric,
    "primaryProfession" text,
    "knownForTitles" text[]
);

create table if not exists episode(
    "tconst"	varchar(100) references show("tconst"),
    "parentTconst" varchar(100) references show("tconst"),
    "seasonNumber" numeric,
    "episodeNumber" numeric,
    UNIQUE("tconst", "parentTconst", "seasonNumber","episodeNumber")
);
create index if not exists episode_tconst_idx on episode(tconst);
create index if not exists episode_parenttconst_idx on episode("parentTconst");

create table if not exists show_crew(
    "tconst"	varchar(100) references show("tconst"),
    "ordering" numeric,
    "nconst" varchar(100) references people("nconst"),
    "category" text,
    "job" text,
    "characters" text[],
    UNIQUE("tconst", "nconst")
);

create index if not exists show_type_idx on show ("titleType");
