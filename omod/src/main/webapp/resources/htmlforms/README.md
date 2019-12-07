HtmlForms should be kept version controlled within this directory or a subdirectory.

This will allow live re-loading of these htmlforms during development, if using the ui framework 2.0, 
and also will enable us to configure the system to ensure these forms are up-to-date in the deployed application.

To enable changes to edited forms to be immediately available during development:

1. Put each of the forms into this directory (omod/src/main/webapp/resources/htmlforms)
  * These file names should be URL friendly (eg. no spaces, use camel case)
  * These forms should have an ".xml" extension
  * These forms will need to contain references to the form name, uuid, and encounter type within the htmlform tag
  * See test.xml for an example
2. Enable development mode on this module
  * Using the SDK, assuming a serverId = "rwink":
    * Go into the root directory for this module code (eg. ~/code/rwandaemr)
    * From this directory, run "mvn openmrs-sdk:watch -DserverId=rwandaemr"
3. Start up the server
  * Using the SDK, assuming a serverId = "rwandaemr":
    * mvn openmrs-sdk:run -DserverId=rwandaemr
4. Load the htmlform:
  * There is a page controller within the rwandaemr module that can be used to create/edit/view an htmlform:
    * For example, to enter a new form contained in the htmlforms folder as "test.xml", for a particular patient:
      * http://localhost:8080/openmrs/rwandaemr/htmlForm.page?patient=0000499a-727d-4161-b7a8-259be9e962c2&formName=test&editMode=true
5. Verify that changing the form will allow you to hot-reload changes:
  * Make some sort of a change to "test.xml"
  * Re-load the page above and confirm that the change is there
  
**IMPORTANT NOTE:**

The step of loading the htmlform via the UI in step 4 above has the side effect of saving a new form or updating an existing form
in the database with the information provided in the htmlform tag, specifically formUuid, formName, formEncounterType, formVersion

So there is no longer an explicit step of saving forms to the database, but there is some risk in overwriting existing forms
if errors are made when moving to this approach.  Care should be taken when migrating to ensure existing forms are not lost by 
backing up your htmlform table before moving to this approach.
