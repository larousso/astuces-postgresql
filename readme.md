# Quelques astuces postgresql 

Quelques petit truc utils en postgresql dans le dév de tous les jours  : 


 1. faire de la recherche full texte + colonne calculée
 2. faire des upsert
 3. requêter les données en JSON
 4. recupérer les données générées lors d'un insert ou d'un update
 5. gérer un singleton sur une infra multi serveur


## Data set 

Data set IMDB avec la liste des films, series, acteurs

Imdb : 
 * https://www.imdb.com/interfaces/
 * https://datasets.imdbws.com/

## Lancer la base 

```
docker-compose up
```

## Lancer l'app 

```
./gradlew bootRun
```

Le lancement de l'app va démarrer le serveur et initialiser les tables de la BDD à partir de `src/main/resources/schema.sql`. 


## Set up des données  


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

```sql
insert into show_crew("tconst", "ordering", "nconst", "category", "job", "characters")
select "tconst", "ordering", "nconst", "category", "job", string_to_array("characters", ',')
from show_crew_import
where
    exists(select from show where show."tconst" = show_crew_import."tconst") and
    exists(select from people where people."nconst" = show_crew_import."nconst")
```

## 1 Les colonnes générées et la recherche full text


```sql
alter table show
    add column "nameIndex" tsvector
        GENERATED ALWAYS AS (to_tsvector('english', "primaryTitle") || to_tsvector('english', "originalTitle") ) stored;

create index show_name_index_idx on show using gin("nameIndex");
```


## 2 Les upsert

On créé une table de search dédiée à la recherche full text. 

 * tconst est la clé primaire mais aussi une foreign key vers l'id du show
   * on delete cascade : si un show est supprimé alors show_search le sera aussi 

```sql
create table show_search(
    "tconst" varchar(100) primary key references show("tconst") on delete cascade,
    search tsvector
);
create index show_search_search_idx on show_search using gin("search");
```

On peut initialiser cette table en utilisant "insert select" : 

```sql
insert into show_search
select
    "tconst",
    to_tsvector('english', "primaryTitle") || to_tsvector('english', "originalTitle") as search
from show;
```

`9,379,384 rows affected in 4 m 54 s 162 ms` 

Maintenant on veut mettre à jour un index pour un id, mais comment savoir si la ligne existe déjà ? 

```sql
insert into show_search
select
    "tconst",
    to_tsvector('english', "primaryTitle") || to_tsvector('english', "originalTitle") as search
from show
where tconst = 'tt0089530'
on conflict ("tconst")
    do update set search = excluded.search;
```

Ou depuis postgresql 15 `MERGE` ? 

## 3 select en json 

```sql
select row_to_json(s)::jsonb || json_build_object('episodes', array(
    select row_to_json(e)::jsonb || row_to_json(ep)::jsonb
    from episode ep
    join show e on ep."tconst" = e.tconst
    where ep."parentTconst" = s.tconst
))::jsonb
from show s
join show_search ss on s.tconst = ss.tconst
where ss."search" @@ plainto_tsquery('game throne')
  and s."titleType" = 'tvSeries'
limit 50;
```

```bash
curl -XGET 'http://localhost:8080/api/shows?size=5&type=TVSERIES&title=chainsaw%20man' | jless
```

## 4 returning 

On ajoute des colonnes qui seront gérées par la base: 
```sql 
alter table show add column if not exists "createdAt" timestamp;
alter table show add column if not exists "updatedAt" timestamp;
```

```sql
insert into show(
   "tconst",
   "titleType",
   "primaryTitle",
   "originalTitle",
   "isAdult",
   "startYear",
   "endYear",
   "runtimeMinutes",
   "genres",
   "createdAt",
   "updatedAt"
) values
   ('tt0008529','movie','Sacrifice','Sacrifice',false,1917, null ,50, '{"Drama","War"}'::text[], now(), now())
   on conflict ("tconst")
do update set
   "titleType" = excluded."titleType",
   "primaryTitle" = excluded."primaryTitle",
   "originalTitle" = excluded."originalTitle",
   "isAdult" = excluded."isAdult",
   "startYear" = excluded."startYear",
   "endYear" = excluded."endYear",
   "runtimeMinutes" = excluded."runtimeMinutes",
   "genres" = excluded."genres",
   "updatedAt" = now()
returning row_to_json(show);
```

```bash
curl -XPUT 'http://localhost:8080/api/shows/tt13616990' -H 'content-type: application/json' -d '{
  "endYear": null,
  "isAdult": false,
  "startYear": 2022,
  "titleType": "tvSeries",
  "primaryTitle": "Chainsaw Man",
  "originalTitle": "Chainsaw Man",
  "runtimeMinutes": null,
  "genres": [
    "Action",
    "Adventure",
    "Animation"
  ]
}' 
```
