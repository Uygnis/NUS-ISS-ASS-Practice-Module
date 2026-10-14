package org.rentez.catalogservice.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * One access-log line per request, tagged with a request id.
 *
 * <p>Stdout is the whole pipeline: Fluent Bit (aws/eksctl/cluster.yaml) ships
 * every container's stdout to CloudWatch, so a line here is queryable in Logs
 * Insights with no AWS change. The id is taken from {@code X-Request-Id} when
 * the caller sent one, put in the MDC so every log line of the request carries
 * it (see logging.pattern.console), and echoed back on the response.
 *
 * <p>Runs ahead of Spring Security so rejected (401/403) requests are logged too.
 * Actuator probes are skipped - kubelet polls them every few seconds.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final String HEADER = "X-Request-Id";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
			requestId = UUID.randomUUID().toString();
		}
		MDC.put("requestId", requestId);
		response.setHeader(HEADER, requestId);
		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			long ms = (System.nanoTime() - start) / 1_000_000;
			int status = response.getStatus();
			if (status >= 500) {
				log.error("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, ms);
			}
			else if (status >= 400) {
				log.warn("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, ms);
			}
			else {
				log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, ms);
			}
			MDC.remove("requestId");
		}
	}
}
