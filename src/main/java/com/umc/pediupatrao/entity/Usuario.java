package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario {
    @Id
    private String id;

    private String username; // Nome de usuário para login

    /**
     * Senha criptografada (BCrypt). WRITE_ONLY: a API aceita a senha na entrada,
     * mas nunca devolve o hash nas respostas JSON.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String role;     // Perfil do usuário: ADMIN, GERENTE ou ATENDENTE (ver enum Perfil)

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
