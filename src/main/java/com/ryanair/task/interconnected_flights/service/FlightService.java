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
                                .map(legs -> new RouteWithNStopDTO(0, legs))
                                .flux();
                    } else if (airports.size() == 3) {
                        return processConnectingFlight(airports.get(0), airports.get(1), airports.get(2), departureDate, arrivalDate)
                                .collectList()
                                .filter(legs -> legs.size() == 2)
                                .map(legs -> new RouteWithNStopDTO(1, legs))
                                .flux();
                    } else {
                        return Flux.empty();
                    }
                }).collectList()
                .filter(routesList -> !routesList.isEmpty())
                .switchIfEmpty(Mono.error(new NoSchedulesFoundException("No schedules found for requested route and dates")))
                .flatMapMany(Flux::fromIterable);
    }

    private Flux<LegDTO> processDirectFlight(String origin, String destination, LocalDateTime departureDate, LocalDateTime arrivalDate) {
        return this.schedulesClient.getSchedule(origin, destination, departureDate.getYear(), departureDate.getMonthValue())
                .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                .filter(day -> day.day() == departureDate.getDayOfMonth())
                .flatMap(day -> Flux.fromIterable(day.flights()))
                .filter(flight -> LocalTime.parse(flight.departureTime()).isAfter(departureDate.toLocalTime()))
                .map(flight -> {
                    LocalDateTime flightDepartureDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight.departureTime()));
                    LocalDateTime flightArrivalDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight.arrivalTime()));
                    if (flightArrivalDateTime.isBefore(flightDepartureDateTime)) {
                        flightArrivalDateTime = flightArrivalDateTime.plusDays(1);
                    }
                    return new LegDTO(origin, destination, flightDepartureDateTime, flightArrivalDateTime);
                })
                .filter(leg -> !leg.arrivalDateTime().isAfter(arrivalDate));
    }

    private Flux<LegDTO> processConnectingFlight(String origin, String stopover, String destination, LocalDateTime departureDate, LocalDateTime arrivalDate) {
        return this.schedulesClient.getSchedule(origin, stopover, departureDate.getYear(), departureDate.getMonthValue())
                .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                .filter(day -> day.day() == departureDate.getDayOfMonth())
                .flatMap(day -> Flux.fromIterable(day.flights()))
                .filter(flight1 -> LocalTime.parse(flight1.departureTime()).isAfter(departureDate.toLocalTime()))
                .flatMap(flight1 -> {
                    LocalDateTime flight1DepartureDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight1.departureTime()));
                    LocalDateTime flight1ArrivalDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight1.arrivalTime()));
                    if (flight1ArrivalDateTime.isBefore(flight1DepartureDateTime)) {
                        flight1ArrivalDateTime = flight1ArrivalDateTime.plusDays(1);
                    }
                    LocalDateTime finalFlight1ArrivalDateTime = flight1ArrivalDateTime;
                    LocalDateTime finalFlight1ArrivalDateTime1 = flight1ArrivalDateTime;
                    return this.schedulesClient.getSchedule(stopover, destination, departureDate.getYear(), departureDate.getMonthValue())
                            .flatMap(monthScheduleDTO -> Flux.fromIterable(monthScheduleDTO.days()))
                            .filter(day -> day.day() == departureDate.getDayOfMonth())
                            .flatMap(day -> Flux.fromIterable(day.flights()))
                            .filter(flight2 -> LocalTime.parse(flight2.departureTime()).isAfter(LocalTime.parse(flight1.arrivalTime())))
                            .map(flight2 -> {
                                LocalDateTime flight2DepartureDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight2.departureTime()));
                                LocalDateTime flight2ArrivalDateTime = LocalDateTime.of(departureDate.toLocalDate(), LocalTime.parse(flight2.arrivalTime()));
                                if (flight2ArrivalDateTime.isBefore(flight2DepartureDateTime)) {
                                    flight2ArrivalDateTime = flight2ArrivalDateTime.plusDays(1);
                                }
                                return new LegDTO(stopover, destination, flight2DepartureDateTime, flight2ArrivalDateTime);
                            })
                            .filter(leg -> !leg.arrivalDateTime().isAfter(arrivalDate))
                            .filter(leg -> Duration.between(finalFlight1ArrivalDateTime.toLocalTime(), leg.departureDateTime().toLocalTime()).toHours() >= 2)
                            .flatMap(leg -> Flux.just(
                                    new LegDTO(origin, stopover, flight1DepartureDateTime, finalFlight1ArrivalDateTime1),
                                    new LegDTO(stopover, destination, leg.departureDateTime(), leg.arrivalDateTime())
                            ));
                });
    }

}
