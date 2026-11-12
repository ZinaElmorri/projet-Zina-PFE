package com.zina.sepa.repository;

import com.zina.sepa.domain.entity.Societe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocieteRepository extends JpaRepository<Societe, Long> {
    Optional<Societe> findByCode(String code);
    boolean existsByCode(String code);
    List<Societe> findAllByOrderByCodeAsc();
}
