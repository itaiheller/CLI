package com.checkmarx.cxconsole.commands.job;

import java.util.concurrent.Callable;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.checkmarx.cxconsole.utils.ConfigMgr;
import com.checkmarx.cxviewer.ws.WSMgr;
import com.checkmarx.cxviewer.ws.generated.CurrentStatusEnum;
import com.checkmarx.cxviewer.ws.results.GetStatusOfScanResult;

public class WaitScanCompletionJob implements Callable<Boolean> {

	public int totalPercentScanned;
	
	private WSMgr wsMgr;
	private String sessionId;
	private String runId;
	private String finalMessage;
	
	private long scanId;
	private long resultId;

	private Logger log;
	
	public WaitScanCompletionJob(WSMgr wsMgr, String sessionId, String scanId) {
		super();
		this.wsMgr = wsMgr;
		this.sessionId = sessionId;
		this.runId = scanId;
	}

	@Override
	public Boolean call() throws Exception {
		
		finalMessage = "";
		int retriesNum = ConfigMgr.getCfgMgr().getIntProperty(ConfigMgr.KEY_RETIRES);
		
		if (log == null) {
			log = Logger.getRootLogger();
		}
		
		int getStatusInterval = ConfigMgr.getCfgMgr()
			.getIntProperty(ConfigMgr.KEY_PROGRESS_INTERVAL);
		
		long currTime;
		long prevTime;
		long exceededTime;
		boolean scanComplete = false;
		int progressRequestAttempt = 0;
		String lastMessage;
		
		try {
			do {
				lastMessage = "";
				currTime = System.currentTimeMillis();
				GetStatusOfScanResult statusOfScanResult = null;
				try {
					statusOfScanResult = wsMgr.getStatusOfScan(runId, sessionId);
					if (log.isEnabledFor(Level.TRACE)) {
						log.trace("getScanStatus: " + statusOfScanResult);
					}					
				} catch (Throwable e) {
					if (log.isEnabledFor(Level.TRACE)) {
						log.trace("Error occurred during invoking webservice mehtod \"getStatusOfScan\"", e);
					}
					lastMessage = e.getMessage();
				}
				
				if (statusOfScanResult != null && statusOfScanResult.isSuccesfullResponce()) {
					// Update progress bar
					totalPercentScanned = statusOfScanResult.getTotalPercent();
					log.info("Total scan worked: " + totalPercentScanned  + "%");
					if (statusOfScanResult.isStatusFailed()) {
						// Scan failed
						String errorMsg;
						log.error(errorMsg = parseScanFault(statusOfScanResult));
						throw new Exception(errorMsg);
					}
					
					if (statusOfScanResult.isRunStatusCanceled()) {
						String errorMsg;
						log.error(errorMsg = "Project scan was cancelled on server side.");
						throw new Exception(errorMsg);
					}
					
					if (statusOfScanResult.getRunStatus() == CurrentStatusEnum.DELETED) {
						String errorMsg;
						log.error(errorMsg = "Project scan was deleted/postponed.");
						throw new Exception(errorMsg);
					}
					
					/*if (statusOfScanResult.getStageMessage() != null 
							&& statusOfScanResult.getStageMessage().contains("Source code has not changed since last scan")) {
						log.error("Project scan was already performed earlier.");
						//throw new Exception(errorMsg);
						//scanComplete = true;
						//return true;
					}*/
					String stageName = statusOfScanResult.getStageName();
					stageName = stageName.isEmpty() ? "" : " \"" + stageName + "\"";
					if (!stageName.isEmpty() && !statusOfScanResult.getStageMessage().isEmpty()) {
						log.info("Current stage: " + stageName + " - " + statusOfScanResult.getStageMessage());
					} else if (!stageName.isEmpty()) {
						log.info("Current stage: " + stageName );
					} else if (!statusOfScanResult.getStageMessage().isEmpty()) {
						log.info("Current stage: " + statusOfScanResult.getStageMessage());
					} else {
						log.info("Scan state: " + statusOfScanResult.getRunStatus());
					}
					
					if (log.isEnabledFor(Level.TRACE)) {
						log.trace(statusOfScanResult.getRunStatus() + stageName
								+ "\n" + statusOfScanResult.getStageMessage()
								+ " ("
								+ statusOfScanResult.getCurrentStagePercent()
								+ "%)" + " "
								+ statusOfScanResult.getStepMessage());
					}
					scanComplete = statusOfScanResult.isStatusFinished();
					scanId = statusOfScanResult.getScanId();
					resultId = statusOfScanResult.getResultId();
					if (scanComplete && !statusOfScanResult.getStageMessage().isEmpty()) {
						if (log.isEnabledFor(Level.INFO)) {
							log.info(statusOfScanResult.getStageMessage());
						}
						finalMessage = statusOfScanResult.getStageMessage();
					}
				} else {
					// Ignore possible problem with scan step result
					// retrieving, just try another attempt to get step
					// results
					log.error("Scan status request failed. " + (statusOfScanResult == null ? "" : statusOfScanResult));					
					progressRequestAttempt++;
				}
				
				if (progressRequestAttempt > retriesNum) {
					if (statusOfScanResult != null && !statusOfScanResult.isSuccesfullResponce()) {
						String errorMsg = "Scan service error: progress request have not succeeded.";
						String responseErrMsg = statusOfScanResult.getErrorMessage();
						if (responseErrMsg != null && !responseErrMsg.isEmpty()) {
							errorMsg += " " + responseErrMsg;
						}
						log.error(errorMsg);
						throw new Exception(errorMsg);
					} else {
						String errorMsg = "Scan progress request failure.";
						if (lastMessage != null && !lastMessage.isEmpty()) {
							errorMsg += " Error message: " + lastMessage;
						}
						log.error(errorMsg);
						throw new Exception(errorMsg);
					}
				} else {
					if ((statusOfScanResult != null && !statusOfScanResult.isSuccesfullResponce()) ||
							(statusOfScanResult == null)) {
						log.error("Performing another request. Attempt#" + progressRequestAttempt);
					}
				}
				
				prevTime = currTime;
				currTime = System.currentTimeMillis();
				exceededTime = (currTime - prevTime) / 1000;
				//Check, maybe no need to wait, and another request should be sent
				while (exceededTime < getStatusInterval && !scanComplete) {
					Thread.sleep(500);	
					currTime = System.currentTimeMillis();
					exceededTime = (currTime - prevTime) / 1000;
				}//while (exceededTime < timeGetStatusOfScan)
			} while(!scanComplete);	
		} catch (InterruptedException e) {
			log.trace(e);			
		}
		
		return scanComplete;
	}
	
	protected String parseScanFault(GetStatusOfScanResult statusOfScanResult) {
		String stageMessage = statusOfScanResult.getStageMessage();
		if (stageMessage != null && stageMessage.equalsIgnoreCase("There is no License for a source code language you are trying to scan.")) {
			return "You are not authorized to scan projects in this selected language.";
		}
		return "Error during scan: " + statusOfScanResult.getStageMessage();
	}

	public void setLog(Logger log) {
		this.log = log;
	}
	
	public String getFinalMessage() {
		return finalMessage;
	}
	
	public long getResultId() {
		return resultId;
	}
	
	public long getScanId() {
		return scanId;
	}
}