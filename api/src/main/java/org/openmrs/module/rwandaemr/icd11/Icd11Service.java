package org.openmrs.module.rwandaemr.icd11;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResponse;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11BrowserNode;

import javax.transaction.Transactional;
import java.util.List;

@Transactional
public interface Icd11Service extends OpenmrsService {

	Icd11SearchResponse search(String query);

	Icd11SearchResponse search(String query, String linearization);

	Icd11SearchResult getCode(String code);

	List<Icd11BrowserNode> browse(String parentCode);

	Icd11PatientDiagnosis saveDiagnosis(Icd11PatientDiagnosis diagnosis);

	Icd11PatientDiagnosis getDiagnosisByUuid(String uuid);

	List<Icd11PatientDiagnosis> getDiagnoses(Patient patient, Encounter encounter);

	Icd11PatientDiagnosis voidDiagnosis(Icd11PatientDiagnosis diagnosis, String reason);

	List<Icd11PatientDiagnosis> replaceDiagnoses(Patient patient, Encounter encounter,
												List<Icd11DiagnosisSelection> selections);
}
