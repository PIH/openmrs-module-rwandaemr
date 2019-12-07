openmrs-module-rwandaemr
===================================

Using the OpenMRS SDK to create a Rwanda development environment
---------------------------------------------------------------

1. Install the OpenMRS SDK as described here: https://wiki.openmrs.org/display/docs/OpenMRS+SDK#OpenMRSSDK-Installation
2. Clone this project
3. Run "mvn clean install" on this project
4. Make sure that the "properties" variables in the main rwandaemr pom are all set the proper versions of modules you want to run
5. Run "mvn openmrs-sdk:setup" (from any directory) --
    **Note**: if the build fails at any time during this process because it is saying it can't find a module in the Maven repo, you may need
    to check out that module and install it locally ("mvn clean install")
    1. Pick the name of the server (this will determine the subdirectory off ~/openmrs/ where it created the information for this server--in my case, I used 'rwanda'
    2. For "Distribution" or "Platform" chose "Distribution"
    3. For distribution version chose "Other...."
    4. For the distribution, use this module "org.openmrs.module:rwandaemr:1.0-SNAPSHOT" (or whatever the current version of this module is)
    5. Choose the port to run tomcat on (usually 8080)
    5. Choose port to debug on (usually 1044)
    6. Chose how you want to use MySQL... via a local install of MySQL or docker
    7. For the DB name, chose the existing Rwanda database you want to use: make sure to select the option to NOT overwrite the existing database
    8. Enter mysql user/password for a user that has rights to create databases, etc
    9. Chose the JAVA HOME you want to use (should be Java 7)

6. Run "mvn openmrs-sdk:run"
7. If everything starts up, but when you try to go to the login page (or any other page) you get a big stack trace that looks like it is due to Groovy, you need to remove the groovy jar file from the lib file.  Use the desktop file UI to open the openmrs-1.9.x.war file and remove the groovy jar from the logic omod.
   - Using the system file UI, double click the openmrs war file (ie. ~/openmrs/rwandaemr/openmrs-1.9.11.war)
   - navigate to the WEB-INF/bundledModules folder
   - find the logic omod and double click on the logic omod
   - find the lib/groovy jar file and delete the groovy jar file
   - delete the ~/openmrs/rwandaemr/tmp directory before running 'mvn openmrs-sdk:run'
   - run "mvn openmrs-sdk:run" again

Updating your Rwanda development environment when module versions change
------------------------------------------------------------------------

1. *From the base directory of this module*, run: "mvn openmrs-sdk:deploy -Ddistro=api/src/main/resources/openmrs-distro.properties"
2. Then just start the server via "mvn openmrs-sdk:run"


Other OpenMRS SDK tips and tricks
---------------------------------

You can use the OpenMRS SDK to "watch" modules (so that an openmrs-sdk:run automatically deploys any changes you make to those modules).  

For more details, read more about the SDK here:

https://wiki.openmrs.org/display/docs/OpenMRS+SDK#OpenMRSSDK-Installation
