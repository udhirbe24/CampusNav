package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.NavigationNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NavigationNodeRepository extends JpaRepository<NavigationNode, Long> {
    Optional<NavigationNode> findByNodeKey(String nodeKey);

    List<NavigationNode> findByCampusId(String campusId);

    List<NavigationNode> findByBuildingId(Long buildingId);

    long countByCampusId(String campusId);
}
