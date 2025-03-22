package com.ryanair.task.interconnected_flights.dto;

import java.time.LocalDateTime;

public record LegDTO(String departureAirport, String arrivalAirport, LocalDateTime departureDateTime, LocalDateTime arrivalDateTime) {

}
