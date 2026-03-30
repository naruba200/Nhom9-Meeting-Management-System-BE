package com.example.shopapp.repository;

import com.example.shopapp.entity.ActivityLog;
import com.example.shopapp.entity.ActivityActionType;
import com.example.shopapp.entity.ActivityEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findAll(Pageable pageable);

    Page<ActivityLog> findByActionType(ActivityActionType actionType, Pageable pageable);

    Page<ActivityLog> findByEntityType(ActivityEntityType entityType, Pageable pageable);

    Page<ActivityLog> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT al FROM ActivityLog al WHERE " +
           "(:actionType IS NULL OR al.actionType = :actionType) AND " +
           "(:entityType IS NULL OR al.entityType = :entityType) AND " +
           "(:userEmail IS NULL OR al.user.email LIKE LOWER(CONCAT('%', :userEmail, '%'))) AND " +
           "(:startDate IS NULL OR al.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR al.timestamp <= :endDate)")
    Page<ActivityLog> searchActivityLogs(
            @Param("actionType") ActivityActionType actionType,
            @Param("entityType") ActivityEntityType entityType,
            @Param("userEmail") String userEmail,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT al FROM ActivityLog al WHERE al.user.id = :userId AND " +
           "(:startDate IS NULL OR al.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR al.timestamp <= :endDate)")
    Page<ActivityLog> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT al FROM ActivityLog al WHERE " +
           "al.timestamp >= :startDate AND al.timestamp <= :endDate")
    List<ActivityLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT al FROM ActivityLog al WHERE al.actionType IN ('LOGIN', 'LOGOUT') " +
           "AND al.user.id = :userId ORDER BY al.timestamp DESC")
    List<ActivityLog> findLoginLogoutHistory(@Param("userId") Long userId);

    @Query("SELECT al FROM ActivityLog al WHERE " +
           "(:actionType IS NULL OR al.actionType = :actionType) AND " +
           "(:entityType IS NULL OR al.entityType = :entityType) AND " +
           "(:userEmail IS NULL OR al.user.email LIKE LOWER(CONCAT('%', :userEmail, '%'))) AND " +
           "(:startDate IS NULL OR al.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR al.timestamp <= :endDate) " +
           "ORDER BY al.timestamp DESC")
    List<ActivityLog> searchActivityLogsForExport(
            @Param("actionType") ActivityActionType actionType,
            @Param("entityType") ActivityEntityType entityType,
            @Param("userEmail") String userEmail,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
