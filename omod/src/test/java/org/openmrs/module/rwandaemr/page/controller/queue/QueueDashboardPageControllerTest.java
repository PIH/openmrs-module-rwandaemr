package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import groovy.text.SimpleTemplateEngine;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.queue.QueueAssignmentFilter;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;

public class QueueDashboardPageControllerTest {

    @Test
    public void shouldDefaultToTenPatientsPerPageAndRejectUnsupportedSizes() {
        assertEquals(Arrays.asList(10, 20, 40, 60, 80, 100),
                QueueDashboardPageController.PAGE_SIZE_OPTIONS);
        assertEquals(10, QueueDashboardPageController.normalizePageSize(null));
        assertEquals(10, QueueDashboardPageController.normalizePageSize(15));
        assertEquals(100, QueueDashboardPageController.normalizePageSize(100));
    }

    @Test
    public void shouldCalculateAndClampPageNumbers() {
        assertEquals(1, QueueDashboardPageController.calculateTotalPages(0, 10));
        assertEquals(3, QueueDashboardPageController.calculateTotalPages(21, 10));
        assertEquals(1, QueueDashboardPageController.normalizePage(-2, 3));
        assertEquals(3, QueueDashboardPageController.normalizePage(8, 3));
    }

    @Test
    public void shouldBuildACompactFivePageWindow() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 5),
                QueueDashboardPageController.buildPageNumbers(1, 12));
        assertEquals(Arrays.asList(5, 6, 7, 8, 9),
                QueueDashboardPageController.buildPageNumbers(7, 12));
        assertEquals(Arrays.asList(8, 9, 10, 11, 12),
                QueueDashboardPageController.buildPageNumbers(12, 12));
    }

    @Test
    public void shouldNormalizeAndResolveArrivalDay() {
        Calendar reference = Calendar.getInstance();
        reference.set(2026, Calendar.AUGUST, 19, 10, 15, 0);

        Calendar yesterday = Calendar.getInstance();
        yesterday.setTime(QueueDashboardPageController.getArrivalDate("yesterday", reference.getTime()));

        assertEquals("TODAY", QueueDashboardPageController.normalizeArrivalDay(null));
        assertEquals("TODAY", QueueDashboardPageController.normalizeArrivalDay("unsupported"));
        assertEquals("YESTERDAY", QueueDashboardPageController.normalizeArrivalDay("yesterday"));
        assertEquals(2026, yesterday.get(Calendar.YEAR));
        assertEquals(Calendar.AUGUST, yesterday.get(Calendar.MONTH));
        assertEquals(18, yesterday.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void shouldNormalizeProviderAssignmentFilter() {
        assertEquals(QueueAssignmentFilter.ALL,
                QueueDashboardPageController.normalizeAssignmentFilter(null));
        assertEquals(QueueAssignmentFilter.ALL,
                QueueDashboardPageController.normalizeAssignmentFilter("unsupported"));
        assertEquals(QueueAssignmentFilter.ASSIGNED_TO_ME,
                QueueDashboardPageController.normalizeAssignmentFilter("assigned_to_me"));
    }

    @Test
    public void shouldMapConfiguredPhoneNumbersForDisplayedPatients() {
        PersonAttributeType phoneNumberType = new PersonAttributeType();
        phoneNumberType.setId(7);
        Patient patient = new Patient(23);
        patient.addAttribute(new PersonAttribute(phoneNumberType, " 0788123456 "));
        QueueEntry entry = new QueueEntry();
        entry.setPatient(patient);
        RwandaEmrConfig rwandaEmrConfig = mock(RwandaEmrConfig.class);
        when(rwandaEmrConfig.getTelephoneNumber()).thenReturn(phoneNumberType);

        Map<Integer, String> phoneNumbers = QueueDashboardPageController.getPhoneNumberByPatientId(
                Collections.singletonList(entry), rwandaEmrConfig);

        assertEquals(Collections.singletonMap(23, "0788123456"), phoneNumbers);
    }

    @Test
    public void shouldUseLiveQueueWindowForDashboardLocation() {
        QueueService queueService = mock(QueueService.class);
        Location reception = new Location();
        Date referenceTime = new Date();
        List<QueueEntry> expectedEntries = Collections.singletonList(new QueueEntry());
        when(queueService.getQueueEntriesByLocation(reception, QueueStatus.WAITING, referenceTime))
                .thenReturn(expectedEntries);
        QueuePageSupport pageSupport = new QueuePageSupport() {
            @Override
            protected boolean canViewAllLocations() {
                return false;
            }
        };

        List<QueueEntry> entries = pageSupport.getEntriesForLocation(
                queueService, reception, QueueStatus.WAITING, referenceTime);

        assertSame(expectedEntries, entries);
        verify(queueService).getQueueEntriesByLocation(reception, QueueStatus.WAITING, referenceTime);
        verify(queueService, never()).getQueueEntriesByLocation(
                reception, QueueStatus.WAITING, referenceTime, referenceTime);
    }

    @Test
    public void shouldLoadOnlyTheRequestedDashboardPageFromTheQueueService() {
        final Location reception = new Location(5);
        QueueDashboardPageController controller = new QueueDashboardPageController() {
            @Override
            protected boolean canViewQueue() {
                return true;
            }

            @Override
            protected boolean canViewAllLocations() {
                return false;
            }

            @Override
            protected boolean canManageQueue() {
                return false;
            }

            @Override
            protected boolean canCallPatient() {
                return false;
            }

            @Override
            protected boolean canTransferPatient() {
                return false;
            }

            @Override
            protected Set<Integer> getCurrentProviderIds(ProviderService providerService) {
                return Collections.singleton(77);
            }
        };
        QueueService queueService = mock(QueueService.class);
        PageModel model = mock(PageModel.class);
        UiSessionContext sessionContext = mock(UiSessionContext.class);
        RwandaEmrConfig rwandaEmrConfig = mock(RwandaEmrConfig.class);
        EncounterService encounterService = mock(EncounterService.class);
        ProviderService providerService = mock(ProviderService.class);
        QueueEntry displayedEntry = new QueueEntry();
        when(sessionContext.getSessionLocation()).thenReturn(reception);
        when(queueService.getServicePointLocations()).thenReturn(Collections.singletonList(reception));
        when(queueService.isLaboratoryServicePoint(reception)).thenReturn(true);
        when(queueService.countQueueEntriesByLocation(
                eq(reception), eq(QueueStatus.WAITING), any(Date.class), any(Date.class), eq("Aline Uwase"),
                eq(QueueAssignmentFilter.ASSIGNED_TO_ME), eq(Collections.singleton(77))))
                .thenReturn(21);
        when(queueService.getQueueEntriesByLocation(
                eq(reception), eq(QueueStatus.WAITING), any(Date.class), any(Date.class), eq("Aline Uwase"),
                eq(QueueAssignmentFilter.ASSIGNED_TO_ME), eq(Collections.singleton(77)),
                eq(10), eq(10)))
                .thenReturn(Collections.singletonList(displayedEntry));
        when(queueService.getQueueEntriesByVisits(Collections.emptySet())).thenReturn(Collections.<QueueEntry>emptyList());

        controller.get(model, sessionContext, queueService, rwandaEmrConfig, encounterService, providerService,
                null, "WAITING", "YESTERDAY", "  Aline Uwase  ", "ASSIGNED_TO_ME", 2, 10);

        verify(queueService).countQueueEntriesByLocation(
                eq(reception), eq(QueueStatus.WAITING), any(Date.class), any(Date.class), eq("Aline Uwase"),
                eq(QueueAssignmentFilter.ASSIGNED_TO_ME), eq(Collections.singleton(77)));
        verify(queueService).getQueueEntriesByLocation(
                eq(reception), eq(QueueStatus.WAITING), any(Date.class), any(Date.class), eq("Aline Uwase"),
                eq(QueueAssignmentFilter.ASSIGNED_TO_ME), eq(Collections.singleton(77)),
                eq(10), eq(10));
        verify(queueService, never()).getQueueEntriesByLocation(
                eq(reception), eq(QueueStatus.WAITING), any(Date.class));
        verify(model).addAttribute("selectedArrivalDay", "YESTERDAY");
        verify(model).addAttribute("patientName", "Aline Uwase");
        verify(model).addAttribute("selectedAssignment", "ASSIGNED_TO_ME");
        verify(model).addAttribute("laboratoryLocation", true);
        verify(providerService, never()).getAllProviders(false);
    }

    @Test
    public void shouldCallPatientAndOpenLabOrderList() {
        QueueDashboardPageController controller = new QueueDashboardPageController();
        QueueService queueService = mock(QueueService.class);
        UiUtils ui = mock(UiUtils.class);
        UiSessionContext sessionContext = mock(UiSessionContext.class);
        EncounterService encounterService = mock(EncounterService.class);
        ProviderService providerService = mock(ProviderService.class);
        Patient patient = new Patient(23);
        patient.addIdentifier(new PatientIdentifier("RW-00123", null, null));
        QueueEntry entry = new QueueEntry();
        entry.setPatient(patient);
        when(queueService.getQueueEntry(45)).thenReturn(entry);
        when(ui.pageLink("pihapps", "labs/labOrderList")).thenReturn("/pihapps/labs/labOrderList.page");

        String redirect = controller.post(ui, sessionContext, queueService, encounterService, providerService,
                "openLabOrders", 45, null, null, null, null, null, null,
                "ALL", "TODAY", "", "ALL", 1, 10);

        assertEquals("redirect:/pihapps/labs/labOrderList.page", redirect);
        verify(queueService).callPatient(entry);
    }

    @Test
    public void shouldCompleteOrCancelAnEntryFromTheDashboard() {
        QueueDashboardPageController controller = new QueueDashboardPageController();
        QueueService queueService = mock(QueueService.class);
        UiUtils ui = mock(UiUtils.class);
        UiSessionContext sessionContext = mock(UiSessionContext.class);
        HttpSession session = mock(HttpSession.class);
        EncounterService encounterService = mock(EncounterService.class);
        ProviderService providerService = mock(ProviderService.class);
        QueueEntry completedEntry = new QueueEntry();
        QueueEntry cancelledEntry = new QueueEntry();
        when(sessionContext.getSession()).thenReturn(session);
        when(ui.pageLink("rwandaemr", "queue/queueDashboard")).thenReturn("/queue-dashboard");
        when(queueService.getQueueEntry(41)).thenReturn(completedEntry);
        when(queueService.getQueueEntry(42)).thenReturn(cancelledEntry);

        controller.post(ui, sessionContext, queueService, encounterService, providerService, "complete", 41,
                null, 1, null, null, null, null, "ALL", "TODAY", "Aline", "ALL", 1, 10);
        controller.post(ui, sessionContext, queueService, encounterService, providerService, "cancel", 42,
                null, 1, null, null, null, "Cancelled from queue dashboard", "ALL", "TODAY", "Aline", "ALL", 1, 10);

        verify(queueService).completeService(completedEntry);
        verify(queueService).cancelQueueEntry(cancelledEntry, "Cancelled from queue dashboard");
        verify(session, org.mockito.Mockito.times(2)).setAttribute(
                UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, "Queue updated");
    }

    @Test
    public void shouldSendSelectedProviderWithTransferredPatient() {
        Location destination = new Location(8);
        QueueDashboardPageController controller = new QueueDashboardPageController() {
            @Override
            protected Location getLocation(Integer locationId) {
                return destination;
            }
        };
        QueueService queueService = mock(QueueService.class);
        UiUtils ui = mock(UiUtils.class);
        UiSessionContext sessionContext = mock(UiSessionContext.class);
        HttpSession session = mock(HttpSession.class);
        EncounterService encounterService = mock(EncounterService.class);
        ProviderService providerService = mock(ProviderService.class);
        QueueEntry entry = new QueueEntry();
        Provider provider = new Provider(19);
        when(sessionContext.getSession()).thenReturn(session);
        when(ui.pageLink("rwandaemr", "queue/queueDashboard")).thenReturn("/queue-dashboard");
        when(queueService.getQueueEntry(44)).thenReturn(entry);
        when(providerService.getProvider(19)).thenReturn(provider);

        controller.post(ui, sessionContext, queueService, encounterService, providerService, "transfer", 44,
                null, 1, 8, 19, null, "Needs consultation", "ALL", "YESTERDAY", "Aline", "ALL", 1, 10);

        verify(queueService).transferPatient(entry, destination, "Needs consultation", provider);
    }

    @Test
    public void shouldUpdateAssignedProviderFromTheDashboard() {
        QueueDashboardPageController controller = new QueueDashboardPageController();
        QueueService queueService = mock(QueueService.class);
        UiUtils ui = mock(UiUtils.class);
        UiSessionContext sessionContext = mock(UiSessionContext.class);
        HttpSession session = mock(HttpSession.class);
        EncounterService encounterService = mock(EncounterService.class);
        ProviderService providerService = mock(ProviderService.class);
        QueueEntry entry = new QueueEntry();
        Provider provider = new Provider(19);
        when(sessionContext.getSession()).thenReturn(session);
        when(ui.pageLink("rwandaemr", "queue/queueDashboard")).thenReturn("/queue-dashboard");
        when(queueService.getQueueEntry(44)).thenReturn(entry);
        when(providerService.getProvider(19)).thenReturn(provider);

        controller.post(ui, sessionContext, queueService, encounterService, providerService, "updateProvider", 44,
                null, 1, null, 19, null, null, "ALL", "TODAY", "Aline", "ASSIGNED_TO_ME", 1, 10);

        verify(queueService).updateAssignedProvider(entry, provider);
    }

    @Test
    public void shouldParseChangedQueueTemplates() throws Exception {
        for (String templatePath : Arrays.asList(
                "src/main/webapp/pages/queue/queueDashboard.gsp",
                "src/main/webapp/pages/queue/providerQueue.gsp",
                "src/main/webapp/pages/queue/servicePointQueue.gsp",
                "src/main/webapp/fragments/queue/transferReasonDialog.gsp",
                "src/main/webapp/fragments/queue/providerAssignmentDialog.gsp")) {
            File template = new File(templatePath);
            assertTrue(template.isFile(), templatePath);
            new SimpleTemplateEngine().createTemplate(template);
        }
    }
}
