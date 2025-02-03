package org.openmrs.module.rwandaemr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides utility methods to retrieve locations by tag and ensure location tags are configured appropriately
 */
@Component
public class LocationTagUtil {

    protected static Log log = LogFactory.getLog(LocationTagUtil.class);

    public static final String MULTI_FACILITY = "multiFacility";
    public static final String MULTI_DEPARTMENT = "multiDepartment";
    public static final String SINGLE_LOCATION = "singleLocation";

    private final LocationService locationService;

    public LocationTagUtil(@Autowired LocationService locationService) {
        this.locationService = locationService;
    }

    public LocationTag getVisitLocationTag() {
        return locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_VISITS);
    }

    public LocationTag getLoginLocationTag() {
        return locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
    }

    public List<Location> sortLocations(List<Location> locations) {
        locations.sort(Comparator.comparing(Location::getName));
        return locations;
    }

    public boolean isVisitLocation(Location location) {
        return location.getTags() != null && location.getTags().contains(getVisitLocationTag());
    }

    public boolean isLoginLocation(Location location) {
        return location.getTags() != null && location.getTags().contains(getLoginLocationTag());
    }

    public List<Location> getVisitLocations() {
        return sortLocations(locationService.getLocationsByTag(getVisitLocationTag()));
    }

    public List<Location> getLoginLocations() {
        return sortLocations(locationService.getLocationsByTag(getLoginLocationTag()));
    }

    public List<Location> getVisitLocationsForLocation(Location location) {
        List<Location> l = getAncestorsWithTag(location, getVisitLocationTag());
        if (isVisitLocation(location)) {
            l.add(location);
        }
        return sortLocations(l);
    }

    public List<Location> getLoginLocationsForLocation(Location location) {
        List<Location> l = getDescendentsWithTag(location, getLoginLocationTag());
        if (isLoginLocation(location)) {
            l.add(location);
        }
        return sortLocations(l);
    }

    // Should be tagged as a Visit Location, with no parent that is a visit location, or children that are visit locations
    public boolean isValidVisitLocation(Location location) {
        if (isVisitLocation(location)) {
            List<Location> ancestorVisitLocations = getAncestorsWithTag(location, getVisitLocationTag());
            List<Location> descendentVisitLocations = getDescendentsWithTag(location, getVisitLocationTag());
            return ancestorVisitLocations.isEmpty() && descendentVisitLocations.isEmpty();
        }
        return false;
    }

    // Should be tagged as a Login Location, with only a single valid visit location, and no ancestor or descendent login locations
    public boolean isValidLoginLocation(Location location) {
        if (isLoginLocation(location)) {
            List<Location> visitLocationsForLogin = getVisitLocationsForLocation(location);
            List<Location> ancestorLoginLocations = getAncestorsWithTag(location, getLoginLocationTag());
            List<Location> descendentLoginLocations = getDescendentsWithTag(location, getLoginLocationTag());
            if (visitLocationsForLogin.size() == 1 && ancestorLoginLocations.isEmpty() && descendentLoginLocations.isEmpty()) {
                return isValidVisitLocation(visitLocationsForLogin.get(0));
            }
        }
        return false;
    }

    public List<Location> getValidVisitLocations() {
        List<Location> l = new ArrayList<>();
        for (Location visitLocation : getVisitLocations()) {
            if (isValidVisitLocation(visitLocation)) {
                l.add(visitLocation);
            }
        }
        return sortLocations(l);
    }

    public List<Location> getInvalidVisitLocations() {
        List<Location> l = new ArrayList<>();
        for (Location visitLocation : getVisitLocations()) {
            if (!isValidVisitLocation(visitLocation)) {
                l.add(visitLocation);
            }
        }
        return sortLocations(l);
    }

    public List<Location> getValidLoginLocations() {
        List<Location> l = new ArrayList<>();
        for (Location loginLocation : getLoginLocations()) {
            if (isValidLoginLocation(loginLocation)) {
                l.add(loginLocation);
            }
        }
        return sortLocations(l);
    }

    public List<Location> getInvalidLoginLocations() {
        List<Location> l = new ArrayList<>();
        for (Location loginLocation : getLoginLocations()) {
            if (!isValidLoginLocation(loginLocation)) {
                l.add(loginLocation);
            }
        }
        return sortLocations(l);
    }

    public Map<Location, List<Location>> getValidVisitAndLoginLocations() {
        Map<Location, List<Location>> ret = new LinkedHashMap<>();
        List<Location> visitLocations = getValidVisitLocations();
        for (Location visitLocation : visitLocations) {
            ret.put(visitLocation, getLoginLocationsForLocation(visitLocation));
        }
        return ret;
    }

    public boolean isLocationSetupRequired() {
        if (getVisitLocations().isEmpty()) {
            return true;
        }
        if (getLoginLocations().isEmpty()) {
            return true;
        }
        if (!getInvalidLoginLocations().isEmpty()) {
            return true;
        }
        if (!getInvalidVisitLocations().isEmpty()) {
            return true;
        }
        return false;
    }

    public String getConfiguredSystemType() {
        if (!isLocationSetupRequired()) {
            int numVisitLocations = getValidVisitLocations().size();
            if (numVisitLocations > 1) {
                return MULTI_FACILITY;
            }
            else {
                int numLoginLocations = getValidLoginLocations().size();
                if (numLoginLocations > 1) {
                    return MULTI_DEPARTMENT;
                }
                else {
                    return SINGLE_LOCATION;
                }
            }
        }
        return "";
    }

    /**
     * @return any parent location, or parent of parent, etc. of the location that is tagged with the given tag
     */
    public List<Location> getAncestorsWithTag(Location location, LocationTag locationTag) {
        List<Location> ret = new ArrayList<>();
        Location parentLocation = location.getParentLocation();
        if (parentLocation != null) {
            if (parentLocation.getTags() != null && parentLocation.getTags().contains(locationTag)) {
                ret.add(parentLocation);
            }
            ret.addAll(getAncestorsWithTag(parentLocation, locationTag));
        }
        return ret;
    }

    /**
     * @return any child location, or child of child, etc. of the location that is tagged with the given tag
     */
    public List<Location> getDescendentsWithTag(Location location, LocationTag locationTag) {
        List<Location> ret = new ArrayList<>();
        if (location.getChildLocations() != null) {
            for (Location childLocation : location.getChildLocations()) {
                if (childLocation.getTags() != null && childLocation.getTags().contains(locationTag)) {
                    ret.add(childLocation);
                }
                ret.addAll(getDescendentsWithTag(childLocation, locationTag));
            }
        }
        return ret;
    }
}
