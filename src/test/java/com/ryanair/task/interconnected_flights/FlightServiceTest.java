package com.ryanair.task.interconnected_flights;

import com.ryanair.task.interconnected_flights.client.RoutesClient;
import com.ryanair.task.interconnected_flights.client.SchedulesClient;
import com.ryanair.task.interconnected_flights.dto.*;
import com.ryanair.task.interconnected_flights.exception.NoSchedulesFoundException;
import com.ryanair.task.interconnected_flights.exception.RouteNotFoundException;
import com.ryanair.task.interconnected_flights.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class FlightServiceTest {

    @Mock
    private RoutesClient routesClient;

    @Mock
    private SchedulesClient schedulesClient;

    private FlightService flightService;

    @BeforeEach
    void setUp(){
        MockitoAnnotations.openMocks(this);
        flightService = new FlightService(routesClient, schedulesClient);
    }

    @Test
    void testFindDirectFlights(){
        RouteDTO route = new RouteDTO("DUB", "WRO", null, false, false, "RYANAIR", null);
        when(routesClient.getRoutes()).thenReturn(Flux.just(route));

        MonthScheduleDTO monthScheduleDTO = new MonthScheduleDTO(1, List.of(new DayDTO(1, List.of(new FlightDTO("99", "12:40", "16:40")))));
        when(schedulesClient.getSchedule("DUB", "WRO", 2018, 3)).thenAnswer(invocation -> Flux.just(monthScheduleDTO));

        Flux<RouteWithNStopDTO> result = flightService.findFlights("DUB", "WRO", LocalDateTime.parse("2018-03-01T12:00:00"), LocalDateTime.parse("2018-03-01T17:00:00"));

        assertEquals(1, result.count().block());

    }

    @Test
    void testFindIndirectFlights() {
        RouteDTO route1 = new RouteDTO("DUB", "STN", null, false, false, "RYANAIR", null);
        RouteDTO route2 = new RouteDTO("STN", "WRO", null, false, false, "RYANAIR", null);

        when(routesClient.getRoutes()).thenReturn(Flux.just(route1, route2));

        MonthScheduleDTO scheduleDUBtoSTN = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("101", "06:25", "07:35")))
        ));
        MonthScheduleDTO scheduleSTNtoWRO = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("102", "09:50", "13:20")))
        ));

        when(schedulesClient.getSchedule("DUB", "STN", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoSTN));
        when(schedulesClient.getSchedule("STN", "WRO", 2018, 3)).thenReturn(Flux.just(scheduleSTNtoWRO));

        Flux<RouteWithNStopDTO> result = flightService.findFlights(
                "DUB", "WRO",
                LocalDateTime.parse("2018-03-01T06:00"),
                LocalDateTime.parse("2018-03-01T14:00")
        );

        List<RouteWithNStopDTO> flights = result.collectList().block();
        assertNotNull(flights);
        assertEquals(1, flights.size());
        assertEquals(1, flights.getFirst().stops());
        assertEquals(2, flights.getFirst().legs().size());

        LegDTO firstLeg = flights.getFirst().legs().get(0);
        LegDTO secondLeg = flights.getFirst().legs().get(1);

        assertEquals("DUB", firstLeg.departureAirport());
        assertEquals("STN", firstLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T06:25"), firstLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T07:35"), firstLeg.arrivalDateTime());

        assertEquals("STN", secondLeg.departureAirport());
        assertEquals("WRO", secondLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T09:50"), secondLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T13:20"), secondLeg.arrivalDateTime());
    }

    @Test
    void testFindDirectAndIndirectFlights() {
        RouteDTO directRoute = new RouteDTO("DUB", "WRO", null, false, false, "RYANAIR", null);
        RouteDTO route1 = new RouteDTO("DUB", "STN", null, false, false, "RYANAIR", null);
        RouteDTO route2 = new RouteDTO("STN", "WRO", null, false, false, "RYANAIR", null);

        when(routesClient.getRoutes()).thenReturn(Flux.just(directRoute, route1, route2));

        MonthScheduleDTO scheduleDUBtoWRO = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("99", "12:40", "16:40")))
        ));
        MonthScheduleDTO scheduleDUBtoSTN = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("101", "06:25", "07:35")))
        ));
        MonthScheduleDTO scheduleSTNtoWRO = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("102", "09:50", "13:20")))
        ));

        when(schedulesClient.getSchedule("DUB", "WRO", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoWRO));
        when(schedulesClient.getSchedule("DUB", "STN", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoSTN));
        when(schedulesClient.getSchedule("STN", "WRO", 2018, 3)).thenReturn(Flux.just(scheduleSTNtoWRO));

        Flux<RouteWithNStopDTO> result = flightService.findFlights(
                "DUB", "WRO",
                LocalDateTime.parse("2018-03-01T06:00"),
                LocalDateTime.parse("2018-03-01T17:00")
        );

        List<RouteWithNStopDTO> flights = result.collectList().block();
        assertNotNull(flights);
        assertEquals(2, flights.size());

        RouteWithNStopDTO directFlight = flights.stream()
                .filter(f -> f.stops() == 0)
                .findFirst()
                .orElseThrow();

        assertEquals(0, directFlight.stops());
        assertEquals(1, directFlight.legs().size());

        LegDTO directLeg = directFlight.legs().getFirst();
        assertEquals("DUB", directLeg.departureAirport());
        assertEquals("WRO", directLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T12:40"), directLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T16:40"), directLeg.arrivalDateTime());

        RouteWithNStopDTO indirectFlight = flights.stream()
                .filter(f -> f.stops() == 1)
                .findFirst()
                .orElseThrow();

        assertEquals(1, indirectFlight.stops());
        assertEquals(2, indirectFlight.legs().size());

        LegDTO firstLeg = indirectFlight.legs().get(0);
        LegDTO secondLeg = indirectFlight.legs().get(1);

        assertEquals("DUB", firstLeg.departureAirport());
        assertEquals("STN", firstLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T06:25"), firstLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T07:35"), firstLeg.arrivalDateTime());

        assertEquals("STN", secondLeg.departureAirport());
        assertEquals("WRO", secondLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T09:50"), secondLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T13:20"), secondLeg.arrivalDateTime());
    }

    @Test
    void testOnlyIndirectFlightAvailableDueToSchedule() {
        RouteDTO route1 = new RouteDTO("DUB", "STN", null, false, false, "RYANAIR", null);
        RouteDTO route2 = new RouteDTO("STN", "WRO", null, false, false, "RYANAIR", null);
        RouteDTO route3 = new RouteDTO("DUB", "WRO", null, false, false, "RYANAIR", null);

        when(routesClient.getRoutes()).thenReturn(Flux.just(route1, route2, route3));

        MonthScheduleDTO scheduleDUBtoSTN = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("101", "06:25", "07:35")))
        ));
        MonthScheduleDTO scheduleSTNtoWRO = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("102", "09:50", "13:20")))
        ));
        MonthScheduleDTO scheduleDUBtoWRO = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("99", "18:00", "22:00"))) // No encaja en la ventana horaria
        ));

        when(schedulesClient.getSchedule("DUB", "STN", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoSTN));
        when(schedulesClient.getSchedule("STN", "WRO", 2018, 3)).thenReturn(Flux.just(scheduleSTNtoWRO));
        when(schedulesClient.getSchedule("DUB", "WRO", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoWRO));

        Flux<RouteWithNStopDTO> result = flightService.findFlights(
                "DUB", "WRO",
                LocalDateTime.parse("2018-03-01T06:00"),
                LocalDateTime.parse("2018-03-01T14:00")
        );

        List<RouteWithNStopDTO> flights = result.collectList().block();
        assertNotNull(flights);
        assertEquals(1, flights.size());

        RouteWithNStopDTO indirectFlight = flights.getFirst();
        assertEquals(1, indirectFlight.stops());
        assertEquals(2, indirectFlight.legs().size());

        LegDTO firstLeg = indirectFlight.legs().get(0);
        LegDTO secondLeg = indirectFlight.legs().get(1);

        assertEquals("DUB", firstLeg.departureAirport());
        assertEquals("STN", firstLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T06:25"), firstLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T07:35"), firstLeg.arrivalDateTime());

        assertEquals("STN", secondLeg.departureAirport());
        assertEquals("WRO", secondLeg.arrivalAirport());
        assertEquals(LocalDateTime.parse("2018-03-01T09:50"), secondLeg.departureDateTime());
        assertEquals(LocalDateTime.parse("2018-03-01T13:20"), secondLeg.arrivalDateTime());
    }

    @Test
    void testNotExistingRouteShouldThrowRouteNotFoundException() {
        RouteDTO route1 = new RouteDTO("DUB", "STN", null, false, false, "RYANAIR", null);
        when(routesClient.getRoutes()).thenReturn(Flux.just(route1));

        MonthScheduleDTO scheduleDUBtoSTN = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("101", "06:25", "07:35")))
        ));
        when(schedulesClient.getSchedule("DUB", "STN", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoSTN));

        assertThrows(RouteNotFoundException.class, () ->
                flightService.findFlights("DUB", "MAD", LocalDateTime.parse("2018-03-01T06:00"), LocalDateTime.parse("2018-03-01T14:00")).blockLast()
        );
    }

    @Test
    void testNotExistingSchedulesShouldTrowNoSchedulesFoundException() {
        RouteDTO route1 = new RouteDTO("DUB", "STN", null, false, false, "RYANAIR", null);
        RouteDTO route2 = new RouteDTO("STN", "WRO", null, false, false, "RYANAIR", null);

        when(routesClient.getRoutes()).thenReturn(Flux.just(route1, route2));

        MonthScheduleDTO scheduleDUBtoSTN = new MonthScheduleDTO(3, List.of(
                new DayDTO(1, List.of(new FlightDTO("101", "06:25", "07:35")))
        ));
        when(schedulesClient.getSchedule("DUB", "STN", 2018, 3)).thenReturn(Flux.just(scheduleDUBtoSTN));
        when(schedulesClient.getSchedule("STN", "WRO", 2018, 3)).thenReturn(Flux.empty());

        assertThrows(NoSchedulesFoundException.class, () ->
                flightService.findFlights("DUB", "WRO", LocalDateTime.parse("2018-03-01T06:00"), LocalDateTime.parse("2018-03-01T14:00")).blockLast()
        );
    }
}
