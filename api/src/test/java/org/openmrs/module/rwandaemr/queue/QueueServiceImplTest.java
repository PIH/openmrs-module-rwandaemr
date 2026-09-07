package org.openmrs.module.rwandaemr.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.htmlformentry.CreateAppointmentAction;
import org.openmrs.module.rwandaemr.htmlformentry.CreateQueueAction;
import org.openmrs.module.rwandaemr.queue.dao.QueueDao;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;

public class QueueServiceImplTest {

    private StubQueueDao dao;

    private TestQueueService service;

    private Concept serviceRequestedQuestion;

    private Concept triageConcept;

    private Location triageLocation;

    private Location consultationLocation;

    private Location laboratoryLocation;

    private Location radiologyLocation;

    private Location imagingLocation;

    private Location nonLoginLocation;

    private User user;

    @BeforeEach
    public void setUp() {
        dao = new StubQueueDao();
        service = new TestQueueService();
        service.setDao(dao);

        user = new User();
        user.setUuid("user-uuid");
        service.user = user;

        serviceRequestedQuestion = concept("service-requested-question");
        triageConcept = concept("triage-service-concept");
        service.serviceRequestedConcept = serviceRequestedQuestion;

        triageLocation = location("Triage");
        consultationLocation = location("Consultation");
        laboratoryLocation = location("Laboratory");
        radiologyLocation = location("Radiology Service");
        imagingLocation = location("Imaging Service");
        nonLoginLocation = location("Store Room");
        service.loginLocations.add(triageLocation);
        service.loginLocations.add(consultationLocation);
        service.loginLocations.add(laboratoryLocation);
        service.loginLocations.add(radiologyLocation);
        service.loginLocations.add(imagingLocation);
        service.sessionLocation = triageLocation;
        service.laboratoryLocation = laboratoryLocation;
        service.diagnosticLocations.add(laboratoryLocation);
        service.diagnosticLocations.add(radiologyLocation);
        service.diagnosticLocations.add(imagingLocation);

        dao.conceptMaps.add(conceptMap(triageConcept, triageLocation));
    }

    @Test
    public void createQueueActionShouldImplementCustomFormSubmissionActionWithoutExtendingAppointmentAction() {
        assertTrue(CustomFormSubmissionAction.class.isAssignableFrom(CreateQueueAction.class));
        assertFalse(CreateAppointmentAction.class.isAssignableFrom(CreateQueueAction.class));
    }

    @Test
    public void createQueueActionShouldCreateQueueBeforeAppointment() {
        List<String> calls = new ArrayList<>();
        CreateQueueAction action = new CreateQueueAction() {

            @Override
            protected void createQueueEntry(FormEntrySession fes) {
                calls.add("queue");
            }

            @Override
            protected void createAppointment(FormEntrySession fes) {
                calls.add("appointment");
            }
        };

        action.applyAction(null);

        assertEquals(2, calls.size());
        assertEquals("queue", calls.get(0));
        assertEquals("appointment", calls.get(1));
    }

    @Test
    public void createQueueActionShouldContinueAppointmentWhenQueueStepFails() {
        List<String> calls = new ArrayList<>();
        CreateQueueAction action = new CreateQueueAction() {

            @Override
            protected void createQueueEntry(FormEntrySession fes) {
                throw new NoClassDefFoundError("QueueService");
            }

            @Override
            protected void createAppointment(FormEntrySession fes) {
                calls.add("appointment");
            }
        };

        action.applyAction(null);

        assertEquals(1, calls.size());
        assertEquals("appointment", calls.get(0));
    }

    @Test
    public void shouldCreateQueueEntryFromRegistrationEncounter() {
        Patient patient = patient("patient-a");
        Visit visit = new Visit();
        Encounter encounter = encounter(patient, visit, triageLocation, triageConcept);

        QueueEntry entry = service.addPatientToQueueFromRegistration(encounter);

        assertNotNull(entry);
        assertEquals(1, dao.entries.size());
        assertSame(patient, entry.getPatient());
        assertSame(visit, entry.getVisit());
        assertSame(encounter, entry.getEncounter());
        assertSame(triageLocation, entry.getEncounterLocation());
        assertSame(triageLocation, entry.getSessionLocation());
        assertSame(triageLocation, entry.getServicePoint());
        assertNull(entry.getPreviousServicePoint());
        assertSame(triageConcept, entry.getServiceRequestedConcept());
        assertEquals(QueuePriority.NORMAL, entry.getPriority());
        assertEquals(QueueStatus.WAITING, entry.getStatus());
        assertEquals(user, entry.getCreator());
        assertTrue(entry.getQueueNumber().endsWith("-TRIA"));

        assertEquals(1, dao.histories.size());
        assertNull(dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.WAITING, dao.histories.get(0).getNewStatus());
        assertSame(entry, dao.histories.get(0).getQueueEntry());
    }

    @Test
    public void shouldUseSelectedRegistrationLocationAsQueueServicePoint() {
        Patient patient = patient("patient-a");
        Visit visit = new Visit();
        Encounter encounter = encounter(patient, visit, consultationLocation, triageConcept);

        QueueEntry entry = service.addPatientToQueueFromRegistration(encounter);

        assertNotNull(entry);
        assertSame(consultationLocation, entry.getEncounterLocation());
        assertSame(consultationLocation, entry.getServicePoint());
        assertTrue(entry.getQueueNumber().endsWith("-CONS"));
    }

    @Test
    public void shouldFallBackToServiceRequestedMappingWhenEncounterLocationIsNotLoginLocation() {
        Encounter encounter = encounter(patient("patient-a"), new Visit(), nonLoginLocation, triageConcept);

        QueueEntry entry = service.addPatientToQueueFromRegistration(encounter);

        assertNotNull(entry);
        assertSame(nonLoginLocation, entry.getEncounterLocation());
        assertSame(triageLocation, entry.getServicePoint());
    }

    @Test
    public void shouldSkipQueueEntryWhenSelectedAndMappedLocationsAreNotLoginLocations() {
        dao.conceptMaps.clear();
        dao.conceptMaps.add(conceptMap(triageConcept, nonLoginLocation));
        Encounter encounter = encounter(patient("patient-a"), new Visit(), nonLoginLocation, triageConcept);

        QueueEntry entry = service.addPatientToQueueFromRegistration(encounter);

        assertNull(entry);
        assertEquals(0, dao.entries.size());
    }

    @Test
    public void shouldNotCreateDuplicateActiveQueueEntryForSameServicePointToday() {
        Encounter encounter = encounter(patient("patient-a"), new Visit(), triageLocation, triageConcept);

        QueueEntry first = service.addPatientToQueueFromRegistration(encounter);
        QueueEntry second = service.addPatientToQueueFromRegistration(encounter);

        assertSame(first, second);
        assertEquals(1, dao.entries.size());
        assertEquals(1, dao.histories.size());
    }

    @Test
    public void shouldUpdateServiceRequestedOnExistingQueueEntryFromEditedRegistration() {
        Patient patient = patient("patient-a");
        Concept consultationConcept = concept("consultation-service-concept");
        QueueEntry entry = service.addPatientToQueueFromRegistration(
                encounter(patient, new Visit(), triageLocation, triageConcept));

        QueueEntry updatedEntry = service.addPatientToQueueFromRegistration(
                encounter(patient, new Visit(), triageLocation, consultationConcept));

        assertSame(entry, updatedEntry);
        assertSame(consultationConcept, entry.getServiceRequestedConcept());
        assertSame(user, entry.getChangedBy());
        assertNotNull(entry.getDateChanged());
        assertEquals(1, dao.entries.size());
        assertEquals(1, dao.histories.size());
    }

