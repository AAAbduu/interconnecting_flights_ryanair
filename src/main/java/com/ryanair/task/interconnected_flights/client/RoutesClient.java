package com.ryanair.task.interconnected_flights.client;

import com.ryanair.task.interconnected_flights.dto.RouteDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
public class RoutesClient {
    private final WebClient webClient;

    public RoutesClient(WebClient.Builder webClient){
        this.webClient = webClient.baseUrl("https://services-api.ryanair.com/views/locate/3").build();
    }

    public Flux<RouteDTO> getRoutes(){
        return this.webClient.get().uri("/routes").retrieve().bodyToFlux(RouteDTO.class);
    }


}
