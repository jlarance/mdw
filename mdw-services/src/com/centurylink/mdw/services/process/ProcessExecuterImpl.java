/**
 * Copyright (c) 2014 CenturyLink, Inc. All Rights Reserved.
 */
package com.centurylink.mdw.services.process;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.naming.NamingException;

import org.apache.xmlbeans.XmlObject;
import org.json.JSONObject;

import com.centurylink.mdw.activity.ActivityException;
import com.centurylink.mdw.activity.types.AdapterActivity;
import com.centurylink.mdw.activity.types.FinishActivity;
import com.centurylink.mdw.activity.types.GeneralActivity;
import com.centurylink.mdw.activity.types.InvokeProcessActivity;
import com.centurylink.mdw.activity.types.SuspendibleActivity;
import com.centurylink.mdw.activity.types.SynchronizationActivity;
import com.centurylink.mdw.common.ApplicationContext;
import com.centurylink.mdw.common.cache.impl.PackageVOCache;
import com.centurylink.mdw.common.constant.OwnerType;
import com.centurylink.mdw.common.constant.ProcessVisibilityConstant;
import com.centurylink.mdw.common.constant.VariableConstants;
import com.centurylink.mdw.common.constant.WorkAttributeConstant;
import com.centurylink.mdw.common.constant.WorkTransitionAttributeConstant;
import com.centurylink.mdw.common.exception.DataAccessException;
import com.centurylink.mdw.common.exception.MDWException;
import com.centurylink.mdw.common.exception.ServiceLocatorException;
import com.centurylink.mdw.common.translator.VariableTranslator;
import com.centurylink.mdw.common.utilities.CollectionUtil;
import com.centurylink.mdw.common.utilities.StringHelper;
import com.centurylink.mdw.common.utilities.logger.LoggerUtil;
import com.centurylink.mdw.common.utilities.logger.StandardLogger;
import com.centurylink.mdw.common.utilities.property.PropertyManager;
import com.centurylink.mdw.common.utilities.timer.Tracked;
import com.centurylink.mdw.common.utilities.timer.TrackingTimer;
import com.centurylink.mdw.dataaccess.DatabaseAccess;
import com.centurylink.mdw.dataaccess.RemoteAccess;
import com.centurylink.mdw.dataaccess.RuntimeDataAccess;
import com.centurylink.mdw.model.data.event.EventType;
import com.centurylink.mdw.model.data.monitor.CertifiedMessage;
import com.centurylink.mdw.model.data.monitor.ScheduledEvent;
import com.centurylink.mdw.model.data.work.WorkStatus;
import com.centurylink.mdw.model.data.work.WorkTransitionStatus;
import com.centurylink.mdw.model.value.activity.ActivityInstance;
import com.centurylink.mdw.model.value.activity.ActivityVO;
import com.centurylink.mdw.model.value.event.EventInstanceVO;
import com.centurylink.mdw.model.value.event.EventWaitInstanceVO;
import com.centurylink.mdw.model.value.event.InternalEventVO;
import com.centurylink.mdw.model.value.process.PackageVO;
import com.centurylink.mdw.model.value.process.ProcessInstanceVO;
import com.centurylink.mdw.model.value.process.ProcessRuntimeContext;
import com.centurylink.mdw.model.value.process.ProcessVO;
import com.centurylink.mdw.model.value.variable.DocumentReference;
import com.centurylink.mdw.model.value.variable.DocumentVO;
import com.centurylink.mdw.model.value.variable.VariableInstanceInfo;
import com.centurylink.mdw.model.value.variable.VariableVO;
import com.centurylink.mdw.model.value.work.ActivityInstanceVO;
import com.centurylink.mdw.model.value.work.WorkTransitionInstanceVO;
import com.centurylink.mdw.model.value.work.WorkTransitionVO;
import com.centurylink.mdw.monitor.MonitorRegistry;
import com.centurylink.mdw.monitor.OfflineMonitor;
import com.centurylink.mdw.monitor.ProcessMonitor;
import com.centurylink.mdw.services.EventException;
import com.centurylink.mdw.services.OfflineMonitorTrigger;
import com.centurylink.mdw.services.ProcessException;
import com.centurylink.mdw.services.dao.process.EngineDataAccess;
import com.centurylink.mdw.services.dao.process.cache.ProcessVOCache;
import com.centurylink.mdw.services.event.CertifiedMessageManager;
import com.centurylink.mdw.services.event.ScheduledEventQueue;
import com.centurylink.mdw.services.messenger.InternalMessenger;
import com.centurylink.mdw.services.task.EngineAccess;
import com.centurylink.mdw.services.task.TaskManagerAccess;
import com.qwest.mbeng.DomDocument;
import com.qwest.mbeng.FormatDom;
import com.qwest.mbeng.MbengException;
import com.qwest.mbeng.MbengNode;

class ProcessExecuterImpl {

    protected static StandardLogger logger;

    protected EngineDataAccess edao;
    private InternalMessenger internalMessenger;
    private final boolean inService;

    protected Map<String,DocumentVO> remoteDocumentCache;		// for remote document

	ProcessExecuterImpl(EngineDataAccess edao,
			InternalMessenger internalMessenger, boolean forServiceProcess) {
		logger = LoggerUtil.getStandardLogger();
	    remoteDocumentCache = null;
	    this.edao = edao;
		inService = forServiceProcess;
		this.internalMessenger = internalMessenger;
	}

    private String logtag(Long procId, Long procInstId, Long actId, Long actInstId) {
    	StringBuffer sb = new StringBuffer();
    	sb.append("p");
    	sb.append(procId);
    	sb.append(".");
    	sb.append(procInstId);
    	sb.append(" a");
    	sb.append(actId);
    	sb.append(".");
    	sb.append(actInstId);
    	return sb.toString();
    }

    private String logtag(Long procId, Long procInstId, String masterRequestId) {
    	StringBuffer sb = new StringBuffer();
    	sb.append("p");
    	sb.append(procId);
    	sb.append(".");
    	sb.append(procInstId);
    	sb.append(" m.");
    	sb.append(masterRequestId);
    	return sb.toString();
    }

    private String logtag(Long procId, Long procInstId, WorkTransitionInstanceVO transInst) {
	    StringBuffer sb = new StringBuffer();
	    sb.append("p");
	    sb.append(procId);
	    sb.append(".");
	    sb.append(procInstId);
	    sb.append(" t");
	    sb.append(transInst.getTransitionID());
	    sb.append(".");
	    sb.append(transInst.getTransitionInstanceID());
	    return sb.toString();
    }

    final EngineDataAccess getDataAccess() {
        return edao;
    }

    final DatabaseAccess getDatabaseAccess() {
        return edao.getDatabaseAccess();
    }

    ActivityInstanceVO createActivityInstance(Long pActivityId, Long procInstId)
    throws ProcessException, SQLException, DataAccessException {
    	ActivityInstanceVO ai = new ActivityInstanceVO();
    	ai.setDefinitionId(pActivityId);
    	ai.setOwnerId(procInstId);
    	ai.setStatusCode(WorkStatus.STATUS_IN_PROGRESS);
    	edao.createActivityInstance(ai);
    	return ai;
    }

    WorkTransitionInstanceVO createTransitionInstance(
    		WorkTransitionVO transition, Long processInstId)
	    throws DataAccessException {
		try {
			WorkTransitionInstanceVO transInst = new WorkTransitionInstanceVO();
			transInst.setTransitionID(transition.getWorkTransitionId());
			transInst.setProcessInstanceID(processInstId);
			transInst.setStatusCode(WorkTransitionStatus.STATUS_INITIATED);
			transInst.setDestinationID(transition.getToWorkId());
			edao.createTransitionInstance(transInst);
			return transInst;
		} catch (SQLException e) {
            throw new DataAccessException(0, e.getMessage(), e);
        }
	}

    VariableInstanceInfo createVariableInstance(ProcessInstanceVO pi,
    		String varname, Object value)
    throws SQLException,DataAccessException {
    	ProcessVO processVO = this.getMainProcessDefinition(pi);
        VariableVO variableVO = processVO.getVariable(varname);
        if (variableVO==null) {
            throw new DataAccessException("Variable "
                    + varname + " is not defined for process " + processVO.getProcessId());
        }
        VariableInstanceInfo var = new VariableInstanceInfo();
        var.setName(variableVO.getVariableName());
        var.setVariableId(variableVO.getVariableId());
        var.setType(variableVO.getVariableType());
        if (value instanceof String) var.setStringValue((String)value);
        else var.setData(value);
        // this no longer supports variable locator
        // see createProcessVariableInstances in WorkManager for old impl
        if (pi.isNewEmbedded() || !pi.getProcessId().equals(processVO.getProcessId()))
        	edao.createVariableInstance(var, pi.getOwnerId());
        else edao.createVariableInstance(var, pi.getId());
        return var;
    }

    DocumentReference createDocument(String type, Long procInstId, String ownerType,
            Long ownerId, String searchKey1, String searchKey2, Object doc) throws DataAccessException {
        DocumentReference docref = null;
        try {
            DocumentVO docvo = new DocumentVO();
            if (doc instanceof String) docvo.setContent((String)doc);
            else docvo.setObject(doc, type);
            docvo.setDocumentType(type);
            docvo.setOwnerType(ownerType);
            docvo.setOwnerId(ownerId);
            docvo.setProcessInstanceId(procInstId);
            docvo.setSearchKey1(searchKey1);
            docvo.setSearchKey2(searchKey2);
            edao.createDocument(docvo);
            docref = new DocumentReference(docvo.getDocumentId(), null);
        } catch (Exception e) {
            throw new DataAccessException(0, e.getMessage(), e);
        }
        return docref;
    }

    DocumentVO getDocument(DocumentReference docref, boolean forUpdate)
    	throws DataAccessException {
    	if (docref.getServer()!=null) {
    		if (forUpdate) throw new DataAccessException("Cannot update remote document");
        	String key = docref.getDocumentId().toString() + "@" + docref.getServer();
            if (remoteDocumentCache==null) remoteDocumentCache = new HashMap<String,DocumentVO>();
            DocumentVO docvo = remoteDocumentCache.get(key);
            if (docvo==null) {
            	try {
					EngineAccess engineAccess = new EngineAccess();
					String dbinfo = engineAccess.getDatabaseCredential(docref.getServer());
					RemoteAccess rao = new RemoteAccess(docref.getServer(), dbinfo);
					RuntimeDataAccess rtinfo = rao.getRuntimeDataAccess();
					docvo = rtinfo.getDocument(docref.getDocumentId());
					if (docvo!=null) remoteDocumentCache.put(key, docvo);
				} catch (Exception e) {
					throw new DataAccessException(-1, "Failed to get remote document " + docref.toString(), e);
				}
            }
            return docvo;
    	} else {
    		try {
				return edao.getDocument(docref.getDocumentId(), forUpdate);
			} catch (SQLException e) {
				throw new DataAccessException(-1, e.getMessage(), e);
			}
    	}
    }

    /**
     * Does not work for remote documents
     */
    DocumentVO loadDocument(DocumentReference docref, boolean forUpdate)
        throws DataAccessException {
        try {
            return edao.loadDocument(docref.getDocumentId(), forUpdate);
        } catch (SQLException e) {
            throw new DataAccessException(-1, e.getMessage(), e);
        }
    }

    void updateDocumentContent(DocumentReference docref, Object doc, String type) throws DataAccessException {
        if (docref.getServer()!=null) {
            throw new DataAccessException("Cannot update remote document " + docref.toString());
        }
        try {
            DocumentVO docvo = edao.getDocument(docref.getDocumentId(), false);
            if (doc instanceof String) docvo.setContent((String)doc);
            else docvo.setObject(doc, type);
            edao.updateDocumentContent(docvo.getDocumentId(), docvo.getContent());
        } catch (SQLException e) {
            throw new DataAccessException(-1, "Failed to update document content", e);
        }
    }