    @Test
    public void shouldResolveServicePointLocationFromServiceRequestedConcept() {
        assertSame(triageLocation, service.resolveServicePointFromServiceRequestedConcept(triageConcept));
        assertNull(service.resolveServicePointFromServiceRequestedConcept(concept("unknown-service")));
    }

    @Test
    public void shouldFilterCurrentSessionLocationAndOrderByPriorityThenArrivalTime() {
        QueueEntry normalAtTriage = queueEntry("normal-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        QueueEntry emergencyAtTriage = queueEntry("emergency-a", triageLocation, QueuePriority.EMERGENCY, todayAt(10, 0));
        QueueEntry elderlyAtTriage = queueEntry("elderly-a", triageLocation, QueuePriority.ELDERLY, todayAt(9, 0));
        QueueEntry normalAtConsultation = queueEntry("normal-b", consultationLocation, QueuePriority.NORMAL, todayAt(7, 0));
        dao.entries.add(normalAtTriage);
        dao.entries.add(emergencyAtTriage);
        dao.entries.add(elderlyAtTriage);
        dao.entries.add(normalAtConsultation);

        service.sessionLocation = triageLocation;
        List<QueueEntry> triageEntries = service.getQueueEntriesForCurrentSessionLocation(QueueStatus.WAITING, todayAt(12, 0));

        assertEquals(3, triageEntries.size());
        assertSame(emergencyAtTriage, triageEntries.get(0));
        assertSame(elderlyAtTriage, triageEntries.get(1));
        assertSame(normalAtTriage, triageEntries.get(2));

        service.sessionLocation = consultationLocation;
        List<QueueEntry> consultationEntries = service.getQueueEntriesForCurrentSessionLocation(QueueStatus.WAITING, todayAt(12, 0));

        assertEquals(1, consultationEntries.size());
        assertSame(normalAtConsultation, consultationEntries.get(0));
    }

    @Test
    public void shouldKeepLateNightPatientActiveAndAllowServicePointChangeNextMorning() {
        Date referenceDate = todayAt(8, 0);
        Patient recentPatient = patient("recent-patient");
        QueueEntry recentEntry = queueEntry("recent", triageLocation, QueuePriority.NORMAL, dayAt(-1, 23, 30));
        recentEntry.setPatient(recentPatient);
        Patient expiredPatient = patient("expired-patient");
        QueueEntry expiredEntry = queueEntry("expired", triageLocation, QueuePriority.NORMAL, dayAt(-1, 7, 0));
        expiredEntry.setPatient(expiredPatient);
        dao.entries.add(recentEntry);
        dao.entries.add(expiredEntry);

        List<QueueEntry> entries = service.getQueueEntriesByLocation(
                triageLocation, QueueStatus.WAITING, referenceDate);

        assertEquals(1, entries.size());
        assertSame(recentEntry, entries.get(0));
        assertSame(recentEntry, service.getActiveQueueEntry(recentPatient, triageLocation, referenceDate));
        assertSame(recentEntry, service.getActiveQueueEntry(recentPatient, referenceDate));
        assertNull(service.getActiveQueueEntry(expiredPatient, triageLocation, referenceDate));
        assertNull(service.getActiveQueueEntry(expiredPatient, referenceDate));

        QueueEntry transferredEntry = service.transferPatient(
                recentEntry, consultationLocation, "Continue consultation next morning");

        assertSame(recentEntry, transferredEntry);
        assertSame(triageLocation, transferredEntry.getPreviousServicePoint());
        assertSame(consultationLocation, transferredEntry.getServicePoint());
        assertSame(consultationLocation, transferredEntry.getSessionLocation());
        assertEquals(QueueStatus.WAITING, transferredEntry.getStatus());
        assertSame(transferredEntry, service.getActiveQueueEntry(
                recentPatient, consultationLocation, transferredEntry.getArrivalTime()));
    }

    @Test
    public void shouldKeepActiveEntriesButNotTerminalEntriesFromThePreviousDayInLiveQueue() {
        Date referenceDate = todayAt(1, 0);
        QueueEntry activeEntry = queueEntry("active", triageLocation, QueuePriority.NORMAL, dayAt(-1, 2, 0));
        QueueEntry completedEntry = queueEntry("completed", triageLocation, QueuePriority.NORMAL, dayAt(-1, 2, 15));
        completedEntry.setStatus(QueueStatus.COMPLETED);
        QueueEntry transferredEntry = queueEntry("transferred", triageLocation, QueuePriority.NORMAL, dayAt(-1, 2, 30));
        transferredEntry.setStatus(QueueStatus.TRANSFERRED);
        QueueEntry todayCompletedEntry = queueEntry("today-completed", triageLocation, QueuePriority.NORMAL,
                todayAt(0, 30));
        todayCompletedEntry.setStatus(QueueStatus.COMPLETED);
        dao.entries.add(activeEntry);
        dao.entries.add(completedEntry);
        dao.entries.add(transferredEntry);
        dao.entries.add(todayCompletedEntry);

        List<QueueEntry> locationEntries = service.getQueueEntriesByLocation(triageLocation, null, referenceDate);
        List<QueueEntry> servicePointEntries = service.getQueueEntriesByServicePoint(
                triageLocation, triageLocation, null, referenceDate);

        assertEquals(2, locationEntries.size());
        assertTrue(locationEntries.contains(activeEntry));
        assertTrue(locationEntries.contains(todayCompletedEntry));
        assertEquals(2, servicePointEntries.size());
        assertTrue(servicePointEntries.contains(activeEntry));
        assertTrue(servicePointEntries.contains(todayCompletedEntry));
        assertFalse(locationEntries.contains(completedEntry));
        assertFalse(locationEntries.contains(transferredEntry));
    }

    @Test
    public void shouldCallWaitingPatientWhenOpeningDashboard() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        dao.entries.add(entry);

        service.callPatient(entry);

        assertEquals(QueueStatus.CALLED, entry.getStatus());
        assertNotNull(entry.getCalledTime());
        assertEquals(1, dao.histories.size());
        assertEquals(QueueStatus.WAITING, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.CALLED, dao.histories.get(0).getNewStatus());
        assertEquals("Patient dashboard opened", dao.histories.get(0).getReason());
    }

    @Test
    public void shouldLeaveNonWaitingPatientStatusUnchangedWhenOpeningDashboard() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setStatus(QueueStatus.IN_PROGRESS);
        dao.entries.add(entry);

        service.callPatient(entry);

