<%
    config.require("label")
    config.require("formFieldName")

    // config supports initialValue that can be set on the form
    // config supports defaultValue that is used if the form value is not set
    // config supports withTag, which is a ; delimited list of location tags to include

    def initialValue = config.initialValue
    if (!initialValue && config.defaultValue) {
        if (config.defaultValue == 'visitLocationForSessionLocation') {
            initialValue = visitLocationForSessionLocation
        }
         else if (config.defaultValue == 'sessionLocation') {
            initialValue = sessionLocation
        }
    }
    if (initialValue instanceof org.openmrs.Location) {
        initialValue = ((org.openmrs.Location) initialValue).id.toString()
    }

    def options;
    def tagList = [];
    if (config.withTag) {
        tagArray = config.withTag.split(";")
        if(tagArray.size() >0){
            tagArray.each{ t ->
                def tag = t instanceof String ? context.locationService.getLocationTagByName(t) : t
                tagList.add(tag)
            }
        }
        options = context.locationService.getLocationsHavingAnyTag(tagList)
    } else {
        options = context.locationService.allLocations
    }
    options = options.collect {
        def selected = (it.id.toString() == initialValue);
        [ label: ui.format(it), value: it.id, selected: selected ]
    }
    options = options.sort { a, b -> a.label <=> b.label }
%>

${ ui.includeFragment("uicommons", "field/dropDown", [ options: options ] << config) }