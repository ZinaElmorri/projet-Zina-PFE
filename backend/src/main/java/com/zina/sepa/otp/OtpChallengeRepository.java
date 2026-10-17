package com.zina.sepa.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findByChallengeId(String challengeId);
    void deleteByUsername(String username);
}
