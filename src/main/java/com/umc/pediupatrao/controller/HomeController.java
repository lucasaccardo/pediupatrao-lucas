package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas gerais (home, usuários, produtos e clientes).
 * As telas de pedidos foram movidas para PedidoWebController.
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;

    public HomeController(ClienteService clienteService, ProdutoService produtoService, UsuarioService usuarioService) {
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        prepararFormUsuario(model, new Usuario());
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        return usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null); // nunca envia o hash para a tela
            prepararFormUsuario(model, u);
            return "layout";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        });
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios/novo";
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private void prepararFormUsuario(Model model, Usuario usuario) {
        model.addAttribute("usuario", usuario);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("usuarioLogado", UsuarioAutenticado.username());
        model.addAttribute("content", "usuarios/form :: content");
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("podeEditar", UsuarioAutenticado.possuiPerfil("ATENDENTE"));
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.novoCliente(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/novo";
        }
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        try {
            clienteService.salvar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso! Alterações registradas na auditoria.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/editar/" + id;
        }
        return "redirect:/clientes";
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
