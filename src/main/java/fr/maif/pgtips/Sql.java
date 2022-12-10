package fr.maif.pgtips;

import io.vavr.control.Option;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.Optional;

public interface Sql {


    record BindWrapper(DatabaseClient.GenericExecuteSpec spec) {
        static BindWrapper binder(DatabaseClient.GenericExecuteSpec spec) {
            return new BindWrapper(spec);
        }
        public <T> BindWrapper bind(String key, T obj, Class<T> clazz) {
            if (obj == null) {
                return new BindWrapper(spec.bindNull(key, clazz));
            } else {
                return new BindWrapper(spec.bind(key, obj));
            }
        }
        public DatabaseClient.GenericExecuteSpec get() {
            return spec;
        }
    }

    record Condition(String condition, String binding, String value) {
        public static Condition cond(String condition, String value) {
            return new Condition(condition, null, value);
        }
    }

    record Conditions(io.vavr.collection.List<Condition> conditions) {

        @SafeVarargs
        public static Conditions conditions(Optional<Condition>... conditions) {
            return new Conditions(io.vavr.collection.List.of(conditions)
                    .flatMap(Option::ofOptional)
                    .zipWithIndex()
                    .map(t -> {
                        int index = t._2 + 1;
                        String binding = "$" + index;
                        return new Condition(t._1.condition.formatted(binding), binding, t._1.value);
                    }));
        }

        public DatabaseClient.GenericExecuteSpec bindTo(DatabaseClient.GenericExecuteSpec spec) {

            return io.vavr.collection.List.ofAll(conditions).foldLeft(spec, (s, c) -> s.bind(c.binding(), c.value));
        }

        public String sqlClauseWithWhere() {
            return sqlClause(" where ");
        }

        public String sqlClause(String prefix) {
            if (conditions.isEmpty()) {
                return "";
            }
            return conditions.map(c -> c.condition).mkString(prefix, " and ", "");
        }

        public int index() {
            return conditions.length();
        }

        public String index(int i) {
            return "$" + (index() + i);
        }
    }

}
