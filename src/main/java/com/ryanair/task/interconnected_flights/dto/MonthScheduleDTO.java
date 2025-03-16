package com.ryanair.task.interconnected_flights.dto;

import java.util.List;

public record MonthScheduleDTO(int month, List<DayDTO> days) {
}