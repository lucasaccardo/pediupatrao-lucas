package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * RF01 - Carrega o usuário para o Spring Security com o PERFIL REAL gravado
 * no banco (antes todos recebiam a role USER).
 *
 * Se o perfil gravado for inválido/nulo (dados antigos), a conta fica
 * bloqueada: assim ninguém entra sem um perfil válido.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    Perfil perfil = null;
                    try {
                        perfil = Perfil.deTexto(usuario.getRole());
                    } catch (IllegalArgumentException e) {
                        // perfil inválido -> conta bloqueada (accountLocked)
                    }
                    return User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword())
                            .roles(perfil == null ? "SEM_PERFIL" : perfil.name())
                            .accountLocked(perfil == null)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
