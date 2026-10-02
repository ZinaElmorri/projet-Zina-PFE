package com.zina.sepa.masse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FichierSepaGenereRepository extends JpaRepository<FichierSepaGenere, Long> {
    List<FichierSepaGenere> findAllByOrderByGenereLeDesc();
}
