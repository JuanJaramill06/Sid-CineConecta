package com.cineconecta.backend.repository.relational;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cineconecta.backend.model.relational.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // Plan is fetched in the same query: the mapper reads its name.
    @Query("select s from Subscription s join fetch s.plan "
         + "where s.account.accountId = :accountId order by s.startsAt desc")
    List<Subscription> findHistory(@Param("accountId") Integer accountId);

    /**
     * Current subscription (RF21): startsAt <= now < endsAt, evaluated with the
     * DATABASE clock ("local datetime" = LOCALTIMESTAMP), the same rule as the view
     * v_active_subscriptions. At most one row: periods never overlap (RF22).
     */
    @Query("select s from Subscription s join fetch s.plan "
         + "where s.account.accountId = :accountId "
         + "and s.startsAt <= local datetime and s.endsAt > local datetime")
    Optional<Subscription> findActive(@Param("accountId") Integer accountId);
}
