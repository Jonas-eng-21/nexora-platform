package br.com.nexora.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		return http
				// API stateless autenticada por token no header: não há sessão/cookie para o CSRF explorar.
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				// 401/403 gerados nos filtros passam pelo GlobalExceptionHandler, mantendo um único contrato de erro.
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((request, response, ex) ->
								exceptionResolver.resolveException(request, response, null, ex))
						.accessDeniedHandler((request, response, ex) ->
								exceptionResolver.resolveException(request, response, null, ex)))
				.build();
	}
}
