package com.chat.controller;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para expor métricas Prometheus.
 * 
 * WORKAROUND: Spring Boot 3.2.5 tem bug com @ConditionalOnAvailableEndpoint
 * que impede o endpoint prometheus de ser auto-configurado corretamente.
 * Esta classe expõe manualmente as métricas em /actuator/prometheus
 */
@RestController
@RequestMapping("/actuator")
public class PrometheusController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public PrometheusController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return prometheusMeterRegistry.scrape();
    }
}
