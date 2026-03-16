package com.example.shopapp.service;

import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.entity.User;
import com.example.shopapp.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final String CALENDAR_API_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events?conferenceDataVersion=1";

    private final ObjectMapper objectMapper;
    private final GoogleOAuthService googleOAuthService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GoogleCalendarSyncResult createEventWithMeetLink(CreateMeetingRequest request, User organizer) {
        String accessToken = googleOAuthService.getValidAccessToken(organizer);

        ZoneId zoneId = resolveZoneId(request.getTimezone());
        String start = request.getStartTime().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String end = request.getEndTime().atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        try {
            String payload = buildEventPayload(request, organizer.getEmail(), start, end, zoneId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CALENDAR_API_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BadRequestException(parseGoogleError(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String eventId = safeText(root, "id");
            String meetLink = resolveMeetLink(root);

            if (eventId.isBlank()) {
                throw new BadRequestException("Google Calendar không trả về eventId");
            }

            return new GoogleCalendarSyncResult(eventId, meetLink);
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được phản hồi từ Google Calendar");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Yêu cầu đồng bộ Google Calendar bị gián đoạn");
        }
    }

    private String buildEventPayload(CreateMeetingRequest request, String organizerEmail, String start, String end, ZoneId zoneId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("summary", request.getTitle());
        payload.put("description", request.getAgenda() == null ? "" : request.getAgenda());

        ObjectNode startNode = payload.putObject("start");
        startNode.put("dateTime", start);
        startNode.put("timeZone", zoneId.getId());

        ObjectNode endNode = payload.putObject("end");
        endNode.put("dateTime", end);
        endNode.put("timeZone", zoneId.getId());

        ArrayNode attendeesNode = payload.putArray("attendees");
        List<String> attendeeEmails = request.getAttendeeEmails();
        if (attendeeEmails != null) {
            for (String attendeeEmail : attendeeEmails) {
                ObjectNode attendee = attendeesNode.addObject();
                attendee.put("email", attendeeEmail);
            }
        }
        ObjectNode organizer = attendeesNode.addObject();
        organizer.put("email", organizerEmail);

        ObjectNode conferenceData = payload.putObject("conferenceData");
        ObjectNode createRequest = conferenceData.putObject("createRequest");
        createRequest.put("requestId", UUID.randomUUID().toString());
        ObjectNode conferenceSolutionKey = createRequest.putObject("conferenceSolutionKey");
        conferenceSolutionKey.put("type", "hangoutsMeet");

        return payload.toString();
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }

        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            throw new BadRequestException("Múi giờ không hợp lệ: " + timezone);
        }
    }

    private String parseGoogleError(int status, String body) {
        if (status == 401 || status == 403) {
            return "googleAccessToken không hợp lệ hoặc đã hết hạn";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText();
            if (!message.isBlank()) {
                return "Không thể đồng bộ Google Calendar: " + message;
            }
        } catch (Exception ignored) {
        }

        return "Không thể đồng bộ Google Calendar (HTTP " + status + ")";
    }

    private String resolveMeetLink(JsonNode root) {
        String hangoutLink = safeText(root, "hangoutLink");
        if (!hangoutLink.isBlank()) {
            return hangoutLink;
        }

        JsonNode entryPoints = root.path("conferenceData").path("entryPoints");
        if (entryPoints.isArray()) {
            for (JsonNode entryPoint : entryPoints) {
                if ("video".equals(entryPoint.path("entryPointType").asText())) {
                    String uri = entryPoint.path("uri").asText();
                    if (!uri.isBlank()) {
                        return uri;
                    }
                }
            }
        }

        return "";
    }

    private String safeText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    public record GoogleCalendarSyncResult(String eventId, String meetLink) {
    }
}
