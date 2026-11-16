package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByCampusIdAndActiveTrueOrderByNameAsc(String campusId);

    Optional<Building> findByShortCode(String shortCode);

    long countByCampusId(String campusId);
}
