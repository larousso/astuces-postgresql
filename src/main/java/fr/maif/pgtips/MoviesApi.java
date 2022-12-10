package fr.maif.pgtips;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static fr.maif.pgtips.Sql.BindWrapper.binder;
import static fr.maif.pgtips.Sql.Condition.cond;
import static fr.maif.pgtips.Sql.Conditions.conditions;

@RestController
public class MoviesApi {

    private ObjectMapper mapper;
    private final DatabaseClient client;

    public MoviesApi(ObjectMapper mapper, DatabaseClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    record Movie(String tconst,
                 String titleType,
                 String primaryTitle,
                 String originalTitle,
                 Boolean isAdult,
                 Integer startYear,
                 Integer endYear,
                 Integer runtimeMinutes,
                 List<String> genres,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 List<Episode> episodes) {
    }

    record Episode(
            String tconst,
            String parentTconst,
            String seasonNumber,
            String episodeNumber,
            String titleType,
            String primaryTitle,
            String originalTitle,
            String isAdult,
            String startYear,
            String endYear,
            String runtimeMinutes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<String> genres
    ) { }

    @GetMapping("/api/shows")
    Flux<Movie> listMovies(@RequestParam(name = "page", defaultValue = "1") Integer page,
                           @RequestParam(name = "size", defaultValue = "20") Integer size,
                           @RequestParam(name = "type", required = false) TitleType showType,
                           @RequestParam(name = "title", required = false) String title) {
        Integer offset = (page - 1) * size;

        var conditions = conditions(
                Optional.ofNullable(showType).map(t -> cond("s.\"titleType\" = %s", t.value)),
                Optional.ofNullable(title).map(t -> cond("ss.\"search\" @@ plainto_tsquery('english', %s )", t))
        );

        return conditions.bindTo(client.sql("""
                                    select row_to_json(s)::jsonb || json_build_object('episodes', array(
                                        select row_to_json(e)::jsonb || row_to_json(ep)::jsonb
                                        from episode ep
                                        join show e on ep."tconst" = e.tconst
                                        where ep."parentTconst" = s.tconst
                                    ))::jsonb
                                    from show s
                                    join show_search ss on s.tconst = ss.tconst
                                     %s
                                    offset %s
                                    limit %s
                                """.formatted(
                                conditions.sqlClauseWithWhere(),
                                conditions.index(1),
                                conditions.index(2)
                        )
                ))
                .bind(conditions.index(1), offset)
                .bind(conditions.index(2), size)
                .map(r -> r.get(0, String.class))
                .all()
                .flatMap(json -> {
                    try {
                        return Mono.just(mapper.readValue(json, Movie.class));
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
    }

    record UpsertMovie(String titleType,
                       String primaryTitle,
                       String originalTitle,
                       Boolean isAdult,
                       Integer startYear,
                       Integer endYear,
                       Integer runtimeMinutes,
                       List<String> genres) {

    }

    @PutMapping("/api/shows/{id}")
    public Mono<Movie> putMovie(@PathVariable("id") String id, @RequestBody UpsertMovie movie) {
        return binder(client
                .sql("""
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
                              ($1, $2, $3, $4, $5, $6, $7 , $8, $9::text[], now(), now())
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
                        returning row_to_json(show)
                        """)
        )
                .bind("$1", id, String.class)
                .bind("$2", movie.titleType(), String.class)
                .bind("$3", movie.primaryTitle(), String.class)
                .bind("$4", movie.originalTitle(), String.class)
                .bind("$5", movie.isAdult(), Boolean.class)
                .bind("$6", movie.startYear(), Integer.class)
                .bind("$7", movie.endYear(), Integer.class)
                .bind("$8", movie.runtimeMinutes(), Integer.class)
                .bind("$9", movie.genres().toArray(String[]::new), String[].class)
                .get()
                .map(r -> r.get(0, String.class))
                .one()
                .flatMap(json -> {
                    try {
                        return Mono.just(mapper.readValue(json, Movie.class));
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
    }

    enum TitleType {
        MOVIE("movie"),
        SHORT("short"),
        TVEPISODE("tvEpisode"),
        TVMINISERIES("tvMiniSeries"),
        TVMOVIE("tvMovie"),
        TVPILOT("tvPilot"),
        TVSERIES("tvSeries"),
        TVSHORT("tvShort"),
        TVSPECIAL("tvSpecial"),
        VIDEO("video"),
        VIDEOGAME("videoGame");

        public final String value;

        TitleType(String value) {
            this.value = value;
        }

        @JsonCreator
        TitleType fromString(String text) {
            return Stream.of(values()).filter(t -> t.value.equals(text)).findAny().orElseGet(() -> null);
        }
    }


}
