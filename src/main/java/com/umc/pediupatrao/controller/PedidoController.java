package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST de pedidos. Protegida no SecurityConfig (URL) e no PedidoService (@PreAuthorize).
 *
 * POST   /api/pedidos                 -> GERENTE, ATENDENTE (total calculado no servidor)
 * GET    /api/pedidos, /api/pedidos/{id} -> ADMIN, GERENTE, ATENDENTE
 * PUT    /api/pedidos/{id}/status?status=EM_PREPARACAO -> GERENTE, ATENDENTE
 * POST   /api/pedidos/{id}/desconto   -> SOMENTE GERENTE  body: {"percentual": 10, "motivo": "..."}
 * POST   /api/pedidos/{id}/cancelar   -> SOMENTE GERENTE  body: {"justificativa": "..."}
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody NovoPedidoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(dados));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.alterarStatus(id, StatusPedido.deTexto(status));
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object percentual = body.get("percentual");
        BigDecimal valor = percentual == null ? null : new BigDecimal(percentual.toString());
        Object motivo = body.get("motivo");
        return pedidoService.aplicarDesconto(id, valor, motivo == null ? null : motivo.toString());
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object justificativa = body.get("justificativa");
        return pedidoService.cancelar(id, justificativa == null ? null : justificativa.toString());
    }
}
