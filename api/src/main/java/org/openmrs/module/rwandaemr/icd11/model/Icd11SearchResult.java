package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A normalized ICD-11 MMS search result suitable for use by RwandaEMR clients.
 */
@Getter
@Setter
public class Icd11SearchResult {

	private String icd11Code;

	private String entityUri;

	private String foundationUri;

	private String title;

	private String linearization;

	private String conceptUuid;

	private String fullySpecifiedName;

	private String definition;

	private String longDefinition;

	private String codingNote;

	private String browserUrl;

	private List<Icd11Term> inclusions;

	private List<Icd11Term> exclusions;

	private List<Icd11Term> indexTerms;

	private List<String> relatedEntitiesInMaternalChapter;

	private List<String> relatedEntitiesInPerinatalChapter;

	private List<Icd11PostcoordinationScale> postcoordinationScales;

	private boolean remoteMetadata;
}
