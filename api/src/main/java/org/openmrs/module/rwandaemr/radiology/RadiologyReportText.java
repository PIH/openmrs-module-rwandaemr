package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.owasp.encoder.Encode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of a radiology report.  Reports entered in OpenMRS are formatted (bold, lists, headings, alignment), and are
 * kept as HTML limited to a few safe tags, wrapped in a marker element.  Reports from PACS are plain text.  Formatted
 * text is always cleaned before it is saved and before it is shown, so a report can never carry script into a page.
 */
public class RadiologyReportText {

	/**
	 * Formatted reports are stored wrapped in this element, which tells them apart from plain text reports from PACS
	 */
	public static final String FORMATTED_START = "<div class=\"radiology-report\">";
	public static final String FORMATTED_END = "</div>";

	/**
	 * The most text a report can hold, as it is stored in an obs value_text column
	 */
	public static final int MAX_LENGTH = 60000;

	private static final Pattern TEXT_ALIGN = Pattern.compile("text-align\\s*:\\s*(left|center|right|justify)",
			Pattern.CASE_INSENSITIVE);

	private static final Set<String> ALIGNMENT_CLASSES = new HashSet<String>(Arrays.asList(
			"ra-left", "ra-center", "ra-right", "ra-justify"));

	private static final String[] BLOCKS = {"p", "div", "h3", "h4", "li"};

	private RadiologyReportText() {
	}

	/**
	 * @return true if the text is a formatted report entered in OpenMRS, rather than a plain text report
	 */
	public static boolean isFormatted(String text) {
		return text != null && text.trim().startsWith(FORMATTED_START);
	}

	/**
	 * @return the report as it is stored: the formatted text from the editor, cleaned and wrapped in the marker element
	 * @throws IllegalArgumentException if it has no text, or is too long
	 */
	public static String toStorage(String editorHtml) {
		String cleaned = clean(editorHtml);
		if (StringUtils.isBlank(Jsoup.parseBodyFragment(cleaned).text())) {
			throw new IllegalArgumentException("The report cannot be empty");
		}
		String stored = FORMATTED_START + cleaned + FORMATTED_END;
		if (stored.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("The report is too long: it can have at most about " + MAX_LENGTH
					+ " characters including its formatting");
		}
		return stored;
	}

	/**
	 * @return the report as HTML that is safe to put in a page: formatted reports cleaned again, plain text reports
	 * escaped with their line breaks kept
	 */
	public static String toHtml(String text) {
		return "<div class=\"radiology-report\">" + toEditorHtml(text) + "</div>";
	}

	/**
	 * @return the content to load into the report editor, safe to put in a page
	 */
	public static String toEditorHtml(String text) {
		if (StringUtils.isBlank(text)) {
			return "";
		}
		if (isFormatted(text)) {
			String inner = text.trim();
			inner = inner.substring(FORMATTED_START.length());
			if (inner.endsWith(FORMATTED_END)) {
				inner = inner.substring(0, inner.length() - FORMATTED_END.length());
			}
			return clean(inner);
		}
		return Encode.forHtml(text).replace("\r\n", "\n").replace("\n", "<br>");
	}

	/**
	 * @return the report without formatting, with a line for each paragraph, heading and list item
	 */
	public static String toPlainText(String text) {
		if (StringUtils.isBlank(text) || !isFormatted(text)) {
			return text;
		}
		Document document = Jsoup.parseBodyFragment(toEditorHtml(text));
		document.outputSettings().prettyPrint(false);
		document.select("br").after("\\n");
		document.select("p, div, h3, h4, li").before("\\n");
		return document.body().text().replace("\\n", "\n").replaceAll("[ \\t]*\n[ \\t]*", "\n")
				.replaceAll("\n{3,}", "\n\n").trim();
	}

	/**
	 * Keeps only paragraphs, line breaks, bold, italic, underline, headings, lists and text alignment.  Alignment is
	 * kept as one of a few known classes rather than as a style, so no style can be injected.
	 */
	static String clean(String html) {
		if (StringUtils.isBlank(html)) {
			return "";
		}
		Document document = Jsoup.parseBodyFragment(html);
		document.outputSettings().prettyPrint(false);
		for (Element element : document.body().select("[style], [align]")) {
			String alignment = null;
			Matcher matcher = TEXT_ALIGN.matcher(element.attr("style"));
			if (matcher.find()) {
				alignment = matcher.group(1);
			}
			else if (element.hasAttr("align")) {
				alignment = element.attr("align");
			}
			element.removeAttr("style");
			element.removeAttr("align");
			if (alignment != null && ALIGNMENT_CLASSES.contains("ra-" + alignment.trim().toLowerCase())) {
				element.addClass("ra-" + alignment.trim().toLowerCase());
			}
		}
		Safelist safelist = new Safelist()
				.addTags("p", "div", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "h3", "h4")
				.addAttributes("p", "class")
				.addAttributes("div", "class")
				.addAttributes("h3", "class")
				.addAttributes("h4", "class")
				.addAttributes("li", "class");
		Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
		String cleaned = Jsoup.clean(document.body().html(), "", safelist, output);

		// Only the alignment classes are kept
		Document result = Jsoup.parseBodyFragment(cleaned);
		result.outputSettings().prettyPrint(false);
		for (Element element : result.body().select(StringUtils.join(BLOCKS, ", "))) {
			Set<String> classes = new HashSet<String>(element.classNames());
			classes.retainAll(ALIGNMENT_CLASSES);
			element.classNames(classes);
			if (classes.isEmpty()) {
				element.removeAttr("class");
			}
		}
		return result.body().html();
	}
}
