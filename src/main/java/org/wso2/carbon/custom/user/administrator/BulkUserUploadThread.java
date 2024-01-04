package org.wso2.carbon.custom.user.administrator;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.custom.user.administrator.internal.CustomUserAdministratorDataHolder;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.jdbc.UniqueIDJDBCUserStoreManager;
import org.wso2.carbon.idp.mgt.util.IdPManagementUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;

import static org.wso2.carbon.custom.user.administrator.Constants.*;

public class BulkUserUploadThread implements Callable<Boolean> {
    private static final Log log = LogFactory.getLog(BulkUserUploadThread.class);
    private Properties properties = null;
    private String tenantDomain = null;
    private int tenantId;
    private String userDomain = null;
    private UniqueIDJDBCUserStoreManager store;
    File[] files = null;
    private String[] firstLine = null;

    public BulkUserUploadThread() {
        super();
    }

    @Override
    public Boolean call() {
        if (!this.doCheckPrerequisites())
            return false;

        long t4 = System.currentTimeMillis();

        if (this.store != null) {
            Set<String[]> userSet = new HashSet<String[]>();
            InputStream targetStream = null;
            BufferedReader reader = null;
            CSVReader csvReader = null;

            try {
                for(File file : this.files) {
                    log.info(BULK_UPLOAD_LOG_PREFIX + "Reading from file " + file.getAbsolutePath());

                    targetStream = new FileInputStream(file);
                    reader = new BufferedReader(new InputStreamReader(targetStream, StandardCharsets.UTF_8));
                    csvReader = new CSVReader(reader, ',', '"', 0);

                    String[] line = csvReader.readNext();
                    this.firstLine = line;

                    while (line != null && line.length > 0) {
                        line = csvReader.readNext();
                        userSet.add(line);
                    }
                }
            } catch (IOException e) {
                log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while reading from CSV files", e);
                return false;
            } finally {
                if(csvReader != null) {
                    try {
                        csvReader.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing the CSVReader", e);
                    }
                }
                if(reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing the BufferedReader", e);
                    }
                }
                if(targetStream != null) {
                    try {
                        targetStream.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing the FileInputStream", e);
                    }
                }
            }

            long t5 = System.currentTimeMillis();
            log.info(BULK_UPLOAD_LOG_PREFIX + "[TIME INDICATOR] Total time taken to read from CSV files " +
                    "(in milliseconds) : " + (t5-t4));
            log.info(BULK_UPLOAD_LOG_PREFIX + "Starting user provisioning to the given user store...");

            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(this.tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(this.tenantId);

            for (String[] user : userSet) {
                if (user != null && user[0] != null && !user[0].isEmpty()) {
                    Map<String, String> claims = new HashMap<String, String>();

                    for (int i = 0; i < firstLine.length; i++) {
                        if (i > 1) {
                            claims.put(firstLine[i], user[i]);
                        }
                    }

                    try {
                        store.doAddUserWithID(user[0], user[1], null, claims, null, false);
                    } catch (UserStoreException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while adding user with the username : " + user[0] + " | claims : " + claims, e);
                    }
                }
            }
            PrivilegedCarbonContext.endTenantFlow();

            long t6 = System.currentTimeMillis();
            log.info(BULK_UPLOAD_LOG_PREFIX + "[TIME INDICATOR] Total time required to add users to the user store " +
                    "(in milliseconds) : " + (t6 - t5));
        }
        return true;
    }

    public boolean doCheckPrerequisites() {
        long t1 = System.currentTimeMillis();
        log.info(BULK_UPLOAD_LOG_PREFIX + "Started prerequisite check.");
        boolean check = false;

        File initialFile = new File(CONFIG_FILE_PATH);
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(initialFile);
            this.properties = new Properties();
        } catch (FileNotFoundException e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. Property file '" + CONFIG_FILE_PATH + "' not found. Task Aborted.");
            return false;
        }

        try {
            this.properties.load(inputStream);
        } catch(IOException ioException) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error while loading input stream. Task Aborted.");
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing the inputStream", e);
            }
        }

        String tenantDomain = properties.getProperty(Constants.TENANT_DOMAIN);
        String userDomain = properties.getProperty(Constants.USER_DOMAIN);
        if (StringUtils.isBlank(tenantDomain) && StringUtils.isBlank(userDomain)) {
            String constant = StringUtils.isBlank(tenantDomain) ? TENANT_DOMAIN : USER_DOMAIN;
            log.error(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. Field '" + constant + "' not found on '" + CONFIG_FILE_NAME + "'. Task Aborted.");
            return false;
        }
        this.tenantDomain = tenantDomain;
        this.userDomain = userDomain;

        int tenantId = this.getTenantIdFromDomain(this.tenantDomain);
        if (tenantId == -2) return false;
        this.tenantId = tenantId;

        File dir = new File(FOLDER_PATH);
        File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".csv");
            }
        });
        if(files == null || files.length == 0) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. No CSV file is found at " + dir.getAbsolutePath());
            return false;
        }
        log.info(BULK_UPLOAD_LOG_PREFIX + "At least one CSV file is found in " + dir.getAbsolutePath());
        this.files = files;

        UniqueIDJDBCUserStoreManager store = null;
        long t2 = System.currentTimeMillis();
        try {
            log.info(BULK_UPLOAD_LOG_PREFIX + "Waiting until secondary user store is found...");
            boolean timeReached = false;
            do {
                store = (UniqueIDJDBCUserStoreManager) CustomUserAdministratorDataHolder.
                        getInstance().getRealmService().getBootstrapRealm().getUserStoreManager().getSecondaryUserStoreManager(userDomain);

                if (System.currentTimeMillis() > t2 + 30000) {
                    log.info(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. Secondary user store not found. Allocated time exceeded. Task aborted.");
                    timeReached = true;
                    break;
                }
            }  while(store == null);

            if (!timeReached) {
                this.store = store;
                log.info(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were satisfied. Continuing on...");
                check = true;
            }
        } catch(UserStoreException e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error while obtaining user store manager", e);
        }
        long t3 = System.currentTimeMillis();
        log.info(BULK_UPLOAD_LOG_PREFIX + "[TIME INDICATOR] Total time taken for the prerequisite check " +
                "(in milliseconds) : " + (t3-t1));
        return check;
    }

    private int getTenantIdFromDomain(String tenantDomain) {
        try {
//            int id = IdPManagementUtil.getTenantIdOfDomain(tenantDomain);
//            int id = IdentityTenantUtil.getTenantId(tenantDomain);
            return -1234;
        } catch (Throwable e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. Error occurred while resolving tenant Id from tenant domain :" + tenantDomain, e);
            return -2;
        }
    }
}
