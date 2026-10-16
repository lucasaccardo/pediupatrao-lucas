package com.umc.pediupatrao.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitário para obter o usuário logado a partir do SecurityContext do
 * Spring Security. O nome e o perfil NUNCA vêm do formulário/requisição:
 * vêm da sessão autenticada, então não podem ser falsificados.
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "sistema" : auth.getName();
    }

    public static String perfil() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "SISTEMA";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("SEM_PERFIL");
    }

    public static boolean possuiPerfil(String perfil) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil));
    }
}
