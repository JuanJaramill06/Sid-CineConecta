package com.cineconecta.backend.repository.relational;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cineconecta.backend.model.relational.Account;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    // LOWER(email) = LOWER(:email) so Oracle uses ux_accounts_email_ci (RF2, RF3).
    // A derived "IgnoreCase" query would generate UPPER(...) and skip the index.
    @Query("select a from Account a where lower(a.email) = lower(:email)")
    Optional<Account> findByEmailCi(@Param("email") String email);

    @Query("select count(a) > 0 from Account a where lower(a.email) = lower(:email)")
    boolean existsByEmailCi(@Param("email") String email);

    /**
     * SELECT ... FOR UPDATE on the account row. Call it first, inside the same
     * transaction, before creating or reactivating a profile (RF9, RF10): it serializes
     * concurrent requests of the same account, as sp_contract_subscription does.
     * See database/oracle/05b_concurrency_demo.sql, case 2.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Integer accountId);
}
