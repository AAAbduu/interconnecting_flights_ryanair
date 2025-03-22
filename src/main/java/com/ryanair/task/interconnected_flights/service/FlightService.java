package com.ryanair.task.interconnected_flights.service;


import com.ryanair.task.interconnected_flights.client.RoutesClient;
import com.ryanair.task.interconnected_flights.client.SchedulesClient;
import com.ryanair.task.interconnected_flights.dto.LegDTO;
import com.ryanair.task.interconnected_flights.dto.RouteDTO;
import com.ryanair.task.interconnected_flights.dto.RouteWithNStopDTO;
import com.ryanair.task.interconnected_flights.exception.NoSchedulesFoundException;
import com.ryanair.task.interconnected_flights.exception.RouteNotFoundException;
import com.ryanair.task.interconnected_flights.model.FlightRoute;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
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

        Flux<FlightRoute> routes = this.getFilteredRoutes(departure, arrival);

        return this.validRoutes(routes, departureDate, arrivalDate);
    }

    private Flux<FlightRoute> getFilteredRoutes(String departure, String arrival) {
        Flux<RouteDTO> ryanairRoutes = this.routesClient.getRoutes()
                .filter(route -> route.connectingAirport() == null && route.operator().equals("RYANAIR"))
                .subscribeOn(Schedulers.parallel());

        Flux<RouteDTO> directRoutesFromDeparture = ryanairRoutes
                .filter(route -> route.airportFrom().equals(departure))
                .subscribeOn(Schedulers.parallel());

        Flux<FlightRoute> directRoutes = directRoutesFromDeparture
                .filter(route -> route.airportTo().equals(arrival))
                .map(route -> new FlightRoute(List.of(route.airportFrom(), route.airportTo())))
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

        Flux<FlightRoute> routesWithOneStop = routesToFinalDestination.flatMap(finalRoute -> directRoutesFromDeparture
                .filter(firstRoute -> firstRoute.airportTo().equals(finalRoute.airportFrom()))
                .map(firstRoute -> new FlightRoute(List.of(firstRoute.airportFrom(), finalRoute.airportFrom(), finalRoute.airportTo())))
                .subscribeOn(Schedulers.parallel()));

        Flux<FlightRoute> allRoutes = directRoutes.mergeWith(routesWithOneStop);

        return allRoutes.switchIfEmpty(Mono.error(new RouteNotFoundException("No available routes from " + departure + " to " + arrival)));
    }

    private Flux<RouteWithNStopDTO> validRoutes(Flux<FlightRoute> routes, LocalDateTime departureDate, LocalDateTime arrivalDate) {
        return routes.flatMap(route -> {
            List<String> airports = route.airports();

            if (airports.size() == 2) {
                return processDirectFlight(airports.get(0), airports.get(1), departureDate, arrivalDate)
                        .collectList()
                        .filter(legs -> !legs.isEmpty())
                        .switchIfEmpty(Mono.error(new NoSchedulesFoundException("No schedules found for direct flight from " + airports.get(0) + " to " + airports.get(1))))
                        .map(legs -> new RouteWithNStopDTO(0, legs))
                        .flux();
            }
            else if (airports.size() == 3) {
                return processConnectingFlight(airports.get(0), airports.get(1), airports.get(2), departureDate, arrivalDate)
                        .collectList()
                        .filter(legs -> legs.size() == 2)
                        .switchIfEmpty(Mono.error(new NoSchedulesFoundException("No schedules found for connecting flight from " + airports.get(0) + " via " + airports.get(1) + " to " + airports.get(2))))
                        .map(legs -> new RouteWithNStopDTO(1, legs))
                        .flux();
            }
            else {
                return Flux.empty();
            }
        });
    }

    private Flux<LegDTO> processDirectFlight(String origin, String destination, LocalDateTime departureDate, LocalDateTime arrivalDate) {
        return this.schedulesClient.getSchedule(origin, destination, departureDate.getYear(), departureDate.getMonthValue())
                .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                .filter(day -> day.day() == departureDate.getDayOfMonth())
                .flatMap(day -> Flux.fromIterable(day.flights()))
                .filter(flight -> LocalTime.parse(flight.departureTime()).isAfter(departureDate.toLocalTime()))
                .filter(flight -> LocalTime.parse(flight.arrivalTime()).isBefore(arrivalDate.toLocalTime()))
                .map(flight -> new LegDTO(origin, destination,
                        LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight.departureTime())),
                        LocalDateTime.of(arrivalDate.toLocalDate(), LocalTime.parse(flight.arrivalTime()))));
    }

    private Flux<LegDTO> processConnectingFlight(String origin, String stopover, String destination, LocalDateTime departureDate, LocalDateTime arrivalDate) {
        return this.schedulesClient.getSchedule(origin, stopover, departureDate.getYear(), departureDate.getMonthValue())
                .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                .filter(day -> day.day() == departureDate.getDayOfMonth())
                .flatMap(day -> Flux.fromIterable(day.flights()))
                .filter(flight1 -> LocalTime.parse(flight1.departureTime()).isAfter(departureDate.toLocalTime()))
                .flatMap(flight1 -> this.schedulesClient.getSchedule(stopover, destination, departureDate.getYear(), departureDate.getMonthValue())
                        .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                        .filter(day -> day.day() == departureDate.getDayOfMonth())
                        .flatMap(day -> Flux.fromIterable(day.flights()))
                        .filter(flight2 -> LocalTime.parse(flight2.departureTime()).isAfter(LocalTime.parse(flight1.arrivalTime())))
                        .filter(flight2 -> LocalTime.parse(flight2.arrivalTime()).isBefore(arrivalDate.toLocalTime()))
                        .filter(flight2 -> Duration.between(LocalTime.parse(flight1.arrivalTime()), LocalTime.parse(flight2.departureTime())).toHours() >= 2)
                        .flatMap(flight2 -> Flux.just(
                                new LegDTO(origin, stopover,
                                        LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight1.departureTime())),
                                        LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight1.arrivalTime()))),
                                new LegDTO(stopover, destination,
                                        LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight2.departureTime())),
                                        LocalDateTime.of(arrivalDate.toLocalDate(), LocalTime.parse(flight2.arrivalTime())))
                        ))
                );
    }

}
