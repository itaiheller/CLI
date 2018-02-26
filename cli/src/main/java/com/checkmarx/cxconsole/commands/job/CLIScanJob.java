package com.checkmarx.cxconsole.commands.job;

import com.checkmarx.cxconsole.clients.login.CxRestLoginClient;
import com.checkmarx.cxconsole.clientsold.soap.login.CxSoapLoginClient;
import com.checkmarx.cxconsole.clientsold.soap.login.exceptions.CxSoapLoginClientException;
import com.checkmarx.cxconsole.clientsold.soap.utils.SoapClientUtils;
import com.checkmarx.cxconsole.commands.job.exceptions.CLIJobException;
import com.checkmarx.cxconsole.commands.job.retriableoperation.RetryableOperation;
import com.checkmarx.cxconsole.commands.job.retriableoperation.RetryableRESTLogin;
import com.checkmarx.cxconsole.commands.job.retriableoperation.RetryableSOAPLogin;
import com.checkmarx.cxconsole.commands.job.utils.JobUtils;
import com.checkmarx.cxconsole.commands.job.utils.PathHandler;
import com.checkmarx.cxconsole.parameters.CLIScanParametersSingleton;
import com.checkmarx.cxconsole.utils.ConfigMgr;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * Created by nirli on 05/11/2017.
 */
public abstract class CLIScanJob implements Callable<Integer> {

    private static Logger log = Logger.getLogger(CLIScanJob.class);

    CxRestLoginClient cxRestLoginClient;
    boolean isAsyncScan;
//    CxSoapLoginClient cxSoapLoginClient = ConfigMgr.getWSMgr();
//    String sessionId;

    private String errorMsg;
    protected CLIScanParametersSingleton params;

    CLIScanJob(CLIScanParametersSingleton params, boolean isAsyncScan) {
        this.params = params;
        this.isAsyncScan = isAsyncScan;
        cxRestLoginClient = ConfigMgr.getRestWSMgr(this.params);
    }

//    void soapLogin() throws CLIJobException {
//        final RetryableOperation login = new RetryableSOAPLogin(params, cxSoapLoginClient);
//        login.run();
//        sessionId = cxSoapLoginClient.getSessionId();
//        errorMsg = login.getError();
//        if (errorMsg != null) {
//            throw new CLIJobException(errorMsg);
//        }
//    }

    void restLogin() throws CLIJobException {
        final RetryableOperation login = new RetryableRESTLogin(params, cxRestLoginClient);
        login.run();

//        sessionId = cxSoapLoginClient.getSessionId();
//        if (cxSoapLoginClient.getCxSoapClient() == null) {
//            URL wsdlLocation;
//            cxSoapLoginClient = ConfigMgr.getWSMgr();
//            try {
//                wsdlLocation = new URL(SoapClientUtils.buildHostWithWSDL(params.getCliMandatoryParameters().getOriginalHost()));
//                cxSoapLoginClient.initSoapClient(wsdlLocation);
//            } catch (MalformedURLException | CxSoapLoginClientException e) {
//                log.error("Error initialize SOAP SAST client: " + e.getMessage());
//                throw new CLIJobException("Error initialize SOAP SAST client: " + e.getMessage());
//            }
//        }
    }

    void storeXMLResults(String fileName, byte[] resultBytes) throws CLIJobException {
        File resFile = initFile(fileName);
        try (FileOutputStream fOut = new FileOutputStream(resFile.getAbsolutePath())) {
            fOut.write(resultBytes);
        } catch (IOException e) {
            log.error("Saving xml results to file [" + resFile.getAbsolutePath() + "] failed");
            log.trace("", e);
        }
    }

    private File initFile(String fileName) throws CLIJobException {
        String folderPath = JobUtils.gerWorkDirectory(params);
        String resultFilePath = PathHandler.initFilePath(params.getCliMandatoryParameters().getProjectName(), fileName, ".xml", folderPath);
        return new File(resultFilePath);
    }

    boolean isProjectDirectoryValid() {
        File projectDir = new File(params.getCliSharedParameters().getLocationPath());
        if (!projectDir.exists()) {
            //if there is a semicolon separator, take the first path
            String[] paths = params.getCliSharedParameters().getLocationPath().split(";");
            if (paths.length > 0) {
                projectDir = new File(paths[0]);
            }
            if (projectDir.exists()) {
                params.getCliSharedParameters().setLocationPath(paths[0]);
            } else {
                log.error("Project directory [" + params.getCliSharedParameters().getLocationPath()
                        + "] does not exist.");

                return false;
            }
        }

        if (!projectDir.isDirectory()) {
            log.error("Project path [" + params.getCliSharedParameters().getLocationPath()
                    + "] should point to a directory.");

            return false;
        }

        return true;
    }

    @Override
    public abstract Integer call() throws CLIJobException;

    String getErrorMsg() {
        return errorMsg;
    }

}