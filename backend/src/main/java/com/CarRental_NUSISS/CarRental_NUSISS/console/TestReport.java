package com.CarRental_NUSISS.CarRental_NUSISS.console;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every check the smoke suite runs and writes it out as a shareable report:
 * a styled, self-contained HTML file and a PDF. Both list each test performed, in order,
 * with its result - so the run can be handed to someone who is not going to sit and watch
 * the console scroll past.
 */
final class TestReport {

	/** One assertion: which section it belonged to, what it claimed, and how it went. */
	record Check(String section, String name, String detail, boolean passed) {
	}

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final String title;
	private final Map<String, String> facts;
	private final LocalDateTime startedAt = LocalDateTime.now();
	private final List<Check> checks = new ArrayList<>();

	private String currentSection = "General";
	private Duration duration = Duration.ZERO;

	TestReport(String title, Map<String, String> facts) {
		this.title = title;
		this.facts = new LinkedHashMap<>(facts);
	}

	void section(String name) {
		this.currentSection = name;
	}

	void record(String name, String detail, boolean passed) {
		checks.add(new Check(currentSection, name, detail, passed));
	}

	void finished(Duration elapsed) {
		this.duration = elapsed;
	}

	int passed() {
		return (int) checks.stream().filter(Check::passed).count();
	}

	int failed() {
		return checks.size() - passed();
	}

