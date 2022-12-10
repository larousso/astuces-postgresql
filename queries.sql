

-- 1 colonnes calculées
select to_tsvector('french', 'salut je m appelle alex');

select to_tsvector('french', 'salut je m appelle alex') @@ plainto_tsquery('french', 'appelle');


alter table show
    add column "nameIndex" tsvector
        GENERATED ALWAYS AS (to_tsvector('english', "primaryTitle") || to_tsvector('english', "originalTitle") ) stored;

create index show_name_index_idx on show using gin("nameIndex");

select *
from show s
where
        s."nameIndex" @@ plainto_tsquery('english', 'chainsaw man')
  and s."titleType" = 'tvSeries';

select * from show where tconst = 'tt0000854';

update show
set "primaryTitle" = 'Edgar Allan Poe'
where tconst = 'tt0000854';


-- 2 upsert

create table show_search(
                            "tconst" varchar(100) primary key references show("tconst") on delete cascade,
                            search tsvector
);

create index show_search_search_idx on show_search using gin("search");

insert into show_search
select
    "tconst",
    to_tsvector('english', "primaryTitle") || to_tsvector('english', "originalTitle") as search
from show
where show.tconst = 'tt0000854'
on conflict ("tconst")
    do update set search = excluded.search
;

select *
from show_search
where tconst = 'tt0000854';

-- 3 returning

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
-- returning show.*;
returning row_to_json(show);

-- 4 select as json

select s.*
from show s
         join show_search ss on s.tconst = ss.tconst
where
        ss."search" @@ plainto_tsquery('chainsaw man') and
        s."titleType" = 'tvSeries';




select s, array (select e
 from episode ep
          join show e on ep."tconst" = e.tconst
 where ep."parentTconst" = s.tconst )
from show s
join show_search ss on s.tconst = ss.tconst
where
ss."search" @@ plainto_tsquery('chainsaw man')
and s."titleType" = 'tvSeries';


select row_to_json(s)::jsonb || json_build_object('episodes', array(
        select row_to_json(e)::jsonb || row_to_json(ep)::jsonb
        from episode ep
                 join show e on ep."tconst" = e.tconst
        where ep."parentTconst" = s.tconst
    ))::jsonb
from show s
         join show_search ss on s.tconst = ss.tconst
where ss."search" @@ plainto_tsquery('chainsaw man')
  and s."titleType" = 'tvSeries'
limit 50;

select *
from show s
         join episode e on s.tconst = e."parentTconst"
         join show ep on e."tconst" = ep.tconst
where s.tconst = 'tt0944947';

select *
from show
limit 10;


-- 5 select for update
begin;
select * from show
where tconst = 'tt0008529'
    for update;
rollback ;

