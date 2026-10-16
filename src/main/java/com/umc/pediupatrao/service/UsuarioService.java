package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * RF01 - Administração de usuários e perfis (somente ADMIN).
 *
 * Proteções contra atribuição indevida de privilégios:
 * 1. Todos os métodos exigem ROLE_ADMIN (@PreAuthorize) - além da regra de URL.
 * 2. O perfil é validado contra o enum Perfil (valores forjados são rejeitados).
 * 3. O ADMIN não pode alterar o próprio perfil nem excluir a si mesmo.
 * 4. O sistema nunca fica sem pelo menos um ADMIN.
 * 5. O id enviado no formulário é ignorado (evita "mass assignment").
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /** Cria um novo usuário (senha criptografada com BCrypt). */
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
        }
        Perfil perfil = perfilValido(dados.getRole());
        validarSenha(dados.getPassword(), true);

        Usuario novo = new Usuario();          // id sempre gerado pelo banco
        novo.setUsername(username);
        novo.setRole(perfil.name());
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        Usuario salvo = usuarioRepository.save(novo);

        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "username", null, salvo.getUsername());
        AuditoriaService.comparar(campos, "perfil", null, salvo.getRole());
        auditoriaService.registrar(TipoOperacaoAuditoria.INCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                salvo.getId(), campos, null);
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        String username = validarUsername(dados.getUsername());
        usuarioRepository.findByUsername(username)
                .filter(outro -> !outro.getId().equals(existente.getId()))
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
                });

        Perfil novoPerfil = perfilValido(dados.getRole());
        boolean ehOProprioUsuario = existente.getUsername().equals(UsuarioAutenticado.username());

        if (ehOProprioUsuario && !novoPerfil.name().equals(existente.getRole())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && novoPerfil != Perfil.ADMIN && contarAdmins() <= 1) {
            throw new RegraNegocioException("O sistema precisa ter pelo menos um ADMIN.");
        }

        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "username", existente.getUsername(), username);
        AuditoriaService.comparar(campos, "perfil", existente.getRole(), novoPerfil.name());

        existente.setUsername(username);
        existente.setRole(novoPerfil.name());
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword(), false);
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
            // registra apenas QUE a senha mudou, nunca o valor
            campos.add(new AlteracaoCampo("senha", null, "[PROTEGIDO]"));
        }
        Usuario salvo = usuarioRepository.save(existente);
        if (!campos.isEmpty()) {
            auditoriaService.registrar(TipoOperacaoAuditoria.ALTERACAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                    salvo.getId(), campos, null);
        }
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(UsuarioAutenticado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && contarAdmins() <= 1) {
            throw new RegraNegocioException("O sistema precisa ter pelo menos um ADMIN.");
        }
        usuarioRepository.deleteById(id);
        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "username", existente.getUsername(), null);
        AuditoriaService.comparar(campos, "perfil", existente.getRole(), null);
        auditoriaService.registrar(TipoOperacaoAuditoria.EXCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                id, campos, null);
    }

    private long contarAdmins() {
        return usuarioRepository.findAll().stream()
                .filter(u -> Perfil.ADMIN.name().equals(u.getRole()))
                .count();
    }

    private static Perfil perfilValido(String role) {
        try {
            return Perfil.deTexto(role);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
    }

    private static String validarUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RegraNegocioException("Username deve ter de 3 a 30 caracteres (letras, números, ponto, _ ou -).");
        }
        return username.trim();
    }

    private static void validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new RegraNegocioException("Informe a senha.");
            }
            return;
        }
        if (senha.length() < 6) {
            throw new RegraNegocioException("A senha deve ter pelo menos 6 caracteres.");
        }
    }
}
