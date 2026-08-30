package com.CarRental_NUSISS.CarRental_NUSISS.console;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately small PDF writer: enough to lay out a text report (three standard
 * fonts, colour, rules, automatic page breaks) and nothing more. It exists so the
 * harness can emit a shareable PDF without pulling iText/PDFBox into the build.
 *
 * <p>Only the 14 standard Type1 fonts are used, so no font data has to be embedded, and
 * text is written in WinAnsi (Latin-1) - anything outside that range is replaced.
 */
final class PdfDocument {

	/** The three fonts declared in every page's resource dictionary. */
	enum Font {

		HELVETICA("/F1", "Helvetica", 0.55f),
		HELVETICA_BOLD("/F2", "Helvetica-Bold", 0.58f),
		COURIER("/F3", "Courier", 0.60f);

		private final String resource;
		private final String baseFont;
		/** Average glyph width as a fraction of the font size; exact for Courier. */
		private final float widthFactor;

		Font(String resource, String baseFont, float widthFactor) {
			this.resource = resource;
			this.baseFont = baseFont;
			this.widthFactor = widthFactor;
		}
	}

	static final float[] BLACK = { 0f, 0f, 0f };
	static final float[] GREY = { 0.42f, 0.42f, 0.45f };
	static final float[] GREEN = { 0.05f, 0.48f, 0.22f };
	static final float[] RED = { 0.72f, 0.11f, 0.11f };

	private static final float PAGE_WIDTH = 595f;
	private static final float PAGE_HEIGHT = 842f;
	private static final float MARGIN_X = 48f;
	private static final float MARGIN_TOP = 56f;
	private static final float MARGIN_BOTTOM = 52f;
	private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X;

	private final List<List<String>> pages = new ArrayList<>();
	private List<String> current;
	private float y;

	PdfDocument() {
		newPage();
	}

	void newPage() {
		current = new ArrayList<>();
		pages.add(current);
		y = PAGE_HEIGHT - MARGIN_TOP;
	}

	void gap(float points) {
		y -= points;
	}

	/**
	 * Breaks to a new page unless {@code needed} points are still free, so a heading is
	 * never stranded at the foot of a page without the rows it belongs to.
	 *
	 * @return true if a page break happened (in which case no separator is wanted)
	 */
	boolean reserve(float needed) {
		if (y - needed < MARGIN_BOTTOM) {
			newPage();
			return true;
		}
		return false;
	}

	/** Horizontal rule across the text column. */
	void rule() {
		ensureRoom(6f);
		current.add("0.80 0.80 0.82 RG 0.6 w %.1f %.1f m %.1f %.1f l S"
				.formatted(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y));
		y -= 8f;
	}

	/**
	 * Writes one logical line, wrapped to the text column and continued with a hanging
	 * indent. Breaks to a new page when the current one runs out.
	 */
	void text(String line, Font font, float size, float[] colour, float indent, float hangingIndent) {
		float leading = size * 1.32f;
		float firstWidth = CONTENT_WIDTH - indent;
		float restWidth = CONTENT_WIDTH - indent - hangingIndent;
		boolean first = true;

		// Fold to WinAnsi before wrapping: "..." for an ellipsis is three glyphs, not one,
		// and measuring the original would let the line run past the right margin.
		for (String chunk : wrap(toWinAnsi(line), font, size, firstWidth, restWidth)) {
			ensureRoom(leading);
			float x = MARGIN_X + indent + (first ? 0f : hangingIndent);
			current.add(fragment(chunk, font, size, colour, x, y));
			y -= leading;
			first = false;
		}
	}

	void text(String line, Font font, float size, float[] colour) {
		text(line, font, size, colour, 0f, 0f);
	}

	/**
	 * A monospaced row whose leading token ("ok" / "FAIL") is coloured independently of the
	 * text beside it. The token shares the first baseline with the body; wrapped body lines
	 * are indented past the token gutter.
	 */
	void tokenLine(String token, float[] tokenColour, String body, float[] bodyColour,
			float size, float indent, int hangingChars) {
		float charWidth = Font.COURIER.widthFactor * size;
		float gutter = 6 * charWidth;

		ensureRoom(size * 1.32f);
		current.add(fragment(token, Font.COURIER, size, tokenColour, MARGIN_X + indent, y));
		text(body, Font.COURIER, size, bodyColour, indent + gutter, hangingChars * charWidth);
	}

	/** Stamps "Page n of m" on every page. Call once, after all content is laid out. */
	void addFooters() {
		for (int i = 0; i < pages.size(); i++) {
			String label = "Page %d of %d".formatted(i + 1, pages.size());
			float width = label.length() * Font.HELVETICA.widthFactor * 8f;
			pages.get(i).add(fragment(label, Font.HELVETICA, 8f, GREY,
					PAGE_WIDTH - MARGIN_X - width, MARGIN_BOTTOM - 20f));
		}
	}