        assertEquals(QueueStatus.IN_PROGRESS, entry.getStatus());
        assertNull(entry.getCalledTime());
        assertEquals(0, dao.histories.size());
    }

    @Test
    public void shouldCallWaitingPatientAfterVitalsAreRecordedForQueueVisit() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        Visit visit = new Visit();
        visit.setUuid("visit-a");
        entry.setVisit(visit);
        Encounter vitalsEncounter = new Encounter();
        vitalsEncounter.setPatient(entry.getPatient());
        vitalsEncounter.setVisit(visit);

        service.callPatientAfterVitals(entry, vitalsEncounter);

        assertEquals(QueueStatus.CALLED, entry.getStatus());
        assertNotNull(entry.getCalledTime());
        assertEquals(1, dao.histories.size());
        assertEquals("Vitals recorded", dao.histories.get(0).getReason());
    }

    @Test
    public void shouldRejectVitalsEncounterForAnotherPatientOrVisit() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        Visit queueVisit = new Visit();
        queueVisit.setUuid("visit-a");
        entry.setVisit(queueVisit);
        Encounter anotherPatientEncounter = new Encounter();
        anotherPatientEncounter.setPatient(patient("another-patient"));
        anotherPatientEncounter.setVisit(queueVisit);
        Encounter anotherVisitEncounter = new Encounter();
        anotherVisitEncounter.setPatient(entry.getPatient());
        Visit anotherVisit = new Visit();
        anotherVisit.setUuid("visit-b");
        anotherVisitEncounter.setVisit(anotherVisit);

        assertThrows(IllegalArgumentException.class,
                () -> service.callPatientAfterVitals(entry, anotherPatientEncounter));
        assertThrows(IllegalArgumentException.class,
                () -> service.callPatientAfterVitals(entry, anotherVisitEncounter));
        assertEquals(QueueStatus.WAITING, entry.getStatus());
        assertEquals(0, dao.histories.size());
    }

    @Test
    public void shouldUpdateQueuePriority() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        dao.entries.add(entry);

        service.updatePriority(entry, QueuePriority.EMERGENCY);

        assertEquals(QueuePriority.EMERGENCY, entry.getPriority());
        assertSame(user, entry.getChangedBy());
        assertNotNull(entry.getDateChanged());
    }

    @Test
    public void shouldRequireQueueEntryAndPriorityWhenUpdatingPriority() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));

        assertThrows(IllegalArgumentException.class,
                () -> service.updatePriority(null, QueuePriority.EMERGENCY));
        assertThrows(IllegalArgumentException.class, () -> service.updatePriority(entry, null));
    }

    @Test
    public void shouldGetQueueEntriesAcrossAnInclusiveDateRange() {
        QueueEntry beforeRange = queueEntry("before", triageLocation, QueuePriority.NORMAL, dayAt(-3, 23, 59));
        QueueEntry firstDay = queueEntry("first", triageLocation, QueuePriority.NORMAL, dayAt(-2, 0, 0));
        QueueEntry lastDay = queueEntry("last", triageLocation, QueuePriority.NORMAL, dayAt(-1, 23, 59));
        QueueEntry afterRange = queueEntry("after", triageLocation, QueuePriority.NORMAL, dayAt(0, 0, 0));
        dao.entries.add(beforeRange);
        dao.entries.add(firstDay);
        dao.entries.add(lastDay);
        dao.entries.add(afterRange);

        List<QueueEntry> entries = service.getQueueEntriesByLocation(triageLocation, null,
                dayAt(-2, 12, 0), dayAt(-1, 12, 0));

        assertEquals(2, entries.size());
        assertTrue(entries.contains(firstDay));
        assertTrue(entries.contains(lastDay));
    }

    @Test
    public void shouldRejectAnInvertedQueueDateRange() {
        assertThrows(IllegalArgumentException.class, () -> service.getQueueEntriesByLocation(
                triageLocation, null, dayAt(0, 0, 0), dayAt(-1, 0, 0)));
    }

    @Test
    public void shouldGetQueueEntriesForMultipleVisits() {
        Visit firstVisit = new Visit();
        Visit secondVisit = new Visit();
        Visit unrelatedVisit = new Visit();
        QueueEntry firstEntry = queueEntry("first", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        firstEntry.setVisit(firstVisit);
        QueueEntry secondEntry = queueEntry("second", consultationLocation, QueuePriority.NORMAL, todayAt(9, 0));
        secondEntry.setVisit(secondVisit);
        QueueEntry unrelatedEntry = queueEntry("unrelated", laboratoryLocation, QueuePriority.NORMAL, todayAt(10, 0));
        unrelatedEntry.setVisit(unrelatedVisit);
        dao.entries.add(firstEntry);
        dao.entries.add(secondEntry);
        dao.entries.add(unrelatedEntry);

        List<QueueEntry> entries = service.getQueueEntriesByVisits(Arrays.asList(firstVisit, secondVisit));

        assertEquals(2, entries.size());
        assertTrue(entries.contains(firstEntry));
        assertTrue(entries.contains(secondEntry));
    }

    @Test
    public void shouldTransferQueueEntryToAnotherLoginLocationAndRecordHistory() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setEncounterLocation(triageLocation);
        Date originalArrivalTime = entry.getArrivalTime();
        entry.setQueueNumber("TRIAGE-20200101-001");
        entry.setStatus(QueueStatus.IN_PROGRESS);
        entry.setCalledTime(todayAt(8, 15));
        entry.setServiceStartTime(todayAt(8, 30));
        entry.setServiceEndTime(todayAt(8, 45));
        entry.setCompletedTime(todayAt(8, 45));
        dao.entries.add(entry);

        service.transferPatient(entry, consultationLocation, "  Needs consultation  ");

        assertSame(consultationLocation, entry.getServicePoint());
        assertSame(triageLocation, entry.getPreviousServicePoint());
        assertEquals("Needs consultation", entry.getTransferReason());
        assertSame(consultationLocation, entry.getSessionLocation());
        assertEquals(QueueStatus.WAITING, entry.getStatus());
        assertTrue(entry.getQueueNumber().endsWith("-CONS"));
        assertFalse(originalArrivalTime.equals(entry.getArrivalTime()));
        assertNull(entry.getCalledTime());
        assertNull(entry.getServiceStartTime());
        assertNull(entry.getServiceEndTime());
        assertNull(entry.getCompletedTime());
        assertEquals(1, dao.histories.size());
        assertEquals(QueueStatus.IN_PROGRESS, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.WAITING, dao.histories.get(0).getNewStatus());
        assertEquals("Needs consultation", dao.histories.get(0).getReason());

        List<QueueEntry> destinationEntries = service.getQueueEntriesByServicePoint(consultationLocation,
                consultationLocation, QueueStatus.WAITING, new Date());
        assertEquals(1, destinationEntries.size());
        assertSame(entry, destinationEntries.get(0));

        List<QueueEntry> originEntries = service.getQueueEntriesByLocation(triageLocation, QueueStatus.WAITING,
                new Date());
        assertEquals(0, originEntries.size());
    }

    @Test
    public void shouldAssignSelectedProviderAtTransferredDestination() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        Provider provider = new Provider(17);
        dao.entries.add(entry);

        QueueEntry transferredEntry = service.transferPatient(
                entry, consultationLocation, "Needs consultation", provider);

        assertSame(entry, transferredEntry);
        assertSame(provider, transferredEntry.getAssignedProvider());
    }

    @Test
    public void shouldAssignSelectedProviderToDiagnosticDestinationQueue() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        Provider provider = new Provider(18);
        dao.entries.add(entry);

        QueueEntry diagnosticEntry = service.transferPatient(
                entry, laboratoryLocation, "Needs laboratory", provider);

        assertNotSame(entry, diagnosticEntry);
        assertSame(provider, diagnosticEntry.getAssignedProvider());
        assertNull(entry.getAssignedProvider());
    }

    @Test
    public void shouldRejectTransferWithoutReason() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.transferPatient(entry, consultationLocation, "   "));

        assertEquals("Transfer reason is required", exception.getMessage());
        assertSame(triageLocation, entry.getServicePoint());
        assertEquals(0, dao.histories.size());
    }

    @Test
    public void shouldSendPatientToLaboratoryWithoutRemovingFromOriginQueue() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setQueueNumber("TRIAGE-20200101-001");
        entry.setStatus(QueueStatus.CALLED);
        entry.setCalledTime(todayAt(8, 10));
        dao.entries.add(entry);

        QueueEntry laboratoryEntry = service.transferPatient(entry, laboratoryLocation, "Needs laboratory");

        assertSame(triageLocation, entry.getServicePoint());
        assertSame(triageLocation, entry.getSessionLocation());
        assertEquals("Needs laboratory", entry.getTransferReason());
        assertEquals(QueueStatus.IN_PROGRESS, entry.getStatus());
        assertEquals("TRIAGE-20200101-001", entry.getQueueNumber());
        assertNotNull(entry.getServiceStartTime());
        assertNull(entry.getServiceEndTime());
        assertNull(entry.getCompletedTime());

        assertNotSame(entry, laboratoryEntry);
        assertSame(laboratoryLocation, laboratoryEntry.getServicePoint());
        assertSame(laboratoryLocation, laboratoryEntry.getSessionLocation());
        assertSame(triageLocation, laboratoryEntry.getPreviousServicePoint());
        assertEquals("Needs laboratory", laboratoryEntry.getTransferReason());
        assertEquals(QueueStatus.WAITING, laboratoryEntry.getStatus());
        assertTrue(laboratoryEntry.getQueueNumber().endsWith("-LABO"));
        assertEquals(2, dao.entries.size());
        assertEquals(2, dao.histories.size());
        assertEquals(QueueStatus.CALLED, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.IN_PROGRESS, dao.histories.get(0).getNewStatus());
        assertNull(dao.histories.get(1).getPreviousStatus());
        assertEquals(QueueStatus.WAITING, dao.histories.get(1).getNewStatus());

        List<QueueEntry> originEntries = service.getQueueEntriesByLocation(triageLocation, QueueStatus.IN_PROGRESS,
                new Date());
        assertEquals(1, originEntries.size());
        assertSame(entry, originEntries.get(0));

        List<QueueEntry> laboratoryEntries = service.getQueueEntriesByServicePoint(laboratoryLocation,
                laboratoryLocation, QueueStatus.WAITING, new Date());
        assertEquals(1, laboratoryEntries.size());
        assertSame(laboratoryEntry, laboratoryEntries.get(0));
    }

    @Test
    public void shouldSendPatientToRadiologyWithoutRemovingFromOriginQueue() {
        assertDiagnosticTransfer(radiologyLocation, "Needs radiology", "-RADI");
    }

    @Test
    public void shouldSendPatientToImagingWithoutRemovingFromOriginQueue() {
        assertDiagnosticTransfer(imagingLocation, "Needs imaging", "-IMAG");
    }

    @Test
    public void shouldRemovePatientFromLaboratoryQueueWhenSentToRadiology() {
        assertDiagnosticSourceTransfer(laboratoryLocation, radiologyLocation);
    }

    @Test
    public void shouldRemovePatientFromRadiologyQueueWhenSentToImaging() {
        assertDiagnosticSourceTransfer(radiologyLocation, imagingLocation);
    }

    @Test
    public void shouldRemovePatientFromImagingQueueWhenSentToLaboratory() {
        assertDiagnosticSourceTransfer(imagingLocation, laboratoryLocation);
    }

    @Test
    public void shouldUseCurrentServicePointAsPreviousServicePointForDiagnosticEntry() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        dao.entries.add(entry);

        service.transferPatient(entry, consultationLocation, "Needs consultation");
        QueueEntry laboratoryEntry = service.transferPatient(entry, laboratoryLocation, "Needs laboratory");

        assertSame(consultationLocation, entry.getServicePoint());
        assertEquals("Needs laboratory", entry.getTransferReason());
        assertSame(laboratoryLocation, laboratoryEntry.getServicePoint());
        assertSame(consultationLocation, laboratoryEntry.getPreviousServicePoint());
    }

    @Test
    public void shouldQueuePatientAtLaboratoryAndRadiologyAtTheSameTime() {
        QueueEntry sourceEntry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        sourceEntry.setQueueNumber("TRIAGE-20200101-001");
        dao.entries.add(sourceEntry);

        QueueEntry laboratoryEntry = service.transferPatient(
                sourceEntry, laboratoryLocation, "Laboratory investigation");
        QueueEntry radiologyEntry = service.transferPatient(
                sourceEntry, radiologyLocation, "Radiology investigation");

        assertEquals(3, dao.entries.size());
        assertEquals(QueueStatus.IN_PROGRESS, sourceEntry.getStatus());
        assertSame(triageLocation, sourceEntry.getServicePoint());
        assertEquals(QueueStatus.WAITING, laboratoryEntry.getStatus());
        assertSame(laboratoryLocation, laboratoryEntry.getServicePoint());
        assertEquals(QueueStatus.WAITING, radiologyEntry.getStatus());
        assertSame(radiologyLocation, radiologyEntry.getServicePoint());

        assertSame(laboratoryEntry, service.getActiveQueueEntry(
                sourceEntry.getPatient(), laboratoryLocation, new Date()));
        assertSame(radiologyEntry, service.getActiveQueueEntry(
                sourceEntry.getPatient(), radiologyLocation, new Date()));
    }

    @Test
    public void shouldReuseActiveDiagnosticQueueEntryWithoutWarning() {
        QueueEntry sourceEntry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        sourceEntry.setQueueNumber("TRIAGE-20200101-001");
        dao.entries.add(sourceEntry);
        QueueEntry laboratoryEntry = service.transferPatient(
                sourceEntry, laboratoryLocation, "Laboratory investigation");
        int historyCount = dao.histories.size();

        QueueEntry repeatedTransfer = service.transferPatient(
                sourceEntry, laboratoryLocation, "Repeated laboratory request");

        assertSame(laboratoryEntry, repeatedTransfer);
        assertEquals(2, dao.entries.size());
        assertEquals(historyCount, dao.histories.size());
        assertEquals(QueueStatus.IN_PROGRESS, sourceEntry.getStatus());
    }

    @Test
    public void shouldRemovePatientFromDiagnosticSourceWhenDestinationEntryIsAlreadyActive() {
        QueueEntry sourceEntry = queueEntry(
                "queue-source", laboratoryLocation, QueuePriority.NORMAL, todayAt(8, 0));
        QueueEntry destinationEntry = queueEntry(
                "queue-destination", radiologyLocation, QueuePriority.NORMAL, todayAt(9, 0));
        destinationEntry.setPatient(sourceEntry.getPatient());
        dao.entries.add(sourceEntry);
        dao.entries.add(destinationEntry);

        QueueEntry result = service.transferPatient(
                sourceEntry, radiologyLocation, "Continue at radiology");

        assertSame(destinationEntry, result);
        assertEquals(QueueStatus.TRANSFERRED, sourceEntry.getStatus());
        assertEquals("Continue at radiology", sourceEntry.getTransferReason());
        assertNotNull(sourceEntry.getServiceEndTime());
        assertNotNull(sourceEntry.getCompletedTime());
        assertNull(service.getActiveQueueEntry(sourceEntry.getPatient(), laboratoryLocation, new Date()));
        assertSame(destinationEntry, service.getActiveQueueEntry(
                sourceEntry.getPatient(), radiologyLocation, new Date()));
    }

    @Test
    public void shouldReturnDiagnosticPatientToPreservedPreviousQueueEntryWithoutWarning() {
        Visit visit = new Visit();
        visit.setUuid("visit-a");
        QueueEntry sourceEntry = queueEntry("queue-a", consultationLocation, QueuePriority.NORMAL, todayAt(8, 0));
        sourceEntry.setVisit(visit);
        sourceEntry.setQueueNumber("CONSUL-20200101-001");
        dao.entries.add(sourceEntry);
        QueueEntry radiologyEntry = service.transferPatient(
                sourceEntry, radiologyLocation, "Radiology investigation");

        QueueEntry returnedEntry = service.transferPatient(
                radiologyEntry, consultationLocation, "Radiology completed");

        assertSame(sourceEntry, returnedEntry);
        assertEquals(2, dao.entries.size());
        assertEquals(QueueStatus.IN_PROGRESS, sourceEntry.getStatus());
        assertEquals(QueueStatus.TRANSFERRED, radiologyEntry.getStatus());
        assertEquals("Radiology completed", radiologyEntry.getTransferReason());
        assertNotNull(radiologyEntry.getServiceEndTime());
        assertNotNull(radiologyEntry.getCompletedTime());
        assertEquals(3, dao.histories.size());
        assertEquals(QueueStatus.WAITING, dao.histories.get(2).getPreviousStatus());
        assertEquals(QueueStatus.TRANSFERRED, dao.histories.get(2).getNewStatus());
        assertEquals("Radiology completed", dao.histories.get(2).getReason());
    }

    @Test
    public void shouldRejectTransferToNonLoginLocation() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));

        assertThrows(IllegalArgumentException.class, () -> service.transferPatient(entry, nonLoginLocation, "Invalid"));
    }

    @Test
    public void shouldRejectTransferToCurrentServicePoint() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));

        assertThrows(IllegalArgumentException.class, () -> service.transferPatient(entry, triageLocation, "Invalid"));
    }

    @Test
    public void shouldRejectTransferWhenPatientAlreadyHasActiveEntryAtDestination() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        QueueEntry activeDestinationEntry = queueEntry("queue-b", consultationLocation, QueuePriority.NORMAL, todayAt(9, 0));
        activeDestinationEntry.setPatient(entry.getPatient());
        dao.entries.add(entry);
        dao.entries.add(activeDestinationEntry);

        assertThrows(IllegalArgumentException.class,
                () -> service.transferPatient(entry, consultationLocation, "Needs consultation"));
    }

    @Test
    public void shouldFindLatestActiveQueueEntryForPatientWithin24Hours() {
        Patient patient = patient("patient-a");
        QueueEntry earlier = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        QueueEntry latest = queueEntry("queue-b", consultationLocation, QueuePriority.NORMAL, todayAt(10, 0));
        QueueEntry completed = queueEntry("queue-c", laboratoryLocation, QueuePriority.NORMAL, todayAt(11, 0));
        earlier.setPatient(patient);
        latest.setPatient(patient);
        completed.setPatient(patient);
        completed.setStatus(QueueStatus.COMPLETED);
        dao.entries.add(earlier);
        dao.entries.add(latest);
        dao.entries.add(completed);

        QueueEntry result = service.getActiveQueueEntry(patient, todayAt(12, 0));

        assertSame(latest, result);
    }

    @Test
    public void shouldCountAndPageLiveQueueEntriesInPriorityOrder() {
        Date referenceTime = todayAt(12, 0);
        QueueEntry normal = queueEntry("queue-normal", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        QueueEntry emergency = queueEntry("queue-emergency", triageLocation, QueuePriority.EMERGENCY, todayAt(10, 0));
        QueueEntry previousDayWaiting = queueEntry(
                "queue-previous-waiting", triageLocation, QueuePriority.ELDERLY, dayAt(-1, 18, 0));
        QueueEntry previousDayCompleted = queueEntry(
                "queue-previous-completed", triageLocation, QueuePriority.PREGNANT, dayAt(-1, 19, 0));
        previousDayCompleted.setStatus(QueueStatus.COMPLETED);
        dao.entries.add(normal);
        dao.entries.add(emergency);
        dao.entries.add(previousDayWaiting);
        dao.entries.add(previousDayCompleted);

        int count = service.countQueueEntriesByLocation(triageLocation, null, referenceTime);
        List<QueueEntry> firstPage = service.getQueueEntriesByLocation(
                triageLocation, null, referenceTime, 0, 2);
        List<QueueEntry> secondPage = service.getQueueEntriesByLocation(
                triageLocation, null, referenceTime, 2, 2);

        assertEquals(3, count);
        assertEquals(Arrays.asList(emergency, previousDayWaiting), firstPage);
        assertEquals(Collections.singletonList(normal), secondPage);
    }

    @Test
    public void shouldCountAndPageEntriesByExactArrivalDate() {
        Date yesterday = dayAt(-1, 12, 0);
        QueueEntry yesterdayWaiting = queueEntry(
                "yesterday-waiting", triageLocation, QueuePriority.NORMAL, dayAt(-1, 8, 0));
        QueueEntry yesterdayCompleted = queueEntry(
                "yesterday-completed", triageLocation, QueuePriority.EMERGENCY, dayAt(-1, 9, 0));
        yesterdayCompleted.setStatus(QueueStatus.COMPLETED);
        QueueEntry todayWaiting = queueEntry(
                "today-waiting", triageLocation, QueuePriority.ELDERLY, todayAt(7, 0));
        dao.entries.add(yesterdayWaiting);
        dao.entries.add(yesterdayCompleted);
        dao.entries.add(todayWaiting);

        int count = service.countQueueEntriesByLocation(
                triageLocation, null, yesterday, yesterday);
        List<QueueEntry> entries = service.getQueueEntriesByLocation(
                triageLocation, null, yesterday, yesterday, 0, 10);

        assertEquals(2, count);
        assertEquals(Arrays.asList(yesterdayCompleted, yesterdayWaiting), entries);
        assertFalse(entries.contains(todayWaiting));
    }

    @Test
    public void shouldFilterExactArrivalDateEntriesByPatientName() {
        Date today = todayAt(12, 0);
        QueueEntry alineUwase = queueEntry(
                "aline-uwase", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        alineUwase.getPatient().addName(personName("Aline", "Uwase"));
        QueueEntry alineMukamana = queueEntry(
                "aline-mukamana", triageLocation, QueuePriority.NORMAL, todayAt(9, 0));
        alineMukamana.getPatient().addName(personName("Aline", "Mukamana"));
        QueueEntry ericUwase = queueEntry(
                "eric-uwase", triageLocation, QueuePriority.NORMAL, todayAt(10, 0));
        ericUwase.getPatient().addName(personName("Eric", "Uwase"));
        dao.entries.add(alineUwase);
        dao.entries.add(alineMukamana);
        dao.entries.add(ericUwase);

        int count = service.countQueueEntriesByLocation(
                triageLocation, null, today, today, "  Aline Uwa  ");
        List<QueueEntry> entries = service.getQueueEntriesByLocation(
                triageLocation, null, today, today, "  Aline Uwa  ", 0, 10);

        assertEquals(1, count);
        assertEquals(Collections.singletonList(alineUwase), entries);
    }

    @Test
    public void shouldFilterExactArrivalDateEntriesByCurrentProviderAssignment() {
        Date today = todayAt(12, 0);
        Provider currentProvider = new Provider(7);
        Provider secondCurrentProvider = new Provider(9);
        Provider otherProvider = new Provider(11);
        QueueEntry assignedToCurrent = queueEntry(
                "assigned-current", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        assignedToCurrent.setAssignedProvider(secondCurrentProvider);
        QueueEntry assignedToOther = queueEntry(
                "assigned-other", triageLocation, QueuePriority.NORMAL, todayAt(9, 0));
        assignedToOther.setAssignedProvider(otherProvider);
        QueueEntry unassigned = queueEntry(
                "unassigned", triageLocation, QueuePriority.NORMAL, todayAt(10, 0));
        dao.entries.add(assignedToCurrent);
        dao.entries.add(assignedToOther);
        dao.entries.add(unassigned);
        List<Integer> currentProviderIds = Arrays.asList(currentProvider.getId(), secondCurrentProvider.getId());

        List<QueueEntry> assignedEntries = service.getQueueEntriesByLocation(
                triageLocation, null, today, today, null, QueueAssignmentFilter.ASSIGNED_TO_ME,
                currentProviderIds, 0, 10);
        List<QueueEntry> notAssignedEntries = service.getQueueEntriesByLocation(
                triageLocation, null, today, today, null, QueueAssignmentFilter.NOT_ASSIGNED_TO_ME,
                currentProviderIds, 0, 10);

        assertEquals(Collections.singletonList(assignedToCurrent), assignedEntries);
        assertEquals(Arrays.asList(assignedToOther, unassigned), notAssignedEntries);
    }

    @Test
    public void shouldAssignChangeAndClearQueueEntryProvider() {
        QueueEntry entry = queueEntry("queue-provider", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        Provider firstProvider = new Provider(7);
        Provider secondProvider = new Provider(9);

        assertSame(entry, service.updateAssignedProvider(entry, firstProvider));
        assertSame(firstProvider, entry.getAssignedProvider());
        assertSame(user, entry.getChangedBy());
        assertNotNull(entry.getDateChanged());

        service.updateAssignedProvider(entry, secondProvider);
        assertSame(secondProvider, entry.getAssignedProvider());

        service.updateAssignedProvider(entry, null);
        assertNull(entry.getAssignedProvider());
    }

    @Test
    public void shouldFindLatestActiveQueueEntryForVisitAcrossDates() {
        Visit visit = new Visit();
        QueueEntry earlier = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, dayAt(-1, 23, 55));
        QueueEntry latest = queueEntry("queue-b", consultationLocation, QueuePriority.NORMAL, todayAt(0, 5));
        QueueEntry completed = queueEntry("queue-c", laboratoryLocation, QueuePriority.NORMAL, todayAt(0, 10));
        earlier.setVisit(visit);
        latest.setVisit(visit);
        completed.setVisit(visit);
        completed.setStatus(QueueStatus.COMPLETED);
        dao.entries.add(earlier);
        dao.entries.add(latest);
        dao.entries.add(completed);

        QueueEntry result = service.getActiveQueueEntry(visit);

        assertSame(latest, result);
    }

    @Test
    public void shouldCompleteQueueEntryAndSetCompletedTime() {
        QueueEntry entry = queueEntry("queue-a", consultationLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setStatus(QueueStatus.IN_PROGRESS);

        service.completeService(entry);

        assertEquals(QueueStatus.COMPLETED, entry.getStatus());
        assertNotNull(entry.getServiceEndTime());
        assertNotNull(entry.getCompletedTime());
        assertEquals(entry.getServiceEndTime(), entry.getCompletedTime());
        assertEquals(1, dao.histories.size());
        assertEquals(QueueStatus.IN_PROGRESS, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.COMPLETED, dao.histories.get(0).getNewStatus());
    }

    @Test
    public void shouldMarkActiveQueueEntryTransferredAndSetCompletedTime() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setStatus(QueueStatus.IN_PROGRESS);
        dao.entries.add(entry);

        service.markPatientTransferred(entry, "External transfer created: transfer-uuid");

        assertEquals(QueueStatus.TRANSFERRED, entry.getStatus());
        assertNotNull(entry.getServiceEndTime());
        assertNotNull(entry.getCompletedTime());
        assertSame(user, entry.getChangedBy());
        assertNotNull(entry.getDateChanged());
        assertEquals(1, dao.histories.size());
        assertEquals(QueueStatus.IN_PROGRESS, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.TRANSFERRED, dao.histories.get(0).getNewStatus());
        assertEquals("External transfer created: transfer-uuid", dao.histories.get(0).getReason());
    }

    @Test
    public void shouldNotDuplicateHistoryWhenQueueEntryIsAlreadyTransferred() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setStatus(QueueStatus.TRANSFERRED);

        service.markPatientTransferred(entry, "External transfer created: transfer-uuid");

        assertEquals(0, dao.histories.size());
    }

    @Test
    public void shouldRejectMarkingTerminalQueueEntryTransferred() {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setStatus(QueueStatus.COMPLETED);

        assertThrows(IllegalArgumentException.class,
                () -> service.markPatientTransferred(entry, "External transfer created: transfer-uuid"));
    }

    @Test
    public void shouldRequireLoginLocationWhenSavingConceptMap() {
        QueueServicePointConceptMap conceptMap = conceptMap(triageConcept, nonLoginLocation);

        assertThrows(IllegalArgumentException.class, () -> service.saveServicePointConceptMap(conceptMap));
    }

    private Encounter encounter(Patient patient, Visit visit, Location location, Concept serviceRequestedValue) {
        Encounter encounter = new Encounter();
        encounter.setUuid("encounter-" + patient.getUuid());
        encounter.setPatient(patient);
        encounter.setVisit(visit);
        encounter.setLocation(location);
        Obs serviceRequestedObs = new Obs();
        serviceRequestedObs.setConcept(serviceRequestedQuestion);
        serviceRequestedObs.setValueCoded(serviceRequestedValue);
        encounter.addObs(serviceRequestedObs);
        return encounter;
    }

    private QueueEntry queueEntry(String uuid, Location servicePoint, QueuePriority priority, Date arrivalTime) {
        QueueEntry entry = new QueueEntry();
        entry.setUuid(uuid);
        entry.setPatient(patient("patient-" + uuid));
        entry.setSessionLocation(servicePoint);
        entry.setServicePoint(servicePoint);
        entry.setPriority(priority);
        entry.setStatus(QueueStatus.WAITING);
        entry.setArrivalTime(arrivalTime);
        entry.setVoided(false);
        return entry;
    }

    private void assertDiagnosticTransfer(Location destination, String reason, String queueNumberSuffix) {
        QueueEntry entry = queueEntry("queue-a", triageLocation, QueuePriority.NORMAL, todayAt(8, 0));
        entry.setQueueNumber("TRIAGE-20200101-001");
        entry.setStatus(QueueStatus.CALLED);
        entry.setCalledTime(todayAt(8, 10));
        dao.entries.add(entry);

        QueueEntry destinationEntry = service.transferPatient(entry, destination, reason);

        assertSame(triageLocation, entry.getServicePoint());
        assertSame(triageLocation, entry.getSessionLocation());
        assertEquals(reason, entry.getTransferReason());
        assertEquals(QueueStatus.IN_PROGRESS, entry.getStatus());
        assertEquals("TRIAGE-20200101-001", entry.getQueueNumber());
        assertNotNull(entry.getServiceStartTime());
        assertNull(entry.getServiceEndTime());
        assertNull(entry.getCompletedTime());
        assertNotSame(entry, destinationEntry);
        assertSame(destination, destinationEntry.getServicePoint());
        assertSame(destination, destinationEntry.getSessionLocation());
        assertSame(triageLocation, destinationEntry.getPreviousServicePoint());
        assertEquals(reason, destinationEntry.getTransferReason());
        assertEquals(QueueStatus.WAITING, destinationEntry.getStatus());
        assertTrue(destinationEntry.getQueueNumber().endsWith(queueNumberSuffix));
        assertEquals(2, dao.entries.size());
        assertEquals(2, dao.histories.size());
        assertEquals(QueueStatus.CALLED, dao.histories.get(0).getPreviousStatus());
        assertEquals(QueueStatus.IN_PROGRESS, dao.histories.get(0).getNewStatus());
        assertEquals(reason, dao.histories.get(0).getReason());
        assertNull(dao.histories.get(1).getPreviousStatus());
        assertEquals(QueueStatus.WAITING, dao.histories.get(1).getNewStatus());
        assertEquals(reason, dao.histories.get(1).getReason());

        List<QueueEntry> originEntries = service.getQueueEntriesByLocation(
                triageLocation, QueueStatus.IN_PROGRESS, new Date());
        assertEquals(1, originEntries.size());
        assertSame(entry, originEntries.get(0));

        List<QueueEntry> destinationEntries = service.getQueueEntriesByServicePoint(
                destination, destination, QueueStatus.WAITING, new Date());
        assertEquals(1, destinationEntries.size());
        assertSame(destinationEntry, destinationEntries.get(0));
    }

    private void assertDiagnosticSourceTransfer(Location source, Location destination) {
        QueueEntry sourceEntry = queueEntry("queue-source", source, QueuePriority.NORMAL, todayAt(8, 0));
        sourceEntry.setStatus(QueueStatus.CALLED);
        sourceEntry.setCalledTime(todayAt(8, 10));
        dao.entries.add(sourceEntry);

        QueueEntry destinationEntry = service.transferPatient(
                sourceEntry, destination, "Continue at " + destination.getName());

        assertNotSame(sourceEntry, destinationEntry);
        assertSame(source, sourceEntry.getServicePoint());
        assertEquals(QueueStatus.TRANSFERRED, sourceEntry.getStatus());
        assertNotNull(sourceEntry.getServiceEndTime());
        assertNotNull(sourceEntry.getCompletedTime());
        assertNull(service.getActiveQueueEntry(sourceEntry.getPatient(), source, new Date()));
        assertSame(destination, destinationEntry.getServicePoint());
        assertSame(source, destinationEntry.getPreviousServicePoint());
        assertEquals(QueueStatus.WAITING, destinationEntry.getStatus());
        assertSame(destinationEntry, service.getActiveQueueEntry(
                sourceEntry.getPatient(), destination, new Date()));
    }

    private Patient patient(String uuid) {
        Patient patient = new Patient();
        patient.setUuid(uuid);
        return patient;
    }

    private PersonName personName(String givenName, String familyName) {
        PersonName name = new PersonName(givenName, null, familyName);
        name.setVoided(false);
        return name;
    }

    private Concept concept(String uuid) {
        Concept concept = new Concept();
        concept.setUuid(uuid);
        return concept;
    }

    private Location location(String name) {
        Location location = new Location();
        location.setUuid(name.toLowerCase().replace(' ', '-') + "-uuid");
        location.setName(name);
        return location;
    }

    private QueueServicePointConceptMap conceptMap(Concept concept, Location servicePoint) {
        QueueServicePointConceptMap conceptMap = new QueueServicePointConceptMap();
        conceptMap.setServiceRequestedConcept(concept);
        conceptMap.setServicePoint(servicePoint);
        conceptMap.setActive(true);
        conceptMap.setVoided(false);
        return conceptMap;
    }

    private Date todayAt(int hour, int minute) {
        return dayAt(0, hour, minute);
    }

    private Date dayAt(int dayOffset, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, dayOffset);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static class TestQueueService extends QueueServiceImpl {

        private Concept serviceRequestedConcept;

        private Location sessionLocation;

        private User user;

        private List<Location> loginLocations = new ArrayList<Location>();

        private Location laboratoryLocation;

        private List<Location> diagnosticLocations = new ArrayList<Location>();

        @Override
        protected Concept getServiceRequestedConcept() {
            return serviceRequestedConcept;
        }

        @Override
        protected Location getCurrentSessionLocation() {
            return sessionLocation;
        }

        @Override
        protected User getAuthenticatedUser() {
            return user;
        }

        @Override
        protected boolean isLoginLocation(Location location) {
            return loginLocations.contains(location);
        }

        @Override
        public boolean isLaboratoryServicePoint(Location location) {
            return laboratoryLocation != null && laboratoryLocation.equals(location);
        }

        @Override
        protected boolean isDiagnosticServicePoint(Location location) {
            return diagnosticLocations.contains(location);
        }

        @Override
        protected Provider getCurrentProvider() {
            return null;
        }

        @Override
        public List<Location> getServicePointLocations() {
            return loginLocations;
        }
    }

    private static class StubQueueDao implements QueueDao {

        private final List<QueueServicePointConceptMap> conceptMaps = new ArrayList<QueueServicePointConceptMap>();

        private final List<QueueEntry> entries = new ArrayList<QueueEntry>();

        private final List<QueueStatusHistory> histories = new ArrayList<QueueStatusHistory>();

        @Override
        public QueueServicePointConceptMap saveServicePointConceptMap(QueueServicePointConceptMap conceptMap) {
            if (!conceptMaps.contains(conceptMap)) {
                conceptMaps.add(conceptMap);
            }
            return conceptMap;
        }

        @Override
        public QueueServicePointConceptMap getServicePointConceptMapByUuid(String uuid) {
            for (QueueServicePointConceptMap conceptMap : conceptMaps) {
                if (uuid != null && uuid.equals(conceptMap.getUuid())) {
                    return conceptMap;
                }
            }
            return null;
        }

        @Override
        public QueueServicePointConceptMap getServicePointConceptMapByConcept(Concept concept) {
            for (QueueServicePointConceptMap conceptMap : conceptMaps) {
                if (!Boolean.TRUE.equals(conceptMap.getVoided()) && Boolean.TRUE.equals(conceptMap.getActive())
                        && conceptMap.getServiceRequestedConcept().equals(concept)) {
                    return conceptMap;
                }
            }
            return null;
        }

        @Override
        public List<QueueServicePointConceptMap> getAllServicePointConceptMaps(boolean includeInactive) {
            return conceptMaps;
        }

        @Override
        public QueueEntry saveQueueEntry(QueueEntry queueEntry) {
            if (!entries.contains(queueEntry)) {
                entries.add(queueEntry);
            }
            return queueEntry;
        }

        @Override
        public QueueEntry getQueueEntry(Integer id) {
            for (QueueEntry entry : entries) {
                if (id != null && id.equals(entry.getId())) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public QueueEntry getQueueEntryByUuid(String uuid) {
            for (QueueEntry entry : entries) {
                if (uuid != null && uuid.equals(entry.getUuid())) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public QueueEntry getActiveQueueEntry(Patient patient, Location servicePoint, Date startOfDay, Date endOfDay,
                                              List<QueueStatus> activeStatuses) {
            for (QueueEntry entry : entries) {
                if (entryMatches(entry, servicePoint, null, startOfDay, endOfDay)
                        && entry.getPatient().equals(patient)
                        && activeStatuses.contains(entry.getStatus())) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public QueueEntry getActiveQueueEntry(Patient patient, Date startOfDay, Date endOfDay,
                                              List<QueueStatus> activeStatuses) {
            QueueEntry latest = null;
            for (QueueEntry entry : entries) {
                if (entryMatches(entry, null, null, startOfDay, endOfDay)
                        && entry.getPatient().equals(patient)
                        && activeStatuses.contains(entry.getStatus())
                        && (latest == null || entry.getArrivalTime().after(latest.getArrivalTime()))) {
                    latest = entry;
                }
            }
            return latest;
        }

        @Override
        public int countQueueEntries(Location servicePoint, Date startOfDay, Date endOfDay) {
            int count = 0;
            for (QueueEntry entry : entries) {
                if (entryMatches(entry, servicePoint, null, startOfDay, endOfDay)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public List<QueueEntry> getQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay) {
            List<QueueEntry> matches = new ArrayList<QueueEntry>();
            for (QueueEntry entry : entries) {
                if (entryMatches(entry, location, status, startOfDay, endOfDay)) {
                    matches.add(entry);
                }
            }
            return matches;
        }

        @Override
        public int countQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay,
                                     Date currentDayStart, List<QueueStatus> activeStatuses, String patientName,
                                     QueueAssignmentFilter assignmentFilter,
                                     Collection<Integer> currentProviderIds) {
            return getPagedQueueEntries(location, status, startOfDay, endOfDay,
                    currentDayStart, activeStatuses, patientName, assignmentFilter, currentProviderIds).size();
        }

        @Override
        public List<QueueEntry> getQueueEntries(Location location, QueueStatus status,
                                                Date startOfDay, Date endOfDay, Date currentDayStart,
                                                List<QueueStatus> activeStatuses, String patientName,
                                                QueueAssignmentFilter assignmentFilter,
                                                Collection<Integer> currentProviderIds,
                                                int firstResult, int maxResults) {
            List<QueueEntry> matches = getPagedQueueEntries(location, status, startOfDay, endOfDay,
                    currentDayStart, activeStatuses, patientName, assignmentFilter, currentProviderIds);
            int fromIndex = Math.min(firstResult, matches.size());
            int toIndex = Math.min(fromIndex + maxResults, matches.size());
            return new ArrayList<QueueEntry>(matches.subList(fromIndex, toIndex));
        }

        @Override
        public List<QueueEntry> getQueueEntriesByServicePoint(Location servicePoint, Location visibleLocation,
                                                              QueueStatus status, Date startOfDay, Date endOfDay) {
            List<QueueEntry> matches = new ArrayList<QueueEntry>();
            for (QueueEntry entry : entries) {
                if (entryMatches(entry, visibleLocation, status, startOfDay, endOfDay)
                        && entry.getServicePoint().equals(servicePoint)) {
                    matches.add(entry);
                }
            }
            return matches;
        }

        @Override
        public List<QueueEntry> getQueueEntriesByVisit(Visit visit) {
            List<QueueEntry> matches = new ArrayList<QueueEntry>();
            for (QueueEntry entry : entries) {
                if (visit != null && visit.equals(entry.getVisit())) {
                    matches.add(entry);
                }
            }
            return matches;
        }

        @Override
        public List<QueueEntry> getQueueEntriesByVisits(Collection<Visit> visits) {
            List<QueueEntry> matches = new ArrayList<QueueEntry>();
            if (visits == null) {
                return matches;
            }
            for (QueueEntry entry : entries) {
                if (visits.contains(entry.getVisit())) {
                    matches.add(entry);
                }
            }
            return matches;
        }

        @Override
        public QueueStatusHistory saveQueueStatusHistory(QueueStatusHistory history) {
            histories.add(history);
            return history;
        }

        @Override
        public List<QueueStatusHistory> getQueueStatusHistory(QueueEntry queueEntry) {
            List<QueueStatusHistory> matches = new ArrayList<QueueStatusHistory>();
            for (QueueStatusHistory history : histories) {
                if (history.getQueueEntry().equals(queueEntry)) {
                    matches.add(history);
                }
            }
            return matches;
        }

        private boolean entryMatches(QueueEntry entry, Location location, QueueStatus status, Date startOfDay, Date endOfDay) {
            if (Boolean.TRUE.equals(entry.getVoided())) {
                return false;
            }
            if (status != null && !status.equals(entry.getStatus())) {
                return false;
            }
            if (location != null && !location.equals(entry.getSessionLocation())
                    && !location.equals(entry.getServicePoint())) {
                return false;
            }
            Date arrivalTime = entry.getArrivalTime();
            return arrivalTime != null && !arrivalTime.before(startOfDay) && arrivalTime.before(endOfDay);
        }

        private List<QueueEntry> getPagedQueueEntries(Location location, QueueStatus status,
                                                       Date startOfDay, Date endOfDay, Date currentDayStart,
                                                       List<QueueStatus> activeStatuses, String patientName,
                                                       QueueAssignmentFilter assignmentFilter,
                                                       Collection<Integer> currentProviderIds) {
            List<QueueEntry> matches = getQueueEntries(location, status, startOfDay, endOfDay);
            if (status == null && currentDayStart != null && activeStatuses != null) {
                List<QueueEntry> liveMatches = new ArrayList<QueueEntry>();
                for (QueueEntry entry : matches) {
                    if (activeStatuses.contains(entry.getStatus())
                            || !entry.getArrivalTime().before(currentDayStart)) {
                        liveMatches.add(entry);
                    }
                }
                matches = liveMatches;
            }
            if (patientName != null) {
                List<QueueEntry> nameMatches = new ArrayList<QueueEntry>();
                for (QueueEntry entry : matches) {
                    if (matchesPatientName(entry.getPatient(), patientName)) {
                        nameMatches.add(entry);
                    }
                }
                matches = nameMatches;
            }
            if (QueueAssignmentFilter.ASSIGNED_TO_ME.equals(assignmentFilter)
                    || QueueAssignmentFilter.NOT_ASSIGNED_TO_ME.equals(assignmentFilter)) {
                List<QueueEntry> assignmentMatches = new ArrayList<QueueEntry>();
                for (QueueEntry entry : matches) {
                    Integer assignedProviderId = entry.getAssignedProvider() == null
                            ? null : entry.getAssignedProvider().getId();
                    boolean assignedToCurrentProvider = assignedProviderId != null && currentProviderIds != null
                            && currentProviderIds.contains(assignedProviderId);
                    if (QueueAssignmentFilter.ASSIGNED_TO_ME.equals(assignmentFilter)
                            ? assignedToCurrentProvider : !assignedToCurrentProvider) {
                        assignmentMatches.add(entry);
                    }
                }
                matches = assignmentMatches;
            }
            Collections.sort(matches, new Comparator<QueueEntry>() {
                @Override
                public int compare(QueueEntry left, QueueEntry right) {
                    int priorityComparison = Integer.compare(left.getPriority().getSortWeight(),
                            right.getPriority().getSortWeight());
                    return priorityComparison != 0 ? priorityComparison
                            : left.getArrivalTime().compareTo(right.getArrivalTime());
                }
            });
            return matches;
        }

        private boolean matchesPatientName(Patient patient, String patientName) {
            if (patient == null) {
                return false;
            }
            String[] tokens = patientName.toLowerCase(Locale.ENGLISH).split("\\s+");
            for (String token : tokens) {
                boolean tokenMatched = false;
                for (PersonName name : patient.getNames()) {
                    if (!Boolean.TRUE.equals(name.getVoided()) && (containsNameToken(name.getGivenName(), token)
                            || containsNameToken(name.getMiddleName(), token)
                            || containsNameToken(name.getFamilyName(), token)
                            || containsNameToken(name.getFamilyName2(), token))) {
                        tokenMatched = true;
                        break;
                    }
                }
                if (!tokenMatched) {
                    return false;
                }
            }
            return true;
        }

        private boolean containsNameToken(String namePart, String token) {
            return namePart != null && namePart.toLowerCase(Locale.ENGLISH).contains(token);
        }
    }
}
