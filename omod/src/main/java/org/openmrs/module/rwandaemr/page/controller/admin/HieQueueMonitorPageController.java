/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.web.HieQueueMonitorService;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin page for monitoring and managing HIE queue files.
 */
@Controller
public class HieQueueMonitorPageController {

    private static final String REQUIRED_PRIVILEGE = "App: coreapps.systemAdministration";

    public void get(PageModel model,
                    @SpringBean HieQueueMonitorService hieQueueMonitorService,
                    @RequestParam(value = "queue", required = false) String queue,
                    @RequestParam(value = "status", required = false) String status,
                    @RequestParam(value = "q", required = false) String q) {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            model.addAttribute("authorized", false);
            return;
        }

        String selectedQueue = normalizeQueue(queue);
        String selectedStatus = normalizeStatus(status);

        model.addAttribute("authorized", true);
        model.addAttribute("dashboard", hieQueueMonitorService.getDashboard(selectedQueue, selectedStatus, q));
        model.addAttribute("queueDefinitions", hieQueueMonitorService.getQueueDefinitions());
        model.addAttribute("statusOptions", hieQueueMonitorService.getStatusOptions());
        model.addAttribute("selectedQueue", selectedQueue);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("search", StringUtils.defaultString(q));
    }

    public String post(UiUtils ui, UiSessionContext sessionContext,
                       @SpringBean HieQueueMonitorService hieQueueMonitorService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "queueType", required = false) String queueType,
                       @RequestParam(value = "fileName", required = false) String fileName,
                       @RequestParam(value = "filterQueue", required = false) String filterQueue,
                       @RequestParam(value = "filterStatus", required = false) String filterStatus,
                       @RequestParam(value = "filterQ", required = false) String filterQ) {

        if (!Context.hasPrivilege(REQUIRED_PRIVILEGE)) {
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
                    "You do not have permission to manage HIE queues");
            return redirectToMonitor(ui, filterQueue, filterStatus, filterQ);
        }

        try {
            String message;
            if ("retry".equals(action)) {
                message = hieQueueMonitorService.retryItem(queueType, fileName);
            } else if ("delete".equals(action)) {
                message = hieQueueMonitorService.deleteItem(queueType, fileName);
            } else if ("process".equals(action)) {
                message = hieQueueMonitorService.processQueue(queueType);
            } else {
                throw new IllegalArgumentException("Unsupported queue action: " + action);
            }
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, message);
        }
        catch (Exception e) {
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
        }

        return redirectToMonitor(ui, filterQueue, filterStatus, filterQ);
    }

    private String normalizeQueue(String queue) {
        return StringUtils.isBlank(queue) ? HieQueueMonitorService.QUEUE_ALL : queue;
    }

    private String normalizeStatus(String status) {
        return StringUtils.isBlank(status) ? HieQueueMonitorService.STATUS_ALL : status;
    }

    private String redirectToMonitor(UiUtils ui, String queue, String status, String search) {
        List<String> params = new ArrayList<String>();
        addParam(params, "queue", normalizeQueue(queue));
        addParam(params, "status", normalizeStatus(status));
        addParam(params, "q", StringUtils.defaultString(search));
        return "redirect:" + ui.pageLink("rwandaemr", "admin/hieQueueMonitor") + "?" + StringUtils.join(params, "&");
    }

    private void addParam(List<String> params, String name, String value) {
        params.add(encode(name) + "=" + encode(value));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }
}
