package org.openmrs.module.rwandaemr;

import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Provides web utility methods around location tags
 */
public class LocationTagWebUtil {

    public static Location getLoginLocation(HttpSession session) {
        Integer locationId = (Integer) session.getAttribute(UiSessionContext.LOCATION_SESSION_ATTRIBUTE);
        if (locationId != null) {
            return Context.getLocationService().getLocation(locationId);
        }
        return null;
    }

    public static void setLoginLocation(Location location, UiSessionContext ctx, HttpServletResponse response) {
        ctx.setSessionLocation(location);
        response.addCookie(new Cookie("emr.lastSessionLocation", location.getLocationId().toString()));
    }
}
