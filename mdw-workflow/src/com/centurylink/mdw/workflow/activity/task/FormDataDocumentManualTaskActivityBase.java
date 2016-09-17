/**
 * Copyright (c) 2014 CenturyLink, Inc. All Rights Reserved.
 */
package com.centurylink.mdw.workflow.activity.task;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.centurylink.mdw.activity.ActivityException;
import com.centurylink.mdw.activity.types.TaskActivity;
import com.centurylink.mdw.common.constant.FormConstants;
import com.centurylink.mdw.common.constant.OwnerType;
import com.centurylink.mdw.common.exception.PropertyException;
import com.centurylink.mdw.common.translator.VariableTranslator;
import com.centurylink.mdw.common.utilities.form.CallURL;
import com.centurylink.mdw.common.utilities.logger.LoggerUtil;
import com.centurylink.mdw.common.utilities.logger.StandardLogger;
import com.centurylink.mdw.model.FormDataDocument;
import com.centurylink.mdw.model.data.event.EventType;
import com.centurylink.mdw.model.data.monitor.ServiceLevelAgreement;
import com.centurylink.mdw.model.data.task.TaskAction;
import com.centurylink.mdw.model.data.work.WorkStatus;
import com.centurylink.mdw.model.value.attribute.RuleSetVO;
import com.centurylink.mdw.model.value.event.EventWaitInstanceVO;
import com.centurylink.mdw.model.value.event.InternalEventVO;
import com.centurylink.mdw.model.value.process.ProcessInstanceVO;
import com.centurylink.mdw.model.value.process.ProcessVO;
import com.centurylink.mdw.model.value.task.TaskVO;
import com.centurylink.mdw.model.value.variable.DocumentReference;
import com.centurylink.mdw.model.value.variable.VariableVO;
import com.centurylink.mdw.model.value.work.ActivityInstanceVO;
import com.centurylink.mdw.model.value.work.WorkTransitionVO;
import com.centurylink.mdw.services.task.TaskManagerAccess;
import com.centurylink.mdw.workflow.activity.AbstractWait;
import com.qwest.mbeng.DomDocument;
import com.qwest.mbeng.FormatDom;
import com.qwest.mbeng.MbengException;
import com.qwest.mbeng.MbengNode;

