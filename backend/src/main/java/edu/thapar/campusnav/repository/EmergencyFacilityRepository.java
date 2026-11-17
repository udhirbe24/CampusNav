package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.EmergencyFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyFacilityRepository extends JpaRepository<EmergencyFacility, Long> {
    List<EmergencyFacility> findByActiveTrue();

    List<EmergencyFacility> findByActiveTrueAndFacilityType(String facilityType);
}
