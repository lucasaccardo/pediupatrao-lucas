package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * RF01 - Autorização efetiva no BACKEND.
 *
 * Duas camadas de proteção (defesa em profundidade):
 * 1. Regras por URL e método HTTP (abaixo).
 * 2. @PreAuthorize nos métodos dos services (habilitado por @EnableMethodSecurity).
 *
 * CSRF agora está HABILITADO: formulários Thymeleaf recebem o token
 * automaticamente e as chamadas fetch() enviam o cabeçalho X-CSRF-TOKEN.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    /** Identifica requisições de API, que devem receber JSON (401/403) em vez de redirecionamento. */
    private static final RequestMatcher API = request -> request.getRequestURI().startsWith("/api/");

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF habilitado (padrão do Spring Security). Antes estava desativado.
                .csrf(csrf -> { })
                .authorizeHttpRequests(auth -> auth
                        // ---------- Público ----------
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // ---------- Usuários e perfis: SOMENTE ADMIN ----------
                        // (antes /api/usuarios era permitAll: qualquer um criava um ADMIN!)
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)

                        // ---------- Auditoria: ADMIN e GERENTE ----------
                        .requestMatchers(HttpMethod.GET, "/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll() // nenhuma alteração/exclusão

                        // ---------- Desconto e cancelamento: SOMENTE GERENTE (nem o ADMIN) ----------
                        .requestMatchers("/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole(GERENTE)

                        // ---------- Pedidos ----------
                        .requestMatchers("/pedidos/novo").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/pedidos", "/pedidos/*/status").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*", "/api/pedidos", "/api/pedidos/*")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").denyAll()

                        // ---------- Clientes ----------
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // ---------- Produtos (cardápio) ----------
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // API sem login -> 401 (em vez de redirecionar para a tela de login)
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API)
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    /**
     * 403 em JSON para as APIs e página de erro "Acesso negado" para as telas.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandlerImpl paginaErro = new AccessDeniedHandlerImpl();
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException ex) -> {
            if (API.matches(request)) {
                escreverJson(response, HttpStatus.FORBIDDEN,
                        "Acesso negado: seu perfil não tem permissão para esta operação.");
            } else {
                paginaErro.handle(request, response, ex);
            }
        };
    }

    private static void escreverJson(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"erro\":\"" + status.getReasonPhrase() + "\",\"mensagem\":\"" + mensagem + "\"}");
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
