package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RadiologyReportTextTest {

	@Test
	public void shouldKeepTheFormattingOfTheEditor() {
		String html = "<h3>Findings</h3><p><b>Liver</b> is <i>normal</i> and <u>smooth</u>.</p>"
				+ "<ul><li>No stones</li></ul><ol><li>Kidneys normal</li></ol><p>Line one<br>Line two</p>";
		assertThat(RadiologyReportText.clean(html), equalTo(html));
	}

	@Test
	public void shouldRemoveAnythingThatCouldRunInThePage() {
		String cleaned = RadiologyReportText.clean("<p onclick=\"alert(1)\">Normal<script>alert(2)</script></p>"
				+ "<img src=x onerror=alert(3)><a href=\"javascript:alert(4)\">link</a><iframe src=\"x\"></iframe>"
				+ "<p style=\"background:url(javascript:alert(5))\">Text</p>");
		assertThat(cleaned, not(containsString("script")));
		assertThat(cleaned, not(containsString("onclick")));
		assertThat(cleaned, not(containsString("onerror")));
		assertThat(cleaned, not(containsString("javascript")));
		assertThat(cleaned, not(containsString("iframe")));
		assertThat(cleaned, not(containsString("style")));
		assertThat(cleaned, containsString("Normal"));
		assertThat(cleaned, containsString("link"));
	}

	@Test
	public void shouldKeepAlignmentAsAKnownClassOnly() {
		assertThat(RadiologyReportText.clean("<p style=\"text-align: center;\">Title</p>"),
				equalTo("<p class=\"ra-center\">Title</p>"));
		assertThat(RadiologyReportText.clean("<div align=\"right\">Signed</div>"),
				equalTo("<div class=\"ra-right\">Signed</div>"));
		assertThat(RadiologyReportText.clean("<p class=\"evil ra-left\" style=\"color:red\">Text</p>"),
				equalTo("<p class=\"ra-left\">Text</p>"));
	}

	@Test
	public void shouldStoreFormattedReportsWrappedInTheMarker() {
		String stored = RadiologyReportText.toStorage("<p><b>Normal</b></p>");
		assertThat(stored, equalTo(RadiologyReportText.FORMATTED_START + "<p><b>Normal</b></p>" + RadiologyReportText.FORMATTED_END));
		assertThat(RadiologyReportText.isFormatted(stored), is(true));
		assertThat(RadiologyReportText.toEditorHtml(stored), equalTo("<p><b>Normal</b></p>"));
		assertThat(RadiologyReportText.toPlainText(stored), equalTo("Normal"));
	}

	@Test
	public void shouldRefuseAnEmptyReport() {
		assertThrows(IllegalArgumentException.class, () -> RadiologyReportText.toStorage("<p><br></p>"));
		assertThrows(IllegalArgumentException.class, () -> RadiologyReportText.toStorage("<script>alert(1)</script>"));
	}

	@Test
	public void shouldShowPlainTextReportsFromPacsEscapedWithTheirLineBreaks() {
		String pacs = "Impression:\r\nNo fracture <seen> & no effusion";
		assertThat(RadiologyReportText.isFormatted(pacs), is(false));
		assertThat(RadiologyReportText.toEditorHtml(pacs), equalTo("Impression:<br>No fracture &lt;seen&gt; &amp; no effusion"));
		assertThat(RadiologyReportText.toHtml(pacs), equalTo("<div class=\"radiology-report\">"
				+ "Impression:<br>No fracture &lt;seen&gt; &amp; no effusion</div>"));
		assertThat(RadiologyReportText.toPlainText(pacs), equalTo(pacs));
	}

	@Test
	public void shouldUndoTheEscapingOfTheLegacyUiXssFilter() {
		// What the filter makes of "<p><b>Findings</b> &amp; notes</p>" from the editor
		String submitted = "&lt;p&gt;&lt;b&gt;Findings&lt;/b&gt; &amp;amp; notes&lt;/p&gt;";
		assertThat(RadiologyReportText.toStorage(submitted), equalTo(RadiologyReportText.FORMATTED_START
				+ "<p><b>Findings</b> &amp; notes</p>" + RadiologyReportText.FORMATTED_END));
		// A script escaped by the filter is still removed once unescaped
		assertThat(RadiologyReportText.toStorage("&lt;p&gt;Normal&lt;script&gt;alert(1)&lt;/script&gt;&lt;/p&gt;"),
				equalTo(RadiologyReportText.FORMATTED_START + "<p>Normal</p>" + RadiologyReportText.FORMATTED_END));
	}

	@Test
	public void shouldRepairReportsSavedWithEscapedTags() {
		// How a report was stored before the filter's escaping was undone, once or twice
		String once = RadiologyReportText.FORMATTED_START + "ADULT CARDIAC ULTRASOUND&lt;p&gt;Findings&lt;/p&gt;&lt;p&gt;Normal&lt;/p&gt;"
				+ RadiologyReportText.FORMATTED_END;
		String twice = RadiologyReportText.FORMATTED_START + "ADULT CARDIAC ULTRASOUND&amp;lt;p&amp;gt;Findings&amp;lt;/p&amp;gt;"
				+ RadiologyReportText.FORMATTED_END;
		assertThat(RadiologyReportText.toEditorHtml(once), equalTo("ADULT CARDIAC ULTRASOUND<p>Findings</p><p>Normal</p>"));
		assertThat(RadiologyReportText.toEditorHtml(twice), equalTo("ADULT CARDIAC ULTRASOUND<p>Findings</p>"));
	}

	@Test
	public void shouldKeepTextAsTypedWhenItIsPlainText() {
		assertThat(RadiologyReportText.fromRequest("Wrong exam &amp; duplicate"), equalTo("Wrong exam & duplicate"));
		assertThat(RadiologyReportText.toEditorHtml("No fracture <seen>"), equalTo("No fracture &lt;seen&gt;"));
	}

	@Test
	public void shouldCleanAStoredReportAgainBeforeShowingIt() {
		String tampered = RadiologyReportText.FORMATTED_START + "<p>Normal</p><script>alert(1)</script>" + RadiologyReportText.FORMATTED_END;
		assertThat(RadiologyReportText.toHtml(tampered), equalTo("<div class=\"radiology-report\"><p>Normal</p></div>"));
	}
}
