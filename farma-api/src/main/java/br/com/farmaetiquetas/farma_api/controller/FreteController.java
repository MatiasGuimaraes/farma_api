package br.com.farmaetiquetas.farma_api.controller;

import br.com.farmaetiquetas.farma_api.Service.FreteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/frete")
public class FreteController {

    private final FreteService freteService;

    // O Spring Boot injeta automaticamente o FreteService aqui
    public FreteController(FreteService freteService) {
        this.freteService = freteService;
    }

    // Endpoint que vai escutar os pedidos do teu programa Swing
    @GetMapping("/calcular")
    public Map<String, Object> calcularFrete(@RequestParam String cep, @RequestParam boolean convenio) {
        return freteService.calcularFrete(cep, convenio);
    }
}