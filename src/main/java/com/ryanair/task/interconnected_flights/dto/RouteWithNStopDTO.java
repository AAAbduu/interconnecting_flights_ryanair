package com.ryanair.task.interconnected_flights.dto;

import java.util.List;

public record RouteWithNStopDTO(int stops, List<LegDTO> legs) {
}
