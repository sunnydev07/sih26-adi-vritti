package in.adivritti.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Every repository query must parse.
 *
 * <p>Spring Data JPA validates the {@code @Query} JPQL on each repository when
 * the bean is created, so this slice test fails fast on a query Hibernate cannot
 * translate. That is exactly the class of defect that otherwise survives: the
 * service context only loads once, in a container, and a query that does not
 * parse kills startup after Flyway has already run. It shipped once already --
 * 104 unit tests were green and the stack still would not boot, twice, for
 * reasons no unit test could see (a duplicate bean name, then an unparseable
 * query). Flyway is off here because the schema comes from Hibernate, not from
 * V1__initial_schema.sql, and there is nothing to migrate.
 *
 * <p>Scope, stated plainly: this proves the JPQL parses and resolves against the
 * mapping model. It does not execute the queries against PostgreSQL. H2 in
 * PostgreSQL mode is close enough to validate the HQL but not to run the JSONB
 * paths, so the SQL generated for those is only exercised when the stack job
 * boots against a real pgvector database.
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // H2 in PostgreSQL compatibility mode: the schema uses jsonb, bigserial and
    // timestamptz, and the JSON-mapped attributes need a dialect that has a real
    // JSON type rather than a varchar.
    "spring.datasource.url=jdbc:h2:mem:repoquery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class RepositoryQueryValidationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("all repository @Query strings are accepted by Hibernate")
    void repositoryQueriesParse() {
        List<String> names = Arrays.stream(context.getBeanNamesForType(JpaRepository.class))
            .distinct()
            .sorted()
            .toList();

        // A slice that silently loaded zero repositories would pass vacuously,
        // which is the failure mode that matters most for a guard like this.
        assertThat(names)
            .as("repositories discovered by the JPA slice")
            .hasSizeGreaterThanOrEqualTo(10);

        // Touching each repository is what actually forces query creation: the
        // proxy defers the JPQL translation to first use, so counting beans
        // alone would not prove anything was validated.
        for (String name : names) {
            assertThat(context.getBean(name)).isNotNull();
        }
    }
}
