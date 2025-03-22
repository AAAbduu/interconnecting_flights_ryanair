package com.ryanair.task.interconnected_flights.client;

import com.ryanair.task.interconnected_flights.dto.MonthScheduleDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SchedulesClient {
    private final WebClient webClient;

    public SchedulesClient(WebClient.Builder webclient){
        this.webClient = webclient.baseUrl("https://services-api.ryanair.com/timtbl/3/schedules").build();
    }

    public Flux<MonthScheduleDTO> getSchedule(String departure, String arrival, int year, int month) {
        return webClient.get()
                .uri("/" + departure + "/" + arrival + "/years/" + year + "/months/" + month)
                .retrieve()
                .bodyToFlux(MonthScheduleDTO.class);
    }

}
