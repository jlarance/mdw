/*
 * Copyright (C) 2017 CenturyLink, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.centurylink.mdw.model.workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.centurylink.mdw.model.Jsonable;
import com.centurylink.mdw.constant.ActivityResultCodeConstant;
import com.centurylink.mdw.constant.WorkAttributeConstant;
import com.centurylink.mdw.constant.WorkTransitionAttributeConstant;
import com.centurylink.mdw.model.attribute.Attribute;
import com.centurylink.mdw.model.event.EventType;
import com.centurylink.mdw.util.JsonUtil;

public class Transition implements Serializable, Jsonable {

    public static final String DELAY_UNIT_SECOND = "s";
    public static final String DELAY_UNIT_MINUTE = "m";
    public static final String DELAY_UNIT_HOUR = "h";

    public static final int STATE_REQUIRED = 0;
    public static final int STATE_IN_PROGRESS = 1;
    public static final int STATE_INVALID = 2;

    private Long workTransitionId;
    private Long fromWorkId;
    private Long toWorkId;
    private Integer eventType;
    private String completionCode;
    private String validatorClassName;
    private List<Attribute> attributes;
    private Long processId;
    private List<Long> dependentTransitionIds;


    public Transition() {
    }

    public Transition(Long pWorkTransId, Long pFromWorkId, Long pToWorkId,
        Integer pEventType, String pCompletionCode, String pValClassName, List<Attribute> pAttribues){
        this.workTransitionId = pWorkTransId;
        this.fromWorkId = pFromWorkId;
        this.toWorkId = pToWorkId;
        this.eventType = pEventType;
        this.validatorClassName = pValClassName;
        this.attributes = pAttribues;
        this.completionCode = pCompletionCode;

    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("WorkTransitionVO[");
        if (attributes == null) {
            buffer.append("attributes = ").append("null");
        }
        else {
            buffer.append("attributes = ").append(attributes.toString());
        }
        buffer.append(" eventType = ").append(eventType);
        buffer.append(" fromWorkId = ").append(fromWorkId);
        buffer.append(" toWorkId = ").append(toWorkId);
        buffer.append(" validatorClassName = ").append(validatorClassName);
        buffer.append(" workTransitionId = ").append(workTransitionId);
        buffer.append("]");
        return buffer.toString();
    }


    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Integer getEventType() {
        return eventType;
    }

    public void setEventType(Integer eventType) {
        this.eventType = eventType;
    }

    public Long getFromWorkId() {
        return fromWorkId;
    }

    public void setFromWorkId(Long fromWorkId) {
        this.fromWorkId = fromWorkId;
    }

    public Long getToWorkId() {
        return toWorkId;
    }

    public void setToWorkId(Long toWorkId) {
        this.toWorkId = toWorkId;
    }

    public String getCompletionCode() {
        return completionCode;
    }

    public void setCompletionCode(String completionCode) {
        this.completionCode = completionCode;
    }

    public String getValidatorClassName() {
        return validatorClassName;
    }

    public void setValidatorClassName(String validatorClassName) {
        this.validatorClassName = validatorClassName;
    }

    public Long getWorkTransitionId() {
        return workTransitionId;
    }

    public void setWorkTransitionId(Long workTransitionId) {
        this.workTransitionId = workTransitionId;
    }

    public void setProcessId(Long processId){
        this.processId = processId;
    }

    public Long getProcessId(){
        return this.processId;
    }

    public List<Long> getDependentTransitionIds(){
        return this.dependentTransitionIds;
    }

    public void setDependentTransitionIds(List<Long> pIds){
        this.dependentTransitionIds = pIds;
    }

    public void addDependentTransitionIdId(Long pId){
        if (this.dependentTransitionIds==null)
            this.dependentTransitionIds = new ArrayList<Long>();
        if (!this.dependentTransitionIds.contains(pId))
            this.dependentTransitionIds.add(pId);
    }

    public String getAttribute(String attrname) {
        return Attribute.findAttribute(attributes, attrname);
    }

    /**
     * Set the value of a process attribute.
     * If the value is null, the attribute is removed.
     * If the attribute does not exist and the value is not null, the attribute
     * is created.
     * @param attrname
     * @param value
     */
    public void setAttribute(String attrname, String value) {
        if (attributes==null) attributes = new ArrayList<Attribute>();
        Attribute.setAttribute(attributes, attrname, value);
    }

    public boolean match(Integer eventType, String compCode) {
        if (!eventType.equals(this.eventType)) return false;
        if (this.completionCode==null) return compCode==null;
        return this.completionCode.equals(compCode);
    }

    public boolean isDefaultTransition(boolean noLabelIsDefault) {
        if (noLabelIsDefault)
            return this.completionCode==null && this.eventType.equals(EventType.FINISH);
        else return this.completionCode!=null &&
            this.completionCode.equals(ActivityResultCodeConstant.RESULT_DEFAULT)
            && this.eventType.equals(EventType.FINISH);

    }

    public int getTransitionDelay() {
        int secDelay;
        String delayAttrib = getAttribute(WorkTransitionAttributeConstant.TRANSITION_DELAY);
        if (delayAttrib != null) {
            int k, n=delayAttrib.length();
            for (k=0; k<n; k++) {
                if (!Character.isDigit(delayAttrib.charAt(k))) break;
            }
            if (k<n) {
                String unit = delayAttrib.substring(k).trim();
                delayAttrib = delayAttrib.substring(0,k);
                if (unit.startsWith("s")) secDelay = Integer.parseInt(delayAttrib);
                else if (unit.startsWith("h")) secDelay = 3600 * Integer.parseInt(delayAttrib);
                else secDelay = 60 * Integer.parseInt(delayAttrib);
            } else secDelay = 60 * Integer.parseInt(delayAttrib);
        } else secDelay = 0;
        return secDelay;
    }

    public String getTransitionDelayUnit() {
        String delayAttrib = getAttribute(WorkTransitionAttributeConstant.TRANSITION_DELAY);
        String unit = DELAY_UNIT_MINUTE;
        if (delayAttrib != null) {
            int k, n=delayAttrib.length();
            for (k=0; k<n; k++) {
                if (!Character.isDigit(delayAttrib.charAt(k))) break;
            }
            if (k<n) {
                unit = delayAttrib.substring(k).trim();
                if (unit.startsWith("s")) unit = DELAY_UNIT_SECOND;
                else if (unit.startsWith("h")) unit = DELAY_UNIT_HOUR;
            }
        }
        return unit;
    }

    public String getLabel() {
        if (eventType.equals(EventType.FINISH)) return completionCode;
        if (completionCode==null) return EventType.getEventTypeName(eventType);
        return EventType.getEventTypeName(eventType) + ":" + completionCode;
    }

    public String getLogicalId() {
        return getAttribute(WorkAttributeConstant.LOGICAL_ID);
    }

    public boolean isHidden() {
        String logicalId = getLogicalId();
        return logicalId != null && logicalId.startsWith("H");
    }

    /**
     * Does not set fromWorkId since JSON transitions are children of activities.
     */
    public Transition(JSONObject json) throws JSONException {
        String logicalId = json.getString("id");
        if (logicalId.startsWith("Transition"))
            logicalId = "T" + logicalId.substring(10);
        workTransitionId = Long.valueOf(logicalId.substring(1));
        this.toWorkId = Long.parseLong(json.getString("to").substring(1));
        if (json.has("resultCode"))
            this.completionCode = json.getString("resultCode");
        if (json.has("event"))
            this.eventType = EventType.getEventTypeFromName(json.getString("event"));
        else
            this.eventType = EventType.FINISH;
        if (json.has("attributes"))
            this.attributes = JsonUtil.getAttributes(json.getJSONObject("attributes"));
        setAttribute(WorkAttributeConstant.LOGICAL_ID, logicalId);
    }

    /**
     * Does not populate from field since JSON transitions are children of activities.
     */
    public JSONObject getJson() throws JSONException {
        JSONObject json = create();
        json.put("id", getLogicalId());
        json.put("to", "A" + toWorkId);
        if (completionCode != null)
            json.put("resultCode", completionCode);
        if (eventType != null)
            json.put("event", EventType.getEventTypeName(eventType));
        if (attributes != null && ! attributes.isEmpty())
            json.put("attributes", JsonUtil.getAttributesJson(attributes));
        return json;
    }

    public String getJsonName() {
        return JsonUtil.padLogicalId(getLogicalId());
    }
}