    private List<VariableInstanceInfo> convertParameters(Map<String,String> eventParams,
            ProcessVO processVO, Long procInstId) throws ProcessException, DataAccessException {
        List<VariableInstanceInfo> vars = new ArrayList<VariableInstanceInfo>();
        if (eventParams == null || eventParams.isEmpty()) {
            return vars;
        }
        for (String varname : eventParams.keySet()) {
            VariableVO variableVO = processVO.getVariable(varname);
            if (variableVO==null) {
            	String msg = "there is no variable named " + varname
                	+ " in process with ID " + processVO.getProcessId()
                	+ " for parameter binding";
            	throw new ProcessException(msg);
            }
            VariableInstanceInfo var = new VariableInstanceInfo();
            var.setName(variableVO.getVariableName());
            var.setVariableId(variableVO.getVariableId());
            var.setType(variableVO.getVariableType());
            String value = eventParams.get(varname);
            if (value!=null && value.length()>0) {
            	if (VariableTranslator.isDocumentReferenceVariable(var.getType())) {
            		if (value.startsWith("DOCUMENT:")) var.setStringValue(value);
            		else {
            			DocumentReference docref = this.createDocument(var.getType(),
            					procInstId, OwnerType.PROCESS_INSTANCE, procInstId, null, null, value);
            			var.setData(docref);
            		}
            	} else var.setStringValue(value);
            	vars.add(var);	// only create variable instances when value is not null
            }
            // vars.add(var);	// if we put here, we create variables regardless if value is null
        }

        return vars;
    }

    /**
     * Create a process instance. The status is PENDING_PROCESS
     * @param processVO
     * @param eventMessageDoc
     * @return
     * @throws ProcessException
     * @throws DataAccessException
     */
    ProcessInstanceVO createProcessInstance(Long processId, String ownerType,
            Long ownerId, String secondaryOwnerType, Long secondaryOwnerId,
            String masterRequestId, Map<String,String> parameters, String label)
    throws ProcessException, DataAccessException
    {
		ProcessInstanceVO pi;
		try {
			ProcessVO processVO;
	    	if (ownerType.equals(OwnerType.MAIN_PROCESS_INSTANCE)) {
	    		ProcessInstanceVO parentPi = getDataAccess().getProcessInstance(ownerId);
	    		ProcessVO parentProcdef = ProcessVOCache.getProcessVO(parentPi.getProcessId());
	    		processVO = parentProcdef.getSubProcessVO(processId);
	    		pi = new ProcessInstanceVO(parentPi.getProcessId(), processVO.getProcessName());
	    		pi.setComment(processId.toString());
	    	} else {
	    		processVO = ProcessVOCache.getProcessVO(processId);
	    		pi = new ProcessInstanceVO(processId, processVO.getProcessName());
	    	}
	    	pi.setOwner(ownerType);
	    	pi.setOwnerId(ownerId);
	    	pi.setSecondaryOwner(secondaryOwnerType);
	    	pi.setSecondaryOwnerId(secondaryOwnerId);
	    	pi.setMasterRequestId(masterRequestId);
	    	pi.setStatusCode(WorkStatus.STATUS_PENDING_PROCESS);
	    	if (label != null)
	    	    pi.setComment(label);
	    	edao.createProcessInstance(pi);
//			if (parameters!=null)	// do not check this, as below will initialize variables array
	    	createVariableInstancesFromEventMessage(pi, parameters);
		} catch (SQLException e) {
			throw new DataAccessException(-1, e.getMessage(), e);
		}
        return pi;
    }

    private void createVariableInstancesFromEventMessage(ProcessInstanceVO pi,
    		Map<String,String> parameters) throws ProcessException, DataAccessException, SQLException {
    	ProcessVO processVO = getProcessDefinition(pi);
    	pi.setVariables(convertParameters(parameters, processVO, pi.getId()));
    	for (VariableInstanceInfo var : pi.getVariables()) {
    		edao.createVariableInstance(var, pi.getId());
    	}
    }

    void updateDocumentInfo(
    		DocumentReference docref, Long processInstId, String documentType, String ownerType, Long ownerId,
            String searchKey1, String searchKey2) throws DataAccessException {
    	if (docref.getServer()!=null) {
            throw new DataAccessException("Cannot update remote document " + docref.toString());
        }
    	try {
			DocumentVO docvo = edao.getDocument(docref.getDocumentId(), false);
			if (documentType!=null) docvo.setDocumentType(documentType);
			if (ownerType!=null) docvo.setOwnerType(ownerType);
			if (ownerId!=null) docvo.setOwnerId(ownerId);
			if (processInstId!=null) docvo.setProcessInstanceId(processInstId);
			if (searchKey1!=null) docvo.setSearchKey1(searchKey1);
			if (searchKey2!=null) docvo.setSearchKey2(searchKey2);
			edao.updateDocumentInfo(docvo);
		} catch (SQLException e) {
			throw new DataAccessException(-1, e.getMessage(), e);
		}
    }

    void cancelEventWaitInstances(Long activityInstanceId)
	throws DataAccessException {
		try {
			getDataAccess().removeEventWaitForActivityInstance(activityInstanceId, "Cancel due to timeout");
		} catch (Exception e) {
			throw new DataAccessException(0, "Failed to cancel event waits", e);
		}
    }

    String getServiceProcessResponse(Long procInstId, String varname)
    	throws DataAccessException {
    	try {
    		VariableInstanceInfo varinst;
    		if (varname==null) {
    			varinst = getDataAccess().getVariableInstance(procInstId, VariableConstants.RESPONSE);
    			if (varinst==null) varinst = getDataAccess().getVariableInstance(procInstId, VariableConstants.MASTER_DOCUMENT);
    			if (varinst==null) varinst = getDataAccess().getVariableInstance(procInstId, VariableConstants.REQUEST);
    		} else {
    			varinst = getDataAccess().getVariableInstance(procInstId, varname);
    		}
			if (varinst==null) return null;
			if (varinst.isDocument()) {
				DocumentVO docvo = getDocument((DocumentReference)varinst.getData(), false);
				return docvo.getContent();
			} else return varinst.getStringValue();
		} catch (SQLException e) {
	        throw new DataAccessException(0, "Failed to get value for variable " + varname, e);
		}
    }

    void updateProcessInstanceStatus(Long pProcInstId, Integer status)
    throws DataAccessException,ProcessException {
        try {
            getDataAccess().setProcessInstanceStatus(pProcInstId, status);
            if (status.equals(WorkStatus.STATUS_COMPLETED) ||
                status.equals(WorkStatus.STATUS_CANCELLED) ||
                status.equals(WorkStatus.STATUS_FAILED)) {
                getDataAccess().removeEventWaitForProcessInstance(pProcInstId);
            }
        } catch (SQLException e) {
            throw new ProcessException(0, "Failed to update process instance status", e);
        }
    }

    protected ProcessVO getProcessDefinition(ProcessInstanceVO procinst) {
    	ProcessVO procdef = ProcessVOCache.getProcessVO(procinst.getProcessId());
    	if (procinst.isNewEmbedded())
    		procdef = procdef.getSubProcessVO(new Long(procinst.getComment()));
    	return procdef;
    }

    protected ProcessVO getMainProcessDefinition(ProcessInstanceVO procinst)
    	throws DataAccessException, SQLException {
    	ProcessVO procdef = ProcessVOCache.getProcessVO(procinst.getProcessId());
    	if (procinst.isNewEmbedded() || procdef.isEmbeddedProcess()) {
    		procinst = edao.getProcessInstance(procinst.getOwnerId());
            procdef = ProcessVOCache.getProcessVO(procinst.getProcessId());
        }
    	return procdef;
    }

    boolean deleteInternalEvent(String eventName)
    throws DataAccessException {
    	try {
			int count = getDataAccess().deleteEventInstance(eventName);
			return count>0;
		} catch (SQLException e) {
	        throw new DataAccessException(0, "Failed to delete internal event" + eventName, e);
        }
    }

    InternalMessenger getInternalMessenger() {
		return internalMessenger;
	}

    ///////////// create process instance

    /**
     * Handles the work Transitions for the passed in collection of Items
     *
     * @param processInst
     * @param transitions
     * @param eventMessageDoc
     */
    void createTransitionInstances(ProcessInstanceVO processInstanceVO,
    		List<WorkTransitionVO> transitions, Long fromActInstId)
           throws ProcessException,DataAccessException {
        WorkTransitionInstanceVO transInst;
        for (WorkTransitionVO transition : transitions) {
            try {
            	if (tooManyMaxTransitionInstances(transition, processInstanceVO.getId())) {
            		// Look for a error transition at this time
                    // In case we find it, raise the error event
                    // Otherwise do not do anything
                	handleWorkTransitionError(processInstanceVO, transition.getWorkTransitionId(), fromActInstId);
            	} else {
            		transInst = createTransitionInstance(transition, processInstanceVO.getId());
            		String tag = logtag(processInstanceVO.getProcessId(),
                			processInstanceVO.getId(), transInst);
                    logger.info(tag, "Transition initiated from " + transition.getFromWorkId() + " to " + transition.getToWorkId());

                    InternalEventVO jmsmsg;
    	    		int delay = 0;
    	    		jmsmsg = InternalEventVO.createActivityStartMessage(
    	    				transition.getToWorkId(), processInstanceVO.getId(),
    	    				transInst.getTransitionInstanceID(), processInstanceVO.getMasterRequestId(),
    	    				transition.getLabel());
    	    		delay = transition.getTransitionDelay();
    	    		String msgid = ScheduledEvent.INTERNAL_EVENT_PREFIX + processInstanceVO.getId()
    	    				+ "start" + transition.getToWorkId() + "by" + transInst.getTransitionInstanceID();
    	    		if (delay>0) this.sendDelayedInternalEvent(jmsmsg, delay, msgid, false);
    	    		else sendInternalEvent(jmsmsg);
            	}
            } catch (SQLException ex) {
                throw new ProcessException(-1, ex.getMessage(), ex);
            } catch (MDWException ex) {
                throw new ProcessException(-1, ex.getMessage(), ex);
            }
        }
    }

    private boolean tooManyMaxTransitionInstances(WorkTransitionVO trans, Long pProcessInstId)
    	throws SQLException {
    	if (inService) return false;
    	String retryAttribVal = trans.getAttribute(WorkTransitionAttributeConstant.TRANSITION_RETRY_COUNT);
    	int retryCount = retryAttribVal==null?-1:Integer.parseInt(retryAttribVal);
    	if (retryCount<0) return false;
    	int count = edao.countTransitionInstances(pProcessInstId, trans.getWorkTransitionId());
    	if (count>0 && count >= retryCount) {
    		String msg = "Transition " + trans.getWorkTransitionId()
    		+ " not made - exceeded allowed retry count of " + retryCount;
    		// log as exception since this message is often overlooked
    		logger.severeException(msg, new ProcessException(msg));
    		return true;
    	} else return false;
    }

    private void handleWorkTransitionError(ProcessInstanceVO processInstVO, Long workTransitionId,
    		Long fromActInstId) throws ProcessException, DataAccessException, SQLException
    {
    	edao.setProcessInstanceStatus(processInstVO.getId(), WorkStatus.STATUS_WAITING);
    	ProcessVO processVO = getMainProcessDefinition(processInstVO);
    	ProcessVO embeddedProcdef = processVO.findEmbeddedProcess(EventType.ERROR, null);
    	while (embeddedProcdef==null && processInstVO.getOwner().equals(OwnerType.PROCESS_INSTANCE)) {
    		processInstVO = edao.getProcessInstance(processInstVO.getOwnerId());
    		processVO = getMainProcessDefinition(processInstVO);
    		embeddedProcdef = processVO.findEmbeddedProcess(EventType.ERROR, null);
    	}
    	if (embeddedProcdef == null) {
    		logger.warn("Error subprocess does not exist. Transition failed. TransitionId-->"
    				+ workTransitionId + " ProcessInstanceId-->" + processInstVO.getId());
    		return;
    	}
    	String tag = logtag(processInstVO.getProcessId(),processInstVO.getId(),processInstVO.getMasterRequestId());
    	logger.info(tag, "Transition to error subprocess " + embeddedProcdef.getProcessName());
    	String secondaryOwnerType;
    	Long secondaryOwnerId;
    	if (fromActInstId==null || fromActInstId.longValue()==0L) {
    		secondaryOwnerType = OwnerType.WORK_TRANSITION;
    		secondaryOwnerId = workTransitionId;
    	} else {
    		secondaryOwnerType = OwnerType.ACTIVITY_INSTANCE;
    		secondaryOwnerId = fromActInstId;
    	}
    	String ownerType = processVO.isInRuleSet()?OwnerType.MAIN_PROCESS_INSTANCE:OwnerType.PROCESS_INSTANCE;
    	ProcessInstanceVO procInst = createProcessInstance(embeddedProcdef.getProcessId(),
    			ownerType, processInstVO.getId(), secondaryOwnerType, secondaryOwnerId,
    			processInstVO.getMasterRequestId(), null, null);
    	startProcessInstance(procInst, 0);
    }

