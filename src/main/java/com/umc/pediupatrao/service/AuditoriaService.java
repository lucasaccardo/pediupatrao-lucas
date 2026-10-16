package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * RF05 - Serviço de auditoria.
 *
 * Responsabilidades:
 * 1. Registrar eventos (quem, perfil, quando, operação, entidade/id, campos alterados, justificativa).
 * 2. Proteger dados sensíveis: senhas/tokens nunca são gravados e telefones são mascarados.
 * 3. Consultar os registros (somente ADMIN e GERENTE - protegido com @PreAuthorize).
 *
 * Não existe nenhum método para alterar ou excluir registros.
 */
@Service
public class AuditoriaService {

    public static final String ENTIDADE_PEDIDO = "PEDIDO";
    public static final String ENTIDADE_CLIENTE = "CLIENTE";
    public static final String ENTIDADE_USUARIO = "USUARIO";

    /** Campos que jamais podem aparecer com valor na auditoria. */
    private static final Set<String> CAMPOS_CREDENCIAIS = Set.of("password", "senha", "token", "secret");

    /** Campos de dados pessoais que são mascarados (minimização - LGPD). */
    private static final Set<String> CAMPOS_MASCARADOS = Set.of("telefone");

    private final RegistroAuditoriaRepository repository;

    public AuditoriaService(RegistroAuditoriaRepository repository) {
        this.repository = repository;
    }

    // ===================== REGISTRO =====================

    public RegistroAuditoria registrar(TipoOperacaoAuditoria operacao, String entidade, String entidadeId,
                                       List<AlteracaoCampo> alteracoes, String justificativa) {
        List<AlteracaoCampo> protegidas = new ArrayList<>();
        if (alteracoes != null) {
            for (AlteracaoCampo a : alteracoes) {
                protegidas.add(new AlteracaoCampo(a.getCampo(),
                        proteger(a.getCampo(), a.getValorAnterior()),
                        proteger(a.getCampo(), a.getValorNovo())));
            }
        }
        RegistroAuditoria registro = RegistroAuditoria.novo(
                LocalDateTime.now(),
                UsuarioAutenticado.username(),
                UsuarioAutenticado.perfil(),
                operacao, entidade, entidadeId, protegidas, justificativa);
        return repository.save(registro);
    }

    /**
     * Compara valor anterior e novo; se forem diferentes, adiciona na lista.
     * Textos vazios e nulos são considerados iguais (evita "falsas" alterações).
     */
    public static void comparar(List<AlteracaoCampo> lista, String campo, Object anterior, Object novo) {
        String a = normalizar(anterior);
        String n = normalizar(novo);
        if (!Objects.equals(a, n)) {
            lista.add(new AlteracaoCampo(campo, a, n));
        }
    }

    private static String normalizar(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }

    /** Remove credenciais e mascara dados pessoais antes de gravar. */
    static String proteger(String campo, String valor) {
        if (valor == null || campo == null) {
            return valor;
        }
        String nome = campo.toLowerCase(Locale.ROOT);
        if (CAMPOS_CREDENCIAIS.stream().anyMatch(nome::contains)) {
            return "[PROTEGIDO]";
        }
        if (CAMPOS_MASCARADOS.contains(nome)) {
            return mascararTelefone(valor);
        }
        return valor;
    }

    /** (11) 98765-4321 -> (11) *****-4321 : mantém DDD e 4 últimos dígitos. */
    static String mascararTelefone(String telefone) {
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 6) {
            return "****";
        }
        String ddd = digitos.substring(0, 2);
        String fim = digitos.substring(digitos.length() - 4);
        return "(" + ddd + ") *****-" + fim;
    }

    // ===================== CONSULTA (ADMIN e GERENTE) =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> consultar(String entidade, String operacao, String usuario) {
        List<RegistroAuditoria> lista;
        if (entidade != null && !entidade.isBlank()) {
            lista = repository.findByEntidadeOrderByDataHoraDesc(entidade.trim().toUpperCase(Locale.ROOT));
        } else {
            lista = repository.findAllByOrderByDataHoraDesc();
        }
        return lista.stream()
                .filter(r -> operacao == null || operacao.isBlank()
                        || (r.getOperacao() != null && r.getOperacao().name().equalsIgnoreCase(operacao.trim())))
                .filter(r -> usuario == null || usuario.isBlank()
                        || (r.getUsuario() != null && r.getUsuario().equalsIgnoreCase(usuario.trim())))
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> historicoDe(String entidade, String entidadeId) {
        return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId);
    }
}