	/**
	 * Writes {@code smoke-test-report.html} and {@code smoke-test-report.pdf} into
	 * {@code dir}, plus a timestamped copy of each so earlier runs are not overwritten.
	 *
	 * @return the files written, in the order written
	 */
	List<Path> write(Path dir) throws IOException {
		Files.createDirectories(dir);
		String stamp = startedAt.format(FILE_STAMP);
		byte[] html = renderHtml().getBytes(StandardCharsets.UTF_8);
		byte[] pdf = renderPdf();

		List<Path> written = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : Map.of(
				"smoke-test-report.html", html,
				"smoke-test-report.pdf", pdf).entrySet()) {
			written.add(Files.write(dir.resolve(file.getKey()), file.getValue()));
		}
		written.add(Files.write(dir.resolve("smoke-test-report-" + stamp + ".html"), html));
		written.add(Files.write(dir.resolve("smoke-test-report-" + stamp + ".pdf"), pdf));
		written.sort(null);
		return written;
	}

	private Map<String, List<Check>> bySection() {
		Map<String, List<Check>> grouped = new LinkedHashMap<>();
		for (Check check : checks) {
			grouped.computeIfAbsent(check.section(), k -> new ArrayList<>()).add(check);
		}
		return grouped;
	}

	private String verdict() {
		return failed() == 0
				? "All %d checks passed".formatted(passed())
				: "%d passed, %d FAILED".formatted(passed(), failed());
	}

	private String elapsed() {
		return "%.1fs".formatted(duration.toMillis() / 1000.0);
	}

	// ---------------------------------------------------------------- HTML

	private String renderHtml() {
		StringBuilder out = new StringBuilder(64 * 1024);
		boolean green = failed() == 0;

		out.append("""
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>""").append(escapeHtml(title)).append("""
				</title>
				<style>
				  :root { --ok: #0d7a38; --bad: #b81d1d; --ink: #16181d; --muted: #6b7280;
				          --line: #e4e6eb; --bg: #f6f7f9; }
				  * { box-sizing: border-box; }
				  body { margin: 0; padding: 32px 20px 64px; background: var(--bg); color: var(--ink);
				         font: 15px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
				  main { max-width: 940px; margin: 0 auto; }
				  h1 { font-size: 22px; margin: 0 0 4px; }
				  .sub { color: var(--muted); margin: 0 0 20px; font-size: 13px; }
				  .card { background: #fff; border: 1px solid var(--line); border-radius: 10px;
				          padding: 20px 22px; margin-bottom: 18px; }
				  .verdict { display: inline-block; font-weight: 700; font-size: 15px; padding: 6px 14px;
				             border-radius: 999px; color: #fff; background: var(--ok); }
				  .verdict.bad { background: var(--bad); }
				  dl.facts { display: grid; grid-template-columns: max-content 1fr; gap: 4px 18px;
				             margin: 18px 0 0; font-size: 13px; }
				  dl.facts dt { color: var(--muted); }
				  dl.facts dd { margin: 0; font-family: ui-monospace, Consolas, monospace; }
				  h2 { font-size: 16px; margin: 0 0 12px; display: flex; align-items: baseline; gap: 10px; }
				  h2 .tally { font-size: 12px; font-weight: 500; color: var(--muted);
				              font-family: ui-monospace, Consolas, monospace; }
				  table { width: 100%; border-collapse: collapse; }
				  tr { border-top: 1px solid var(--line); }
				  tr:first-child { border-top: 0; }
				  td { padding: 5px 6px; vertical-align: top; }
				  td.num { color: #b6bac2; width: 34px; text-align: right;
				           font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
				  td.status { width: 52px; font-weight: 700; font-size: 12px; letter-spacing: .04em;
				              padding-top: 7px; font-family: ui-monospace, Consolas, monospace; }
				  tr.ok td.status { color: var(--ok); }
				  tr.bad td.status { color: var(--bad); }
				  tr.bad td.name { font-weight: 600; }
				  td.detail { color: var(--muted); font-size: 12.5px; width: 30%;
				              font-family: ui-monospace, Consolas, monospace; word-break: break-word; }
				  ul.failures { margin: 12px 0 0; padding-left: 20px; color: var(--bad); font-size: 13.5px; }
				  footer { color: var(--muted); font-size: 12px; text-align: center; margin-top: 26px; }
				  @media print {
				    body { background: #fff; padding: 0; font-size: 10.5pt; }
				    .card { border: 0; border-top: 1px solid var(--line); border-radius: 0;
				            padding: 12px 0; margin: 0; break-inside: avoid; }
				    @page { margin: 14mm; }
				  }
				</style>
				</head>
				<body>
				<main>
				""");

		out.append("<h1>").append(escapeHtml(title)).append("</h1>\n");
		out.append("<p class=\"sub\">Every assertion the automated suite ran, in order, "
				+ "with the result.</p>\n");

		out.append("<div class=\"card\">\n<span class=\"verdict").append(green ? "" : " bad")
				.append("\">").append(escapeHtml(verdict())).append("</span>\n<dl class=\"facts\">\n");
		facts.forEach((k, v) -> out.append("  <dt>").append(escapeHtml(k)).append("</dt><dd>")
				.append(escapeHtml(v)).append("</dd>\n"));
		out.append("  <dt>Started</dt><dd>").append(startedAt.format(STAMP)).append("</dd>\n");
		out.append("  <dt>Duration</dt><dd>").append(elapsed()).append("</dd>\n");
		out.append("</dl>\n");

		if (failed() > 0) {
			out.append("<ul class=\"failures\">\n");
			for (Check check : checks) {
				if (!check.passed()) {
					out.append("  <li>").append(escapeHtml(check.section())).append(" &mdash; ")
							.append(escapeHtml(check.name()))
							.append(check.detail() == null ? ""
									: " <em>(" + escapeHtml(check.detail()) + ")</em>")
							.append("</li>\n");
				}
			}
			out.append("</ul>\n");
		}
		out.append("</div>\n");

		int number = 0;
		int index = 0;
		for (Map.Entry<String, List<Check>> section : bySection().entrySet()) {
			List<Check> rows = section.getValue();
			long ok = rows.stream().filter(Check::passed).count();
			out.append("<div class=\"card\">\n<h2>").append(++index).append(". ")
					.append(escapeHtml(section.getKey()))
					.append("<span class=\"tally\">").append(ok).append("/").append(rows.size())
					.append(ok == rows.size() ? " passed" : " passed, " + (rows.size() - ok) + " failed")
					.append("</span></h2>\n<table>\n");
			for (Check check : rows) {
				out.append("<tr class=\"").append(check.passed() ? "ok" : "bad")
						.append("\"><td class=\"num\">").append(++number)
						.append("</td><td class=\"status\">").append(check.passed() ? "ok" : "FAIL")
						.append("</td><td class=\"name\">").append(escapeHtml(check.name()))
						.append("</td><td class=\"detail\">")
						.append(check.detail() == null ? "" : escapeHtml(check.detail()))
						.append("</td></tr>\n");
			}
			out.append("</table>\n</div>\n");
		}

		out.append("<footer>Generated by the CarRental-NUSISS console harness "
				+ "(<code>--smoke</code>). Services are called directly; no HTTP layer.</footer>\n");
		out.append("</main>\n</body>\n</html>\n");
		return out.toString();
	}

	private static String escapeHtml(String raw) {
		return raw == null ? "" : raw.replace("&", "&amp;").replace("<", "&lt;")
				.replace(">", "&gt;").replace("\"", "&quot;");
	}

	// ---------------------------------------------------------------- PDF

	private byte[] renderPdf() {
		PdfDocument pdf = new PdfDocument();

		pdf.text(title, PdfDocument.Font.HELVETICA_BOLD, 16f, PdfDocument.BLACK);
		pdf.gap(4f);
		pdf.text("Every assertion the automated suite ran, in order, with the result.",
				PdfDocument.Font.HELVETICA, 9.5f, PdfDocument.GREY);
		pdf.gap(8f);
		pdf.rule();
		pdf.text(verdict(), PdfDocument.Font.HELVETICA_BOLD, 13f,
				failed() == 0 ? PdfDocument.GREEN : PdfDocument.RED);
		pdf.gap(6f);

		facts.forEach((k, v) -> pdf.text("%-14s %s".formatted(k, v),
				PdfDocument.Font.COURIER, 8.5f, PdfDocument.GREY));
		pdf.text("%-14s %s".formatted("Started", startedAt.format(STAMP)),
				PdfDocument.Font.COURIER, 8.5f, PdfDocument.GREY);
		pdf.text("%-14s %s".formatted("Duration", elapsed()),
				PdfDocument.Font.COURIER, 8.5f, PdfDocument.GREY);

		if (failed() > 0) {
			pdf.gap(10f);
			pdf.text("Failures", PdfDocument.Font.HELVETICA_BOLD, 11f, PdfDocument.RED);
			pdf.gap(3f);
			for (Check check : checks) {
				if (!check.passed()) {
					pdf.tokenLine("FAIL", PdfDocument.RED,
							check.section() + " - " + check.name()
									+ (check.detail() == null ? "" : " (" + check.detail() + ")"),
							PdfDocument.BLACK, 8.5f, 0f, 0);
				}
			}
		}

		int number = 0;
		int index = 0;
		for (Map.Entry<String, List<Check>> section : bySection().entrySet()) {
			List<Check> rows = section.getValue();
			long ok = rows.stream().filter(Check::passed).count();
			// Keep the heading with the first few of its rows; a fresh page needs no rule.
			if (!pdf.reserve(100f)) {
				pdf.gap(14f);
				pdf.rule();
			}
			pdf.text("%d. %s".formatted(++index, section.getKey()),
					PdfDocument.Font.HELVETICA_BOLD, 11.5f, PdfDocument.BLACK);
			pdf.text(ok == rows.size() ? "%d/%d passed".formatted(ok, rows.size())
					: "%d/%d passed, %d failed".formatted(ok, rows.size(), rows.size() - ok),
					PdfDocument.Font.HELVETICA, 8.5f, PdfDocument.GREY);
			pdf.gap(4f);
			for (Check check : rows) {
				String body = "%3d  %s".formatted(++number, check.name())
						+ (check.detail() == null ? "" : "   [" + check.detail() + "]");
				// 5 = width of the "%3d  " number column, so wrapped text lines up under the name
				pdf.tokenLine(check.passed() ? "ok" : "FAIL",
						check.passed() ? PdfDocument.GREEN : PdfDocument.RED,
						body, check.passed() ? PdfDocument.BLACK : PdfDocument.RED, 8.5f, 6f, 5);
			}
		}

		pdf.addFooters();
		return pdf.toBytes();
	}
}