    /**
     * Starting a process instance, which has been created already.
     * The method sets the status to "In Progress",
     * find the start activity, and sends an internal message to start the activity
     *
     * @param processInstanceVO
     */
    void startProcessInstance(ProcessInstanceVO processInstanceVO, int delay)
      throws ProcessException {

        try {
        	ProcessVO processVO = getProcessDefinition(processInstanceVO);
            edao.setProcessInstanceStatus(processInstanceVO.getId(), WorkStatus.STATUS_PENDING_PROCESS);
            // setProcessInstanceStatus will really set to STATUS_IN_PROGRESS - hint to set START_DT as well
            if (logger.isInfoEnabled()) {
            	logger.info(logtag(processInstanceVO.getProcessId(), processInstanceVO.getId(),
            			processInstanceVO.getMasterRequestId()),
            			WorkStatus.LOGMSG_PROC_START + " - " + processVO.getProcessName()
            			+ (processInstanceVO.isNewEmbedded() ?
            					(" (embedded process " + processVO.getProcessId() + ")") :
            					("/" + processVO.getVersionString())));
            }
            notifyMonitors(processInstanceVO, WorkStatus.LOGMSG_PROC_START);
            // get start activity ID
        	Long startActivityId;
            if (processVO.isInRuleSet() || processInstanceVO.isNewEmbedded()) {
	            edao.setProcessInstanceStatus(processInstanceVO.getId(), WorkStatus.STATUS_PENDING_PROCESS);
	            startActivityId = processVO.getStartActivity().getActivityId();
        	} else {
	        	ActivityVO startActivity = processVO.getStartActivity();
	            if (startActivity == null) {
	                throw new ProcessException("WorkTransition has not been defined for START event! ProcessID = " + processVO.getProcessId());
	            }
	            startActivityId = startActivity.getActivityId();
        	}
            InternalEventVO event = InternalEventVO.createActivityStartMessage(
          		  startActivityId, processInstanceVO.getId(),
          		  null, processInstanceVO.getMasterRequestId(),
          		  EventType.EVENTNAME_START + ":");
            if (delay>0) {
	    		String msgid = ScheduledEvent.INTERNAL_EVENT_PREFIX + processInstanceVO.getId()
					+ "start" + startActivityId;
            	this.sendDelayedInternalEvent(event, delay, msgid, false);
            } else sendInternalEvent(event);
        }
        catch (Exception ex) {
            logger.severeException(ex.getMessage(), ex);
            throw new ProcessException(ex.getMessage());
        }
    }

	///// execute activity

	/**
     * determine if activity needs to wait (such as synchronous
     * process invocation, wait activity, synchronization)
	 *
	 * @param activity
	 * @param activityInstanceId
	 * @param eventMessageDoc
	 * @return whether to wait
	 */
	private boolean activityNeedsWait(GeneralActivity activity)
			throws ActivityException {
		if (activity instanceof SuspendibleActivity)
			return ((SuspendibleActivity) activity).needSuspend();
		return false;
	}

	/**
	 * Reports the error status of the activity instance to the activity manager
	 *
	 * @param eventMessageDoc
	 * @param activityInstId
	 * @param processInstId
	 * @param activity
	 * @param cause
	 */
	void failActivityInstance(InternalEventVO event,
    		ProcessInstanceVO processInst, Long activityId, Long activityInstId,
            BaseActivity activity, Throwable cause) {

		String tag = logtag(processInst.getProcessId(), processInst.getId(), activityId, activityInstId);
        logger.severeException("Failed to execute activity - " + cause.getClass().getName(), cause);
		String compCode = null;
		String statusMsg = buildStatusMessage(cause);
		try {
		    ActivityInstanceVO actInstVO = null;
			if (activity != null && activityInstId != null) {
				activity.setReturnMessage(statusMsg);
				actInstVO = edao.getActivityInstance(activityInstId);
                failActivityInstance(actInstVO, statusMsg, processInst, tag, cause.getClass().getName());
				compCode = activity.getReturnCode();
			}
            if (!AdapterActivity.COMPCODE_AUTO_RETRY.equals(compCode)) {
                DocumentReference docRef = CreateActivityExceptionDocument(processInst, actInstVO, activity, cause);
            	InternalEventVO outgoingMsg =
            		InternalEventVO.createActivityErrorMessage(activityId, activityInstId, processInst.getId(), compCode,
            			event.getMasterRequestId(), statusMsg.length() > 2000 ? statusMsg.substring(0, 1999) : statusMsg, docRef.getDocumentId());  // avoid overflowing data col
            	sendInternalEvent(outgoingMsg);
            }
        }
        catch (Exception ex) {
			logger.severeException("Exception thrown during failActivityInstance", ex);
		}
	}

	private String buildStatusMessage(Throwable t) {
		if (t == null)
			return "";
        StringBuffer message = new StringBuffer(t.toString());
        String v = PropertyManager.getProperty("MDWFramework.WorkflowEngine/ActivityStatusMessage.ShowStackTrace");
		boolean includeStackTrace = !"false".equalsIgnoreCase(v);
		if (includeStackTrace) {
            // get the root cause
            Throwable cause = t;
            while (cause.getCause() != null)
                cause = cause.getCause();
            if (t != cause)
                message.append("\nCaused by: " + cause);
            for (StackTraceElement element : cause.getStackTrace()) {
                message.append("\n").append(element.toString());
            }
		}

		if (message.length() > 4000) {
			return message.toString().substring(0, 3998);
		}

		return message.toString();
	}

	void cancelActivityInstance(ActivityInstanceVO actInst,
			ProcessInstanceVO procinst, String statusMsg) {
		String logtag = this.logtag(procinst.getProcessId(), procinst.getId(), actInst.getDefinitionId(), actInst.getId());
		try {
			this.cancelActivityInstance(actInst, statusMsg, procinst, logtag);
		} catch (Exception e) {
			logger.severeException("Exception thrown during canceActivityInstance", e);
		}
	}

	void holdActivityInstance(ActivityInstanceVO actInst, Long procId) {
		String logtag = this.logtag(procId, actInst.getOwnerId(), actInst.getDefinitionId(), actInst.getId());
		try {
			this.holdActivityInstance(actInst, logtag);
		} catch (Exception e) {
			logger.severeException("Exception thrown during canceActivityInstance", e);
		}
	}

    private ActivityInstanceVO waitForActivityDone(ActivityInstanceVO actInst)
		throws DataAccessException, InterruptedException, SQLException {
    	int max_retry = 10;
    	int retry_interval = 2;
    	int count = 0;
    	while (count<max_retry && actInst.getStatusCode()==WorkStatus.STATUS_IN_PROGRESS.intValue()) {
    		logger.debug("wait for synch activity to finish: " + actInst.getId());
    		Thread.sleep(retry_interval*1000);
    		actInst = getDataAccess().getActivityInstance(actInst.getId());
    		count++;
    	}
    	return actInst;
    }

	ActivityRuntimeVO prepareActivityInstance(InternalEventVO event, ProcessInstanceVO procInst)
			throws DataAccessException, ProcessException, ServiceLocatorException {
		try {
			// for asynch engine, procInst is always null
			ActivityRuntimeVO ar = new ActivityRuntimeVO();
			Long activityId = event.getWorkId();
			Long workTransInstanceId = event.getTransitionInstanceId();

			// check if process instance is still alive
			ar.procinst = procInst;
			if (WorkStatus.STATUS_CANCELLED.equals(ar.procinst.getStatusCode())
					|| WorkStatus.STATUS_COMPLETED.equals(ar.procinst.getStatusCode())) {
				ar.startCase = ActivityRuntimeVO.STARTCASE_PROCESS_TERMINATED;
				return ar;
			}

			ProcessVO processVO = getProcessDefinition(ar.procinst);
			ActivityVO actVO = processVO.getActivityVO(activityId);
			PackageVO pkg = PackageVOCache.getProcessPackage(getMainProcessDefinition(procInst).getId());
        	try {
				GeneralActivity activity = pkg.getActivityImplementor(actVO);
				ar.activity = (BaseActivity)activity;
			} catch (Throwable e) {
				String logtag = this.logtag(procInst.getProcessId(), procInst.getId(), activityId, 0L);
				logger.exception(logtag, "Failed to create activity implementor instance", e);
				ar.activity = null;
			}
			boolean isSynchActivity = ar.activity!=null && ar.activity instanceof SynchronizationActivity;
			if (isSynchActivity) getDataAccess().lockProcessInstance(procInst.getId());

			List<ActivityInstanceVO> actInsts;
			if (this.inService) actInsts = null;
			else actInsts = getDataAccess().getActivityInstances(activityId, procInst.getId(), true, isSynchActivity);
			if (actInsts==null || actInsts.isEmpty()) {
				// create activity instance and prepare it
				ar.actinst = createActivityInstance(activityId, procInst.getId());
				prepareActivitySub(processVO, actVO, ar.procinst, ar.actinst,
						workTransInstanceId, event, ar.activity);
				if (ar.activity==null) {
					logger.severe("Failed to load the implementor class or create instance: " + actVO.getImplementorClassName());
					ar.startCase = ActivityRuntimeVO.STARTCASE_ERROR_IN_PREPARE;
				} else {
					ar.startCase = ActivityRuntimeVO.STARTCASE_NORMAL;
					// send message to BAM when configured (OSGi is handled through monitors)
					/*if (!ApplicationContext.isOsgi())
			            ar.activity.sendMessageToBam(WorkAttributeConstant.BAM_START_MSGDEF);*/
			        // notify registered monitors
			        ar.activity.notifyMonitors(WorkStatus.LOGMSG_START);
				}
			} else if (isSynchActivity) {
				ar.actinst = actInsts.get(0);
       			if (ar.actinst.getStatusCode()==WorkStatus.STATUS_IN_PROGRESS.intValue())
    				ar.actinst = waitForActivityDone(ar.actinst);
				if (ar.actinst.getStatusCode()==WorkStatus.STATUS_WAITING.intValue()) {
					if (workTransInstanceId!=null && workTransInstanceId.longValue()>0L) {
						getDataAccess().completeTransitionInstance(workTransInstanceId, ar.actinst.getId());
					}
					ar.startCase = ActivityRuntimeVO.STARTCASE_SYNCH_WAITING;
				} else if (ar.actinst.getStatusCode()==WorkStatus.STATUS_HOLD.intValue()) {
					if (workTransInstanceId!=null && workTransInstanceId.longValue()>0L) {
						getDataAccess().completeTransitionInstance(workTransInstanceId, ar.actinst.getId());
					}
					ar.startCase = ActivityRuntimeVO.STARTCASE_SYNCH_WAITING;
				} else {	// completed - possible when there are OR conditions
					if (workTransInstanceId!=null && workTransInstanceId.longValue()>0L) {
						getDataAccess().completeTransitionInstance(workTransInstanceId, ar.actinst.getId());
					}
					ar.startCase = ActivityRuntimeVO.STARTCASE_SYNCH_COMPLETE;
				}
			} else {
				ActivityInstanceVO onHoldActInst = null;
				for (ActivityInstanceVO actInst : actInsts) {
					if (actInst.getStatusCode()==WorkStatus.STATUS_HOLD.intValue()) {
						onHoldActInst = actInst;
						break;
					}
				}
				if (onHoldActInst!=null) {
					if (workTransInstanceId!=null && workTransInstanceId.longValue()>0L) {
						getDataAccess().completeTransitionInstance(workTransInstanceId, onHoldActInst.getId());
					}
					ar.startCase = ActivityRuntimeVO.STARTCASE_RESUME_WAITING;
					ar.actinst = onHoldActInst;
				} else {	// WAITING or IN_PROGRESS
					ar.startCase = ActivityRuntimeVO.STARTCASE_INSTANCE_EXIST;
				}
			}
			return ar;
		} catch (SQLException e) {
			throw new ProcessException(-1, e.getMessage(), e);
		} catch (NamingException e) {
			throw new ProcessException(-1, e.getMessage(), e);
		} catch (InterruptedException e) {
			throw new ProcessException(-1, e.getMessage(), e);
		} catch (MDWException e) {
			throw new ProcessException(-1, e.getMessage(), e);
		}
	}

