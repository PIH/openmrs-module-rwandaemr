RwandaEMR Migrations
===================================

This submodule has been created to contain the migration code that can be executed against a given database.
This is not packaged within the API or OMOD, but is intended to be used standalone from the command line.

To execute this, you should run the following command from this directory:

`mvn liquibase-update`

This accepts several arguments to control access to the database to execute the migrations against.  
The arguments are as follows, which show their default values that will be used if they are not explicity included:

-Ddb_host=localhost
-Ddb_port=3306
-Ddb_name=openmrs
-Ddb_user=openmrs
-Ddb_password=openmrs

So, to execute migrations on an instance of MySQL, where the above are accurate aside from db_port and db_password,
you would run:

`mvn liquibase-update -Ddb_port=3308 -Ddb_password=MyRootPassword123`
