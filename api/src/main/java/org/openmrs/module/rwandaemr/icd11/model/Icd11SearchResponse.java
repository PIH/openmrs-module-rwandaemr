package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Search results with enough metadata for callers to identify cached fallback responses.
 */
@Getter
@Setter
public class Icd11SearchResponse {

	private String query;

	private String linearization;

	private boolean fromCache;

	private boolean stale;

	private boolean localDictionary;

	private List<Icd11SearchResult> results = new ArrayList<>();
}
