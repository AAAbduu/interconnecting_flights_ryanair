package com.ryanair.task.interconnected_flights.service;


import com.ryanair.task.interconnected_flights.client.RoutesClient;
import com.ryanair.task.interconnected_flights.client.SchedulesClient;
import com.ryanair.task.interconnected_flights.dto.Leg;
import com.ryanair.task.interconnected_flights.dto.RouteWithNStopDTO;
import com.ryanair.task.interconnected_flights.dto.RouteDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;


@Service
public class FlightService {

    private final RoutesClient routesClient;
    private final SchedulesClient schedulesClient;

    public FlightService(RoutesClient routesClient, SchedulesClient schedulesClient){
        this.routesClient = routesClient;
        this.schedulesClient = schedulesClient;
    }

    public Flux<RouteWithNStopDTO> findFlights(String departure, String arrival, LocalDateTime departureDate, LocalDateTime arrivalDate){
        int departureYear = departureDate.getYear();
        int departureMonth = departureDate.getMonthValue();
        int departureDay = departureDate.getDayOfMonth();
        LocalTime departureHour = departureDate.toLocalTime();
        LocalTime arrivalHour = arrivalDate.toLocalTime();
        System.out.println("departureHour = " + departureHour);
        System.out.println("arrivalHour = " + arrivalHour);


        return this.getFilteredRoutes(departure, arrival);
    }

    private Flux<RouteWithNStopDTO> getFilteredRoutes(String departure, String arrival) {

        Flux<RouteDTO> ryanairRoutes = this.routesClient.getRoutes()
                .filter(route -> route.connectingAirport() == null && route.operator().equals("RYANAIR"))
                .subscribeOn(Schedulers.parallel());

        Flux<RouteDTO> directRoutesFromDeparture = ryanairRoutes
                .filter(route -> route.airportFrom().equals(departure))
                .subscribeOn(Schedulers.parallel());

        Flux<RouteWithNStopDTO> directRoutes = directRoutesFromDeparture
                .filter(route -> route.airportTo().equals(arrival))
                .map(route -> new RouteWithNStopDTO(
                        0,
                        List.of(new Leg(route.airportFrom(), route.airportTo()))
                ))
                .subscribeOn(Schedulers.parallel());

        Flux<String> intermediateAirports = directRoutesFromDeparture
                .map(RouteDTO::airportTo)
                .distinct()
                .subscribeOn(Schedulers.parallel());

        Flux<RouteDTO> routesToFinalDestination = intermediateAirports
                .flatMap(intermediate -> ryanairRoutes
                        .filter(route -> route.airportFrom().equals(intermediate) && route.airportTo().equals(arrival))
                        .subscribeOn(Schedulers.parallel()))
                .subscribeOn(Schedulers.parallel());

        Flux<RouteWithNStopDTO> routesWithOneStop = routesToFinalDestination.flatMap(finalRoute -> directRoutesFromDeparture
                .filter(firstRoute -> firstRoute.airportTo().equals(finalRoute.airportFrom()))
                .map(firstRoute -> new RouteWithNStopDTO(
                        1,
                        List.of(
                                new Leg(firstRoute.airportFrom(), firstRoute.airportTo()),
                                new Leg(finalRoute.airportFrom(), finalRoute.airportTo())
                        )
                ))
                .subscribeOn(Schedulers.parallel()));

        return directRoutes.mergeWith(routesWithOneStop);
    }
}
