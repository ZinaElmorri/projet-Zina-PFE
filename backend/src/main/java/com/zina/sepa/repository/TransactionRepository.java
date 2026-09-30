package com.zina.sepa.repository;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByStatut(TransactionStatus statut);
    long countByStatut(TransactionStatus statut);
    List<Transaction> findByEmployeId(String employeId);
}
