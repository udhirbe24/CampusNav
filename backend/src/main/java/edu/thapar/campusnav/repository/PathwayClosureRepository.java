package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.PathwayClosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathwayClosureRepository extends JpaRepository<PathwayClosure, Long> {
    List<PathwayClosure> findByActiveTrue();

    List<PathwayClosure> findByPathwayIdAndActiveTrue(Long pathwayId);

    long countByActiveTrue();
}
