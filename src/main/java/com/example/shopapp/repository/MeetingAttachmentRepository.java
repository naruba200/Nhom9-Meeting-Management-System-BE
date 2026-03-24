package com.example.shopapp.repository;

import com.example.shopapp.entity.MeetingAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingAttachmentRepository extends JpaRepository<MeetingAttachment, Long> {
    List<MeetingAttachment> findAllByMeetingIdOrderByIdAsc(Long meetingId);
    Optional<MeetingAttachment> findByIdAndMeetingId(Long id, Long meetingId);
    Optional<MeetingAttachment> findByMeetingIdAndCloudPublicId(Long meetingId, String cloudPublicId);
    void deleteAllByMeetingId(Long meetingId);

    @Query("SELECT a FROM MeetingAttachment a WHERE " +
           "(:fileName IS NULL OR LOWER(a.fileName) LIKE LOWER(CONCAT('%', :fileName, '%'))) AND " +
           "(:fileType IS NULL OR a.fileType = :fileType)")
    Page<MeetingAttachment> findAll(@Param("fileName") String fileName,
                                     @Param("fileType") String fileType,
                                     Pageable pageable);
}
