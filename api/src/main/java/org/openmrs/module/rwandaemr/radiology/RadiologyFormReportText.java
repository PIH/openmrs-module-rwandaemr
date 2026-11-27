package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptName;
import org.openmrs.Obs;
import org.owasp.encoder.Encode;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The text of a radiology report filled in on a form, so that it can be read wherever a typed report is: on the order,
 * in the queue and on the print.  Each answered question is shown with the label the form gives it, under the heading
 * of its section, in the order of the form.  The answers of a question are labelled as on the form too.  A question whose
 * row holds several fields, or text such as units around a field, is shown as its row reads, with each field replaced
 * by its answer: for example "Size: 1.2 cm RAD X 0.8 cm AP X 1 cm ARAD".
 * <p>
 * The form's layout is read from its HTML Form Entry XML: a question's row is the nearest element with class "row", its
 * label the element with class "lbl" in that row, and its section the legend of its fieldset.
 */
public class RadiologyFormReportText {

	private final Document form;

	private final Collection<Obs> observations;

	private final Locale locale;

	/**
	 * The observations already shown, so none is shown twice
	 */
	private final Set<Obs> shown = new HashSet<Obs>();

	private RadiologyFormReportText(String formXml, Collection<Obs> observations, Locale locale) {
		this.form = Jsoup.parse(StringUtils.defaultString(formXml), "", Parser.xmlParser());
		this.observations = new ArrayList<Obs>();
		for (Obs obs : observations) {
			if (!obs.getVoided() && !obs.isObsGrouping()) {
				this.observations.add(obs);
			}
		}
		this.locale = locale == null ? Locale.ENGLISH : locale;
	}

	/**
	 * @return the report as formatted text for {@link RadiologyReportText#toStorage}: a heading with the form's title,
	 * then each answered question of the form under the heading of its section
	 */
	public static String toEditorHtml(String formXml, Collection<Obs> observations, Locale locale) {
		return new RadiologyFormReportText(formXml, observations, locale).build();
	}

	private String build() {
		StringBuilder html = new StringBuilder();
		String title = getTitle();
		if (title != null) {
			html.append("<h3>").append(Encode.forHtml(title)).append("</h3>");
		}

		// The questions of each row, in the order of the form
		Map<Element, List<Element>> rows = new LinkedHashMap<Element, List<Element>>();
		List<Element> withoutRow = new ArrayList<Element>();
		for (Element tag : form.getElementsByTag("obs")) {
			Element row = closest(tag, "row");
			if (row == null) {
				withoutRow.add(tag);
			}
			else {
				rows.computeIfAbsent(row, r -> new ArrayList<Element>()).add(tag);
			}
		}

		String currentSection = null;
		for (Map.Entry<Element, List<Element>> row : rows.entrySet()) {
			String value = getRowValue(row.getKey(), row.getValue());
			if (StringUtils.isBlank(value)) {
				continue;
			}
			String section = getSection(row.getKey());
			if (section != null && !section.equals(currentSection)) {
				html.append("<h4>").append(Encode.forHtml(section)).append("</h4>");
				currentSection = section;
			}
			String label = getLabel(row.getKey());
			if (label == null) {
				label = getConceptName(getAnswered(row.getValue().get(0)));
			}
			appendLine(html, label, value);
		}

		// Questions outside a row are shown by the name of their concept
		for (Element tag : withoutRow) {
			String value = StringUtils.join(getValues(tag), ", ");
			if (StringUtils.isNotBlank(value)) {
				appendLine(html, StringUtils.defaultString(getConceptName(getAnswered(tag))), value);
			}
		}
		return html.toString();
	}

	private void appendLine(StringBuilder html, String label, String value) {
		html.append("<p>");
		if (StringUtils.isNotBlank(label)) {
			html.append("<b>").append(Encode.forHtml(label)).append(":</b> ");
		}
		html.append(Encode.forHtml(value)).append("</p>");
	}

	/**
	 * @return the answers of the row: the chosen answers when the row only has choices, or else the row as it reads with
	 * each field replaced by its answer, or null if nothing in the row was answered
	 */
	private String getRowValue(Element row, List<Element> tags) {
		Map<Element, List<String>> values = new LinkedHashMap<Element, List<String>>();
		boolean answered = false;
		boolean onlyChoices = true;
		for (Element tag : tags) {
			List<String> tagValues = getValues(tag);
			values.put(tag, tagValues);
			answered = answered || !tagValues.isEmpty();
			onlyChoices = onlyChoices && (hasAttribute(tag, "answerConceptId") || hasAttribute(tag, "answerConceptIds"));
		}
		if (!answered) {
			return null;
		}
		if (onlyChoices) {
			List<String> ret = new ArrayList<String>();
			for (List<String> tagValues : values.values()) {
				ret.addAll(tagValues);
			}
			return StringUtils.join(ret, ", ");
		}
		StringBuilder text = new StringBuilder();
		appendText(row, values, text);
		return text.toString().replaceAll("\\s+", " ").trim();
	}

	private void appendText(Element element, Map<Element, List<String>> values, StringBuilder text) {
		for (Node node : element.childNodes()) {
			if (node instanceof TextNode) {
				text.append(((TextNode) node).text());
			}
			else if (node instanceof Element) {
				Element child = (Element) node;
				if (child.tagName().equalsIgnoreCase("obs")) {
					List<String> tagValues = values.get(child);
					text.append(' ').append(tagValues == null || tagValues.isEmpty() ? "-" :
							StringUtils.join(tagValues, ", ")).append(' ');
				}
				else if (!child.hasClass("lbl") && !child.tagName().equalsIgnoreCase("script")
						&& !child.tagName().equalsIgnoreCase("style")) {
					appendText(child, values, text);
				}
			}
		}
	}

