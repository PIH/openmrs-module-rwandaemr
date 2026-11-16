package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.Date;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QueueDisplayPageController extends QueuePageSupport {

    public void get(PageModel model,
                    UiSessionContext sessionContext,
                    @SpringBean QueueService queueService,
                    @RequestParam(value = "servicePointId", required = false) Integer servicePointId) {
        if (!canViewQueue()) {
            model.addAttribute("authorized", false);
            return;
        }
        Location location = sessionContext.getSessionLocation();
        Location servicePoint = getLocation(servicePointId);
        model.addAttribute("authorized", true);
        model.addAttribute("location", location);
        model.addAttribute("servicePoints", queueService.getServicePointLocations());
        model.addAttribute("selectedServicePoint", servicePoint);
        model.addAttribute("calledEntries", servicePoint == null ? queueService.getQueueEntriesByLocation(location, QueueStatus.CALLED, new Date()) :
                queueService.getQueueEntriesByServicePoint(servicePoint, location, QueueStatus.CALLED, new Date()));
        model.addAttribute("waitingEntries", servicePoint == null ? queueService.getQueueEntriesByLocation(location, QueueStatus.WAITING, new Date()) :
                queueService.getQueueEntriesByServicePoint(servicePoint, location, QueueStatus.WAITING, new Date()));
    }
}
