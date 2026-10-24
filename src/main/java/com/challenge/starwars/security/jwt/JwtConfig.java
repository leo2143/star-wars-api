package com.challenge.starwars.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;

/**
 * Codificador/decodificador JWT HS256 que comparten una única clave simétrica de la configuración.
 *
 * <p>Como esta aplicación define su propio {@link JwtDecoder}, Spring Boot ignora las propiedades que construyen el
 * decodificador ({@code jwk-set-uri}, {@code issuer-uri}, {@code public-key-location}, {@code jws-algorithms} y
 * {@code audiences} bajo {@code spring.security.oauth2.resourceserver.jwt}), y ninguna admite una clave simétrica HS256;
 * por eso la firma, el algoritmo, la expiración, el emisor y la audiencia se validan explícitamente aquí. Siguen
 * vigentes {@code authorities-claim-name}, {@code authority-prefix}, {@code authorities-claim-delimiter} y
 * {@code principal-claim-name}, porque configuran el {@code JwtAuthenticationConverter} de Boot y no el decodificador.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey key = JwtSecretKeys.fromBase64(properties.secretBase64());
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = JwtSecretKeys.fromBase64(properties.secretBase64());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtAudienceValidator(properties.audience())));
        return decoder;
    }
}
