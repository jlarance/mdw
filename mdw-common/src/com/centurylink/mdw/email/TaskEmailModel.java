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
package com.centurylink.mdw.email;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.centurylink.mdw.app.ApplicationContext;
import com.centurylink.mdw.constant.OwnerType;
import com.centurylink.mdw.model.JsonObject;
import com.centurylink.mdw.model.task.TaskInstance;
import com.centurylink.mdw.model.task.TaskState;
import com.centurylink.mdw.model.task.TaskStates;
import com.centurylink.mdw.model.task.TaskStatuses;

public class TaskEmailModel implements TemplatedEmail.Model {

    private TaskInstance taskInstance;
    public TaskInstance getTaskInstance() { return taskInstance; }

    private Map<String,Object> variables;
    public Map<String,Object> getVariables() { return variables; }

    // TODO index values

    public TaskEmailModel(TaskInstance taskInstance, Map<String,Object> variables) {
        this.taskInstance = taskInstance;
        this.variables = variables;
    }

    public TaskEmailModel(JSONObject taskInstanceJson, Map<String,Object> variables) throws JSONException {
        this.taskInstance = new TaskInstance();
        taskInstance.setTaskName(taskInstanceJson.getString("taskName"));
        taskInstance.setTaskInstanceId(taskInstanceJson.getLong("taskInstanceId"));
        String baseUrl = ApplicationContext.getMdwHubUrl();
        if (!baseUrl.endsWith("/"))
          baseUrl += "/";
        String taskInstUrl = baseUrl + "#/tasks/" + taskInstance.getTaskInstanceId();
        taskInstance.setTaskInstanceUrl(taskInstUrl);
        taskInstance.setMasterRequestId(taskInstanceJson.getString("masterRequestId"));
        if (taskInstanceJson.has("startDate"))
            taskInstance.setStart(Instant.parse(taskInstanceJson.getString("start")));
        if (taskInstanceJson.has("endDate"))
            taskInstance.setEnd(Instant.parse(taskInstanceJson.getString("end")));
        if (taskInstanceJson.has("dueDate"))
            taskInstance.setDue(Instant.parse(taskInstanceJson.getString("due")));
        if (taskInstanceJson.has("assignee"))
            taskInstance.setAssigneeCuid(taskInstanceJson.getString("assignee"));
        if (taskInstanceJson.has("userId"))
            taskInstance.setUserIdentifier(taskInstanceJson.getString("userId"));
        taskInstance.setStatusCode(taskInstanceJson.getInt("statusCode"));
        taskInstance.setStateCode(taskInstanceJson.getInt("stateCode"));
        if (taskInstanceJson.has("comments"))
            taskInstance.setComments(taskInstanceJson.getString("comments"));
        taskInstance.setOwnerType(OwnerType.PROCESS_INSTANCE);
        taskInstance.setOwnerId(taskInstanceJson.getLong("processInstanceId"));
        if (taskInstanceJson.has("taskId"))
            taskInstance.setTaskId(taskInstanceJson.getLong("taskId"));
        this.variables = variables;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JsonObject();
        jsonObject.put("taskName", getTaskName());
        jsonObject.put("taskInstanceId", getTaskInstanceId());
        jsonObject.put("masterRequestId", getMasterRequestId());
        jsonObject.put("startDate", getStartDate());
        jsonObject.put("endDate", getEndDate());
        jsonObject.put("dueDate", getFormattedDueDate());
        jsonObject.put("assignee", getAssignee());
        jsonObject.put("userId", getUserIdentifier());
        jsonObject.put("statusCode", getStatusCode());
        jsonObject.put("stateCode", getStateCode());
        jsonObject.put("comments", getComments());
        jsonObject.put("processInstanceId", getProcessInstanceId());
        jsonObject.put("taskId", getTaskId());
        return jsonObject;
    }

    private String description;
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    private String taskActionUrl;
    public String getTaskActionUrl() { return taskActionUrl; }
    public void setTaskActionUrl(String taskActionUrl) { this.taskActionUrl = taskActionUrl; }

    public String getTaskName() { return taskInstance.getTaskName(); }
    public String getName() { return getTaskName(); }
    public Long getTaskInstanceId() { return taskInstance.getTaskInstanceId(); }
    public Long getInstanceId() { return getTaskInstanceId(); }
    public String getTaskInstanceUrl() { return taskInstance.getTaskInstanceUrl(); }
    public String getInstanceUrl() { return getTaskInstanceUrl(); }
    public String getMasterRequestId() { return taskInstance.getMasterRequestId(); }
    public String getOrderId() { return taskInstance.getMasterRequestId(); }
    public Date getStartDate() { return Date.from(taskInstance.getStart()); }
    public Date getEndDate() { return Date.from(taskInstance.getEnd()); }
    public String getAssignee() { return taskInstance.getAssigneeCuid();  }
    public Date getDueDate() { return Date.from(taskInstance.getDue()); }
    public String getUserIdentifier() { return taskInstance.getUserIdentifier(); }
    public Integer getStatusCode() { return taskInstance.getStatusCode(); }
    public Integer getStateCode() { return taskInstance.getStateCode(); }
    public String getStatus() { return TaskStatuses.getTaskStatuses().get(getStatusCode()); }
    public String getComments() { return taskInstance.getComments(); }
    public Long getTaskId() { return taskInstance.getTaskId(); }

    public String getFormattedDueDate() {
        Date dd = getDueDate();
        if (dd == null)
            return null;
        return new SimpleDateFormat("MM/dd/yyyy").format(dd);
    }

    public Long getProcessInstanceId() {
        if (taskInstance.getOwnerType().equals(OwnerType.PROCESS_INSTANCE))
            return taskInstance.getOwnerId();
        else
            return null;
    }

    public String getAdvisory()
    {
      String state = TaskStates.getTaskStates().get(getStateCode());
      if (!TaskState.STATE_JEOPARDY.equals(state) && !TaskState.STATE_ALERT.equals(state))
          return null;
      else
          return state;
    }

    @Override
    public Map<String,String> getKeyParameters() {
        Map<String,String> keyParams = new HashMap<String,String>();
        keyParams.put("taskInstanceId", getTaskInstanceId().toString());
        if (getProcessInstanceId() != null)
          keyParams.put("processInstanceId", getProcessInstanceId().toString());
        if (getUserIdentifier() != null)
          keyParams.put("userId", getUserIdentifier());
        try {
            keyParams.put("taskInstanceJson", toJson().toString());
        }
        catch (JSONException ex) {
            ex.printStackTrace();  // TODO
        }
        return keyParams;
    }

}
