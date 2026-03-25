# Copilot Instructions - Meeting Management System (Backend)

## Purpose
This repository is a backend for meeting management workflows.
All generated code, suggestions, and refactors must stay within management features only.

## Product Scope (Management Only)
Only implement and suggest features related to:
- CRUD for meetings
- Meeting room management
- Scheduling and calendar-related meeting time planning
- Participant list management
- Attachment/document management for meetings
- Reporting and statistics for meeting data

If a request is outside this scope, explicitly redirect back to management features.

## Strict Non-Goals (No Streaming)
Do NOT propose, scaffold, or integrate any online meeting streaming/real-time media stack, including:
- WebRTC
- Socket.io
- Video/audio call signaling
- Live conferencing transport
- Any feature for joining meetings via real-time video/audio stream

If user asks for online meeting/call/video stream features, respond with a management-only alternative (for example: attendance tracking, agenda management, minutes, or post-meeting reports).

## Meeting Status Logic (Critical)
Prioritize meeting lifecycle state transition rules.
When implementing meeting logic, always define allowed transitions and validate them in service layer.

Example states:
- `SCHEDULED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

Example valid transitions:
- `SCHEDULED -> IN_PROGRESS`
- `SCHEDULED -> CANCELLED`
- `IN_PROGRESS -> COMPLETED`
- `IN_PROGRESS -> CANCELLED` (optional, based on business rule)

Example invalid transitions (must be rejected):
- `COMPLETED -> SCHEDULED`
- `CANCELLED -> IN_PROGRESS`
- `COMPLETED -> CANCELLED`

Implementation guidance:
- Enforce transition validation in service layer (not only controller).
- Return clear business error messages for invalid transitions.
- Keep status updates auditable (timestamp, actor, reason when cancelled).

## Backend Design Guidance
- Use clear layered structure: controller -> service -> repository.
- Keep DTOs separate from entities.
- Validate input with explicit constraints.
- Keep transactional boundaries in service layer where needed.
- Add/update tests for status transitions and core CRUD behavior.

## API Behavior Expectations
- CRUD endpoints must follow consistent naming and response format.
- Filter/list endpoints should support practical query parameters (date range, room, status, organizer).
- Attachment handling should focus on metadata and storage references, not media streaming.
- Reports should be derived from persisted meeting metadata and status history.

## What Copilot Should Optimize For
- Correct business logic over UI/real-time features.
- Data integrity and permission-safe update flows.
- Maintainable Java Spring Boot code consistent with project conventions.
- Explicit handling of edge cases in status transition flows.

## Final Rule
If there is any ambiguity, choose meeting management behavior and avoid online meeting/streaming implementation paths and ask for clarification if needed.
