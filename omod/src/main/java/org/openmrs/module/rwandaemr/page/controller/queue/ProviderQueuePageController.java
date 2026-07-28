package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.Date;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProviderQueuePageController extends QueuePageSupport {

    public void get(PageModel model,
                    UiSessionContext sessionContext,
                    @SpringBean QueueService queueService,
                    @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                    @RequestParam(value = "status", required = false) String status) {
        if (!canViewQueue()) {
            model.addAttribute("authorized", false);
            return;
        }
        Location location = sessionContext.getSessionLocation();
        QueueStatus selectedStatus = parseStatus(status);
        Location servicePoint = getLocation(servicePointId);

        model.addAttribute("authorized", true);
        model.addAttribute("location", location);
        model.addAttribute("servicePoints", queueService.getServicePointLocations());
        model.addAttribute("selectedServicePoint", servicePoint);
        model.addAttribute("selectedStatus", selectedStatus == null ? "ALL" : selectedStatus.name());
        model.addAttribute("statuses", QueueStatus.values());
        model.addAttribute("priorities", QueuePriority.values());
        model.addAttribute("canCallPatient", canCallPatient());
        model.addAttribute("canManageQueue", canManageQueue());
        model.addAttribute("canTransferPatient", canTransferPatient());
        if (servicePoint == null) {
            model.addAttribute("entries", queueService.getQueueEntriesByLocation(location, selectedStatus, new Date()));
        } else {
            model.addAttribute("entries", queueService.getQueueEntriesByServicePoint(servicePoint, location, selectedStatus, new Date()));
        }
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean QueueService queueService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "entryId", required = false) Integer entryId,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                       @RequestParam(value = "destinationServicePointId", required = false) Integer destinationServicePointId,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "reason", required = false) String reason,
                       @RequestParam(value = "status", required = false) String status) {
        try {
            if ("callNext".equals(action)) {
                queueService.callNextPatient(getLocation(servicePointId), sessionContext.getSessionLocation());
            } else if ("openDashboard".equals(action)) {
                return openPatientDashboard(ui, queueService, entryId);
            } else {
                processEntryAction(queueService, action, entryId, destinationServicePointId, priority, reason);
            }
            setToast(sessionContext, "Queue updated");
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return redirect(ui, "queue/providerQueue", "servicePointId", servicePointId, "status", status);
    }
}
