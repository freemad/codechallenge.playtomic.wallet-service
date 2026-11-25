package com.playtomic.tests.wallet.configs;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration(proxyBeanMethods = false)
class WalletMongoConfiguration {

    @Bean
    MongoClientSettingsBuilderCustomizer walletMongoDurability() {
        return builder -> builder.readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.MAJORITY).writeConcern(WriteConcern.MAJORITY);
    }

    @Bean
    ReactiveMongoTransactionManager walletMongoTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory, TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.SNAPSHOT)
                .writeConcern(WriteConcern.MAJORITY)
                .build());
    }

    @Bean
    TransactionalOperator walletTransactions(ReactiveMongoTransactionManager manager) {
        return TransactionalOperator.create(manager);
    }
}