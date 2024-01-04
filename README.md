## 1 - Assumptions

You are using a JDBC RDBMS as the secondary user store to provision users,
You are using the super tenant (carbon.super).

## 2 - Points to note

The solution is capable of loading data from multiple CSVs.
In this solution, it can load data from CSV files only,
You can add one or multiple CSVs to the specified location or change it in the code (which is the same behavior as the solution provided before).
It is important that you try out the solution in a test setup first.

## 3 - The Solution

The claim-config.xml claim configuration before the initial server start up is no longer necessary with this solution for the secondary user store as this component waits until the given secondary user store is found to carry out the user provisioning.

The CSV format is the same as mentioned previously. If you require, you can utilize a library such as OpenCSV (i.e. CSVWriter) to generate a csv file with the preferred custom delimiter other than a comma. However, if you change the delimiter, you would have to also change the source code of the solution provided (i.e. line number 54 in BulkUserUploadThread.java file) in order to change the separator accordingly and build the new artifact.

Since this solution is for a secondary user store, there is a config.properties file that should be placed in <IS_HOME>/repository/resources/identity/properties directory (create the "properties" directory since it is not present by default).

This "config.properties" file contains two key=value pairs: tenantDomain & userDomain.

tenantDomain=carbon.super
userDomain=secondaryuserstore

The key string SHOULD NOT be changed,

The value string for the "tenantDomain" key must be "carbon.super": Due to the urgency, this code is optimised for the super tenant domain only. We assume you do not have users in different tenants to be bulk imported.

The value for the "userDomain" key must be changed to reflect the secondary user store "Domain Name" to which you are trying to import the records. Use the domain name as you have provided in the management console (You can verify the name from the "User Stores > List" on the management console). You can also verify it by looking at the xml at "<IS_HOME>/repository/deployment/server/userstores" directory where an xml file will be present in the following format <user_store_domain_name>.xml and the DomainName property within that XML file would contain the userDomain value that you should configure.

### 3.1 - Performing the bulk-user-import via the custom solution

If you have already configured the custom claims via the carbon management console, ensure that you have also added the required mappings for each custom claim to the secondary user store as well.

Shutdown the WSO2 Identity Server instance if already running (and any other instances).

Copy the org.wso2.carbon.custom.user.administrator-2.0.jar file into "<IS_HOME>/repository/components/dropins" directory, replacing the previous solution if you had already added it (i.e. org.wso2.carbon.custom.user.administrator-1.0.jar should not co-exist with org.wso2.carbon.custom.user.administrator-2.0.jar in the <IS_HOME>/repository/components/dropins directory).

Start the server again by providing the bulkupload parameter and setting the flag to true as follows.

sh wso2server.sh -Dbulkupload=true

You can find the bulk uploader specific logs by searching for the "[CUSTOM BULK USER UPLOADER]" string in the terminal/log file as in the previous solution,

It is important to note that the updated component (v2.0) also tries to provisions users at server startup, BUT does not block the startup as in v1.0.

Therefore, do not terminate the WSO2 Identity Server instance after the server seems to start normally.

If the users started provisioning to the secondary userstore, you would see the following log in the wso2carbon.log file.

[CUSTOM BULK USER UPLOADER] =========================> Starting user provisioning to the given user store...

When the provisioning is completed you would observe the following log, after which you can perform any action in WSO2 Identity Server.

[CUSTOM BULK USER UPLOADER] =========================> [TIME INDICATOR] Total time required to add users to the user store (in milliseconds) : 17660425

If any user has complications while being imported, the username of the specific user will be on the log, and that user will be skipped as in the v1.0 solution, importing will still proceed for other records.

If any of the prerequisites are not met (i.e. config.properties file not present, require key values are missing in the file, no CSVs, etc.), the following log will be output and the import of the users will be stopped.

[CUSTOM BULK USER UPLOADER] =========================> Prerequisites were not satisfied. <reason>

After the import is complete, you can navigate to the management console and verify if the users have been created successfully.

As per the solution provided previously (v1.0), if you restart the WSO2 Identity Server again with the argument to trigger the bulk user import after using a CSV with a specific set of records, make sure to not use the same CSV. You can remove the CSV from the path or not use the flag "-Dbulkupload=true". Otherwise, duplicate users will be created in the database.

