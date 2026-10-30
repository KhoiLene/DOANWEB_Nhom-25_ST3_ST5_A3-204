package com.shopchain.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantConnectionDetails;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Avoid an optional network version probe while creating the Qdrant client during app startup. */
@Configuration
public class QdrantClientStartupConfiguration {

    @Bean
    @ConditionalOnMissingBean(QdrantClient.class)
    QdrantClient qdrantClient(QdrantVectorStoreProperties properties,
                              QdrantConnectionDetails connectionDetails) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                connectionDetails.getHost(), connectionDetails.getPort(), properties.isUseTls(), false);
        if (connectionDetails.getApiKey() != null && !connectionDetails.getApiKey().isBlank()) {
            builder.withApiKey(connectionDetails.getApiKey());
        }
        return new QdrantClient(builder.build());
    }
}