	/**
	 * @return the answers recorded for the question, labelled as on the form.  A checkbox is answered if its answer was
	 * chosen.
	 */
	private List<String> getValues(Element tag) {
		List<String> ret = new ArrayList<String>();
		String conceptRef = getAttribute(tag, "conceptId");
		if (conceptRef == null) {
			return ret;
		}
		String checkboxAnswer = getAttribute(tag, "answerConceptId");
		for (Obs obs : observations) {
			if (shown.contains(obs) || !matches(obs.getConcept(), conceptRef)) {
				continue;
			}
			if (checkboxAnswer != null) {
				if (matches(obs.getValueCoded(), checkboxAnswer)) {
					shown.add(obs);
					String label = getAttribute(tag, "answerLabel");
					ret.add(label != null ? label : getConceptName(obs.getValueCoded()));
				}
				continue;
			}
			String value = getValue(tag, obs);
			if (StringUtils.isNotBlank(value)) {
				shown.add(obs);
				ret.add(value);
			}
		}
		return ret;
	}

	/**
	 * @return the obs answering the question, whether it was shown or not, for its concept name
	 */
	private Concept getAnswered(Element tag) {
		String conceptRef = getAttribute(tag, "conceptId");
		for (Obs obs : observations) {
			if (conceptRef != null && matches(obs.getConcept(), conceptRef)) {
				return obs.getConcept();
			}
		}
		return null;
	}

	private String getValue(Element tag, Obs obs) {
		if (obs.getValueCoded() != null) {
			List<String> answers = split(getAttribute(tag, "answerConceptIds"));
			List<String> labels = split(getAttribute(tag, "answerLabels"));
			for (int i = 0; i < answers.size() && i < labels.size(); i++) {
				if (matches(obs.getValueCoded(), answers.get(i))) {
					return labels.get(i);
				}
			}
			return getConceptName(obs.getValueCoded());
		}
		if (obs.getValueNumeric() != null) {
			return new BigDecimal(obs.getValueNumeric().toString()).stripTrailingZeros().toPlainString();
		}
		if (obs.getValueDatetime() != null) {
			return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(obs.getValueDatetime());
		}
		if (StringUtils.isNotBlank(obs.getValueText())) {
			return obs.getValueText().trim();
		}
		return StringUtils.trimToNull(obs.getValueAsString(locale));
	}

	/**
	 * @return true if the reference (uuid, id or source:code mapping, as in a form) is the concept
	 */
	static boolean matches(Concept concept, String reference) {
		if (concept == null || StringUtils.isBlank(reference)) {
			return false;
		}
		String ref = reference.trim();
		if (ref.equals(concept.getUuid()) || (concept.getConceptId() != null && ref.equals(concept.getConceptId().toString()))) {
			return true;
		}
		int separator = ref.indexOf(':');
		if (separator > 0 && concept.getConceptMappings() != null) {
			String source = ref.substring(0, separator).trim();
			String code = ref.substring(separator + 1).trim();
			for (ConceptMap map : concept.getConceptMappings()) {
				if (map.getConceptReferenceTerm() != null && map.getConceptReferenceTerm().getConceptSource() != null
						&& code.equalsIgnoreCase(map.getConceptReferenceTerm().getCode())
						&& source.equalsIgnoreCase(map.getConceptReferenceTerm().getConceptSource().getName())) {
					return true;
				}
			}
		}
		return false;
	}

	private String getTitle() {
		for (Element heading : form.getElementsByTag("h2")) {
			if (StringUtils.isNotBlank(heading.text())) {
				return heading.text().trim();
			}
		}
		Element root = form.getElementsByTag("htmlform").first();
		return root == null ? null : getAttribute(root, "formName");
	}

	private String getLabel(Element row) {
		for (Element child : row.children()) {
			if (child.hasClass("lbl")) {
				String label = StringUtils.trimToNull(child.text().replaceAll("\\s+", " ").replaceAll("[\\s*:]+$", ""));
				if (label != null) {
					return label;
				}
			}
		}
		return null;
	}

	private String getSection(Element row) {
		for (Element parent : row.parents()) {
			if (parent.tagName().equalsIgnoreCase("fieldset")) {
				for (Element child : parent.children()) {
					if (child.tagName().equalsIgnoreCase("legend")) {
						return StringUtils.trimToNull(child.text());
					}
				}
				return null;
			}
		}
		return null;
	}

	private Element closest(Element element, String className) {
		for (Element parent : element.parents()) {
			if (parent.hasClass(className)) {
				return parent;
			}
		}
		return null;
	}

	private String getConceptName(Concept concept) {
		if (concept == null) {
			return null;
		}
		ConceptName name = concept.getName(locale);
		if (name == null) {
			name = concept.getName(Locale.ENGLISH);
		}
		return name != null ? name.getName() : concept.getDisplayString();
	}

	private static boolean hasAttribute(Element element, String name) {
		return getAttribute(element, name) != null;
	}

	private static String getAttribute(Element element, String name) {
		String value = element.attributes().getIgnoreCase(name);
		return StringUtils.trimToNull(value);
	}

	private static List<String> split(String value) {
		List<String> ret = new ArrayList<String>();
		for (String item : StringUtils.split(StringUtils.defaultString(value), ",")) {
			ret.add(item.trim());
		}
		return ret;
	}
}
