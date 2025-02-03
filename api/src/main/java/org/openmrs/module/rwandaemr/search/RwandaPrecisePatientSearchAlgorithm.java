package org.openmrs.module.rwandaemr.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.module.registrationcore.api.search.PatientAndMatchQuality;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("rwandaemr.RwandaPrecisePatientSearchAlgorithm")
public class RwandaPrecisePatientSearchAlgorithm extends RwandaFastPatientSearchAlgorithm {

    protected static final Log log = LogFactory.getLog(RwandaPrecisePatientSearchAlgorithm.class);

    @Override
    public List<PatientAndMatchQuality> findSimilarPatients(Patient search, Map<String, Object> otherDataPoints, Double cutoff, Integer maxResults) {
        List<PatientAndMatchQuality> ret = super.findSimilarPatients(search, otherDataPoints, cutoff, maxResults);
        return ret;
    }
}
