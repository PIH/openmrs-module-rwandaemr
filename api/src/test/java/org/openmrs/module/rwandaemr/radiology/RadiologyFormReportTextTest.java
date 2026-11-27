package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Obs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadiologyFormReportTextTest {

	/**
	 * A part of the Diagnostic Breast Ultrasound Report Form, as it is laid out
	 */
	private static final String FORM = "<htmlform formName=\"Diagnostic Breast Ultrasound Report Form\">" +
			"<style>.bur .opts &gt; .lbl { min-width: 0; }</style>" +
			"<script type=\"text/javascript\">var x = 1;</script>" +
			"<div class=\"bur\"><h2>Diagnostic Breast Ultrasound Report Form</h2>" +
			"<fieldset class=\"enc\"><legend>Encounter / Patient Information</legend>" +
			"<div class=\"row\"><span class=\"lbl\">Date of exam *</span><encounterDate default=\"today\"/></div>" +
			"<div class=\"row\"><span class=\"lbl\">Ultrasound test</span><radiologyReportOrder/></div>" +
			"</fieldset>" +
			"<fieldset><legend>II. Reason for US</legend>" +
			"<div class=\"row opts\"><span class=\"lbl\">Indication (choose all that apply)</span><span class=\"choice-options\">" +
			"<span class=\"option-item\"><obs conceptId=\"indication\" answerConceptId=\"mass\" answerLabel=\"Breast mass\" style=\"checkbox\"/></span>" +
			"<span class=\"option-item\"><obs conceptId=\"indication\" answerConceptId=\"pain\" answerLabel=\"Focal pain\" style=\"checkbox\"/></span>" +
			"<span class=\"option-item\"><obs conceptId=\"indication\" answerConceptId=\"skin\" answerLabel=\"Skin changes\" style=\"checkbox\"/></span>" +
			"</span></div>" +
			"<div class=\"row\" id=\"indicationSkinDetails\"><span class=\"lbl\">Skin changes (specify)</span><obs conceptId=\"skinDetails\"/></div>" +
			"<div class=\"row\"><span class=\"lbl\">Pregnant?</span><obs conceptId=\"pregnant\" answerConceptIds=\"yes,no\" answerLabels=\"Yes,No\" style=\"radio\"/></div>" +
			"</fieldset>" +
			"<fieldset><legend>III. US Findings</legend>" +
			"<div class=\"sub\"><h4>Breast lesion details</h4>" +
			"<div class=\"row small\"><span class=\"lbl\">Size</span><obs conceptId=\"rad\"/> cm RAD X <obs conceptId=\"ap\"/> cm AP X <obs conceptId=\"arad\"/> cm ARAD</div>" +
			"<div class=\"row\"><span class=\"lbl\">Intramammary max cortical thickness</span><obs conceptId=\"thickness\"/> mm</div>" +
			"<div class=\"row\"><span class=\"lbl\">Shape</span><obs conceptId=\"shape\" answerConceptIds=\"oval,irregular\" answerLabels=\"Oval,Irregular\" style=\"radio\"/></div>" +
			"</div></fieldset>" +
			"<fieldset><legend>IV. Assessment and Management</legend>" +
			"<div class=\"row\"><span class=\"lbl\">BI-RADS Category</span><obs conceptId=\"CIEL:birads\" answerConceptIds=\"b3,b4\" answerLabels=\"3 - Probably benign,4 - Suspicious\" style=\"radio\"/></div>" +
			"<div class=\"row checkboxOther\" id=\"ultrasoundFollowupMonths\"><span class=\"lbl\">US follow-up in</span><obs conceptId=\"months\"/> months</div>" +
			"<div class=\"row\"><span class=\"lbl\">Other Recommendations</span><obs conceptId=\"other\" rows=\"3\" cols=\"60\"/></div>" +
			"</fieldset>" +
			"<submit/></div></htmlform>";

	private int nextId = 1;

	@Test
	public void shouldShowEachAnsweredQuestionAsTheFormLabelsItUnderItsSection() {
		Concept indication = concept("indication", "Breast US indication");
		Concept birads = concept("birads-uuid", "BI-RADS");
		mapTo(birads, "CIEL", "birads");
		List<Obs> obs = new ArrayList<Obs>();
		obs.add(coded(indication, concept("mass", "Breast mass concept")));
		obs.add(coded(indication, concept("skin", "Skin changes concept")));
		obs.add(text(concept("skinDetails", "Skin details"), "Peau d'orange <b>left</b>"));
		obs.add(numeric(concept("rad", "RAD"), 1.20));
		obs.add(numeric(concept("arad", "ARAD"), 1.0));
		obs.add(numeric(concept("ap", "AP"), 0.8));
		obs.add(numeric(concept("thickness", "Thickness"), 3.0));
		obs.add(coded(concept("shape", "Shape concept"), concept("irregular", "Irregular concept")));
		obs.add(coded(birads, concept("b4", "BI-RADS 4")));
		obs.add(numeric(concept("months", "Months"), 6.0));
		// Not on the form, such as the link to the radiology test
		obs.add(text(concept("accession", "Radiology accession number"), "ORD-1"));
		Obs voided = numeric(concept("other", "Other recommendations"), 1.0);
		voided.setVoided(true);
		obs.add(voided);

		String html = RadiologyFormReportText.toEditorHtml(FORM, obs, Locale.ENGLISH);

		assertThat(html, equalTo("<h3>Diagnostic Breast Ultrasound Report Form</h3>" +
				"<h4>II. Reason for US</h4>" +
				"<p><b>Indication (choose all that apply):</b> Breast mass, Skin changes</p>" +
				"<p><b>Skin changes (specify):</b> Peau d&#39;orange &lt;b&gt;left&lt;/b&gt;</p>" +
				"<h4>III. US Findings</h4>" +
				"<p><b>Size:</b> 1.2 cm RAD X 0.8 cm AP X 1 cm ARAD</p>" +
				"<p><b>Intramammary max cortical thickness:</b> 3 mm</p>" +
				"<p><b>Shape:</b> Irregular</p>" +
				"<h4>IV. Assessment and Management</h4>" +
				"<p><b>BI-RADS Category:</b> 4 - Suspicious</p>" +
				"<p><b>US follow-up in:</b> 6 months</p>"));
	}

	@Test
	public void shouldShowAFieldLeftEmptyInARowOfSeveralFields() {
		List<Obs> obs = Arrays.asList(numeric(concept("rad", "RAD"), 2.0));

		String html = RadiologyFormReportText.toEditorHtml(FORM, obs, Locale.ENGLISH);

		assertThat(html, equalTo("<h3>Diagnostic Breast Ultrasound Report Form</h3>" +
				"<h4>III. US Findings</h4>" +
				"<p><b>Size:</b> 2 cm RAD X - cm AP X - cm ARAD</p>"));
	}

	@Test
	public void shouldBeStoredAsAFormattedReport() {
		List<Obs> obs = Arrays.asList(coded(concept("pregnant", "Pregnant"), concept("no", "No")));

		String stored = RadiologyReportText.toStorage(RadiologyFormReportText.toEditorHtml(FORM, obs, Locale.ENGLISH));

		assertThat(RadiologyReportText.toPlainText(stored), equalTo("Diagnostic Breast Ultrasound Report Form\n" +
				"II. Reason for US\nPregnant?: No"));
	}

	private Concept concept(String uuid, String name) {
		Concept concept = new Concept(nextId++);
		concept.setUuid(uuid);
		concept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		return concept;
	}

	private void mapTo(Concept concept, String source, String code) {
		ConceptSource conceptSource = new ConceptSource();
		conceptSource.setName(source);
		ConceptReferenceTerm term = new ConceptReferenceTerm(conceptSource, code, null);
		// A mock, as a new mapping looks up its default map type
		ConceptMap map = mock(ConceptMap.class);
		when(map.getConceptReferenceTerm()).thenReturn(term);
		concept.setConceptMappings(new HashSet<ConceptMap>(Collections.singleton(map)));
	}

	private Obs coded(Concept question, Concept answer) {
		Obs obs = obs(question);
		obs.setValueCoded(answer);
		return obs;
	}

	private Obs numeric(Concept question, double value) {
		Obs obs = obs(question);
		obs.setValueNumeric(value);
		return obs;
	}

	private Obs text(Concept question, String value) {
		Obs obs = obs(question);
		obs.setValueText(value);
		return obs;
	}

	private Obs obs(Concept question) {
		Obs obs = new Obs(nextId++);
		obs.setConcept(question);
		return obs;
	}
}
