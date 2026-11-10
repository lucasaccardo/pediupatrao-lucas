package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.Pedido;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * RF05/RF06 - Consultas de pedidos para o dashboard, direto no MongoDB.
 *
 * Os FILTROS (período, status, modalidade) são aplicados no banco, usando o
 * índice em "dataHoraEntrada" criado no MongoInitConfig. Assim só os pedidos
 * do período chegam na aplicação. Os cálculos (somas com BigDecimal) ficam no
 * DashboardService - ver justificativa na documentação.
 */
@Repository
public class PedidoConsultaRepository {

    private final MongoTemplate mongoTemplate;

    public PedidoConsultaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Todos os pedidos que atendem ao filtro. */
    public List<Pedido> buscar(FiltroDashboard filtro) {
        return mongoTemplate.find(montarConsulta(filtro), Pedido.class);
    }

    /** Os pedidos mais recentes que atendem ao filtro (ordenados pela entrada, do mais novo). */
    public List<Pedido> buscarRecentes(FiltroDashboard filtro, int limite) {
        Query consulta = montarConsulta(filtro)
                .with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"))
                .limit(limite);
        return mongoTemplate.find(consulta, Pedido.class);
    }

    /** Monta o critério do MongoDB: entrada >= início E entrada < dia seguinte ao fim (+ status/modalidade). */
    static Query montarConsulta(FiltroDashboard filtro) {
        Criteria criterio = Criteria.where("dataHoraEntrada")
                .gte(filtro.inicioDataHora())
                .lt(filtro.fimExclusivo());
        if (filtro.status() != null) {
            criterio = criterio.and("status").is(filtro.status());
        }
        if (filtro.modalidade() != null) {
            criterio = criterio.and("modalidade").is(filtro.modalidade());
        }
        return new Query(criterio);
    }
}
