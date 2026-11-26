package com.playtomic.tests.wallet;

import com.mongodb.ConnectionString;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;

@TestConfiguration(proxyBeanMethods = false)
class AuditNetworkFixture {
    private static final int PROXY_PORT = 8666;

    @Bean(destroyMethod = "close")
    Network auditNetwork() {
        return Network.newNetwork();
    }

    @Bean
    MongoDBContainer auditMongo(Network network) {
        return new MongoDBContainer(System.getProperty("wallet.test.mongo.image", "mongo:8.3.2-noble"))
                .withReplicaSet().withNetwork(network).withNetworkAliases("audit-mongo");
    }

    @Bean
    GenericContainer<?> auditProxy(Network network, MongoDBContainer auditMongo) {
        String config = """
                [{"name":"mongo","listen":"0.0.0.0:%d","upstream":"audit-mongo:27017","enabled":true}]
                """.formatted(PROXY_PORT);
        return new GenericContainer<>(System.getProperty("wallet.test.toxiproxy.image", "ghcr.io/shopify/toxiproxy:2.12.0"))
                .withNetwork(network).dependsOn(auditMongo).withExposedPorts(8474, PROXY_PORT)
                .withCopyToContainer(Transferable.of(config), "/proxies.json")
                .withCommand("-host=0.0.0.0", "-config=/proxies.json").waitingFor(Wait.forHttp("/proxies/mongo").forPort(8474));
    }

    @Bean
    MongoConnectionDetails proxiedMongoDetails(GenericContainer<?> auditProxy) {
        // Prevent replica-set discovery from bypassing the fault injector.
        return () -> new ConnectionString("mongodb://%s:%d/audit?directConnection=true&w=majority&serverSelectionTimeoutMS=4000"
                .formatted(auditProxy.getHost(), auditProxy.getMappedPort(PROXY_PORT)));
    }

    @Bean
    @ServiceConnection
    KafkaContainer auditBroker() {
        return new KafkaContainer(System.getProperty("wallet.test.kafka.image", "apache/kafka-native:4.0.0"));
    }
}
