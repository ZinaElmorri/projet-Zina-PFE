package com.zina.sepa.repository;

import com.zina.sepa.domain.entity.XsdSchema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface XsdSchemaRepository extends JpaRepository<XsdSchema, Long> {
    List<XsdSchema> findByNomOrderByVersionDesc(String nom);
    Optional<XsdSchema> findFirstByNomOrderByVersionDesc(String nom);
}
