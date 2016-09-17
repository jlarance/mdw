/**
 * Copyright (c) 2014 CenturyLink, Inc. All Rights Reserved.
 */
package com.centurylink.mdw.workflow.activity.timer;

import java.util.Date;

import com.centurylink.mdw.activity.ActivityException;
import com.centurylink.mdw.common.constant.OwnerType;
import com.centurylink.mdw.common.constant.WorkAttributeConstant;
import com.centurylink.mdw.common.exception.PropertyException;
import com.centurylink.mdw.common.utilities.StringHelper;
import com.centurylink.mdw.common.utilities.logger.StandardLogger.LogLevel;
import com.centurylink.mdw.common.utilities.timer.Tracked;
import com.centurylink.mdw.dataaccess.DatabaseAccess;
import com.centurylink.mdw.model.data.event.EventType;
import com.centurylink.mdw.model.data.monitor.ScheduledEvent;
import com.centurylink.mdw.model.value.event.EventWaitInstanceVO;
import com.centurylink.mdw.model.value.event.InternalEventVO;
import com.centurylink.mdw.model.value.work.ActivityInstanceVO;
import com.centurylink.mdw.services.process.ProcessExecuter;
import com.centurylink.mdw.workflow.activity.AbstractWait;



/**
 * Base class for all the Timer Activities
 * This class will be extended by the custom activity
 * that do not want a completion event raised.
 * This will act as a stopper for the workflow
 * In cases where in case of an error or a maual task creation,
 * we can use this activity to pause the flow and the flow can be
 * re started from an external event.
 */
@Tracked(LogLevel.TRACE)
public class TimerWaitActivity extends AbstractWait {

    protected static final int DEFAULT_WAIT = 30;
    private static final String WAIT_UNIT = "Unit";
    private static final String MINUTES = "Minutes";
    private static final String HOURS = "Hours";

    /**
     * The method is used to schedule sending an activity resume message
     * after the given number of seconds.
     * @param seconds time for delay
     * @throws ActivityException
     */
    protected final void sendDelayedResumeMessage(int seconds)
    	throws ActivityException
    {
    	sendOrUpdateExpirationMessage(seconds, false);
    }

    /**
     * The method is used to update the expiration time.
     * Note that for the implementation using JMS delay
     * (when the delay in minutes is less 5 which can be altered by property mdw.timer.ThresholdForDelay),
     * the method simply sends another JMS message, so the calling application
     * needs to take care of the duplicated messages as follows:
     *   In processTimerExpiration() method, validate if the timer is really expired.
     *   if the timer is not expired, return the number of seconds remaining + 60.
     * This has the following impacts:
     * a) If the new expiration time is later than the original, when the first message
     *    arrives, the timer is not really expired, a third message is scheduled to
     *    sent on expiration time + 60. When the 2nd message is received (on expiration time),
     *    the activity instance is completed. When the third message is received, the
     *    engine ignores it as the activity instance is already completed.
     * b) If the new expiration time is earlier than the original, when the second message
     *    (which arrives first) arrives on expiration time, the activity is completed.
     *    When the first message arrives later (on original expiration time), the
     *    engine ignores it as the activity is already completed.
     *
     * @param seconds time for delay
     * @throws ActivityException
     */
    protected final void updateDelayedResumeMessage(int seconds)
    throws ActivityException
    {
    	sendOrUpdateExpirationMessage(seconds, true);
    }

    private void sendOrUpdateExpirationMessage(int seconds, boolean isUpdate)
    throws ActivityException
    {
    	try {
    		ProcessExecuter engine = getEngine();
    		long currentTime = DatabaseAccess.getCurrentTime();
    		engine.updateActivityInstanceEndTime(getActivityInstanceId(),
    				new Date(currentTime+seconds*1000L));
    	} catch (Exception e) {
     		super.logwarn("Failed to set timer expiration time in DB: " + e.getMessage());
    	}
    	InternalEventVO message = InternalEventVO.createActivityNotifyMessage(getActivityInstance(),
    			EventType.RESUME, getMasterRequestId(), null);
     	message.setMessageDelay(seconds);
        try {
        	ProcessExecuter engine = getEngine();
        	String eventName = ScheduledEvent.INTERNAL_EVENT_PREFIX+this.getActivityInstanceId() + "timer";
        	engine.sendDelayedInternalEvent(message, seconds, eventName, isUpdate);
        } catch (Exception e) {
        	throw new ActivityException(-1, e.getMessage(), e);
        }
    }

