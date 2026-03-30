package com.example.shopapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("userEmail")
    private String userEmail;

    @JsonProperty("userFullName")
    private String userFullName;

    @JsonProperty("action")
    private String action;

    @JsonProperty("actionType")
    private String actionType;

    @JsonProperty("entityType")
    private String entityType;

    @JsonProperty("entityId")
    private Long entityId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("ipAddress")
    private String ipAddress;

    @JsonProperty("userAgent")
    private String userAgent;

    @JsonProperty("statusCode")
    private Integer statusCode;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("details")
    private String details;
}
