# Tips en postgresql 

## Data set 

Imdb : 
 * https://www.imdb.com/interfaces/
 * https://datasets.imdbws.com/



## Import de masse 


Se connecter sur le container pour utiliser copy :

```
docker-compose exec tips_postgres bash
```


Créer des tables tmp pour pouvoir retravailler les données 

```sql
create table if not exists show_import(
   "tconst" varchar(100) primary key,
   "titleType" text,
   "primaryTitle" text,
   "originalTitle" text,
   "isAdult"	bool,
   "startYear" numeric,
   "endYear" numeric,
   "runtimeMinutes" numeric,
   "genres" text
);
create table if not exists people_import(
    "nconst" varchar(100) primary key,
    "primaryName" text,
    "birthYear" numeric,
    "deathYear" numeric,
    "primaryProfession" text,
    "knownForTitles" text
);
create table if not exists episode_import(
    "tconst"	varchar(100),
    "parentTconst" varchar(100),
    "seasonNumber" numeric,
    "episodeNumber" numeric
);
create table if not exists show_crew_import(
    "tconst"	varchar(100),
    "ordering" numeric,
    "nconst" varchar(100),
    "category" text,
    "job" text,
    "characters" text
);
```

Sur le container : 
```
PGPASSWORD="movies" psql -h localhost -p 5432 -U movies -d movies -c "\copy show_import from '/home/pg/title.basics.tsv' with NULL '\N' DELIMITER E'\t' CSV HEADER QUOTE E'\b';"
```

```sql
insert into show("tconst","titleType","primaryTitle","originalTitle","isAdult","startYear","endYear","runtimeMinutes", "genres")
 select "tconst","titleType","primaryTitle","originalTitle","isAdult","startYear","endYear","runtimeMinutes", string_to_array("genres", ',')
 from show_import;

drop table show_import;
```

```
PGPASSWORD="movies" psql -h localhost -p 5432 -U movies -d movies -c "\copy people_import from '/home/pg/name.basics.tsv' with NULL '\N' DELIMITER E'\t' CSV HEADER QUOTE E'\b';"
```

```sql
insert into people(
    "nconst",
    "primaryName",
    "birthYear",
    "deathYear",
    "primaryProfession",
    "knownForTitles"
) select "nconst",
         "primaryName",
         "birthYear",
         "deathYear",
         "primaryProfession",
         string_to_array("knownForTitles", ',')
from people_import;

drop table people_import;

```

```
PGPASSWORD="movies" psql -h localhost -p 5432 -U movies -d movies -c "\copy episode_import from '/home/pg/title.episode.tsv' with NULL '\N' DELIMITER E'\t' CSV HEADER QUOTE E'\b';"
```

```sql
insert into episode(
    "tconst",
    "parentTconst",
    "seasonNumber",
    "episodeNumber"
)
select "tconst",
       "parentTconst",
       "seasonNumber",
       "episodeNumber"
from episode_import
where
    exists(select from show where show."tconst" = episode_import."tconst") and
    exists(select from show where show."tconst" = episode_import."parentTconst")
;
```

```
PGPASSWORD="movies" psql -h localhost -p 5432 -U movies -d movies -c "\copy show_crew_import from '/home/pg/title.principals.tsv' with DELIMITER E'\t' CSV HEADER QUOTE E'\b';"

```