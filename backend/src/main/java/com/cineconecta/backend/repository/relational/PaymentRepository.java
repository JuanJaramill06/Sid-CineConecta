package com.cineconecta.backend.repository.relational;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cineconecta.backend.model.relational.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    @Query("select p from Payment p join fetch p.subscription s join fetch s.plan "
         + "where s.account.accountId = :accountId order by p.paidAt desc")
    List<Payment> findHistory(@Param("accountId") Integer accountId);
}
