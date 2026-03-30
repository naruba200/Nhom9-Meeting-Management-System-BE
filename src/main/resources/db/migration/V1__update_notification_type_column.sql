-- V1: Update notification type column to support new task notification types
-- This migration ensures the 'type' column can store TASK_ASSIGNED and TASK_UPDATED

ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL;