	private void prepareActivitySub(ProcessVO processVO, ActivityVO actVO,
			ProcessInstanceVO pi, ActivityInstanceVO ai, Long pWorkTransInstId,
			InternalEventVO event, BaseActivity activity)
	throws DataAccessException, SQLException, NamingException, MDWException, ServiceLocatorException {


		if (logger.isInfoEnabled())
			logger.info(logtag(pi.getProcessId(), pi.getId(), ai.getDefinitionId(), ai.getId()),
					WorkStatus.LOGMSG_START + " - " + actVO.getActivityName());

		if (pWorkTransInstId!=null && pWorkTransInstId.longValue()!=0)
			edao.completeTransitionInstance(pWorkTransInstId, ai.getId());

		if (activity==null) {
            edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_FAILED, "Failed to instantiate activity implementor");
            return;
            // note cannot throw exception here, as when implementor is not defined,
            // the error handling itself will throw exception. We failed the activity outright here.
		}
		Class<?> implClass = activity.getClass();
		TrackingTimer activityTimer = null;
		Tracked t = implClass.getAnnotation(Tracked.class);
		if (t != null) {
			String logTag = logtag(pi.getProcessId(), pi.getId(), ai.getDefinitionId(), ai.getId());
			activityTimer = new TrackingTimer(logTag, actVO.getImplementorClassName(), t.value());
		}

		List<VariableInstanceInfo> vars;
		if (processVO.isEmbeddedProcess())
			vars = edao.getProcessInstanceVariables(pi.getOwnerId());
		else
		    vars = edao.getProcessInstanceVariables(pi.getId());

        event.setWorkInstanceId(ai.getId());

