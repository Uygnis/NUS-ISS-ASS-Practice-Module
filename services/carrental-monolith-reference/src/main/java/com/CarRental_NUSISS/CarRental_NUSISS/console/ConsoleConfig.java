package com.CarRental_NUSISS.CarRental_NUSISS.console;

import com.CarRental_NUSISS.CarRental_NUSISS.CarRentalNusissApplication;
import com.CarRental_NUSISS.CarRental_NUSISS.JwtAuthFilter;
import com.CarRental_NUSISS.CarRental_NUSISS.SecurityConfig;
import com.CarRental_NUSISS.CarRental_NUSISS.UserDetailsServiceImpl;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring wiring for the headless console harness: the real repositories, services,
 * seeder and JWT/BCrypt beans, but no web layer at all.
 *
 * <p>Everything under the application package is scanned except the pieces that only
 * make sense inside an HTTP request: {@link SecurityConfig} (needs {@code HttpSecurity}
 * and a servlet filter chain), {@link JwtAuthFilter}, the {@code @RestController}s and
 * the {@code @RestControllerAdvice}. The two beans SecurityConfig would otherwise have
 * contributed - the password encoder and an {@link AuthenticationManager} - are declared
 * here with the same implementations, so {@code AuthService.login} still goes through the
 * real {@code DaoAuthenticationProvider} + BCrypt + {@link UserDetailsServiceImpl} path.
 *
 * <p>Guarded by the {@code console} profile so that the normal
 * {@link CarRentalNusissApplication} boot never picks this class up and never sees
 * duplicate {@code PasswordEncoder} / {@code AuthenticationManager} definitions.
 *
 * <p>{@code @EntityScan}/{@code @EnableJpaRepositories} are needed because Boot derives
 * both from the package of the class carrying {@code @EnableAutoConfiguration} - which
 * here is this subpackage, not the package holding the entities and repositories.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CarRentalNusissApplication.class)
@EnableJpaRepositories(basePackageClasses = CarRentalNusissApplication.class)
@Profile(ConsoleConfig.PROFILE)
@ComponentScan(
		basePackageClasses = CarRentalNusissApplication.class,
		excludeFilters = {
				@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
						CarRentalNusissApplication.class, ConsoleConfig.class,
						SecurityConfig.class, JwtAuthFilter.class }),
				@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = {
						RestController.class, RestControllerAdvice.class })
		})
public class ConsoleConfig {

	/** Profile activated only by {@link CarRentalConsoleApp} in standalone (no-web) mode. */
	public static final String PROFILE = "console";

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * The non-web equivalent of what {@code AuthenticationConfiguration} builds for the
	 * web app: one {@code DaoAuthenticationProvider} over the real UserDetailsService.
	 */
	@Bean
	public AuthenticationManager authenticationManager(UserDetailsServiceImpl userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}
}
