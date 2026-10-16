package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * RF05 - Registro de auditoria persistido na coleção "auditoria" do MongoDB.
 *
 * Integridade: a classe não possui setters. Todos os dados são definidos no
 * construtor e o repositório só expõe operações de inclusão e consulta
 * (não existe update nem delete), então o registro não pode ser alterado
 * nem apagado pelas telas ou APIs da aplicação.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private String id; // preenchido pelo MongoDB na inclusão

    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final TipoOperacaoAuditoria operacao;
    private final String entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    @PersistenceCreator
    public RegistroAuditoria(String id, LocalDateTime dataHora, String usuario, String perfil,
                             TipoOperacaoAuditoria operacao, String entidade, String entidadeId,
                             List<AlteracaoCampo> alteracoes, String justificativa) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? new ArrayList<>() : new ArrayList<>(alteracoes);
        this.justificativa = justificativa;
    }

    /** Fábrica usada pelo AuditoriaService para criar um registro novo (sem id). */
    public static RegistroAuditoria novo(LocalDateTime dataHora, String usuario, String perfil,
                                         TipoOperacaoAuditoria operacao, String entidade, String entidadeId,
                                         List<AlteracaoCampo> alteracoes, String justificativa) {
        return new RegistroAuditoria(null, dataHora, usuario, perfil, operacao, entidade,
                entidadeId, alteracoes, justificativa);
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public TipoOperacaoAuditoria getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return Collections.unmodifiableList(alteracoes); }
    public String getJustificativa() { return justificativa; }
}
