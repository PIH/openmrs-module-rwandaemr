openmrs-module-rwandaemr
===================================

Using Docker to ensure a valid MySQL starter database is available
---------------------------------------------------------------

1. Create a starting directory containing a mysql data directory that can be mounted as a volume (eg. ~/environments/rwandaemr/mysqldata)
2. Create another directory on your machine that you want to make available to share files with your container: (eg. ~/environments/rwandaemr/share)
3. Assume you want to create a container called mysql-rwanda, with MySQL running on port 3308, with root password of "root" and linked to these directories
4. docker run --name mysql-rwanda -d -p 3308:3306 -e MYSQL_ROOT_PASSWORD=root -v ~/environments/rwandaemr/share:/rwandaemr -v ~/environments/rwandaemr/mysqldata:/var/lib/mysql mysql:5.6 --character-set-server=utf8 --collation-server=utf8_unicode_ci --max_allowed_packet=1G

NOTES:

* In some cases we found that we needed to upgrade module versions or exclude certain modules that are not in Maven.  See pom.xml for specific modules and versions.

Using the OpenMRS SDK to create a Rwanda development environment that uses this starter DB
---------------------------------------------------------------

1. Install the OpenMRS SDK as described here: https://wiki.openmrs.org/display/docs/OpenMRS+SDK#OpenMRSSDK-Installation
   * Note, you will need to ensure you are using openjdk-8-jdk for this, as Oracle JDK will not work.  You will also need openjdk-7-jdk to run the 1.9.x instance.
2. Clone this project
3. Run "mvn clean install" on this project
4. Make sure that the "properties" variables in the main rwandaemr pom are all set the proper versions of modules you want to run
5. Run "mvn openmrs-sdk:setup" (from any directory) --
    **Note**: if the build fails at any time during this process because it is saying it can't find a module in the Maven repo, you may need
    to check out that module and install it locally ("mvn clean install")
    1. Pick the name of the server (this will determine the subdirectory off ~/openmrs/ where it created the information for this server--in my case, I used 'rwandaemr'
    2. For "Distribution" or "Platform" chose "Distribution"
    3. For distribution version chose "Other...."
    4. For the distribution, use this module "org.openmrs.module:rwandaemr:1.0-SNAPSHOT" (or whatever the current version of this module is)
    5. Choose the port to run tomcat on (usually 8080)
    5. Choose port to debug on (usually 1044)
    6. Chose how you want to use MySQL... via a local install of MySQL or docker, you should use option #3 and point it to your DB created above.
    7. For the DB name, chose the existing Rwanda database you want to use: make sure to select the option to NOT overwrite the existing database
    8. For the DB url, you likely need to append some arguments like follows:   &zeroDateTimeBehavior=convertToNull
    9. Enter mysql user/password for a user that has rights to create databases, etc
    10. Chose the JAVA HOME you want to use (should be Java 7)

6. Run "mvn openmrs-sdk:run"

Troubleshooting:
------------------

* In Butaro, there is currently an issue with pre-existing billing tables that are causing issues.
  See:  https://pihemr.atlassian.net/browse/RWA-773

* There is an incompatiblity between the Logic Module and the UI Framework regarding Groovy if using the SDK. 
  If everything starts up, but when you try to go to the login page (or any other page) you get a big stack trace 
  that looks like it is due to Groovy, you need to remove the groovy jar file from the lib file.  
  Use the desktop file UI to open the openmrs-1.9.x.war file and remove the groovy jar from the logic omod.
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

-----

Releasing packages and versions
---------------------------------

This repository uses Github Actions and Github Packages to orchestrate and publish build artifacts.  Still to work on:

* Add some mechanism, either just instructions or a shell script to automate for the below...
  * Read pom.xml and determine current version to release
  * Increment this by one maintenance (or minor, not sure) version to determine the next version to set and add -SNAPSHOT
  * Prompt user for these 2 values, showing the above that will be used as defaults, but allowing to override
  * Use mvn versions (see below) to set poms to release versions, commit and push
    - mvn versions:set versions:update-child-modules -DnewVersion=x.y.z
  * Tag this with the given version number
  * Use mvn versions again to set poms to new development versions, commit, and push
  * Initiate github release off of the created tag?

* Figure out how to configure dependencies building.  Look into configuring webhooks on PackageEvent and other triggers.
  * One possibility are to trigger a repository dispatch action from one build to kick off another
    - https://help.github.com/en/actions/reference/events-that-trigger-workflows#external-events-repository_dispatch
  * Another possibility, at least initially, would be to just run all jobs on a regular cron (hourly, daily, etc.) 
    - https://help.github.com/en/actions/reference/events-that-trigger-workflows#scheduled-events-schedule

Test Change Text