	/**
	 * One positioned, coloured run of text as a content-stream fragment. The WinAnsi fold is
	 * repeated here (it is idempotent) so callers that bypass {@link #text} are covered too.
	 */
	private static String fragment(String text, Font font, float size, float[] colour, float x, float y) {
		return "BT %s %.1f Tf %.3f %.3f %.3f rg %.1f %.1f Td (%s) Tj ET"
				.formatted(font.resource, size, colour[0], colour[1], colour[2], x, y,
						escape(toWinAnsi(text)));
	}

	private void ensureRoom(float needed) {
		if (y - needed < MARGIN_BOTTOM) {
			newPage();
		}
	}

	private List<String> wrap(String line, Font font, float size, float firstWidth, float restWidth) {
		float charWidth = font.widthFactor * size;
		int firstLimit = Math.max(8, (int) (firstWidth / charWidth));
		int restLimit = Math.max(8, (int) (restWidth / charWidth));

		List<String> out = new ArrayList<>();
		String remaining = line;
		int limit = firstLimit;
		while (remaining.length() > limit) {
			int cut = remaining.lastIndexOf(' ', limit);
			if (cut <= 0) {
				cut = limit;
			}
			out.add(remaining.substring(0, cut).stripTrailing());
			remaining = remaining.substring(cut).stripLeading();
			limit = restLimit;
		}
		out.add(remaining);
		return out;
	}

	/**
	 * Folds the typography WinAnsi cannot represent to an ASCII equivalent rather than losing
	 * it. Applied before measuring, since some replacements are longer than one character.
	 */
	private static String toWinAnsi(String raw) {
		StringBuilder sb = new StringBuilder(raw.length() + 8);
		for (char c : raw.toCharArray()) {
			switch (c) {
				case '–', '—' -> sb.append('-');
				case '‘', '’' -> sb.append('\'');
				case '“', '”' -> sb.append('"');
				case '…' -> sb.append("...");
				case '→' -> sb.append("->");
				case '\t' -> sb.append("    ");
				default -> sb.append(c > 0xFF || c < 0x20 ? '?' : c);
			}
		}
		return sb.toString();
	}

	/** PDF string literals need \, ( and ) escaped. Length in glyphs is unchanged. */
	private static String escape(String raw) {
		return raw.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
	}

	// ---------------------------------------------------------------- serialisation

	/**
	 * Objects: 1 catalog, 2 page tree, 3-5 fonts, then a page and a content stream per
	 * page. The xref table needs each object's exact byte offset, so bodies are appended
	 * one at a time and the offset recorded as we go.
	 */
	byte[] toBytes() {
		List<String> objects = new ArrayList<>();
		int firstPageObject = 6;

		StringBuilder kids = new StringBuilder();
		for (int i = 0; i < pages.size(); i++) {
			kids.append(i == 0 ? "" : " ").append(firstPageObject + 2 * i).append(" 0 R");
		}

		objects.add("<</Type/Catalog/Pages 2 0 R>>");
		objects.add("<</Type/Pages/Kids[%s]/Count %d>>".formatted(kids, pages.size()));
		for (Font font : Font.values()) {
			objects.add("<</Type/Font/Subtype/Type1/BaseFont/%s/Encoding/WinAnsiEncoding>>"
					.formatted(font.baseFont));
		}

		for (int i = 0; i < pages.size(); i++) {
			String content = String.join("\n", pages.get(i));
			objects.add(("<</Type/Page/Parent 2 0 R/MediaBox[0 0 %.0f %.0f]"
					+ "/Resources<</Font<</F1 3 0 R/F2 4 0 R/F3 5 0 R>>>>/Contents %d 0 R>>")
					.formatted(PAGE_WIDTH, PAGE_HEIGHT, firstPageObject + 2 * i + 1));
			objects.add("<</Length %d>>\nstream\n%s\nendstream"
					.formatted(content.getBytes(StandardCharsets.ISO_8859_1).length, content));
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out, "%PDF-1.4\n%âãÏÓ\n");

		int[] offsets = new int[objects.size() + 1];
		for (int i = 0; i < objects.size(); i++) {
			offsets[i + 1] = out.size();
			write(out, "%d 0 obj\n%s\nendobj\n".formatted(i + 1, objects.get(i)));
		}

		int xref = out.size();
		write(out, "xref\n0 %d\n".formatted(objects.size() + 1));
		write(out, "0000000000 65535 f \n");
		for (int i = 1; i <= objects.size(); i++) {
			write(out, "%010d 00000 n \n".formatted(offsets[i]));
		}
		write(out, "trailer\n<</Size %d/Root 1 0 R>>\nstartxref\n%d\n%%%%EOF\n"
				.formatted(objects.size() + 1, xref));
		return out.toByteArray();
	}

	private static void write(ByteArrayOutputStream out, String s) {
		out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
	}
}
