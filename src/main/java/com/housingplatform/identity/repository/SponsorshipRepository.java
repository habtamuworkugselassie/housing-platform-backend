package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.Sponsorship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SponsorshipRepository extends JpaRepository<Sponsorship, UUID> {
    
    List<Sponsorship> findByStatus(Sponsorship.SponsorshipStatus status);
    
    List<Sponsorship> findByType(Sponsorship.SponsorshipType type);
    
    Optional<Sponsorship> findByName(String name);
}
