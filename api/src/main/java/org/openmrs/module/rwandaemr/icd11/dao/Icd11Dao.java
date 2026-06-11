package org.openmrs.module.rwandaemr.icd11.dao;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Concept;
import org.openmrs.module.rwandaemr.icd11.model.Icd11CodeCache;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;

import java.util.List;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11BrowserNode;

public interface Icd11Dao {

	Icd11CodeCache getCodeCacheByKey(String cacheKey);

	Icd11CodeCache saveCodeCache(Icd11CodeCache cacheEntry);

	Icd11PatientDiagnosis saveDiagnosis(Icd11PatientDiagnosis diagnosis);

	Icd11PatientDiagnosis getDiagnosisByUuid(String uuid);

	List<Icd11PatientDiagnosis> getDiagnoses(Patient patient, Encounter encounter);

	List<Icd11SearchResult> searchLocalCodes(String query, String linearization);

	Concept getLocalConceptByCode(String code);

	Icd11SearchResult getLocalCode(String code, String linearization);

	List<Icd11BrowserNode> getLocalBrowserNodes(String parentCode);
}
