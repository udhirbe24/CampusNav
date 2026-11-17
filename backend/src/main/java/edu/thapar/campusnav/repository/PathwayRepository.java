package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.Pathway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathwayRepository extends JpaRepository<Pathway, Long> {
    List<Pathway> findByCampusId(String campusId);

    long countByCampusId(String campusId);

    long countByCampusIdAndBlockedTrue(String campusId);
}
