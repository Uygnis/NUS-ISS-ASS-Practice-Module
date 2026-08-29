package com.CarRental_NUSISS.CarRental_NUSISS.console;

import com.CarRental_NUSISS.CarRental_NUSISS.CarRentalNusissApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Second entry point: a text-based harness for exercising the backend without Docker,
 * without the React frontend and without the H2 web console.
 *
 * <pre>
 *   (no args)     headless: real services + embedded in-memory H2, no Tomcat, no port
 *   --with-web    boots the real {@link CarRentalNusissApplication} (REST API on :8080,
 *                 JWT security, CORS) AND drives the same beans from the menu, so the
 *                 frontend or curl can hit the API while you drive the console
 *   --smoke       run the scripted end-to-end suite, print a pass/fail report and exit
 *                 (exit code 1 if anything failed); combinable with --with-web
 *   --report-dir=DIR
 *                 where the suite writes its HTML/PDF report (default target/reports)
 *   --help        show this
 * </pre>
 *
 * Both modes call the same {@code AuthService}/{@code BookingService}/... beans the
 * controllers call, so what passes here is the production code path minus HTTP.
 */
public final class CarRentalConsoleApp {

	private static final List<String> WEB_FLAGS = List.of("--with-web", "--web", "web");
	private static final List<String> SMOKE_FLAGS = List.of("--smoke", "--smoke-test", "smoke");
	private static final List<String> HELP_FLAGS = List.of("--help", "-h", "help");
	private static final String REPORT_DIR_FLAG = "--report-dir=";
	private static final Path DEFAULT_REPORT_DIR = Path.of("target", "reports");

	private CarRentalConsoleApp() {
	}

	public static void main(String[] args) {
		List<String> flags = Arrays.stream(args).map(a -> a.toLowerCase()).toList();
		if (flags.stream().anyMatch(HELP_FLAGS::contains)) {
			printUsage();
			return;
		}

		boolean withWeb = flags.stream().anyMatch(WEB_FLAGS::contains);
		boolean smokeOnly = flags.stream().anyMatch(SMOKE_FLAGS::contains);
		Path reportDir = Arrays.stream(args)
				.filter(a -> a.startsWith(REPORT_DIR_FLAG))
				.map(a -> Path.of(a.substring(REPORT_DIR_FLAG.length())))
				.reduce((first, second) -> second)
				.orElse(DEFAULT_REPORT_DIR);

		// Anything we consumed ourselves must not reach Spring as a --property=value pair.
		String[] springArgs = Arrays.stream(args)
				.filter(a -> !WEB_FLAGS.contains(a.toLowerCase())
						&& !SMOKE_FLAGS.contains(a.toLowerCase())
						&& !a.startsWith(REPORT_DIR_FLAG))
				.toArray(String[]::new);

		SpringApplicationBuilder builder = withWeb
				? new SpringApplicationBuilder(CarRentalNusissApplication.class).web(WebApplicationType.SERVLET)
				: new SpringApplicationBuilder(ConsoleConfig.class)
						.web(WebApplicationType.NONE)
						.profiles(ConsoleConfig.PROFILE);

		int failures;
		try (ConfigurableApplicationContext context = builder.run(springArgs)) {
			ConsoleMenu menu = new ConsoleMenu(context, withWeb, reportDir);
			failures = smokeOnly ? menu.runSmokeSuite() : menu.runInteractive();
		}

		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("""

				CarRental backend console harness

				  java ... CarRentalConsoleApp [options]

				  (no options)  headless mode - real services over embedded in-memory H2.
				                No Docker, no Tomcat, no frontend, no H2 web console.
				  --with-web    also start the real Spring Boot web app (REST API on :8080)
				                so other code / the frontend can talk to the same data while
				                you drive the console menu.
				  --smoke       run the scripted end-to-end suite, print the report, exit
				                non-zero if any check failed. Good for a quick regression run.
				                Every check and its result is also written to target/reports
				                as smoke-test-report.html and smoke-test-report.pdf.
				  --report-dir=DIR
				                put those report files somewhere else.
				  --help        this message
				""");
	}
}
