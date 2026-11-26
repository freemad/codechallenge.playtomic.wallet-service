package com.playtomic.tests.wallet.configs;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.playtomic.tests.wallet.enums.WalletOperationMongoStatus;
import com.playtomic.tests.wallet.enums.WalletOperationMongoType;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.ConverterBuilder;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
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

    @Bean
    MongoCustomConversions walletMongoConversions() {
        return MongoCustomConversions.create(adapter -> {
            adapter.registerConverters(ConverterBuilder.writing(WalletOperationMongoType.class, Integer.class,
                            type -> WalletOperationMongoType.validate(type).getValue())
                    .andReading(code -> WalletOperationMongoType.validate(WalletOperationMongoType.ofValue(code))).getConverters());
            adapter.registerConverters(ConverterBuilder.writing(WalletOperationMongoStatus.class, Integer.class,
                            status -> WalletOperationMongoStatus.validate(status).getValue())
                    .andReading(code -> WalletOperationMongoStatus.validate(WalletOperationMongoStatus.ofValue(code))).getConverters());
        });
    }
}