package com.playtomic.tests.wallet.controllers;

import com.playtomic.tests.common.dtos.ResponseDtoV1;
import com.playtomic.tests.common.dtos.StringDtoV1;
import com.playtomic.tests.common.models.Error;
import com.playtomic.tests.common.models.Outcome;
import com.playtomic.tests.common.utils.ResponseMappingUtil;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import com.playtomic.tests.wallet.mappers.WalletResponseMapperV1;
import com.playtomic.tests.wallet.models.WalletModel;
import com.playtomic.tests.wallet.services.WalletLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("api/v1/wallets")
@RequiredArgsConstructor
public class WalletControllerV1 {
    private static final Logger log = LoggerFactory.getLogger(WalletControllerV1.class);

    private final WalletLookupService walletService;
    private final WalletResponseMapperV1 walletResponseMapper;

    @GetMapping({"/log"})
    Mono<ResponseEntity<ResponseDtoV1>> log() {
        long currentTimeMillis = System.currentTimeMillis();
        log.info("Logging at {}", currentTimeMillis);
        return Mono.fromCallable(() -> {
            var response = new ResponseDtoV1()
                    .setResult(new StringDtoV1().setString(String.valueOf(currentTimeMillis)));
            return ResponseEntity.status(HttpStatus.OK).body(response);
        });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ResponseDtoV1>> getWallet(@PathVariable("id") String id, ServerWebExchange exchange) {
        return walletService.findById(id).map(outcome -> {
            var status = lookupStatus(outcome);
            var response = toResponse(outcome, exchange);
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                response.getError().setMessage(status.getReasonPhrase());
            }
            return ResponseEntity.status(status).body(response);
        });
    }

    private ResponseDtoV1 toResponse(Outcome<WalletModel, Error> outcome, ServerWebExchange exchange) {
        return ResponseMappingUtil.toSingleResponseDtoV1(outcome, exchange.getRequest().getId(),
                walletResponseMapper::toDto);
    }

    private static HttpStatus lookupStatus(Outcome<WalletModel, Error> outcome) {
        if (!outcome.isFailure()) {
            return HttpStatus.OK;
        }
        var code = outcome.getError().getCode();
        if (code == WalletErrorCode.INVALID_WALLET_ID) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == WalletErrorCode.WALLET_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
