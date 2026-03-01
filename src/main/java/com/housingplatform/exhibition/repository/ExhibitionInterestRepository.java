package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExhibitionInterestRepository extends JpaRepository<ExhibitionInterest, UUID> {}
