package com.challenge.starwars.controllers;

import com.challenge.starwars.config.OpenApiConfig;
import com.challenge.starwars.dtos.auth.*;
import com.challenge.starwars.services.AuthService;
import com.challenge.starwars.services.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/{version}/auth", version = "v1")
@Tag(name = "Authentication", description = "Create an account and obtain the JWT needed by every other endpoint")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Log in and obtain a Bearer token", description = "Verifies the demo user credentials and returns a signed JWT (HS256).")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Missing or malformed credentials", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials (generic message)", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new token pair", description = "The refresh token is single use: it is replaced by the one in the response. The session " + "keeps the expiry it had at login. Presenting a token that was already used revokes the whole " + "session. Unknown, expired, revoked and reused tokens get the same generic 401.")
    @ApiResponse(responseCode = "200", description = "New access token and refresh token")
    @ApiResponse(responseCode = "400", description = "Missing or oversized refresh token", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "401", description = "Invalid refresh token (generic message)", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "429", description = "Too many refresh attempts from this address", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "End the session of a refresh token", description = "Revokes every refresh token of the session. Always answers 204, whether or not the token " + "existed. The access token already issued stays valid until it expires.")
    @ApiResponse(responseCode = "204", description = "Session ended")
    @ApiResponse(responseCode = "400", description = "Missing or oversized refresh token", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Create an account", description = "Usernames are 3 to 50 characters (letters, digits, '.', '_', '-') and are stored in lower " + "case. Passwords are 15 to 64 characters and at most 72 bytes in UTF-8; no composition rules. " + "Registration does not log in: call the login endpoint afterwards.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Invalid username or password", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "409", description = "Username is not available", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    @ApiResponse(responseCode = "429", description = "Too many registration attempts from this address", content = @Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = @Schema(ref = OpenApiConfig.PROBLEM_SCHEMA_REF)))
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
