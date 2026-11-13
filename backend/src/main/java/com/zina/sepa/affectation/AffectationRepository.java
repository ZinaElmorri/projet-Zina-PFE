package com.zina.sepa.affectation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AffectationRepository extends JpaRepository<AffectationSociete, Long> {
    List<AffectationSociete> findByFinLeIsNull();
    Optional<AffectationSociete> findBySocieteCodeAndFinLeIsNull(String societeCode);
    List<AffectationSociete> findBySocieteCodeOrderByDebutLeDescIdDesc(String societeCode);
    List<AffectationSociete> findByExpertUsernameAndFinLeIsNull(String expertUsername);
}
