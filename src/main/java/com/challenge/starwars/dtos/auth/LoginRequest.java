package com.challenge.starwars.dtos.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "user name", example = "demo")
        @NotBlank @Size(max = 100) String username,
        @Schema(description = "user password", example = "your password", format = "password")
        @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
