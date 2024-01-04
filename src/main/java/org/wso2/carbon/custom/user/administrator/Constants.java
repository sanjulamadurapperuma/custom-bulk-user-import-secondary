package org.wso2.carbon.custom.user.administrator;

public class Constants {

    public static final int DEFAULT_BULK_USER_UPLOAD_POOL_SIZE = 4;
    public static final String BULK_UPLOAD = "bulkupload";
    public static final String BULK_UPLOAD_LOG_PREFIX = "[CUSTOM BULK USER UPLOADER] =========================> ";
    public static final String FOLDER_PATH = "./repository/resources/identity/users/";
    public static final String FOLDER_PATH_PROPERTIES = "./repository/resources/identity/properties/";
    public static final String CONFIG_FILE_NAME = "config.properties";
    public static final String CONFIG_FILE_PATH = FOLDER_PATH_PROPERTIES + CONFIG_FILE_NAME;
    public static final String TENANT_DOMAIN = "tenantDomain";
    public static final String USER_DOMAIN = "userDomain";
}
