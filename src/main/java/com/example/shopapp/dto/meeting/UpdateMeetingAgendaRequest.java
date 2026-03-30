package com.example.shopapp.dto.meeting;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateMeetingAgendaRequest {

    @Valid
    private List<AgendaItemRequest> agendaItems;
}
