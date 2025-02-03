package ch.admin.bit.jeap.deploymentlog.web.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Value("${jeap.deploymentlog.read-user.username}")
    private String readUserUsername;

    @Value("${jeap.deploymentlog.read-user.password}")
    private String readUserPassword;

    @Value("${jeap.deploymentlog.write-user.username}")
    private String writeUserUsername;

    @Value("${jeap.deploymentlog.write-user.password}")
    private String writeUserPassword;

    @Bean
    @Order(100)
        // same as on the deprecated WebSecurityConfigurerAdapter
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/deployment-doc/**").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasAnyRole("deploymentlog-write", "deploymentlog-read"));

        http.authenticationManager(createApiAuthManager(http.getSharedObject(AuthenticationManagerBuilder.class)));

        return http.build();
    }

    private AuthenticationManager createApiAuthManager(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser(readUserUsername).password(readUserPassword).roles("deploymentlog-read").and()
                .withUser(writeUserUsername).password(writeUserPassword).roles("deploymentlog-write");
        return auth.build();
    }

}
