package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas (Thymeleaf) de pedidos: lista, novo pedido, detalhes, transições,
 * desconto e cancelamento. As regras ficam no PedidoService; aqui apenas
 * recebemos os dados e mostramos mensagens de sucesso/erro.
 */
@Controller
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final AuditoriaService auditoriaService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
                               ProdutoService produtoService, AuditoriaService auditoriaService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/pedidos")
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novo(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()))
                .toList());
        model.addAttribute("modalidades", ModalidadeEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/pedidos")
    public String criar(@RequestParam(required = false) String clienteId,
                        @RequestParam(required = false) ModalidadeEntrega modalidade,
                        @RequestParam(required = false) String observacao,
                        @RequestParam(name = "produtoId", required = false) List<String> produtoIds,
                        @RequestParam(name = "quantidade", required = false) List<String> quantidades,
                        RedirectAttributes ra) {
        try {
            List<NovoPedidoRequest.ItemRequest> itens = new ArrayList<>();
            List<String> ids = produtoIds == null ? Collections.emptyList() : produtoIds;
            for (int i = 0; i < ids.size(); i++) {
                String qtdTexto = (quantidades != null && i < quantidades.size()) ? quantidades.get(i) : "0";
                itens.add(new NovoPedidoRequest.ItemRequest(ids.get(i), converterQuantidade(qtdTexto)));
            }
            Pedido pedido = pedidoService.criarPedido(new NovoPedidoRequest(clienteId, modalidade, observacao, itens));
            ra.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo() + " registrado (RECEBIDO).");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes ra) {
        Pedido pedido;
        try {
            pedido = pedidoService.buscarPorId(id);
        } catch (RecursoNaoEncontradoException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        model.addAttribute("pedido", pedido);
        model.addAttribute("proximosStatus", pedidoService.proximosStatusPermitidos(pedido));
        if (UsuarioAutenticado.possuiPerfil("ADMIN") || UsuarioAutenticado.possuiPerfil("GERENTE")) {
            model.addAttribute("auditoria", auditoriaService.historicoDe(AuditoriaService.ENTIDADE_PEDIDO, id));
        }
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String novoStatus, RedirectAttributes ra) {
        try {
            Pedido p = pedidoService.alterarStatus(id, StatusPedido.deTexto(novoStatus));
            ra.addFlashAttribute("sucesso", "Status alterado para " + p.getStatus().getDescricao() + ".");
        } catch (RegraNegocioException | IllegalArgumentException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id,
                                  @RequestParam(required = false) String percentual,
                                  @RequestParam(required = false) String motivo,
                                  RedirectAttributes ra) {
        try {
            BigDecimal valor = new BigDecimal(percentual == null ? "" : percentual.replace(',', '.').trim());
            Pedido p = pedidoService.aplicarDesconto(id, valor, motivo);
            ra.addFlashAttribute("sucesso", "Desconto de " + p.getPercentualDesconto() + "% aplicado.");
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("erro", "Percentual de desconto inválido.");
        } catch (RegraNegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
                           RedirectAttributes ra) {
        try {
            pedidoService.cancelar(id, justificativa);
            ra.addFlashAttribute("sucesso", "Pedido cancelado. Dados históricos preservados.");
        } catch (RegraNegocioException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private static Integer converterQuantidade(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("Quantidade inválida: " + texto);
        }
    }
}
