package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;

import groovy.text.SimpleTemplateEngine;
import org.junit.jupiter.api.Test;

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
    public void shouldParseQueueDashboardTemplate() throws Exception {
        File template = new File("src/main/webapp/pages/queue/queueDashboard.gsp");

        assertTrue(template.isFile());
        new SimpleTemplateEngine().createTemplate(template);
    }
}
