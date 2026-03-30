package com.example.shopapp.repository;

import com.example.shopapp.entity.MeetingAgendaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingAgendaItemRepository extends JpaRepository<MeetingAgendaItem, Long> {

    List<MeetingAgendaItem> findAllByMeetingIdOrderByItemOrderAsc(Long meetingId);

    void deleteAllByMeetingId(Long meetingId);
}
