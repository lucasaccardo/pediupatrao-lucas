package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria os dados iniciais para teste/demonstração:
 * - um usuário para cada perfil (ADMIN, GERENTE, ATENDENTE);
 * - alguns produtos do cardápio, caso a coleção esteja vazia.
 *
 * As senhas são gravadas criptografadas (BCrypt) e NÃO são escritas no log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos, PasswordEncoder encoder,
                                   DadosDemonstracao demonstracao,
                                   @Value("${app.demo.pedidos:false}") boolean gerarPedidosDemo) {
        return args -> {
            criarOuCorrigirUsuario(usuarios, encoder, "admin", "teste123", Perfil.ADMIN);
            criarOuCorrigirUsuario(usuarios, encoder, "gerente", "gerente123", Perfil.GERENTE);
            criarOuCorrigirUsuario(usuarios, encoder, "atendente", "atendente123", Perfil.ATENDENTE);

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa", "Pizza", 49.90));
                produtos.save(produto("Pizza Margherita", "Pizza", 45.00));
                produtos.save(produto("Pizza Portuguesa", "Pizza", 54.50));
                produtos.save(produto("Refrigerante 2L", "Bebida", 12.00));
                log.info("Produtos iniciais do cardápio criados.");
            }

            // Dashboard: massa de pedidos de demonstração (só com DEMO_PEDIDOS=true e banco sem pedidos)
            if (gerarPedidosDemo) {
                demonstracao.gerarSeVazio();
            }
        };
    }

    /**
     * Cria o usuário se não existir. Se existir SEM perfil (versão antiga do
     * projeto gravava o admin sem role), corrige o perfil.
     */
    private void criarOuCorrigirUsuario(UsuarioRepository repo, PasswordEncoder encoder,
                                        String username, String senhaInicial, Perfil perfil) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (existente.getRole() == null || existente.getRole().isBlank()) {
                existente.setRole(perfil.name());
                repo.save(existente);
                log.info("Perfil do usuário '{}' ajustado para {}.", username, perfil);
            }
        }, () -> {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPassword(encoder.encode(senhaInicial));
            u.setRole(perfil.name());
            repo.save(u);
            log.info("Usuário inicial '{}' criado com perfil {}.", username, perfil);
        });
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }
}
