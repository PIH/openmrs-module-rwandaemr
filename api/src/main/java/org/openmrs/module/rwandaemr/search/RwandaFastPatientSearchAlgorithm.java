package org.openmrs.module.rwandaemr.search;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.PatientService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.namephonetics.NamePhoneticsService;
import org.openmrs.module.namephonetics.phoneticsalgorithm.KinyarwandaSoundex;
import org.openmrs.module.registrationcore.api.search.PatientAndMatchQuality;
import org.openmrs.module.registrationcore.api.search.SimilarPatientSearchAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service("rwandaemr.RwandaFastPatientSearchAlgorithm")
public class RwandaFastPatientSearchAlgorithm implements SimilarPatientSearchAlgorithm {

    protected static final Log log = LogFactory.getLog(RwandaFastPatientSearchAlgorithm.class);

    @Autowired
    NamePhoneticsService namePhoneticsService;

    @Autowired
    PatientService patientService;

    @Autowired
    RwandaEmrConfig rwandaEmrConfig;

    private final DoubleMetaphone doubleMetaphone = new DoubleMetaphone();

    private final KinyarwandaSoundex kinyarwandaSoundex = new KinyarwandaSoundex();

    @Override
    public List<PatientAndMatchQuality> findSimilarPatients(Patient search, Map<String, Object> otherDataPoints, Double cutoff, Integer maxResults) {
        List<PatientAndMatchQuality> ret = new ArrayList<>();

        String givenName = search.getGivenName();
        String familyName = search.getFamilyName();
        String gender = search.getGender();

        // If any identifiers are entered, and any match exactly, these should take precedence over anything else
        for (PatientIdentifier pi : search.getIdentifiers()) {
            for (Patient p : patientService.getPatients(null, pi.getIdentifier(), Collections.singletonList(pi.getIdentifierType()), true)) {
                List<String> matchedFields = new ArrayList<>();
                matchedFields.add("identifier." + pi.getIdentifierType().getName());
                ret.add(new PatientAndMatchQuality(p, cutoff + 100, matchedFields));
            }
        }

        // If we do not match by identifier, match by name and filter down as additional fields are entered
        if (ret.isEmpty()) {
            // Require given and family names at minimum to return matches
            if (StringUtils.isNotBlank(givenName) && StringUtils.isNotBlank(familyName)) {
                for (Patient candidate : getPhoneticsMatches(givenName, familyName)) {

                    // If we make it here, we have matched on givenName and familyName
                    boolean match = true;
                    List<String> matchedFields = new ArrayList<>();
                    matchedFields.add("names.givenName");
                    matchedFields.add("names.familyName");

                    // Refine based on gender if entered and if it matches exactly
                    if (StringUtils.isNotBlank(gender)) {
                        match = gender.equals(candidate.getGender());
                        if (match) {
                            matchedFields.add("gender");
                        }
                    }

                    // Refine based on birthdate if entered and patient's birthday falls within 1 year
                    if (match) {
                        Date enteredBirthdate = getBirthdate(search, otherDataPoints);
                        if (enteredBirthdate != null) {
                            Date candidateBirthdate = candidate.getBirthdate();
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(enteredBirthdate);
                            cal.add(Calendar.YEAR, -1);
                            Date fromDate = cal.getTime();
                            cal.add(Calendar.YEAR, 2);
                            Date toDate = cal.getTime();
                            match = candidateBirthdate != null && !candidateBirthdate.before(fromDate) && !candidateBirthdate.after(toDate);
                            if (match) {
                                matchedFields.add("birthdate");
                            }
                        }
                    }

                    // Refine based on mother's name and father's name if entered, using phonetic match
                    if (match) {
                        PersonAttributeType mothersNameType = rwandaEmrConfig.getMothersName();
                        PersonAttributeType fathersNameType = rwandaEmrConfig.getFathersName();
                        boolean hasMothersName = false;
                        boolean hasMothersNameMatch = false;
                        boolean hasFathersName = false;
                        boolean hasFathersNameMatch = false;
                        for (PersonAttribute attribute : search.getActiveAttributes()) {
                            if (attribute.getAttributeType().equals(mothersNameType)) {
                                hasMothersName = true;
                                hasMothersNameMatch = hasMothersNameMatch || hasAttributeWithPhoneticMatch(candidate, attribute.getValue(), mothersNameType);
                            }
                            else if (attribute.getAttributeType().equals(fathersNameType)) {
                                hasFathersName = true;
                                hasFathersNameMatch = hasFathersNameMatch || hasAttributeWithPhoneticMatch(candidate, attribute.getValue(), fathersNameType);
                            }
                        }
                        if (hasMothersName) {
                            match = hasMothersNameMatch;
                            if (match) {
                                matchedFields.add("attribute." + mothersNameType.getName());
                            }
                        }
                        if (match && hasFathersName) {
                            match = hasFathersNameMatch;
                            if (match) {
                                matchedFields.add("attribute." + fathersNameType.getName());
                            }
                        }
                    }

                    if (match) {
                        ret.add(new PatientAndMatchQuality(candidate, cutoff, matchedFields));
                    }
                }
            }
        }
        return ret;
    }

    public Set<Patient> getPhoneticsMatches(String givenName, String familyName) {
        log.trace("Searching phonetic matches for " + givenName + " " + familyName);
        Set<Patient> ret = new HashSet<>(namePhoneticsService.findPatient(givenName, null, familyName, null));
        // TODO: This matches the existing primary care behavior, but do we want to always search both directions?
        if (ret.isEmpty()) {
            ret.addAll(namePhoneticsService.findPatient(familyName, null, givenName, null));
        }
        log.trace("Phonetics result count: " + ret.size());
        return ret;
    }

    public boolean hasAttributeWithPhoneticMatch(Patient p, String search, PersonAttributeType type) {
        String doubleMetaphoneSearch = doubleMetaphone.encode(search);
        String kinyarwandaSearch = kinyarwandaSoundex.encode(search);
        for (PersonAttribute attribute : p.getActiveAttributes()) {
            if (attribute.getAttributeType().equals(type)) {
                boolean matches = doubleMetaphoneSearch.equalsIgnoreCase(doubleMetaphone.encode(attribute.getValue()));
                if (!matches) {
                    matches = kinyarwandaSearch.equalsIgnoreCase(kinyarwandaSoundex.encode(attribute.getValue()));
                }
                if (matches) {
                    return true;
                }
            }
        }
        return false;
    }

    public Date getBirthdate(Patient patient, Map<String, Object> otherDataPoints) {
        if (patient.getBirthdate() != null) {
            return patient.getBirthdate();
        }
        if (otherDataPoints != null) {
            Integer years = (Integer) otherDataPoints.get("birthdateYears");
            Integer months = (Integer) otherDataPoints.get("birthdateMonths");
            if (years != null || months != null) {
                Calendar cal = Calendar.getInstance();
                if (years != null) {
                    cal.add(Calendar.YEAR, -1 * years);
                }
                if (months != null) {
                    cal.add(Calendar.MONTH, -1 * months);
                }
                return cal.getTime();
            }
        }
        return null;
    }
}
