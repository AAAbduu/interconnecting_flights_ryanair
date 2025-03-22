package com.ryanair.task.interconnected_flights.controller;

import com.ryanair.task.interconnected_flights.dto.RouteDTO;
import com.ryanair.task.interconnected_flights.dto.RouteWithNStopDTO;
import com.ryanair.task.interconnected_flights.service.FlightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@RestController
public class FlightController {
    private final FlightService flightService;

    public FlightController(FlightService flightService){
        this.flightService = flightService;
    }

    @GetMapping("/interconnections")
    public Flux<RouteWithNStopDTO> findFlights(@RequestParam String departure,
                                               @RequestParam String arrival,
                                               @RequestParam LocalDateTime departureDateTime,
                                               @RequestParam LocalDateTime arrivalDateTime){

        return this.flightService.findFlights(departure, arrival, departureDateTime, arrivalDateTime);
    }

    @GetMapping("/test")
    public Flux<RouteDTO> test(){

        return null;
    }
}
