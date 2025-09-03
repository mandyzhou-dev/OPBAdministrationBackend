package ca.openbox.user.configuration;

import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:8081");
        configuration.addAllowedMethod("*");
        configuration.setAllowedMethods(Arrays.asList("POST","GET", "PUT", "DELETE", "OPTIONS"));
        configuration.addAllowedOriginPattern("*");
        configuration.setAllowCredentials(true);
        configuration.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(HttpMethod.PUT,"/shift/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/user/**").permitAll()
                                .requestMatchers(HttpMethod.GET,"/user/**").permitAll()
                                .requestMatchers(HttpMethod.PUT,"/process/**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/process/**").permitAll()
                                .requestMatchers(HttpMethod.DELETE,"/process/**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/announcement/**").permitAll()
                                .requestMatchers(HttpMethod.DELETE,"/announcement/**").permitAll()
                                .requestMatchers(HttpMethod.PUT,"/announcement/**").permitAll()
                                .requestMatchers(HttpMethod.PUT,"/announcement**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/announcement**").permitAll()
                                .requestMatchers(HttpMethod.DELETE,"/announcement**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/user/send_code**").permitAll()
                                .requestMatchers(HttpMethod.POST,"register**").permitAll()
                                .requestMatchers(HttpMethod.PUT,"/regulation/**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/employment/**").permitAll()
                                .requestMatchers(HttpMethod.GET,"/employment/**").permitAll()
                                .requestMatchers(HttpMethod.POST,"/resignation/**").permitAll()

                ).csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }
    @Bean
    PasswordEncoder passwordEncoder(){
        String idForEncode = "bcrypt";
        Map encoders = new HashMap<>();
        encoders.put(idForEncode,new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder){
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }
}