        activity.prepare(actVO, pi, ai, vars, pWorkTransInstId,
        		event.getCompletionCode(), activityTimer, new ProcessExecuter(this));
    		// prepare Activity to update SLA Instance
            // now moved to EventWaitActivity
		return;
    }

	private void removeActivitySLA(ActivityInstanceVO ai, ProcessInstanceVO procInst) {
		ProcessVO procdef = getProcessDefinition(procInst);
		ActivityVO actVO = procdef.getActivityVO(ai.getDefinitionId());
		int sla_seconds = actVO==null?0:actVO.getSlaSeconds();
		if (sla_seconds > 0) {
			ScheduledEventQueue eventQueue = ScheduledEventQueue.getSingleton();
			try {
				eventQueue.unscheduleEvent(ScheduledEvent.INTERNAL_EVENT_PREFIX+ai.getId());
			} catch (Exception e) {
				if (logger.isDebugEnabled()) logger.debugException("Failed to unschedule SLA", e);
			}
		}
	}

	private void failActivityInstance(ActivityInstanceVO ai, String statusMsg,
			ProcessInstanceVO procinst, String logtag, String abbrStatusMsg)
	throws DataAccessException, SQLException {
		edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_FAILED, statusMsg);
		removeActivitySLA(ai, procinst);
		if (logger.isInfoEnabled())
			logger.info(logtag, WorkStatus.LOGMSG_FAILED + " - " + abbrStatusMsg);
	}

	private void completeActivityInstance(ActivityInstanceVO ai, String compcode,
			ProcessInstanceVO procInst, String logtag)
	throws DataAccessException, SQLException {
		edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_COMPLETED, compcode);
		removeActivitySLA(ai, procInst);
		if (logger.isInfoEnabled())
			logger.info(logtag, WorkStatus.LOGMSG_COMPLETE + " - completion code "
					+ (compcode==null?"null":("'"+compcode+"'")));

	}

	private void cancelActivityInstance(ActivityInstanceVO ai, String statusMsg,
			ProcessInstanceVO procInst, String logtag)
	throws DataAccessException, SQLException {
		edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_CANCELLED, statusMsg);
		removeActivitySLA(ai, procInst);
		if (logger.isInfoEnabled())
			logger.info(logtag, WorkStatus.LOGMSG_CANCELLED + " - " + statusMsg);
	}

	private void holdActivityInstance(ActivityInstanceVO ai, String logtag)
	throws DataAccessException, SQLException {
		edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_HOLD, null);
		if (logger.isInfoEnabled()) logger.info(logtag, WorkStatus.LOGMSG_HOLD);
	}

	private void suspendActivityInstance(ActivityInstanceVO ai, String logtag, String additionalMsg)
	throws DataAccessException, SQLException {
		edao.setActivityInstanceStatus(ai, WorkStatus.STATUS_WAITING, null);
		if (logger.isInfoEnabled()) {
			if (additionalMsg!=null) logger.info(logtag, WorkStatus.LOGMSG_SUSPEND + " - " + additionalMsg);
			else logger.info(logtag, WorkStatus.LOGMSG_SUSPEND);
		}
	}

	CompletionCode finishActivityInstance(BaseActivity activity,
			ProcessInstanceVO pi, ActivityInstanceVO ai, InternalEventVO event, boolean bypassWait)
			throws DataAccessException, ProcessException, ActivityException, ServiceLocatorException {
		try {
			if (activity.getTimer() != null)
				activity.getTimer().start("Finish Activity");

            // Step 3  get and parse completion code
            boolean mayNeedWait = !bypassWait && activityNeedsWait(activity);
            String origCompCode = activity.getReturnCode();
            CompletionCode compCode = new CompletionCode();
            compCode.parse(origCompCode);
            Integer actInstStatus = compCode.getActivityInstanceStatus();
            if (actInstStatus==null && mayNeedWait) actInstStatus = WorkStatus.STATUS_WAITING;
            String logtag = logtag(pi.getProcessId(), pi.getId(), ai.getDefinitionId(), ai.getId());

            // Step 3a if activity not successful
            if (compCode.getEventType().equals(EventType.ERROR)) {
				failActivityInstance(ai, activity.getReturnMessage(),
						pi, logtag, activity.getReturnMessage());
                if (!AdapterActivity.COMPCODE_AUTO_RETRY.equals(compCode.getCompletionCode())) {
                    DocumentReference docRef = CreateActivityExceptionDocument(pi, ai, activity, null);
                	InternalEventVO outmsg = InternalEventVO.createActivityErrorMessage(ai.getDefinitionId(),
                		ai.getId(), pi.getId(), compCode.getCompletionCode(),
            			event.getMasterRequestId(), activity.getReturnMessage(), docRef.getDocumentId());
                	sendInternalEvent(outmsg);
                }
            }

			// Step 3b if activity needs to wait
            else if (mayNeedWait && actInstStatus!=null && !actInstStatus.equals(WorkStatus.STATUS_COMPLETED)) {
                if (actInstStatus.equals(WorkStatus.STATUS_HOLD)) {
                	holdActivityInstance(ai, logtag);
    				InternalEventVO outmsg = InternalEventVO.createActivityNotifyMessage(ai, compCode.getEventType(),
    						pi.getMasterRequestId(), compCode.getCompletionCode());
    				sendInternalEvent(outmsg);
                } else if (actInstStatus.equals(WorkStatus.STATUS_WAITING) &&
                		(compCode.getEventType().equals(EventType.ABORT) || compCode.getEventType().equals(EventType.CORRECT)
                				|| compCode.getEventType().equals(EventType.ERROR))) {
                	suspendActivityInstance(ai, logtag, null);
    				InternalEventVO outmsg =  InternalEventVO.createActivityNotifyMessage(ai, compCode.getEventType(),
    						pi.getMasterRequestId(), compCode.getCompletionCode());
    				sendInternalEvent(outmsg);
                }
                else if (actInstStatus.equals(WorkStatus.STATUS_CANCELLED)) {
                    cancelActivityInstance(ai, compCode.getCompletionCode(), pi,  logtag);
                    InternalEventVO outmsg =  InternalEventVO.createActivityNotifyMessage(ai, compCode.getEventType(),
                            pi.getMasterRequestId(), compCode.getCompletionCode());
                    sendInternalEvent(outmsg);
                }
                else {
                	suspendActivityInstance(ai, logtag, null);
                }
            }

			// Step 3c. otherwise, activity is successful and complete it
			else {
				completeActivityInstance(ai, origCompCode, pi, logtag);
                // send message to BAM when configured (OSGi is handled through monitors)
               /* if (!ApplicationContext.isOsgi())
				    activity.sendMessageToBam(WorkAttributeConstant.BAM_FINISH_MSGDEF);*/
                // notify registered monitors
                activity.notifyMonitors(WorkStatus.LOGMSG_COMPLETE);

				if (activity instanceof FinishActivity) {
					String compcode = ((FinishActivity)activity).getProcessCompletionCode();
                	boolean noNotify = ((FinishActivity)activity).doNotNotifyCaller();
					completeProcessInstance(pi, compcode, noNotify);
					List<ProcessInstanceVO> subProcessInsts = getDataAccess().getProcessInstances(pi.getProcessId(), OwnerType.MAIN_PROCESS_INSTANCE, pi.getId());
                    for (ProcessInstanceVO subProcessInstanceVO : subProcessInsts) {
                        if (!subProcessInstanceVO.getStatusCode().equals(WorkStatus.STATUS_COMPLETED) &&
                                !subProcessInstanceVO.getStatusCode().equals(WorkStatus.STATUS_CANCELLED))
                        completeProcessInstance(subProcessInstanceVO, compcode, noNotify);
                    }
				} else {
					InternalEventVO outmsg = InternalEventVO.createActivityNotifyMessage(ai,
							compCode.getEventType(), event.getMasterRequestId(), compCode.getCompletionCode());
					sendInternalEvent(outmsg);
				}
			}
            return compCode;	// not used by asynch engine
		} catch (Exception e) {
			throw new ProcessException(-1, e.getMessage(), e);
		} finally {
			if (activity.getTimer() != null)
				activity.getTimer().stopAndLogTiming();
		}
	}

	///////////// process finish

	void handleProcessFinish(InternalEventVO event) throws ProcessException
	{
		try {
			String ownerType = event.getOwnerType();
			String secondaryOwnerType = event.getSecondaryOwnerType();
			if (!OwnerType.ACTIVITY_INSTANCE.equals(secondaryOwnerType)) {
			    // top level processes (non-remote) or ABORT embedded processes
			    ProcessInstanceVO pi = edao.getProcessInstance(event.getWorkInstanceId());
			    ProcessVO subProcVO = getProcessDefinition(pi);
			    if (pi.isNewEmbedded() || subProcVO.isEmbeddedProcess()) {
			    	if (pi.isNewEmbedded()) subProcVO.getSubProcessVO(event.getWorkId());
			        String embeddedProcType = subProcVO.getAttribute(WorkAttributeConstant.EMBEDDED_PROCESS_TYPE);
			        if (ProcessVisibilityConstant.EMBEDDED_ABORT_PROCESS.equals(embeddedProcType)) {
			            Long parentProcInstId = event.getOwnerId();
			        	pi = edao.getProcessInstance(parentProcInstId);
			            this.cancelProcessInstanceTree(pi);
		        		if (logger.isInfoEnabled()) {
		            		logger.info(logtag(pi.getProcessId(), pi.getId(), pi.getMasterRequestId()),
		            				"Process cancelled");
		            	}
		        		InternalEventVO procFinishMsg = InternalEventVO.createProcessFinishMessage(pi);
	                	if (OwnerType.ACTIVITY_INSTANCE.equals(pi.getSecondaryOwner())) {
	                		procFinishMsg.setSecondaryOwnerType(pi.getSecondaryOwner());
	                		procFinishMsg.setSecondaryOwnerId(pi.getSecondaryOwnerId());
	                	}
	                	this.sendInternalEvent(procFinishMsg);
			        }
			    }
			} else if (ownerType.equals(OwnerType.PROCESS_INSTANCE)
					|| ownerType.equals(OwnerType.MAIN_PROCESS_INSTANCE)) {
			    // local process call or call to error/correction/delay handler
			    Long activityInstId = event.getSecondaryOwnerId();
			    ActivityInstanceVO actInst = edao.getActivityInstance(activityInstId);
			    ProcessInstanceVO procInst = edao.getProcessInstance(actInst.getOwnerId());
			    BaseActivity cntrActivity = prepareActivityForResume(event,procInst, actInst);
			    if (cntrActivity!=null) {
			    	resumeProcessInstanceForSecondaryOwner(event, cntrActivity);
			    }	// else the process is completed/cancelled
			} else {	// remote process call - forward the finish message to the remote caller
				event.setOwnerType(OwnerType.PROCESS_INSTANCE);
				logger.info("*********Reply to remote process invoker*********");
				// remote reference will be converted by the caller
				ProcessVO processVO = ProcessVOCache.getProcessVO(event.getWorkId());
				Long procInstId = event.getWorkInstanceId();
	        	List<VariableVO> variables = processVO.getVariables();
	        	Map<String,String> params = null;
	        	for (VariableVO var : variables) {
	        		if (var.getVariableCategory().intValue()==VariableVO.CAT_OUTPUT
	        				|| var.getVariableCategory().intValue()==VariableVO.CAT_INOUT) {
	        			VariableInstanceInfo vio = edao.getVariableInstance(procInstId,var.getVariableName());
	        			if (vio!=null) {
	        				if (params==null) {
	        					params = new HashMap<String,String>();
	        					event.setParameters(params);
	        				}
	        				params.put(var.getVariableName(), vio.getStringValue());
	        			}
	        		}
	        	}
                DomDocument domdoc = new DomDocument();
            	FormatDom fmter = new FormatDom();
            	domdoc.getRootNode().setName("_mdw_remote_process");
            	MbengNode node = domdoc.newNode("direction", "return", "", ' ');
            	domdoc.getRootNode().appendChild(node);
            	node = domdoc.newNode("message", event.toXml(), "", ' ');
            	domdoc.getRootNode().appendChild(node);
            	String msg = fmter.format(domdoc);
				DocumentReference docref = this.createDocument(XmlObject.class.getName(),
						event.getWorkInstanceId(), OwnerType.PROCESS_INSTANCE, event.getWorkInstanceId(),
						null, null, msg);
				CertifiedMessageManager manager = CertifiedMessageManager.getSingleton();
				Map<String,String> props = new HashMap<String,String>();
				props.put(CertifiedMessage.PROP_PROTOCOL, CertifiedMessage.PROTOCOL_MDW2MDW);
				props.put(CertifiedMessage.PROP_JNDI_URL, ownerType);
				manager.sendTextMessage(props, msg, docref.getDocumentId(), "procinst." + event.getWorkInstanceId());
			}
		} catch (Exception e) {
			throw new ProcessException(-1, e.getMessage(), e);
		}
	}

    private void handleResumeOnHold(GeneralActivity cntrActivity, ActivityInstanceVO actInst,
    		ProcessInstanceVO procInst)
    	throws DataAccessException, MDWException {
    	try {
    		InternalEventVO event = InternalEventVO.createActivityNotifyMessage(actInst,
    				EventType.RESUME, procInst.getMasterRequestId(), actInst.getStatusCode()==WorkStatus.STATUS_COMPLETED? "Completed" : null);
    		boolean finished = ((SuspendibleActivity)cntrActivity).resumeWaiting(event);
			this.resumeActivityFinishSub(actInst, (BaseActivity)cntrActivity, procInst,
					finished, true);
    	} catch (Exception e) {
//          throw new ProcessException(-1, e.getMessage(), e);
    		logger.severeException("Resume failed", e);
    		String statusMsg = "activity failed during resume";
    		try {
    	    	String logtag = logtag(procInst.getProcessId(), procInst.getId(),
    	    			actInst.getDefinitionId(), actInst.getId());
				failActivityInstance(actInst, statusMsg, procInst, logtag, statusMsg);
			} catch (SQLException e1) {
				throw new DataAccessException(-1, e1.getMessage(), e1);
			}
    		DocumentReference docRef = CreateActivityExceptionDocument(procInst, actInst, (BaseActivity)cntrActivity, e);
			InternalEventVO event = InternalEventVO.createActivityErrorMessage(
    	    		actInst.getDefinitionId(), actInst.getId(), procInst.getId(), null,
    	    		procInst.getMasterRequestId(), statusMsg, docRef.getDocumentId());
			this.sendInternalEvent(event);
    	}
    }

	 /**
     * Resumes the process instance for the secondary owner
     *
     * @param childInst child process instance
     * @param masterReqId
	 * @throws DataAccessException
	 * @throws ProcessException
	 * @throws JMSException
	 * @throws NamingException
	 * @throws MbengException
	 * @throws ServiceLocatorException
     */
    private void resumeProcessInstanceForSecondaryOwner(InternalEventVO event,
    		BaseActivity cntrActivity) throws Exception {
    	Long actInstId = event.getSecondaryOwnerId();
    	ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
    	String masterRequestId = event.getMasterRequestId();
//            Long parentInstId = eventMessageDoc.getEventMessage().getWorkOwnerId();
    	Long parentInstId = actInst.getOwnerId();
    	ProcessInstanceVO parentInst = edao.getProcessInstance(parentInstId);
    	String logtag = logtag(parentInst.getProcessId(), parentInstId, actInst.getDefinitionId(), actInstId);
    	boolean isEmbeddedProcess;
    	if (event.getOwnerType().equals(OwnerType.MAIN_PROCESS_INSTANCE)) isEmbeddedProcess = true;
    	else if (event.getOwnerType().equals(OwnerType.PROCESS_INSTANCE)) {
    		try {
    			ProcessVO subprocdef = ProcessVOCache.getProcessVO(event.getWorkId());
    			isEmbeddedProcess = subprocdef.isEmbeddedProcess();
    		} catch (Exception e1) {
    			// can happen when the subprocess is remote
    			logger.info(logtag,
    					"subprocess definition cannot be found - treat it as a remote process - id "
    					+ event.getWorkId());
    			isEmbeddedProcess = false;
    		}
    	} else isEmbeddedProcess = false;	// including the case the subprocess is remote
    	String compCode = event.getCompletionCode();
        if (isEmbeddedProcess) {
			// mark parent process instance in progress
			edao.setProcessInstanceStatus(parentInst.getId(), WorkStatus.STATUS_IN_PROGRESS);
    		if (logger.isInfoEnabled())
    			logger.info(logtag,	"Activity resumed from embedded subprocess, which returns completion code " + compCode);
            CompletionCode parsedCompCode = new CompletionCode();
            parsedCompCode.parse(event.getCompletionCode());
            WorkTransitionVO outgoingWorkTransVO = null;
            if (compCode==null || parsedCompCode.getEventType().equals(EventType.RESUME)) {		// default behavior
            	if (actInst.getStatusCode()==WorkStatus.STATUS_HOLD ||
            	        actInst.getStatusCode()==WorkStatus.STATUS_COMPLETED) {
            		handleResumeOnHold(cntrActivity, actInst, parentInst);
            	} else if (actInst.getStatusCode()==WorkStatus.STATUS_FAILED) {
        			completeActivityInstance(actInst, compCode, parentInst, logtag);
                    // send message to BAM when configured (OSGi is handled through monitors)
                    /*if (!ApplicationContext.isOsgi())
    				    cntrActivity.sendMessageToBam(WorkAttributeConstant.BAM_FINISH_MSGDEF);*/
                    // notify registered monitors - (both OSGi and Cloud mode)
                    cntrActivity.notifyMonitors(WorkStatus.LOGMSG_FAILED);

    				InternalEventVO jmsmsg = InternalEventVO.createActivityNotifyMessage(actInst,
    		    			EventType.FINISH, masterRequestId, null);
    				sendInternalEvent(jmsmsg);
            	} else {
            		// other status simply ignore
            	}
            } else if (parsedCompCode.getEventType().equals(EventType.ABORT)) {	// TaskAction.ABORT and TaskAction.CANCEL
            	String comment = actInst.getStatusMessage() + "  \nException handler returns " + compCode;
            	if (actInst.getStatusCode()!=WorkStatus.STATUS_COMPLETED) {
            		cancelActivityInstance(actInst, comment, parentInst, logtag);
            	}
           	 	if (parsedCompCode.getCompletionCode()!=null && parsedCompCode.getCompletionCode().startsWith("process"))	{// TaskAction.ABORT
           	 		boolean invoke_abort_handler = true;
           	 		if (invoke_abort_handler) {
           	 			InternalEventVO outgoingMsg = InternalEventVO.createActivityNotifyMessage(actInst,
           	 					EventType.ABORT, parentInst.getMasterRequestId(), null);
           	 			sendInternalEvent(outgoingMsg);
           	 		} else {
           	 			completeProcessInstance(parentInst, EventType.EVENTNAME_ABORT, false);
           	 		}
           	 	}
            } else if (parsedCompCode.getEventType().equals(EventType.START)) {		// TaskAction.RETRY
            	String comment = actInst.getStatusMessage() +
            		"  \nException handler returns " + compCode;
            	if (actInst.getStatusCode()!=WorkStatus.STATUS_COMPLETED) {
            		cancelActivityInstance(actInst, comment, parentInst, logtag);
            	}
            	retryActivity(parentInst, actInst.getDefinitionId(), null, masterRequestId);
            } else {	// event type must be FINISH
            	if (parsedCompCode.getCompletionCode()!=null)
            		outgoingWorkTransVO = findTaskActionWorkTransition(parentInst, actInst, parsedCompCode.getCompletionCode());
            	if (actInst.getStatusCode()!=WorkStatus.STATUS_COMPLETED) {
        			completeActivityInstance(actInst, compCode, parentInst, logtag);
        			if (!ApplicationContext.isOsgi())
        			    cntrActivity.sendMessageToBam(WorkAttributeConstant.BAM_FINISH_MSGDEF);
                    cntrActivity.notifyMonitors(WorkStatus.LOGMSG_COMPLETE);
            	}
            	InternalEventVO jmsmsg;
            	int delay = 0;
            	if (outgoingWorkTransVO != null) {
            		// is custom action (RESUME), transition accordingly
    				WorkTransitionInstanceVO workTransInst = createTransitionInstance(outgoingWorkTransVO, parentInstId);
    		        jmsmsg = InternalEventVO.createActivityStartMessage(
    		        		outgoingWorkTransVO.getToWorkId(), parentInstId,
    		        		  workTransInst.getTransitionInstanceID(), masterRequestId,
    		        		  outgoingWorkTransVO.getLabel());
    		        delay = outgoingWorkTransVO.getTransitionDelay();
            	} else {
            		jmsmsg = InternalEventVO.createActivityNotifyMessage(actInst,
    		    			EventType.FINISH, masterRequestId, null);
            	}
            	if (delay>0) {
    	    		String msgid = ScheduledEvent.INTERNAL_EVENT_PREFIX + parentInstId
    					+ "start" + outgoingWorkTransVO.getToWorkId();
            		sendDelayedInternalEvent(jmsmsg, delay, msgid, false);
            	} else sendInternalEvent(jmsmsg);
            }
        } else {	// must be InvokeProcessActivity
        	if (actInst.getStatusCode()==WorkStatus.STATUS_WAITING || actInst.getStatusCode()==WorkStatus.STATUS_HOLD) {
        		boolean isSynchronized = ((InvokeProcessActivity)cntrActivity).resume(event);
        		if (isSynchronized) {   // all subprocess instances terminated
        			// mark parent process instance in progress
        			edao.setProcessInstanceStatus(parentInst.getId(), WorkStatus.STATUS_IN_PROGRESS);
        			// complete the activity and send activity FINISH message
                    CompletionCode parsedCompCode = new CompletionCode();
                    parsedCompCode.parse(event.getCompletionCode());
        			if (parsedCompCode.getEventType().equals(EventType.ABORT)) {
	        			cancelActivityInstance(actInst, "Subprocess is cancelled", parentInst, logtag);
        			} else {
	        			completeActivityInstance(actInst, compCode, parentInst, logtag);
	        			if (!ApplicationContext.isOsgi())
	        			    cntrActivity.sendMessageToBam(WorkAttributeConstant.BAM_FINISH_MSGDEF);
	                    cntrActivity.notifyMonitors(WorkStatus.LOGMSG_COMPLETE);
        			}
        			InternalEventVO jmsmsg = InternalEventVO.createActivityNotifyMessage(actInst,
    		    			EventType.FINISH, masterRequestId, compCode);
        			sendInternalEvent(jmsmsg);
        		}  else {
        			// multiple instances and not all terminated - do nothing
        			logger.info(logtag, "Activity continue suspend - not all child processes have completed");
        		}
        	} else {  // status is COMPLETED or others
        		// do nothing - asynchronous subprocess call
        		logger.info(logtag, "Activity not waiting for subprocess - asynchronous subprocess call");
        	}
        }
    }

    private void completeProcessInstance(ProcessInstanceVO procInst) throws Exception {
    	edao.setProcessInstanceStatus(procInst.getId(), WorkStatus.STATUS_COMPLETED);
    	if (!inService) {
    		edao.removeEventWaitForProcessInstance(procInst.getId());
    		this.cancelTasksOfProcessInstance(procInst);
    	}
    }

    /**
     *
     * @param processInstVO
     * @param processVO
     * @param pMessage
     * @throws SQLException
     * @throws DataAccessException
     * @throws JMSException
     * @throws NamingException
     * @throws MbengException
     * @throws ServiceLocatorException
     * @throws ProcessHandlerException
     */
    private void completeProcessInstance(ProcessInstanceVO processInst, String completionCode, boolean noNotify)
    throws Exception {

    	ProcessVO processVO = getProcessDefinition(processInst);
        InternalEventVO retMsg = InternalEventVO.createProcessFinishMessage(processInst);

        if (OwnerType.ACTIVITY_INSTANCE.equals(processInst.getSecondaryOwner())) {
        	retMsg.setSecondaryOwnerType(processInst.getSecondaryOwner());
        	retMsg.setSecondaryOwnerId(processInst.getSecondaryOwnerId());
        }

    	if (completionCode==null) completionCode = processInst.getCompletionCode();
    	if (completionCode!=null) retMsg.setCompletionCode(completionCode);

    	boolean isCancelled = false;
        if (completionCode==null) {
        	completeProcessInstance(processInst);
        } else if (processVO.isEmbeddedProcess()) {
         	completeProcessInstance(processInst);
            retMsg.setCompletionCode(completionCode);
        } else {
        	CompletionCode parsedCompCode = new CompletionCode();
        	parsedCompCode.parse(completionCode);
        	if (parsedCompCode.getEventType().equals(EventType.ABORT)) {
        		this.cancelProcessInstanceTree(processInst);
        		isCancelled = true;
        	} else if (parsedCompCode.getEventType().equals(EventType.FINISH)) {
            	completeProcessInstance(processInst);
        		if (parsedCompCode.getCompletionCode()!=null) {
        			completionCode = parsedCompCode.getCompletionCode();
        			retMsg.setCompletionCode(completionCode);
        		} else completionCode = null;
        	} else {
            	completeProcessInstance(processInst);
        		retMsg.setCompletionCode(completionCode);
        	}
        }
        if (!noNotify) sendInternalEvent(retMsg);
        if (logger.isInfoEnabled()) {
    		logger.info(logtag(processVO.getProcessId(), processInst.getId(), processInst.getMasterRequestId()),
    				(isCancelled?WorkStatus.LOGMSG_PROC_CANCEL:WorkStatus.LOGMSG_PROC_COMPLETE) + " - " + processVO.getProcessName()
    				+ (isCancelled?"":completionCode==null?" completion code is null":(" completion code = "+completionCode)));
        }
        notifyMonitors(processInst, WorkStatus.LOGMSG_PROC_COMPLETE);
    }

    /**
     * Look up the appropriate work transition for an embedded exception handling subprocess.
     * @param parentInstance the parent process
     * @param activityInstance the activity in the main process
     * @param taskAction the selected task action
     * @return the matching work transition, if found
     */
    private WorkTransitionVO findTaskActionWorkTransition(ProcessInstanceVO parentInstance,
            ActivityInstanceVO activityInstance, String taskAction) {
        if (taskAction == null)
            return null;

        ProcessVO processVO = getProcessDefinition(parentInstance);
        WorkTransitionVO workTransVO = processVO.getWorkTransition(activityInstance.getDefinitionId(), EventType.RESUME, taskAction);
        if (workTransVO == null) {
            // try upper case
            workTransVO = processVO.getWorkTransition(activityInstance.getDefinitionId(), EventType.RESUME, taskAction.toUpperCase());
        }
        if (workTransVO == null) {
            workTransVO = processVO.getWorkTransition(activityInstance.getDefinitionId(), EventType.FINISH, taskAction);
        }
        return workTransVO;
    }

    private void retryActivity(ProcessInstanceVO procInst, Long actId,
            String completionCode, String masterRequestId)
            throws DataAccessException, SQLException, MDWException {
    	// make sure any other activity instances are closed
    	List<ActivityInstanceVO> activityInstances = edao.getActivityInstances(actId, procInst.getId(),
    			true, false);
    	for (ActivityInstanceVO actInst :  activityInstances) {
    		if (actInst.getStatusCode()==WorkStatus.STATUS_IN_PROGRESS.intValue()
                        || actInst.getStatusCode()==WorkStatus.STATUS_PENDING_PROCESS.intValue()) {
    			String logtag = logtag(procInst.getProcessId(), procInst.getId(),
    					actId, actInst.getId());
    			failActivityInstance(actInst, "Retry Activity Action",
    					procInst, logtag, "Retry Activity Action");
    		}
    	}
    	// start activity again
    	InternalEventVO event = InternalEventVO.createActivityStartMessage(actId,
    			procInst.getId(), null, masterRequestId, EventType.EVENTNAME_START);
    	sendInternalEvent(event);
    }

	/////////////// activity resume

    private boolean validateProcessInstance(ProcessInstanceVO processInst) {
        Integer status = processInst.getStatusCode();
        if (WorkStatus.STATUS_CANCELLED.equals(status)) {
            logger.info("ProcessInstance has been cancelled. ProcessInstanceId = " + processInst.getId());
            return false;
        } else if (WorkStatus.STATUS_COMPLETED.equals(status)) {
            logger.info("ProcessInstance has been completed. ProcessInstanceId = " + processInst.getId());
            return false;
        } else return true;
    }


    private BaseActivity prepareActivityForResume(InternalEventVO event,
	    		ProcessInstanceVO procInst, ActivityInstanceVO actInst)
    throws DataAccessException, SQLException
    {
    	Long actId = actInst.getDefinitionId();
    	Long procInstId = actInst.getOwnerId();

    	if (!validateProcessInstance(procInst)) {
    		if (logger.isInfoEnabled())
    			logger.info(logtag(procInst.getProcessId(), procInstId, actId, actInst.getId()),
    				"Activity would resume, but process is no longer alive");
    		return null;
    	}
    	if (logger.isInfoEnabled())
    		logger.info(logtag(procInst.getProcessId(), procInstId, actId, actInst.getId()), "Activity to resume");

    	ProcessVO processVO = getProcessDefinition(procInst);
    	ActivityVO actVO = processVO.getActivityVO(actId);

		TrackingTimer activityTimer = null;
    	try {
            // use design-time package
            PackageVO pkg = PackageVOCache.getProcessPackage(getMainProcessDefinition(procInst).getId());

			BaseActivity cntrActivity = (BaseActivity)pkg.getActivityImplementor(actVO);
	    	Tracked t = cntrActivity.getClass().getAnnotation(Tracked.class);
	    	if (t != null) {
	    		String logTag = logtag(procInst.getProcessId(), procInst.getId(), actId, actInst.getId());
	    		activityTimer = new TrackingTimer(logTag, cntrActivity.getClass().getName(), t.value());
	    		activityTimer.start("Prepare Activity for Resume");
	    	}
    		List<VariableInstanceInfo> vars = processVO.isEmbeddedProcess()?
    				edao.getProcessInstanceVariables(procInst.getOwnerId()):
    					edao.getProcessInstanceVariables(procInstId);
    		// procInst.setVariables(vars);	 set inside edac method
    		Long workTransitionInstId = event.getTransitionInstanceId();
    		cntrActivity.prepare(actVO, procInst, actInst, vars, workTransitionInstId,
    				event.getCompletionCode(), activityTimer, new ProcessExecuter(this));
    		return cntrActivity;
    	} catch (Exception e) {
			logger.severeException("Unable to instantiate implementer " + actVO.getImplementorClassName(), e);
			return null;
		}
    	finally {
    		if (activityTimer != null) {
    			activityTimer.stopAndLogTiming();
    		}
    	}
    }

    private boolean isProcessInstanceResumable(ProcessInstanceVO pInstance) {
        int statusCd = pInstance.getStatusCode().intValue();
        if (statusCd == WorkStatus.STATUS_COMPLETED.intValue()) {
            return false;
        } else if (statusCd == WorkStatus.STATUS_CANCELLED.intValue()) {
            return false;
        }
        return true;
    }

    ActivityRuntimeVO resumeActivityPrepare(ProcessInstanceVO procInst,
    		InternalEventVO event, boolean resumeOnHold)
    		throws ProcessException, DataAccessException {
    	Long actInstId = event.getWorkInstanceId();
    	try {
    		ActivityRuntimeVO ar = new ActivityRuntimeVO();
    		ar.startCase = ActivityRuntimeVO.RESUMECASE_NORMAL;
    		ar.actinst = edao.getActivityInstance(actInstId);
    		ar.procinst = procInst;
            if (!this.isProcessInstanceResumable(ar.procinst)) {
            	ar.startCase = ActivityRuntimeVO.RESUMECASE_PROCESS_TERMINATED;
                logger.info(logtag(ar.procinst.getProcessId(), ar.procinst.getId(),
                			ar.actinst.getDefinitionId(), actInstId),
                		"Cannot resume activity instance as the process is completed/canceled");
                return ar;
            }
            if (!resumeOnHold && ar.actinst.getStatusCode()!=WorkStatus.STATUS_WAITING.intValue()) {
            	logger.info(logtag(ar.procinst.getProcessId(), ar.procinst.getId(),
            			ar.actinst.getDefinitionId(), actInstId),
            		"Cannot resume activity instance as it is not waiting any more");
            	ar.startCase = ActivityRuntimeVO.RESUMECASE_ACTIVITY_NOT_WAITING;
            	return ar;
            }
            ar.activity = prepareActivityForResume(event, ar.procinst, ar.actinst);
        	if (resumeOnHold) event.setEventType(EventType.RESUME);
        	else event.setEventType(EventType.FINISH);
            return ar;
    	} catch (SQLException e) {
    		throw new ProcessException(-1, e.getMessage(), e);
    	}
    }

    private void resumeActivityFinishSub(ActivityInstanceVO actinst,
    		BaseActivity activity, ProcessInstanceVO procinst,
    		boolean finished, boolean resumeOnHold)
    	throws DataAccessException, SQLException, MDWException {
		String logtag = logtag(procinst.getProcessId(),procinst.getId(),
				actinst.getDefinitionId(),actinst.getId());
    	if (finished) {
			CompletionCode completionCode = new CompletionCode();
			completionCode.parse(activity.getReturnCode());
	    	if (WorkStatus.STATUS_HOLD.equals(completionCode.getActivityInstanceStatus())) {
				holdActivityInstance(actinst, logtag);
			} else if (WorkStatus.STATUS_WAITING.equals(completionCode.getActivityInstanceStatus())) {
				suspendActivityInstance(actinst, logtag, "continue suspend");
			} else if (WorkStatus.STATUS_CANCELLED.equals(completionCode.getActivityInstanceStatus())) {
				cancelActivityInstance(actinst, "Cancelled upon resume", procinst, logtag);
			} else if (WorkStatus.STATUS_FAILED.equals(completionCode.getActivityInstanceStatus())) {
				failActivityInstance(actinst, "Failed upon resume", procinst, logtag, activity.getReturnMessage());
			} else {	// status is null or Completed
				completeActivityInstance(actinst, completionCode.toString(), procinst, logtag);
                // send message to BAM when configured (OSGi is handled through monitors)
                /*if (!ApplicationContext.isOsgi())
				    activity.sendMessageToBam(WorkAttributeConstant.BAM_FINISH_MSGDEF);*/
                // notify registered monitors
                activity.notifyMonitors(WorkStatus.LOGMSG_COMPLETE);
			}
	    	InternalEventVO event = InternalEventVO.createActivityNotifyMessage(actinst,
					completionCode.getEventType(), procinst.getMasterRequestId(),
					completionCode.getCompletionCode());
	    	sendInternalEvent(event);
    	} else {
    		if (resumeOnHold) {
        		suspendActivityInstance(actinst, logtag, "resume waiting after hold");
        	} else {
				if (logger.isInfoEnabled()) logger.info(logtag, "continue suspend");
			}
    	}
    }

    void resumeActivityFinish(ActivityRuntimeVO ar,
    		boolean finished, InternalEventVO event, boolean resumeOnHold)
    		throws DataAccessException, ProcessException {
    	try {
            if (ar.activity.getTimer() != null)
            	ar.activity.getTimer().start("Resume Activity Finish");
			this.resumeActivityFinishSub(ar.actinst, ar.activity, ar.procinst,
					finished, resumeOnHold);
    	} catch (SQLException e) {
			throw new ProcessException(-1, e.getMessage(), e);
    	} catch (MDWException e) {
			throw new ProcessException(-1, e.getMessage(), e);
		} finally {
			if (ar.activity.getTimer() != null)
				ar.activity.getTimer().stopAndLogTiming();
		}
    }

    boolean resumeActivityExecute(ActivityRuntimeVO ar,
    		InternalEventVO event, boolean resumeOnHold) throws ActivityException {
    	boolean finished;
    	try {
    		if (ar.activity.getTimer() != null)
    			ar.activity.getTimer().start("Resume Activity");
    		if (resumeOnHold) finished = ((SuspendibleActivity)ar.activity).resumeWaiting(event);
    		else finished = ((SuspendibleActivity)ar.activity).resume(event);
    	}
    	finally {
    		if (ar.activity.getTimer() != null)
    			ar.activity.getTimer().stopAndLogTiming();
    	}
    	return finished;
    }

	Map<String, String> getOutputParameters(Long procInstId, Long procId) throws SQLException,
			ProcessException, DataAccessException {
		ProcessVO subprocDef = ProcessVOCache.getProcessVO(procId);
		Map<String, String> params = new HashMap<String, String>();
		boolean passDocContent = (isInService() && getDataAccess().getPerformanceLevel() >= 5) || getDataAccess().getPerformanceLevel() >= 9 ;  // DHO  (if not serviceProc then lvl9)
		for (VariableVO var : subprocDef.getVariables()) {
			if (var.getVariableCategory().intValue() == VariableVO.CAT_OUTPUT
					|| var.getVariableCategory().intValue() == VariableVO.CAT_INOUT) {
				VariableInstanceInfo vio = getDataAccess()
						.getVariableInstance(procInstId,
								var.getVariableName());
				if (vio != null) {
					if (passDocContent && vio.isDocument()) {
						DocumentVO docvo = getDocument((DocumentReference)vio.getData(), false);
						if (docvo!=null) params.put(var.getVariableName(), docvo.getContent());
					} else {
						params.put(var.getVariableName(), vio.getStringValue());
					}
				}
			}
		}
		return params;
	}

    void resumeActivityException(
    		ProcessInstanceVO procInst,
    		Long actInstId, BaseActivity activity, Throwable cause) {
        String compCode = null;
        try {
			String statusMsg = buildStatusMessage(cause);
			ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
			String logtag = logtag(procInst.getProcessId(), procInst.getId(),
					actInst.getDefinitionId(), actInst.getId());
			failActivityInstance(actInst, statusMsg, procInst, logtag, "Exception in resume");
            if (activity==null || !AdapterActivity.COMPCODE_AUTO_RETRY.equals(activity.getReturnCode())) {
                DocumentReference docRef = CreateActivityExceptionDocument(procInst, actInst, activity, cause);
				InternalEventVO outgoingMsg = InternalEventVO.createActivityErrorMessage(
							actInst.getDefinitionId(), actInst.getId(),
							procInst.getId(), compCode, procInst.getMasterRequestId(),
							statusMsg, docRef.getDocumentId());
				sendInternalEvent(outgoingMsg);
            }
		} catch (Exception e) {
			logger.severeException("\n\n*****Failed in handleResumeException*****\n", e);
		}
    }

    //////// handle process abort

    /**
     * Abort a single process instance by process instance ID,
     * or abort potentially multiple (but typically one) process instances
     * by process ID and owner ID.
     *
     * @param pMessage
     * @param pProcessInst
     * @param pCause
     * @throws ProcessHandlerException
     *
     */
    void abortProcessInstance(InternalEventVO event)
      throws ProcessException {
        Long processId = event.getWorkId();
        String processOwner = event.getOwnerType();
        Long processOwnerId = event.getOwnerId();
        Long processInstId = event.getWorkInstanceId();
        try {
        	if (processInstId!=null && processInstId.longValue()!=0L) {
	        	ProcessInstanceVO pi = edao.getProcessInstance(processInstId);
        		cancelProcessInstanceTree(pi);
        		if (logger.isInfoEnabled()) {
            		logger.info(logtag(pi.getProcessId(), pi.getId(), pi.getMasterRequestId()),
            				"Process cancelled");
            	}
        	} else {
	            List<ProcessInstanceVO> coll = edao.getProcessInstances(processId, processOwner, processOwnerId);
	            if (CollectionUtil.isEmpty(coll)) {
	                logger.info("No Process Instances for the Process and Owner");
	                return;
	            }
	            for (ProcessInstanceVO pi : coll) {
	                // there really should have only one
	                cancelProcessInstanceTree(pi);
	            }
        	}
        }
        catch (Exception ex) {
            logger.severeException(ex.getMessage(), ex);
            throw new ProcessException(ex.getMessage());
        }
    }

    /**
     * Cancels the process instance as well as all descendant process instances.
     *
     * The method deregister associated event wait instances.
     *
     * @param pProcessInstId
     * @return new WorkInstance
     * @throws SQLException
     * @throws ProcessException
     */
    private void cancelProcessInstanceTree(ProcessInstanceVO pi)
    throws Exception {
    	if (pi.getStatusCode().equals(WorkStatus.STATUS_COMPLETED) ||
    			pi.getStatusCode().equals(WorkStatus.STATUS_CANCELLED)) {
    		throw new ProcessException("ProcessInstance is not in a cancellable state");
    	}
    	List<ProcessInstanceVO> childInstances = edao.getChildProcessInstances(pi.getId());
    	for (ProcessInstanceVO child : childInstances) {
    		if (!child.getStatusCode().equals(WorkStatus.STATUS_COMPLETED)
    				&& !child.getStatusCode().equals(WorkStatus.STATUS_CANCELLED)) {
    			this.cancelProcessInstanceTree(child);
    		} else {
    			logger.info("Descendent ProcessInstance in not in a cancellable state. ProcessInstanceId="
                    + child.getId());
    		}
    	}
    	this.cancelProcessInstance(pi);
    }

    /**
     * Cancels a single process instance.
     * It cancels all active transition instances, all event wait instances,
     * and sets the process instance into canceled status.
     *
     * The method does not cancel task instances
     *
     * @param pProcessInst
     * @return new WorkInstance
     */
    private void cancelProcessInstance(ProcessInstanceVO pProcessInst)
        throws Exception {
        edao.cancelTransitionInstances(pProcessInst.getId(),
                "ProcessInstance has been cancelled.", null);
        edao.setProcessInstanceStatus(pProcessInst.getId(), WorkStatus.STATUS_CANCELLED);
        edao.removeEventWaitForProcessInstance(pProcessInst.getId());
		this.cancelTasksOfProcessInstance(pProcessInst);
    }

    /////////////////////// other

    private void cancelTasksOfProcessInstance(ProcessInstanceVO procInst)
    	throws NamingException, JMSException, MbengException,
    			SQLException, ServiceLocatorException, MDWException {
    	List<ProcessInstanceVO> processInstanceList =
    		edao.getChildProcessInstances(procInst.getId());
		List<Long> procInstIds = new ArrayList<Long>();
		procInstIds.add(procInst.getId());
		for (ProcessInstanceVO pi : processInstanceList) {
			ProcessVO pidef = getProcessDefinition(pi);
			if (pidef.isEmbeddedProcess()) procInstIds.add(pi.getId());
		}
		TaskManagerAccess.getInstance().cancelTasksOfProcessInstances(procInstIds);
    }

    EventWaitInstanceVO createEventWaitInstance(
    		Long actInstId, String pEventName, String compCode,
    		boolean pRecurring, boolean notifyIfArrived)
    throws DataAccessException, ProcessException {
    	try {
    		String FINISH = EventType.getEventTypeName(EventType.FINISH);
    		if (compCode==null||compCode.length()==0) compCode = FINISH;
    		EventWaitInstanceVO ret = null;
    		Long documentId = edao.recordEventWait(pEventName,
    				!pRecurring,
    				3600,       // TODO set this value in designer!
    				actInstId, compCode);
    		if (logger.isInfoEnabled()) {
    			logger.info("registered event wait event='"
    					+ pEventName + "' actInst=" + actInstId
    					+ (pRecurring?" as recurring":"as non-recurring"));
    		}
    		if (documentId!=null) {
    			if (logger.isInfoEnabled()) {
    				logger.info((notifyIfArrived?"notify":"return") +
    						" event before registration: event='"
    						+ pEventName + "' actInst=" + actInstId);
    			}
    			if (notifyIfArrived) {
    				if (compCode.equals(FINISH)) compCode = null;
    				ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
    				resumeActivityInstance(actInst, compCode, documentId, null, 0);
        			edao.removeEventWaitForActivityInstance(actInstId, "activity notified");
    			} else {
        			edao.removeEventWaitForActivityInstance(actInstId, "activity to notify is returned");
    			}
    			ret = new EventWaitInstanceVO();
    			ret.setMessageDocumentId(documentId);
    			ret.setCompletionCode(compCode);
    			ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
    			DocumentVO docvo = edao.getDocument(documentId, true);
    			docvo.setProcessInstanceId(actInst.getOwnerId());
    			edao.updateDocumentInfo(docvo);
    		}
    		return ret;
    	} catch (MDWException e) {
    		throw new ProcessException(-1, e.getMessage(), e);
    	} catch (SQLException e) {
    		throw new ProcessException(-1, e.getMessage(), e);
    	}

    }

    /**
     * Method that creates the event log based on the passed in params
     *
     * @param pEventNames
     * @param pEventSources
     * @param pEventOwner
     * @param pEventOwnerId
     * @param pWorkTransInstId
     * @param pEventTypes
     * @param pEventOccurances
     * @param pDeRegisterSiblings
     * @return EventWaitInstance
     * @throws SQLException
     * @throws ProcessException
     */
    EventWaitInstanceVO createEventWaitInstances(Long actInstId,
    		String[] pEventNames, String[] pWakeUpEventTypes,
    		boolean[] pEventOccurances, boolean notifyIfArrived)
    throws DataAccessException, ProcessException {

    	try {
            EventWaitInstanceVO ret = null;
            Long documentId = null;
            String pCompCode = null;
            int i;
            for (i=0; i < pEventNames.length; i++) {
                pCompCode = pWakeUpEventTypes[i];
                documentId = edao.recordEventWait(pEventNames[i],
                        !pEventOccurances[i],
                        3600,       // TODO set this value in designer!
                        actInstId, pWakeUpEventTypes[i]);
                if (logger.isInfoEnabled()) {
        			logger.info("registered event wait event='"
        					+ pEventNames[i] + "' actInst=" + actInstId
        					+ (pEventOccurances[i]?" as recurring":"as non-recurring"));
        		}
               	if (documentId!=null) break;
            }
            if (documentId!=null) {
            	if (logger.isInfoEnabled()) {
    				logger.info((notifyIfArrived?"notify":"return") +
    						" event before registration: event='"
    						+ pEventNames[i] + "' actInst=" + actInstId);
    			}
                if (pCompCode!=null && pCompCode.length()==0) pCompCode = null;
                if (notifyIfArrived) {
                	ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
	                resumeActivityInstance(actInst, pCompCode, documentId, null, 0);
        			edao.removeEventWaitForActivityInstance(actInstId, "activity notified");
                } else {
        			edao.removeEventWaitForActivityInstance(actInstId, "activity to notify is returned");
                }
                ret = new EventWaitInstanceVO();
                ret.setMessageDocumentId(documentId);
                ret.setCompletionCode(pCompCode);
                ActivityInstanceVO actInst = edao.getActivityInstance(actInstId);
                DocumentVO docvo = edao.getDocument(documentId, true);
                docvo.setProcessInstanceId(actInst.getOwnerId());
                edao.updateDocumentInfo(docvo);
            }
            return ret;
    	} catch (SQLException e) {
    		throw new ProcessException(-1, e.getMessage(), e);
       	} catch (MDWException e) {
    		throw new ProcessException(-1, e.getMessage(), e);
    	}
    }

    Integer notifyProcess(String pEventName, Long pEventInstId,
    		String message, int delay)
    throws DataAccessException, EventException, SQLException {
    	List<EventWaitInstanceVO> waiters = edao.recordEventArrive(pEventName, pEventInstId);
    	if (waiters!=null) {
    		boolean hasFailures = false;
    		try {
    			for (EventWaitInstanceVO inst : waiters) {
    				String pCompCode = inst.getCompletionCode();
    				if (pCompCode!=null && pCompCode.length()==0) pCompCode = null;
    				if (logger.isInfoEnabled()) {
    					logger.info("notify event after registration: event='"
    							+ pEventName + "' actInst=" + inst.getActivityInstanceId());
    				}
    				ActivityInstanceVO actInst = edao.getActivityInstance(inst.getActivityInstanceId());
    				if (actInst.getStatusCode()==WorkStatus.STATUS_IN_PROGRESS.intValue()) {
    					// assuming it is a service process waiting for message
    					JSONObject json = new JSONObject();
    			    	json.put("ACTION", "NOTIFY");
    			    	json.put("CORRELATION_ID", pEventName);
    			    	json.put("MESSAGE", message);
    			    	internalMessenger.broadcastMessage(json.toString());
    				} else {
    					resumeActivityInstance(actInst, pCompCode, pEventInstId, message, delay);
    				}
	                // deregister wait instances
	                edao.removeEventWaitForActivityInstance(inst.getActivityInstanceId(), "activity notified");
	                if (pEventInstId!=null && pEventInstId.longValue()>0) {
	                	DocumentVO docvo = edao.getDocument(pEventInstId, true);
	                	docvo.setProcessInstanceId(actInst.getOwnerId());
	                	edao.updateDocumentInfo(docvo);
	                }
    			}
    		} catch (Exception ex) {
    			logger.severeException(ex.getMessage(), ex);
    			throw new EventException(ex.getMessage());
    		}
    		if (hasFailures) return EventInstanceVO.RESUME_STATUS_PARTIAL_SUCCESS;
    		else return EventInstanceVO.RESUME_STATUS_SUCCESS;
    	} else return EventInstanceVO.RESUME_STATUS_NO_WAITERS;
    }


    private boolean isProcessInstanceProgressable(ProcessInstanceVO pInstance) {

        int statusCd = pInstance.getStatusCode().intValue();
        if (statusCd == WorkStatus.STATUS_COMPLETED.intValue()) {
            return false;
        } else if (statusCd == WorkStatus.STATUS_CANCELLED.intValue()) {
            return false;
        } else if (statusCd == WorkStatus.STATUS_HOLD.intValue()) {
            return false;
        }
        return true;
    }

    /**
     * Sends a RESUME internal event to resume the activity instance.
     *
     * This may be called in the following cases:
     *   1) received an external event (including the case the message is received before registration)
     *   	In this case, the argument message is populated.
     *   2) when register even wait instance, and the even has already arrived. In this case
     *   	the argument message null.
     *
     * @param pProcessInstId
     * @param pCompletionCode
     */
    private void resumeActivityInstance(ActivityInstanceVO actInst,
            String pCompletionCode, Long documentId, String message, int delay)
    throws DataAccessException, MDWException, SQLException {
        ProcessInstanceVO pi = edao.getProcessInstance(actInst.getOwnerId());
        if (!this.isProcessInstanceResumable(pi)) {
            logger.info("ProcessInstance in NOT resumable. ProcessInstanceId:" + pi.getId());
        }
        InternalEventVO outgoingMsg = InternalEventVO.
        	createActivityNotifyMessage(actInst, EventType.RESUME,
        			pi.getMasterRequestId(), pCompletionCode);
        if (documentId!=null) {		// should be always true
        	outgoingMsg.setSecondaryOwnerType(OwnerType.DOCUMENT);
        	outgoingMsg.setSecondaryOwnerId(documentId);
        }
        if (message!=null && message.length()<2500) {
        	outgoingMsg.addParameter("ExternalEventMessage", message);
    	}
        if (this.isProcessInstanceProgressable(pi)) {
            edao.setProcessInstanceStatus(pi.getId(), WorkStatus.STATUS_IN_PROGRESS);
        }
        if (delay>0) this.sendDelayedInternalEvent(outgoingMsg, delay,
        		ScheduledEvent.INTERNAL_EVENT_PREFIX+actInst.getId(), false);
        else this.sendInternalEvent(outgoingMsg);
    }

    void sendInternalEvent(InternalEventVO event) throws MDWException {
    	internalMessenger.sendMessage(event, edao);
    }

    void sendDelayedInternalEvent(InternalEventVO event, int delaySeconds, String msgid, boolean isUpdate)
    	throws MDWException {
    	internalMessenger.sendDelayedMessage(event, delaySeconds, msgid, isUpdate, edao);
    }

    boolean isInService() {
    	return inService;
    }

    boolean isInMemory() {
    	if (null != edao && edao.getPerformanceLevel() >= 9)
    		return true;
    	return false;
    }

    /**
     * Notify registered ProcessMonitors.
     */
    public void notifyMonitors(ProcessInstanceVO processInstance, String event) throws SQLException, DataAccessException {
        // notify registered monitors
        List<ProcessMonitor> monitors = MonitorRegistry.getInstance().getProcessMonitors();
        if (!monitors.isEmpty()) {
            ProcessVO processVO = getMainProcessDefinition(processInstance);
            PackageVO pkg = PackageVOCache.getProcessPackage(processVO.getId());
            Map<String, Object> vars = new HashMap<String, Object>();
            if (processInstance.getVariables() != null) {
                for (VariableInstanceInfo var : processInstance.getVariables()) {
                    Object value = var.getData();
                    if (value instanceof DocumentReference) {
                        try {
                            DocumentVO docVO = getDocument((DocumentReference) value, false);
                            value = docVO == null ? null : docVO.getObject(var.getType(), pkg);
                        }
                        catch (DataAccessException ex) {
                            logger.severeException(ex.getMessage(), ex);
                        }
                    }
                    vars.put(var.getName(), value);
                }
            }
            ProcessRuntimeContext runtimeContext = new ProcessRuntimeContext(pkg, processVO, processInstance, vars);

            for (ProcessMonitor monitor : monitors) {
                try {
                    if (monitor instanceof OfflineMonitor) {
                        @SuppressWarnings("unchecked")
                        OfflineMonitor<ProcessRuntimeContext> processOfflineMonitor = (OfflineMonitor<ProcessRuntimeContext>) monitor;
                        new OfflineMonitorTrigger<ProcessRuntimeContext>(processOfflineMonitor, runtimeContext).fire(event);
                    }
                    else {
                        if (WorkStatus.LOGMSG_PROC_START.equals(event)) {
                            Map<String, Object> updated = monitor.onStart(runtimeContext);
                            if (updated != null) {
                                for (String varName : updated.keySet()) {
                                    if (processInstance.getVariables() == null)
                                        processInstance.setVariables(new ArrayList<VariableInstanceInfo>());
                                    VariableVO varVO = processVO.getVariable(varName);
                                    if (varVO == null || !varVO.isInput())
                                        throw new ProcessException("Process '" + processVO.getLabel() + "' has no such input variable defined: " + varName);
                                    if (processInstance.getVariable(varName) != null)
                                        throw new ProcessException("Process '" + processVO.getLabel() + "' input variable already populated: " + varName);
                                    if (varVO.isDocument()) {
                                        DocumentReference docRef = createDocument(varVO.getVariableType(), processInstance.getId(), OwnerType.VARIABLE_INSTANCE, new Long(0), null, null, updated.get(varName));
                                        VariableInstanceInfo varInst = createVariableInstance(processInstance, varName, docRef);
                                        processInstance.getVariables().add(varInst);
                                    }
                                    else {
                                        VariableInstanceInfo varInst = createVariableInstance(processInstance, varName, updated.get(varName));
                                        processInstance.getVariables().add(varInst);
                                    }
                                }
                            }
                        }
                        else if (WorkStatus.LOGMSG_PROC_COMPLETE.equals(event)) {
                            monitor.onFinish(runtimeContext);
                        }
                    }
                }
                catch (Exception ex) {
                    logger.severeException(ex.getMessage(), ex);
                }
            }
        }
    }

    private DocumentReference CreateActivityExceptionDocument(ProcessInstanceVO processInst, ActivityInstanceVO actInstVO, BaseActivity activity, Throwable cause) throws DataAccessException {
        ActivityException actEx = new ActivityException(cause != null ? cause.getMessage() : "Unknown Exception or Error", cause);
        if (actInstVO != null) {
            ActivityInstance actInst = new ActivityInstance();
            actInst.setStartDate(StringHelper.stringToDate(actInstVO.getStartDate()));
            actInst.setEndDate(new Date(DatabaseAccess.getCurrentTime()));
            actInst.setId(actInstVO.getId());
            actInst.setDefinitionId("A" + actInstVO.getDefinitionId());
            actInst.setMasterRequestId(processInst.getMasterRequestId());
            actInst.setPackageName(processInst.getPackageName());
            actInst.setProcessId(processInst.getProcessId());
            actInst.setProcessName(processInst.getProcessName());
            actInst.setProcessVersion(processInst.getProcessVersion());
            actInst.setStatus(WorkStatus.STATUSNAME_FAILED);
            actInst.setDefinitionMissing(false);
            if (activity == null) {
                actInst.setMessage(actInstVO.getStatusMessage());
                actInst.setName(null);
                actInst.setResult(null);
            }
            else {
                actInst.setMessage(actInstVO.getStatusMessage() != null ? actInstVO.getStatusMessage() + "\n" + activity.getReturnMessage() : activity.getReturnMessage());
                actInst.setName(activity.getActivityName());
                actInst.setResult(activity.getReturnCode());
            }
            actEx.setActivityInstance(actInst);
        }
        DocumentReference docRef = createDocument("java.lang.Object", processInst.getId(), OwnerType.DOCUMENT, actInstVO.getId(), null, null, actEx);
        return docRef;
    }
}
