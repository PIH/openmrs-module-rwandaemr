package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.BaseOpenmrsData;

import java.util.Date;

/**
 * A cached WHO ICD-11 API response used when the remote endpoint is unavailable.
 */
@Getter
@Setter
public class Icd11CodeCache extends BaseOpenmrsData {

	private Integer id;

	private String cacheKey;

	private String icd11Code;

	private String entityUri;

	private String foundationUri;

	private String title;

	private String linearization;

	private String language;

	private String apiVersion;

	private String responseJson;

	private Date expiresAt;
}
