package com.ryanair.task.interconnected_flights.dto;

import java.util.List;

public record DayDTO(int day, List<FlightDTO> flights) {
}
