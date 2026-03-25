package com.example.shopapp.repository;

import com.example.shopapp.entity.MinutesTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinutesTaskRepository extends JpaRepository<MinutesTask, Long> {
    List<MinutesTask> findByMinutesId(Long minutesId);
}
