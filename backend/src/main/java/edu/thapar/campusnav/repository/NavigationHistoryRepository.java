package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.NavigationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NavigationHistoryRepository extends JpaRepository<NavigationHistory, Long> {
    List<NavigationHistory> findTop50ByUserIdOrderByTimestampDesc(Long userId);

    List<NavigationHistory> findTop10ByOrderByTimestampDesc();

    @Query("select h.toNodeKey, count(h) from NavigationHistory h group by h.toNodeKey order by count(h) desc")
    List<Object[]> countByDestination();

    @Query("select h.routeMode, count(h) from NavigationHistory h group by h.routeMode")
    List<Object[]> countByMode();
}
