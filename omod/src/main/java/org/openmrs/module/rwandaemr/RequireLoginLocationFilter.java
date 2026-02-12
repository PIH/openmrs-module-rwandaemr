/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr;

import org.apache.log4j.Logger;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.ui.framework.WebConstants;
import org.openmrs.util.ConfigUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Redirects an authenticated user to the login location selection page if they do not have a login location set in their session
 */
public class RequireLoginLocationFilter implements Filter {

	private static final Logger log = Logger.getLogger(RequireLoginLocationFilter.class);

	public static final String LOGIN_LOCATION_PAGE = "/" + WebConstants.CONTEXT_PATH + "/rwandaemr/loginLocation.page";

	public static final List<String> EXCLUSION_EXTENSIONS = Arrays.asList(
			"js", "css", "gif", "jpg", "jpeg", "png", ".ttf", ".woff", ".action", "/csrfguard",
			"/rwandaemr/admin/configureLoginLocations.page", "/ws/rest/v1/irembopay/status", "/ws/rest/v1/irembopay/callback"
	);

	public boolean disabled = false;

	@Override
	public void init(FilterConfig filterConfig) {
		String value = ConfigUtil.getRuntimeProperty("rwandaemr.disableRequireLoginLocationFilter");
		disabled = Boolean.parseBoolean(value);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (!disabled) {
			if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
				HttpServletRequest httpRequest = (HttpServletRequest) request;
				HttpServletResponse httpResponse = (HttpServletResponse) response;
				HttpSession session = httpRequest.getSession();
				User currentUser = Context.getAuthenticatedUser();
				if (currentUser != null) {
					// Only redirect for page requests, not for included resources
					if (!isExcluded(httpRequest.getRequestURI())) {
						if (LocationTagWebUtil.getLoginLocation(session) == null) {
							log.debug("Redirecting " + currentUser + " from " + httpRequest.getRequestURI() + " to " + LOGIN_LOCATION_PAGE);
							httpResponse.sendRedirect(LOGIN_LOCATION_PAGE);
							return;
						}
					}
				}
			}
		}
		chain.doFilter(request, response);
	}

	public boolean isExcluded(String uri) {
		if (uri.equals(LOGIN_LOCATION_PAGE)) {
			return true;
		}
		for (String extension : EXCLUSION_EXTENSIONS) {
			if (uri.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void destroy() {
	}
}
