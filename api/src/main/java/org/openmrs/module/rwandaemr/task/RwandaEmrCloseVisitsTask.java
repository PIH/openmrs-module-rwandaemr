package org.openmrs.module.rwandaemr.task;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Visit;
import org.openmrs.api.LocationService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.emrapi.adt.AdtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Custom implementation to close stale visits
 * This mimics the AdtService#CloseStaleVisitsTask, but is executed by Spring and adds additional logging
 */
public class RwandaEmrCloseVisitsTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static boolean isExecuting = false;

    @Override
    public void run() {
        if (isExecuting) {
            log.info(getClass() + " is still executing, not running again");
            return;
        }
        isExecuting = true;
        try {
            log.info("Executing " + getClass());
            StopWatch sw = new StopWatch();
            sw.start();

            AdtService adtService = Context.getService(AdtService.class);
            VisitService visitService = Context.getVisitService();
            LocationService locationService = Context.getLocationService();

            LocationTag visitLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_VISITS);
            List<Location> locations = locationService.getLocationsByTag(visitLocationTag);

            if (locations == null || locations.isEmpty()) {
                log.error("Unable to close stale visits, no locations with Visit Location tag");
                return;
            }

            List<Visit> openVisits = visitService.getVisits(null, null, locations, null, null, null, null, null, null, false, false);
            log.info("Found " + openVisits.size() + " open visits");
            int numVisitsClosed = 0;
            for (Visit visit : openVisits) {
                if (adtService.shouldBeClosed(visit)) {
                    try {
                        adtService.closeAndSaveVisit(visit);
                        numVisitsClosed++;
                    }
                    catch (Exception ex) {
                        log.warn("Failed to close inactive visit " + visit, ex);
                    }
                }
            }
            sw.stop();
            log.info("Getting open visits completed.");
            log.info(getClass() + " Completed in " + sw + " - " + numVisitsClosed + " Visits Closed");
        }
        finally {
            isExecuting = false;
        }
    }
}
