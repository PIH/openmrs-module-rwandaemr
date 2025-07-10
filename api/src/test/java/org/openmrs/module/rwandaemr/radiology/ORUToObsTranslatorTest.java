package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class ORUToObsTranslatorTest extends BaseHL7TranslatorTest {

    @Test
    public void shouldTranslateFromORUToObs() throws Exception {
        String completedMessage = IOUtils.resourceToString("/orur01-example-1-completed.txt", StandardCharsets.UTF_8);
        oruToObsTranslator.fromORU_R01(completedMessage);
        List<Encounter> encounters = rwandaEmrService.getEncounters();
        assertThat(encounters.size(), equalTo(1));
        testStudyEncounter(encounters.get(0), false);
        rwandaEmrService.getEncounters().clear();

        String finalMessage = IOUtils.resourceToString("/orur01-example-1-final.txt", StandardCharsets.UTF_8);
        oruToObsTranslator.fromORU_R01(finalMessage);
        encounters = rwandaEmrService.getEncounters();
        assertThat(encounters.size(), equalTo(2));
        testStudyEncounter(encounters.get(0), true);

        Encounter resultsEncounter = encounters.get(1);
        assertThat(resultsEncounter.getPatient(), equalTo(patient));
        assertThat(resultsEncounter.getEncounterType(), equalTo(radiologyConfig.getRadiologyReportEncounterType()));
        assertThat(resultsEncounter.getEncounterDatetime(), equalTo(ymdhms.parse("20250520220127")));
        assertThat(resultsEncounter.getLocation(), equalTo(rwandaEmrConfig.getUnknownLocation()));
        EncounterProvider resultsProvider = resultsEncounter.getEncounterProviders().iterator().next();
        assertThat(resultsProvider.getProvider(), equalTo(rwandaEmrConfig.getUnknownProvider()));
        assertThat(resultsProvider.getEncounterRole(), equalTo(radiologyConfig.getPrincipalResultsInterpreterEncounterRole()));
        assertThat(resultsEncounter.getObsAtTopLevel(false).size(), equalTo(1));
        Obs resultObs = resultsEncounter.getObsAtTopLevel(false).iterator().next();
        assertThat(resultObs.getConcept(), equalTo(radiologyConfig.getRadiologyReportConstruct()));
        assertThat(resultObs.getOrder(), equalTo(testOrder));
        assertThat(resultObs.getGroupMembers().size(), equalTo(4));
        assertThat(getObs(resultObs, radiologyAccessionNumber).getValueText(), equalTo(testOrder.getOrderNumber()));
        assertThat(getObs(resultObs, radiologyAccessionNumber).getOrder(), equalTo(testOrder));
        assertThat(getObs(resultObs, radiologyProcedurePerformed).getValueCoded(), equalTo(xrayConcept));
        assertThat(getObs(resultObs, radiologyProcedurePerformed).getOrder(), equalTo(testOrder));
        assertThat(getObs(resultObs, radiologyReportComments).getValueText(), containsString("TEST TEST REPORT FOR HIS TESTING"));
        assertThat(getObs(resultObs, radiologyReportComments).getOrder(), equalTo(testOrder));
        assertThat(getObs(resultObs, radiologyReportType).getValueCoded(), equalTo(finalConcept));
        assertThat(getObs(resultObs, radiologyReportType).getOrder(), equalTo(testOrder));
    }

    private void testStudyEncounter(Encounter studyEncounter, boolean hasImageUrl) throws Exception {
        assertThat(studyEncounter.getPatient(), equalTo(patient));
        assertThat(studyEncounter.getEncounterType(), equalTo(radiologyConfig.getRadiologyStudyEncounterType()));
        assertThat(studyEncounter.getEncounterDatetime(), equalTo(ymdhms.parse("20250520000000")));
        assertThat(studyEncounter.getLocation(), equalTo(rwandaEmrConfig.getUnknownLocation()));
        assertThat(studyEncounter.getEncounterProviders().size(), equalTo(1));
        EncounterProvider studyProvider = studyEncounter.getEncounterProviders().iterator().next();
        assertThat(studyProvider.getProvider(), equalTo(rwandaEmrConfig.getUnknownProvider()));
        assertThat(studyProvider.getEncounterRole(), equalTo(radiologyConfig.getRadiologyTechnicianEncounterRole()));
        assertThat(studyEncounter.getObsAtTopLevel(false).size(), equalTo(1));
        Obs studyObs = studyEncounter.getObsAtTopLevel(false).iterator().next();
        assertThat(studyObs.getConcept(), equalTo(radiologyConfig.getRadiologyStudyConstruct()));
        assertThat(studyObs.getOrder(), equalTo(testOrder));
        assertThat(studyObs.getGroupMembers().size(), equalTo(3));
        assertThat(getObs(studyObs, radiologyAccessionNumber).getValueText(), equalTo(testOrder.getOrderNumber()));
        assertThat(getObs(studyObs, radiologyAccessionNumber).getOrder(), equalTo(testOrder));
        assertThat(getObs(studyObs, radiologyProcedurePerformed).getValueCoded(), equalTo(xrayConcept));
        assertThat(getObs(studyObs, radiologyProcedurePerformed).getOrder(), equalTo(testOrder));
        assertThat(getObs(studyObs, radiologyImagesAvailable).getOrder(), equalTo(testOrder));
        if (hasImageUrl) {
            assertThat(getObs(studyObs, radiologyImagesAvailable).getValueCoded(), equalTo(conceptService.getTrueConcept()));
            assertThat(getObs(studyObs, radiologyImagesAvailable).getComment(), equalTo("^https://192.168.3.100/PACSAPI/Launch_Viewer?Username=hisuser&Password=hisuser&AccessionNumber=ORD-764591"));
        }
        else {
            assertThat(getObs(studyObs, radiologyImagesAvailable).getValueCoded(), equalTo(conceptService.getFalseConcept()));
            assertThat(getObs(studyObs, radiologyImagesAvailable).getComment(), nullValue());
        }
    }

    Obs getObs(Obs obsGroup, Concept memberObs) {
        for (Obs o : obsGroup.getGroupMembers()) {
            if (o.getConcept().equals(memberObs)) {
                return o;
            }
        }
        return null;
    }
}