package com.cineconecta.backend.repository.relational;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cineconecta.backend.dto.report.IncomeReportRow;
import com.cineconecta.backend.model.relational.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    @Query("select p from Payment p join fetch p.subscription s join fetch s.plan "
         + "where s.account.accountId = :accountId order by p.paidAt desc")
    List<Payment> findHistory(@Param("accountId") Integer accountId);

    /**
     * RF66: ingresos por plan en [from, to). Mismo join y agrupación que la consulta ya medida
     * en database/oracle/06_index_experiment.sql (usa el índice ix_payments_paidat).
     */
    @Query("select new com.cineconecta.backend.dto.report.IncomeReportRow("
         + "pl.planId, pl.name, count(p), sum(p.amount)) "
         + "from Payment p join p.subscription s join s.plan pl "
         + "where p.paidAt >= :from and p.paidAt < :to "
         + "group by pl.planId, pl.name order by pl.name")
    List<IncomeReportRow> incomeByPlan(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
