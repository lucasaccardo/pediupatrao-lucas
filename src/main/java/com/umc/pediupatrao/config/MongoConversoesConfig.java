package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.StatusPedido;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Compatibilidade com pedidos gravados pela versão inicial do projeto, que
 * usavam status em texto livre (ex.: "EM_PREPARO", "ENTREGUE").
 * Ao LER do MongoDB, esses textos são convertidos para o enum StatusPedido.
 */
@Configuration
public class MongoConversoesConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StatusPedidoReadConverter()));
    }

    @ReadingConverter
    static class StatusPedidoReadConverter implements Converter<String, StatusPedido> {
        @Override
        public StatusPedido convert(String source) {
            try {
                return StatusPedido.deTexto(source);
            } catch (IllegalArgumentException e) {
                return StatusPedido.RECEBIDO;
            }
        }
    }
}