public abstract class FormDataDocumentManualTaskActivityBase extends AbstractWait
    implements TaskActivity {

    protected static StandardLogger logger = LoggerUtil.getStandardLogger();

    protected static final String WAIT_FOR_TASK = "Wait for Task";

    public boolean needSuspend() {
        String waitForTask = this.getAttributeValue(WAIT_FOR_TASK);
        return waitForTask==null || waitForTask.equalsIgnoreCase("true");
    }

    protected final DomDocument loadForm() throws ActivityException {
        DomDocument formdoc;
        String formname = this.getAttributeValue(TaskActivity.ATTRIBUTE_FORM_NAME);
        String formVersion = this.getAttributeValue(TaskActivity.ATTRIBUTE_FORM_VERSION);
        int version = (formVersion==null)?0:Integer.parseInt(formVersion);
        try {
            RuleSetVO ruleset = super.getRuleSet(formname, RuleSetVO.FORM, version);
            if (ruleset==null) throw new
                ActivityException("Failed to load the ruleset " + formname);
            FormatDom fmter = new FormatDom();
            formdoc = new DomDocument();
            fmter.load(formdoc, ruleset.getRuleSet());
        } catch (Exception e) {
            throw new ActivityException(-1, "Cannot load ruleset " + formname, e);
        }
        return formdoc;
    }

    protected void populateFormDataMetaInfo(FormDataDocument datadoc, boolean subsequentCall,
    		boolean updateActivityInstanceId)
    throws ActivityException {
        try {
//          String taskName = this.getAttributeValue(TaskActivity.ATTRIBUTE_TASK_NAME);
            // taskName is taken from TaskVO - should consider retire that and take it from here
            String formName = this.getAttributeValue(TaskActivity.ATTRIBUTE_FORM_NAME);
			if (formName!=null && formName.length()>0) {
				datadoc.setAttribute(FormDataDocument.ATTR_FORM, formName);
	            String formVersion = this.getAttributeValue(TaskActivity.ATTRIBUTE_FORM_VERSION);
	            datadoc.setMetaValue(FormDataDocument.META_FORM_VERSION, formVersion==null?"0":formVersion);
			}
			// else subsequent call no need to set form name
			if (subsequentCall) {
	    		datadoc.setAttribute(FormDataDocument.ATTR_ACTION, FormConstants.ACTION_RESPOND_TASK);
	    		if (updateActivityInstanceId || datadoc.getMetaValue(FormDataDocument.META_ACTIVITY_INSTANCE_ID)==null)
	    			datadoc.setMetaValue(FormDataDocument.META_ACTIVITY_INSTANCE_ID,
		    				this.getActivityInstanceId().toString());
			} else {
				datadoc.setAttribute(FormDataDocument.ATTR_ACTION, FormConstants.ACTION_CREATE_TASK);
	    		datadoc.setMetaValue(FormDataDocument.META_ACTIVITY_INSTANCE_ID,
	    				this.getActivityInstanceId().toString());
			}
            datadoc.setAttribute(FormDataDocument.ATTR_NAME, this.getActivityId().toString());
            datadoc.setMetaValue(FormDataDocument.META_PROCESS_INSTANCE_ID, getProcessInstanceId().toString());
			if (!subsequentCall) {
				String sla = getAttributeValueSmart(TaskActivity.ATTRIBUTE_TASK_SLA);
				String slaUnits = this.getAttributeValue(TaskActivity.ATTRIBUTE_TASK_SLA_UNITS);
				if (slaUnits==null) slaUnits = ServiceLevelAgreement.INTERVAL_HOURS;
				datadoc.setMetaValue(FormDataDocument.META_DUE_IN_SECONDS,
						Integer.toString(ServiceLevelAgreement.unitsToSeconds(sla,slaUnits)));
				datadoc.setMetaValue(FormDataDocument.META_TASK_NAME, this.getAttributeValue(TaskActivity.ATTRIBUTE_TASK_NAME));
				// task name is not used by MDW task manager currently, but set for foreign task manager
				String taskLogicalId = this.getAttributeValue(TaskActivity.ATTRIBUTE_TASK_LOGICAL_ID);
				if (taskLogicalId==null) {		// MDW 4.*/5.0 in-flight order compatibility code
	          	  	taskLogicalId = TaskVO.MDW4_TASK_LOGICAL_ID_PREFIX + this.getActivityId().toString();
	            }
	            datadoc.setMetaValue(FormDataDocument.META_TASK_LOGICAL_ID, taskLogicalId);
	            datadoc.setMetaValue(FormDataDocument.META_MASTER_REQUEST_ID, getMasterRequestId());
	            this.fillInCustomActions(datadoc);
			}
        } catch (PropertyException e) {
            throw new ActivityException(-1, e.getMessage(), e);
        }
    }

    /**
     * create form document data to be sent to the task manager.
     * @return
     * @throws ActivityException
     */
    abstract protected FormDataDocument createFormData()
    throws ActivityException;

    /**
     * Populate back the data modified by task.
     *
     * @param datadoc
     * @param formnode
     * @return completion code; when it returns null, the completion
     *   code is taken from the completionCode parameter of
     *   the form data document attribute FormDataDocument.ATTR_ACTION
     * @throws ActivityException
     */
    abstract protected String extractFormData(FormDataDocument datadoc)
    throws ActivityException;

    // EventName is a correlation ID between task manager and engine
    protected String getEventName(FormDataDocument formdata) {
        return formdata.getAttribute(FormDataDocument.ATTR_ID);
    }

    /**
     * This method is invoked to process a received event (other than task completion).
     * You will need to override this method to customize processing of the event.
     *
     * The default method does nothing.
     *
     * The status of the activity after processing the event is configured in the designer, which
     * can be either Hold or Waiting.
     *
     * When you override this method, you can optionally set different completion
     * code from those configured in the designer by calling setReturnCode().
     *
     * @param messageString the entire message content of the external event (from document table)
     * @throws ActivityException
     */
    protected void processOtherMessage(String messageString)
        throws ActivityException {
    }

    public final boolean resume(InternalEventVO event)
            throws ActivityException {
        // secondary owner type must be OwnerType.EXTERNAL_EVENT_INSTANCE
        String messageString = super.getMessageFromEventMessage(event);
        return resume(messageString, event.getCompletionCode());
    }

    protected boolean resume(String message, String completionCode) throws ActivityException {
        if (messageIsTaskAction(message)) {
            processTaskAction(message);
            return true;
        } else {
            this.setReturnCode(completionCode);
            processOtherMessage(message);
            Integer actInstStatus = super.handleEventCompletionCode();
            if (actInstStatus.equals(WorkStatus.STATUS_CANCELLED)) {
                try {
                	TaskManagerAccess.getInstance().
                        cancelTasksOfActivityInstance(getActivityInstanceId(), getProcessInstanceId());
                } catch (Exception e) {
                    logger.severeException("Failed to cancel task instance - process moves on", e);
                }
            } else if (actInstStatus.equals(WorkStatus.STATUS_WAITING)) {
            	try {
            		FormDataDocument formdata = this.getFormDataDocumentFromVariable();
            		getEngine().createEventWaitInstance(
            				this.getActivityInstanceId(),
            				getEventName(formdata),
            				null, true, true);
				} catch (Exception e) {
					logger.severeException("Failed to re-register task action listening", e);
				}
				// unsolicited event listening is already registered by handleEventCompletionCode
            }
            return true;
        }
    }

    protected boolean messageIsTaskAction(String messageString) throws ActivityException {
    	if (messageString.startsWith("{")) {
    		JSONObject jsonobj;
			try {
				jsonobj = new JSONObject(messageString);
	    		JSONObject meta = jsonobj.has("META")?jsonobj.getJSONObject("META"):null;
	    		if (meta==null || !meta.has(FormDataDocument.META_ACTION)) return false;
	    		String action = meta.getString(FormDataDocument.META_ACTION);
	    		return action!=null && action.startsWith(FormConstants.SPECIAL_ACTION_PREFIX);
	    	} catch (JSONException e) {
				throw new ActivityException(0, "Failed to parse JSON message", e);
			}
    	} else {
    		int k = messageString.indexOf("FORMDATA");
    		return k>0 && k<8;
    	}
    }

    protected void processTaskAction(String messageString)
    throws ActivityException {
        try {
            FormDataDocument datadoc = new FormDataDocument();
            datadoc.load(messageString);
            String compCode = extractFormData(datadoc); // this handles both embedded proc and not
            String action = datadoc.getAttribute(FormDataDocument.ATTR_ACTION);
            CallURL callurl = new CallURL(action);
            action = callurl.getAction();
            ProcessVO procdef = getProcessDefinition();
            if (compCode==null) compCode = datadoc.getMetaValue(FormConstants.URLARG_COMPLETION_CODE);
            if (compCode==null) compCode = callurl.getParameter(FormConstants.URLARG_COMPLETION_CODE);
        	String subaction = datadoc.getMetaValue(FormConstants.URLARG_ACTION);
        	if (subaction==null) subaction = callurl.getParameter(FormConstants.URLARG_ACTION);
            if (this.getProcessInstance().isNewEmbedded() || procdef.isEmbeddedExceptionHandler()) {
            	if (subaction==null)
            		subaction = compCode;
                if (action.equals(FormConstants.ACTION_CANCEL_TASK)) {
                	if (TaskAction.ABORT.equalsIgnoreCase(subaction))
                		compCode = EventType.EVENTNAME_ABORT + ":process";
                	else compCode = EventType.EVENTNAME_ABORT;
                } else {    // FormConstants.ACTION_COMPLETE_TASK
                	if (TaskAction.RETRY.equalsIgnoreCase(subaction))
                		compCode = TaskAction.RETRY;
                	else if (compCode==null) compCode = EventType.EVENTNAME_FINISH;
                    else compCode = EventType.EVENTNAME_FINISH + ":" + compCode;
                }
                	this.setProcessInstanceCompletionCode(compCode);
                	setReturnCode(null);
            } else {
                if (action.equals(FormConstants.ACTION_CANCEL_TASK)) {
                    if (TaskAction.ABORT.equalsIgnoreCase(subaction))
                    	compCode = WorkStatus.STATUSNAME_CANCELLED + "::" + EventType.EVENTNAME_ABORT;
                    else compCode = WorkStatus.STATUSNAME_CANCELLED + "::";
                    setReturnCode(compCode);
                } else {    // FormConstants.ACTION_COMPLETE_TASK
                    setReturnCode(compCode);
                }
            }
        } catch (Exception e) {
            String errmsg = "Failed to parse task completion message";
            logger.severeException(errmsg, e);
            throw new ActivityException(-1, errmsg, e);
        }
    }

    public final boolean resumeWaiting(InternalEventVO event) throws ActivityException {
        boolean done;
        EventWaitInstanceVO received;
        try {
        	// re-register wait events
            FormDataDocument formdata = this.getFormDataDocumentFromVariable();
            received = getEngine().createEventWaitInstance(
                    this.getActivityInstanceId(),
                    getEventName(formdata),
                    null, true, false);
            if (received==null) received = registerWaitEvents(false,true);
        } catch (Exception e) {
            throw new ActivityException(-1, e.getMessage(), e);
        }
        if (received!=null) {
            done = resume(getExternalEventInstanceDetails(received.getMessageDocumentId()),
                    received.getCompletionCode());
        } else {
            done = false;
        }
        return done;
    }


    /*==================================================================================*/
    /* older semantics: auto-generate form data from process variables, and             */
    /* auto-generate custom task actions from process definitions.                      */
    /* Also, extract response data and assign them to process variables.                */
    /*==================================================================================*/

    protected final void fillInCustomActions(FormDataDocument formdatadoc)
            throws ActivityException {
        ProcessVO procdef = getProcessDefinition();
        ActivityInstanceVO actInst = null;
        if (procdef.isEmbeddedExceptionHandler()) {
            try {
                ProcessInstanceVO procInst = getEngine().getProcessInstance(getProcessInstanceId());
                actInst = getEngine().getActivityInstance(procInst.getSecondaryOwnerId());
            } catch (Exception e) {
                throw new ActivityException(-1, "Failed to get request data", e);
            }
        } // else handled inside getCustomActions()
        List<String> customActions = getCustomActions(actInst);
        StringBuffer sb = new StringBuffer();
        for (String one : customActions) {
            if (sb.length()>0) sb.append('#');
            sb.append(one);
        }
        formdatadoc.setMetaValue(FormDataDocument.META_TASK_CUSTOM_ACTIONS, sb.toString());
    }

    protected final void fillInDataFromProcessVariables(FormDataDocument formdatadoc)
    		throws ActivityException {
    	DomDocument formdoc = loadForm();
        fillInData(formdatadoc, formdoc.getRootNode());
    }

    private void fillInData(FormDataDocument datadoc, MbengNode formnode)
            throws ActivityException {
        String type = formnode.getName();
        if (type.equals(FormConstants.WIDGET_TEXT)
                || type.equals(FormConstants.WIDGET_TEXTAREA)
                || type.equals(FormConstants.WIDGET_RADIOBUTTONS)
                || type.equals(FormConstants.WIDGET_CHECKBOX)
                || type.equals(FormConstants.WIDGET_DATE)
                || type.equals(FormConstants.WIDGET_DROPDOWN)) {
            String datapath = formnode
                    .getAttribute(FormConstants.FORMATTR_DATA);
            if (datapath != null && datapath.length() > 0) {
                String data = this.getDataFromVariable(datapath);
                try {
                    if (data != null)
                        datadoc.setValue(datapath, data,
                                FormDataDocument.KIND_FIELD);
                } catch (MbengException e) {
                    throw new ActivityException(-1,
                            "Failed to set value", e);
                }
            }
        } else if (type.equals(FormConstants.WIDGET_PANEL)
                || type.equals(FormConstants.WIDGET_TABBEDPANE)
                || type.equals(FormConstants.WIDGET_TAB)
                || type.equals(FormConstants.WIDGET_PAGELET)) {
            MbengNode child;
            for (child = formnode.getFirstChild(); child != null; child = child
                    .getNextSibling()) {
                fillInData(datadoc, child);
            }

        } else {
            // Do nothing:
            // FormConstants.WIDGET_TABLE
            // FormConstants.WIDGET_COLUMN
            // FormConstants.WIDGET_BUTTON
            // FormConstants.WIDGET_HYPERLINK
            // FormConstants.WIDGET_MENUBAR
            // FormConstants.WIDGET_MENU
            // FormConstants.WIDGET_MENUITEM
            // FormConstants.WIDGET_LIST
            // FormConstants.WIDGET_LISTPICKER
        }
    }

    /**
     * This invoked by fillInData to get value for the given data path
     * specification on the form. The method assumes datapath
     * is a variable name and returns the value of the variable (or null
     * if variable is not found or not bound).
     * You can override this method to get values that are not from
     * process variables.
     *
     * @param datapath the data specified by a form widget
     * @throws ActivityException
     */
    protected String getDataFromVariable(String datapath) throws ActivityException {
        Object data = this.getParameterValue(datapath);
        if (data==null) return null;
        String pType = this.getParameterType(datapath);
        if (VariableTranslator.isDocumentReferenceVariable(pType))
            return VariableTranslator.realToString(pType, data);
        else return VariableTranslator.toString(pType, data);
    }

    /**
     * Return a list of customizable actions, including optional standard
     * actions "Retry", "Complete", "Cancel" and custom actions.
     *
     * @param actInst when the task is in exception handler, this is the activity instance
     *      throwing the exception; null if not in exception handler.
     */
    protected List<String> getCustomActions(ActivityInstanceVO actInst)
            throws ActivityException {
        List<String> actions = new ArrayList<String>();
        ProcessVO procdef;
        Long activityId;

        try {
            // if the activity is within exception handler, add Retry
            if (actInst!=null) {
                actions.add("Retry");
                procdef = getMainProcessDefinition();
                activityId = actInst.getDefinitionId();
            } else {
                procdef = getProcessDefinition();
                activityId = this.getActivityId();
            }

            // if the activity has an out transition w/o completion code, add Complete/Cancel
            // if the activity has transitions with completion code, add them
            List<WorkTransitionVO> outTrans = procdef.getAllWorkTransitions(activityId);
            boolean foundNullResultCode = false;
            for (WorkTransitionVO workTransVO : outTrans) {
                Integer eventType = workTransVO.getEventType();
                if (eventType.equals(EventType.FINISH) || eventType.equals(EventType.RESUME)) {
                    String resultCode = workTransVO.getCompletionCode();
                    if (workTransVO.getCompletionCode() == null) foundNullResultCode = true;
                    else actions.add(resultCode);
                }
            }
            if (foundNullResultCode) {
                actions.add("Complete");
                actions.add("Cancel");
            }
        } catch (Exception e) {
            throw new ActivityException(-1, "Failed infer task actions", e);
        }
        return actions;
    }

    protected void setDataToVariable(String datapath, String value)
            throws ActivityException {
        if (value == null || value.length() == 0)
            return;
        // w/o above, hit oracle constraints that variable value must not be
        // null
        // shall we consider removing that constraint? and shall we check
        // if the variable should be updated?
        String pType = this.getParameterType(datapath);
        if (pType == null)
            return; // ignore data that is not a variable
        if (VariableTranslator.isDocumentReferenceVariable(pType))
            this.setParameterValueAsDocument(datapath, pType, value);
        else
            this.setParameterValue(datapath, value);
    }

    protected final void extractDataToProcessVariables(FormDataDocument datadoc, MbengNode formnode)
            throws ActivityException {
        String type = formnode.getName();
        if (type.equals(FormConstants.WIDGET_TEXT)
                || type.equals(FormConstants.WIDGET_TEXTAREA)
                || type.equals(FormConstants.WIDGET_RADIOBUTTONS)
                || type.equals(FormConstants.WIDGET_CHECKBOX)
                || type.equals(FormConstants.WIDGET_DATE)
                || type.equals(FormConstants.WIDGET_DROPDOWN)) {
            String datapath = formnode
                    .getAttribute(FormConstants.FORMATTR_DATA);
            if (datapath != null && datapath.length() > 0) {
                setDataToVariable(datapath, datadoc.getValue(datapath));
            }
        } else if (type.equals(FormConstants.WIDGET_PANEL)
                || type.equals(FormConstants.WIDGET_TABBEDPANE)
                || type.equals(FormConstants.WIDGET_TAB)
                || type.equals(FormConstants.WIDGET_PAGELET)) {
            MbengNode child;
            for (child = formnode.getFirstChild(); child != null; child = child
                    .getNextSibling()) {
                extractDataToProcessVariables(datadoc, child);
            }
        } else {
            // Do nothing:
            // FormConstants.WIDGET_TABLE
            // FormConstants.WIDGET_COLUMN
            // FormConstants.WIDGET_BUTTON
            // FormConstants.WIDGET_HYPERLINK
            // FormConstants.WIDGET_MENUBAR
            // FormConstants.WIDGET_MENU
            // FormConstants.WIDGET_MENUITEM
            // FormConstants.WIDGET_LIST
            // FormConstants.WIDGET_LISTPICKER
        }
    }

    /**
     * Drives variable setting from the contents of the datadoc for the case
     * where no
     */
    protected void extractDataToProcessVariables(FormDataDocument formDataDoc) throws ActivityException {
        ProcessVO processVO = getProcessDefinition();
        for (VariableVO variable : processVO.getVariables()) {
            String value = formDataDoc.getValue(variable.getVariableName());
            if (value != null)
              setDataToVariable(variable.getVariableName(), value);
        }
    }

    protected final FormDataDocument getFormDataDocumentFromVariable()
            throws Exception {
        String formDataVar = this.getAttributeValue(TaskActivity.ATTRIBUTE_FORM_DATA_VAR);
        if (formDataVar==null) throw new ActivityException("Form data variable not defined");
        Object datadocref = this.getParameterValue(formDataVar);
        FormDataDocument datadoc = null;
        if (datadocref == null) {
            datadoc = new FormDataDocument();  // starting with blank
            datadoc.setMetaValue(FormDataDocument.META_FORM_DATA_VARIABLE_NAME, formDataVar);
            DocumentReference docRef = createDocument(FormDataDocument.class.getName(), datadoc, OwnerType.PROCESS_INSTANCE, getProcessInstanceId(), null, null);
            setParameterValue(formDataVar, docRef);
        }
        else {
            if (!(datadocref instanceof DocumentReference))
                throw new ActivityException("Form data variable not bound to document");
            String datadocContent = getDocumentContent((DocumentReference)datadocref);
            if (datadocContent==null) throw new ActivityException("Form data document not exist");
            datadoc = new FormDataDocument();
            datadoc.load(datadocContent);
        }
        return datadoc;
    }

    /**
     * Returns the task Name attribute
     */
    public String getTaskName(){
        return super.getAttributeValue(ATTRIBUTE_TASK_NAME);
    }

}