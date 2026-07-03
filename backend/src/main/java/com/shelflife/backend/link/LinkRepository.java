package com.shelflife.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findByExpiresAtAfterOrderByExpiresAtAsc(Instant now);
}
