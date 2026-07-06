package com.shelflife.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findByPinnedFalseAndExpiresAtAfterOrderByExpiresAtAsc(Instant now);

    List<Link> findByPinnedFalseAndExpiresAtLessThanEqualAndExpiresAtAfterOrderByExpiresAtAsc(
            Instant activeThreshold, Instant graveyardThreshold);

    // pinned = false is correctness-critical here (research.md §1): without it, a pinned link's
    // stale expiresAt could let this sweep permanently delete it despite being pinned.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Link l WHERE l.pinned = false AND l.expiresAt <= :threshold")
    void deleteByPinnedFalseAndExpiresAtLessThanEqual(Instant threshold);

    List<Link> findByPinnedTrueOrderByPinnedAtDesc();
}
