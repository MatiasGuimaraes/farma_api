package br.com.farmaetiquetas.farma_api.controller;

import br.com.farmaetiquetas.farma_api.dto.PedidoDTO;
import br.com.farmaetiquetas.farma_api.Service.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/buscar/{numPedido}")
    public ResponseEntity<PedidoDTO> buscarPedido(@PathVariable String numPedido) {
        PedidoDTO pedido = pedidoService.buscarPedido(numPedido);

        if (pedido == null) {
            // Devolve erro 404 (Not Found) se o número da nota não existir no banco
            return ResponseEntity.notFound().build();
        }

        // Devolve 200 (OK) e o JSON completo com os dados do cliente e remédios
        return ResponseEntity.ok(pedido);
    }
}