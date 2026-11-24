package com.playtomic.tests.common.contracts.dtos;

public interface IAccessTokenDto
        extends IDto {

    String getTokenType();

    String getIssuer();

    String getTokenVersion();

    String getToken();

    String getRefreshToken();

    Long getCreatedAt();

    Long getExpiresIn();

    String getScope();
}