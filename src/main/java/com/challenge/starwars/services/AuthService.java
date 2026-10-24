package com.challenge.starwars.services;

import com.challenge.starwars.dtos.auth.LoginRequest;
import com.challenge.starwars.dtos.auth.RefreshTokenRequest;
import com.challenge.starwars.dtos.auth.TokenResponse;
import com.challenge.starwars.entities.User;
import com.challenge.starwars.repositories.UserRepository;
import com.challenge.starwars.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Autentica credenciales mediante Spring Security y emite el access token junto con un refresh token rotativo.
 *
 * <p>El resource server de Spring Security solo valida tokens; emitirlos es tarea de esta aplicación.
 * Una autenticación fallida aparece como {@code AuthenticationException}, traducida a un 401 genérico.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;

    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(User.normalize(request.username()),
                        request.password()));
        User user = users.findByUsername(authentication.getName()).orElseThrow();
        return toTokenResponse(refreshTokenService.startSession(user));
    }

    /**
     * Intercambia un refresh token por un nuevo par. Cualquier problema con el token es un 401 genérico.
     */
    public TokenResponse refresh(RefreshTokenRequest request) {
        return toTokenResponse(refreshTokenService.rotate(request.refreshToken()));
    }

    /**
     * Termina la sesión a la que pertenece el token. El access token ya emitido sigue siendo válido hasta que expire.
     */
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse toTokenResponse(RefreshTokenService.IssuedRefreshToken issued) {
        Instant now = clock.instant();
        // Se redondea hacia arriba: los pocos milisegundos transcurridos desde la creación del token no deben verse como un segundo perdido.
        long refreshExpiresIn = Math.max(0, Math.ceilDiv(Duration.between(now, issued.expiresAt()).toMillis(), 1000));
        return signAccessToken(issued.username(), issued.token(), refreshExpiresIn);
    }

    private TokenResponse signAccessToken(String subject, String refreshToken, long refreshExpiresIn) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .audience(List.of(jwtProperties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofSeconds(jwtProperties.ttlSeconds())))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, TokenResponse.BEARER, jwtProperties.ttlSeconds(), refreshToken,
                refreshExpiresIn);
    }
}
