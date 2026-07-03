package com.shelflife.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findByExpiresAtAfterOrderByExpiresAtAsc(Instant now);

    List<Link> findByExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(
            Instant activeThreshold, Instant graveyardThreshold);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Link l WHERE l.expiresAt <= :threshold")
    void deleteByExpiresAtLessThanEqual(Instant threshold);
}
