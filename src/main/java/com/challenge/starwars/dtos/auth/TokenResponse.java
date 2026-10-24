package com.challenge.starwars.dtos.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "Signed JWT to send as 'Authorization: Bearer <token>'") String accessToken,
        @Schema(description = "Always 'Bearer'", example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900") long expiresIn,
        @Schema(description = "Opaque, single-use token to obtain a new pair at the refresh endpoint")
        String refreshToken,
        @Schema(description = "Seconds until the refresh token (and the session) expires", example = "604800")
        long refreshExpiresIn) {

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", refreshToken=***, refreshExpiresIn=" + refreshExpiresIn + "]";
    }
}
