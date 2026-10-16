package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * RF05 - Repositório da auditoria.
 *
 * Estende {@link Repository} (interface "marcadora", sem métodos prontos) em vez
 * de MongoRepository. Assim expomos APENAS inclusão e consulta: não existem
 * métodos delete, deleteAll, deleteById etc. Isso protege a integridade dos
 * registros contra alteração ou exclusão pela aplicação.
 */
public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, String> {

    /**
     * Inclusão de um novo registro. O AuditoriaService sempre cria objetos
     * novos (id nulo), então o MongoDB executa um INSERT, nunca um UPDATE.
     */
    <S extends RegistroAuditoria> S save(S registro);

    List<RegistroAuditoria> findAllByOrderByDataHoraDesc();

    List<RegistroAuditoria> findByEntidadeOrderByDataHoraDesc(String entidade);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);

    List<RegistroAuditoria> findByOperacaoOrderByDataHoraDesc(TipoOperacaoAuditoria operacao);

    List<RegistroAuditoria> findByUsuarioOrderByDataHoraDesc(String usuario);

    long count();
}
