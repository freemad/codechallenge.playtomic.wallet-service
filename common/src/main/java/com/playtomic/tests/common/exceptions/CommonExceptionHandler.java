package com.playtomic.tests.common.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import java.util.Map;

//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.oauth2.jwt.JwtException;

@Slf4j
@ControllerAdvice
//@RequiredArgsConstructor
public class CommonExceptionHandler
        extends AbstractErrorWebExceptionHandler {

    private static final HttpStatus DEFAULT_ERROR_HTTP_STATUS = HttpStatus.OK;

    /**
     * Create a new {@code AbstractErrorWebExceptionHandler}.
     *
     * @param errorAttributes    the error attributes
     * @param webProperties      the web configuration properties
     * @param applicationContext the application context
     */
    public CommonExceptionHandler(ErrorAttributes errorAttributes, WebProperties webProperties,
                                  ApplicationContext applicationContext, ServerCodecConfigurer codecs,
                                  ObjectProvider<ViewResolver> viewResolvers) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageReaders(codecs.getReaders());
        setMessageWriters(codecs.getWriters());
        setViewResolvers(viewResolvers.orderedStream().toList());
    }

//    @ResponseStatus(HttpStatus.OK)
//    @ExceptionHandler({
//            BusinessException.class,
//            NullPointerException.class,
////            AuthenticationException.class,
////            JwtException.class,
//            IllegalArgumentException.class,
//            IllegalStateException.class,
//            RuntimeException.class,
//            Exception.class,
//    })
//    public ResponseEntity<Object> handleInternal(final Throwable ex, final WebRequest request) {
//        Assert.notNull(ex, "throwable must not be null");
//        boolean debugMode = commonProps.getDevMode().getDebugMode();
//        if (Boolean.FALSE.equals(commonProps.getDevMode().getExpWrapperEnabled())) {
//            return getRawBadResponse((Exception) ex);
//        }
//        var businessException = switch (ex) {
//            case BusinessException bex -> bex;
//            case NullPointerException nex -> getBusinessException(nex, CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, "null pointer exception");
////            case AuthenticationException aex -> getBusinessException(aex, CommonErrorCode.AUTHENTICATION_EXCEPTION, "authentication exception");
////            case JwtException jex -> getBusinessException(jex, CommonErrorCode.AUTHENTICATION_EXCEPTION, "jwt exception");
//            case IllegalArgumentException iaex -> getBusinessException(iaex, CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, "illegal argument exception");
//            case IllegalStateException isex -> getBusinessException(isex, CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, "illegal state exception");
//            case RuntimeException rex -> getBusinessException(rex, CommonErrorCode.UNKNOWN_EXCEPTION, "unknown runtime exception");
//            case Exception exp -> getBusinessException(exp, CommonErrorCode.UNKNOWN_EXCEPTION, "unknown exception");
//            default -> getBusinessException((Exception) ex, CommonErrorCode.UNKNOWN_EXCEPTION, "unknown exception");
//        };
//
//        return ResponseEntity
//                .status(DEFAULT_ERROR_HTTP_STATUS)
//                .body(ExceptionUtil.getBizExceptionResponseDto(businessException, errorMapperV1, debugMode));
//    }
//
//    @Override
//    protected ResponseEntity<Object> handleMethodArgumentNotValid(
//            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
//        Assert.notNull(ex, "methodArgumentNotValidException must not be null");
//        boolean debugMode = commonProps.getDevMode().getDebugMode();
//        var errorMsg = getMethodArgumentNotValidErrorMsg(ex);
//        BusinessException bizExp = getBusinessException(ex, CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, errorMsg);
//        return ResponseEntity
//                .status(DEFAULT_ERROR_HTTP_STATUS)
//                .body(ExceptionUtil.getBizExceptionResponseDto(bizExp, errorMapperV1, debugMode));
//    }
//
//    private static String getMethodArgumentNotValidErrorMsg(MethodArgumentNotValidException ex) {
//        Map<String, String> errors = new HashMap<>();
//        ex.getBindingResult().getFieldErrors().forEach(error -> {
//            String fieldName = error.getField();
//            String errorMessage = error.getDefaultMessage();
//            errors.put(fieldName, errorMessage);
//        });
//        return errors.entrySet().stream()
//                .map(entry -> entry.getKey() + ": " + entry.getValue())
//                .collect(Collectors.joining(" | "));
//    }
//
//    private static ResponseEntity<Object> getRawBadResponse(Exception ex) {
//        return ResponseEntity
//                .status(HttpStatus.BAD_REQUEST)
//                .body(ex);
//    }
//
//    private static BusinessException getBusinessException(Exception ex, IErrorCode errorCode, String message) {
//        return new BusinessException(errorCode, message, ex);
//    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(
                RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(
            ServerRequest request) {

        Map<String, Object> errorPropertiesMap = getErrorAttributes(request,
                ErrorAttributeOptions.defaults());

        return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(errorPropertiesMap));
    }
}
