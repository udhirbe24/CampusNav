package edu.thapar.campusnav.repository;

import edu.thapar.campusnav.model.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {
    List<UserReport> findAllByOrderBySubmittedAtDesc();

    List<UserReport> findByStatusOrderBySubmittedAtDesc(String status);

    long countByStatus(String status);
}
