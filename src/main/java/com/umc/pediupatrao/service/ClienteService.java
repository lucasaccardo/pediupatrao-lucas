package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes + RF05 (auditoria das alterações cadastrais).
 *
 * Permissões (RF01):
 * - ATENDENTE: cadastra, atualiza e exclui clientes.
 * - GERENTE: consulta clientes.
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "nome", existente.getNome(), null);
        auditoriaService.registrar(TipoOperacaoAuditoria.EXCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                id, campos, null);
    }

    /**
     * Salva um cliente novo ou atualiza um existente, auditando cada campo
     * alterado (valor anterior e novo). Endereço e telefone são os principais
     * campos acompanhados pela auditoria.
     */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente dados) {
        validar(dados);

        // ----- Inclusão -----
        if (dados.getId() == null || dados.getId().isBlank()) {
            dados.setId(null);
            Cliente salvo = clienteRepository.save(dados);
            List<AlteracaoCampo> campos = new ArrayList<>();
            compararTodos(campos, new Cliente(), salvo);
            auditoriaService.registrar(TipoOperacaoAuditoria.INCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                    salvo.getId(), campos, null);
            return salvo;
        }

        // ----- Alteração -----
        Cliente existente = clienteRepository.findById(dados.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));

        List<AlteracaoCampo> campos = new ArrayList<>();
        compararTodos(campos, existente, dados);

        // Copia apenas os campos de cadastro permitidos para o objeto do banco
        existente.setNome(dados.getNome());
        existente.setTelefone(dados.getTelefone());
        existente.setCep(dados.getCep());
        existente.setLogradouro(dados.getLogradouro());
        existente.setNumero(dados.getNumero());
        existente.setComplemento(dados.getComplemento());
        existente.setBairro(dados.getBairro());
        existente.setCidade(dados.getCidade());
        existente.setEstado(dados.getEstado());

        Cliente salvo = clienteRepository.save(existente);
        if (!campos.isEmpty()) {
            auditoriaService.registrar(TipoOperacaoAuditoria.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                    salvo.getId(), campos, null);
        }
        return salvo;
    }

    private static void compararTodos(List<AlteracaoCampo> campos, Cliente antes, Cliente depois) {
        AuditoriaService.comparar(campos, "nome", antes.getNome(), depois.getNome());
        AuditoriaService.comparar(campos, "telefone", antes.getTelefone(), depois.getTelefone());
        AuditoriaService.comparar(campos, "cep", antes.getCep(), depois.getCep());
        AuditoriaService.comparar(campos, "logradouro", antes.getLogradouro(), depois.getLogradouro());
        AuditoriaService.comparar(campos, "numero", antes.getNumero(), depois.getNumero());
        AuditoriaService.comparar(campos, "complemento", antes.getComplemento(), depois.getComplemento());
        AuditoriaService.comparar(campos, "bairro", antes.getBairro(), depois.getBairro());
        AuditoriaService.comparar(campos, "cidade", antes.getCidade(), depois.getCidade());
        AuditoriaService.comparar(campos, "estado", antes.getEstado(), depois.getEstado());
    }

    private static void validar(Cliente c) {
        if (c == null) {
            throw new RegraNegocioException("Dados do cliente não informados.");
        }
        if (c.getNome() == null || c.getNome().trim().length() < 2) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        String digitos = c.getTelefone() == null ? "" : c.getTelefone().replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 11) {
            throw new RegraNegocioException("Telefone inválido. Use DDD + número, ex.: (11) 99999-9999.");
        }
        if (c.getEstado() != null && !c.getEstado().isBlank() && c.getEstado().trim().length() != 2) {
            throw new RegraNegocioException("Estado deve ter 2 letras (ex.: SP).");
        }
    }
}
