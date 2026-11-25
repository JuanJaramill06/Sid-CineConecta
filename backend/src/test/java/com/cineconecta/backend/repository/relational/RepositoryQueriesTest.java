package com.cineconecta.backend.repository.relational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.OracleDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.cineconecta.backend.model.relational.Account;
import com.cineconecta.backend.model.relational.Payment;
import com.cineconecta.backend.model.relational.Plan;
import com.cineconecta.backend.model.relational.Profile;
import com.cineconecta.backend.model.relational.Subscription;

import jakarta.persistence.EntityManager;

/**
 * Compiles every JPQL @Query of the relational repositories against the real entity mapping
 * and the Oracle dialect, without a database. Without this, a typo only shows up when the
 * app starts against ESTUD.
 */
class RepositoryQueriesTest {

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void bootHibernateWithoutConnecting() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", OracleDialect.class.getName())
                .applySetting("jakarta.persistence.database-product-name", "Oracle")
                .applySetting("jakarta.persistence.database-major-version", "21")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClasses(Account.class, Plan.class, Profile.class, Subscription.class, Payment.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) sessionFactory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @Test
    void everyJpqlQueryCompiles() {
        List<Class<?>> repositories = List.of(AccountRepository.class, ProfileRepository.class,
                SubscriptionRepository.class, PaymentRepository.class, PlanRepository.class);
        List<String> queries = new ArrayList<>();
        for (Class<?> repository : repositories) {
            for (Method method : repository.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null && !query.nativeQuery()) {
                    queries.add(repository.getSimpleName() + "." + method.getName() + ": " + query.value());
                }
            }
        }
        assertThat(queries).hasSizeGreaterThanOrEqualTo(6);

        try (EntityManager em = sessionFactory.createEntityManager()) {
            for (String entry : queries) {
                String jpql = entry.substring(entry.indexOf(": ") + 2);
                assertThatCode(() -> em.createQuery(jpql)).as(entry).doesNotThrowAnyException();
            }
        }
    }
}
