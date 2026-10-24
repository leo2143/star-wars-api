package com.challenge.starwars.security;

import com.challenge.starwars.security.ratelimit.RateLimitFilter;
import com.challenge.starwars.security.ratelimit.RateLimitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Seguridad REST sin estado: tokens JWT bearer validados por el soporte de resource server de Spring Security.
 *
 * <p>Superficie pública: {@code POST /api/v1/auth/{login,register,refresh,logout}} (el refresh token es la credencial de
 * los dos últimos), el documento OpenAPI y Swagger UI (solo
 * documentación, sin datos), y {@code GET /actuator/health}. Todo lo demás, incluidas las rutas desconocidas, requiere
 * un token válido. La protección CSRF está deshabilitada porque nunca se usa una cookie ni una sesión para autenticar.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    // CSRF solo protege credenciales que el navegador envía automáticamente (cookies, sesión); un token bearer nunca se envía así.
    @SuppressWarnings("java:S4502")
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler,
                                            RateLimitProperties rateLimits, JsonMapper jsonMapper) throws Exception {
        http
                // Se crea aquí y no como bean, para que el contenedor de servlets no lo registre una segunda vez.
                // Se ejecuta después de CorsFilter y antes de la autenticación.
                .addFilterBefore(new RateLimitFilter(rateLimits, jsonMapper), BearerTokenAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/loggers", "/actuator/loggers/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
