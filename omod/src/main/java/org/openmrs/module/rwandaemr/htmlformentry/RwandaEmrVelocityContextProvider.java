package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.velocity.VelocityContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.context.Context;
import org.openmrs.api.LocationService;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.velocity.VelocityContextContentProvider;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.mohappointment.model.Services;
import org.openmrs.module.mohappointment.utils.AppointmentUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RwandaEmrVelocityContextProvider implements VelocityContextContentProvider {

    protected static Log log = LogFactory.getLog(RwandaEmrVelocityContextProvider.class);

    @Override
    public void populateContext(FormEntrySession formEntrySession, VelocityContext velocityContext) {

        // Add in appointment services
        List<String> appointmentServiceConceptUuids = new ArrayList<>();
        List<String> appointmentServiceNames = new ArrayList<>();

        List<Services> services = AppointmentUtil.getAllServices();
        services.sort(Comparator.comparing(Services::getName));

        for (Services service : services) {
            if (service.getConcept() != null) {
                appointmentServiceConceptUuids.add(service.getConcept().getUuid());
                appointmentServiceNames.add(service.getName());
            }
        }
        formEntrySession.addToVelocityContext("appointmentServiceConceptUuids", String.join(",", appointmentServiceConceptUuids));
        formEntrySession.addToVelocityContext("appointmentServiceConceptNames", String.join(",", appointmentServiceNames));

        List<String> loginLocationUuids = new ArrayList<>();
        List<String> loginLocationNames = new ArrayList<>();
        for (Location location : getLoginLocations()) {
            loginLocationUuids.add(location.getUuid());
            loginLocationNames.add(location.getName());
        }
        formEntrySession.addToVelocityContext("loginLocationUuids", String.join(",", loginLocationUuids));
        formEntrySession.addToVelocityContext("loginLocationNames", String.join(",", loginLocationNames));
        formEntrySession.addToVelocityContext("loginLocationTagName", EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
    }

    private List<Location> getLoginLocations() {
        LocationService locationService = Context.getLocationService();
        LocationTag loginLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
        if (loginLocationTag == null) {
            log.warn("Location tag was not found: " + EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
            return new ArrayList<>();
        }
        List<Location> loginLocations = locationService.getLocationsByTag(loginLocationTag);
        loginLocations.sort(Comparator.comparing(Location::getName));
        if (loginLocations.isEmpty()) {
            log.warn("No locations are tagged as " + EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
        }
        return loginLocations;
    }
}