    @Override
    public void execute() throws ActivityException {
    	int seconds = getWaitPeriodInSeconds();
    	EventWaitInstanceVO received = registerWaitEvents(false, true);
    	sendDelayedResumeMessage(seconds);
    	if (received!=null) {
    		this.setReturnCode(received.getCompletionCode());
    		processOtherMessage(getExternalEventInstanceDetails(received.getMessageDocumentId()));
    		handleEventCompletionCode();
    	}
    }

    /**
     * Method that returns the wait period for the activity
     * @return Wait period
     */
    protected int getWaitPeriodInSeconds() throws ActivityException {
        String unit = super.getAttributeValue(WAIT_UNIT);
        int factor;
        if (unit==null || unit.equals(MINUTES)) factor = 60;
        else if (unit.equals(HOURS)) factor = 3600;
        else factor = 1;
        int retTime;
        String timeAttr;
        try {
            timeAttr = super.getAttributeValueSmart(WorkAttributeConstant.TIMER_WAIT);
        } catch (PropertyException e) {
            throw new ActivityException(-1, "failed to evaluate time expression", e);
        }
        retTime = StringHelper.getInteger(timeAttr, 0);
        if (retTime*factor < DEFAULT_WAIT) {
            retTime = DEFAULT_WAIT;
        }
        return retTime*factor;
    }

     /**
      * This method is invoked to process a received event (other than timer expiration).
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

    private boolean isOtherEvent(InternalEventVO event) {
    	return OwnerType.DOCUMENT.equals(event.getSecondaryOwnerType());
    }

    /**
     * This method is invoked when timer expires. The method simply returns 0,
     *
     * You can override this method to handle custom logic, and you can force the activity
     * to wait further by returning the number of seconds to wait further.
     * Returning 0 or negative numbers will stop waiting.
     *
     * @return 0
     * @throws ActivityException
     */
      protected int processTimerExpiration() throws ActivityException {
    	return 0;
    }

    /**
     * The resume method for this class is handling internal functions related to
     * events processing in addition to timer expiration, so it is not supposed to be overriden. The method
     * is therefore declared as final. To customize handling of events, please override
     * the method {@link #processOtherMessage(String)}, and to customize
     * the activity so that it can reset timer and wait again, please override
     * the method {@link #processTimerExpiration()}.
     */
    public final boolean resume(InternalEventVO event) throws ActivityException {
    	if (isOtherEvent(event)) {
         	String messageString = this.getMessageFromEventMessage(event);
        	this.setReturnCode(event.getCompletionCode());
        	processOtherMessage(messageString);
        	handleEventCompletionCode();
         	return true;
    	} else {	// timer expires
    		int moreToWaitInSeconds = processTimerExpiration();
        	if (moreToWaitInSeconds<=0) super.deregisterEvents();
        	else sendDelayedResumeMessage(moreToWaitInSeconds);
        	return moreToWaitInSeconds<=0;
    	}
    }

    /**
     * This method is made final for the class, as it contains internal logic handling resumption
     * of waiting. It re-register the event waits including waiting for task to complete.
     * If any event has already arrived, it processes it immediately.
     *
     * Customization should be done with the methods {@link #processOtherMessage(String, String)},
     * {@link #registerWaitEvents()}, and {@link #processTimerExpiration()}.
     */
    public final boolean resumeWaiting(InternalEventVO event)
    	throws ActivityException {
    	// check if timer is expired at this time?
    	try {
			ActivityInstanceVO ai = getEngine().getActivityInstance(getActivityInstanceId());
			Date expectedEndTime = StringHelper.stringToDate(ai.getEndDate());
			long currentTime = DatabaseAccess.getCurrentTime();
			if (currentTime>expectedEndTime.getTime()) {
				int moreSeconds = processTimerExpiration();
				if (moreSeconds>0) sendDelayedResumeMessage(moreSeconds);
				else return true;
			}
		} catch (Exception e) {
			throw new ActivityException(-1, e.getMessage(), e);
		}

		EventWaitInstanceVO received = registerWaitEvents(true,true);
    	if (received!=null) {
 	    	this.setReturnCode(received.getCompletionCode());
 	    	processOtherMessage(getExternalEventInstanceDetails(received.getMessageDocumentId()));
 	    	handleEventCompletionCode();
 	    	return true;
 		} else {
 			return false;
 		}
    }

}