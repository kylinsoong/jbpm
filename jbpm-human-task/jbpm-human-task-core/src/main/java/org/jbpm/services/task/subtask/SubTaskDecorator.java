/*
 * Copyright 2012 JBoss by Red Hat.
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
package org.jbpm.services.task.subtask;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

import org.jbpm.services.task.impl.model.TaskImpl;
import org.jbpm.shared.services.api.JbpmServicesPersistenceManager;
import org.kie.api.command.Command;
import org.kie.api.task.model.I18NText;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.task.api.TaskInstanceService;
import org.kie.internal.task.api.TaskQueryService;
import org.kie.internal.task.api.model.ContentData;
import org.kie.internal.task.api.model.FaultData;
import org.kie.internal.task.api.model.InternalTask;
import org.kie.internal.task.api.model.SubTasksStrategy;

/**
 *
 */
@Decorator
public class SubTaskDecorator implements TaskInstanceService {
    @Inject
    @Delegate 
    private TaskInstanceService instanceService;
    
    @Inject
    private JbpmServicesPersistenceManager pm;
    
    @Inject 
    private TaskQueryService queryService;

    public SubTaskDecorator() {
    }

    public void setInstanceService(TaskInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    public void setPm(JbpmServicesPersistenceManager pm) {
        this.pm = pm;
    }

    public void setQueryService(TaskQueryService queryService) {
        this.queryService = queryService;
    }

    
    
    public long addTask(Task task, Map<String, Object> params) {
        return instanceService.addTask(task, params);
    }

    public long addTask(Task task, ContentData data) {
        return instanceService.addTask(task, data);
    }

    public void activate(long taskId, String userId) {
        instanceService.activate(taskId, userId);
    }

    public void claim(long taskId, String userId) {
        instanceService.claim(taskId, userId);
    }

    public void claim(long taskId, String userId, List<String> groupIds) {
        instanceService.claim(taskId, userId, groupIds);
    }

    public void claimNextAvailable(String userId, String language) {
        instanceService.claimNextAvailable(userId, language);
    }

    public void claimNextAvailable(String userId, List<String> groupIds, String language) {
        instanceService.claimNextAvailable(userId, groupIds, language);
    }

    public void complete(long taskId, String userId, Map<String, Object> data) {
        checkSubTaskStrategies(taskId, userId, data);
        instanceService.complete(taskId, userId, data);
        
    }

    public void delegate(long taskId, String userId, String targetUserId) {
        instanceService.delegate(taskId, userId, targetUserId);
    }

    //@TODO: remove this from here
    public void deleteFault(long taskId, String userId) {
        instanceService.deleteFault(taskId, userId);
    }

    //@TODO: remove this from here
    public void deleteOutput(long taskId, String userId) {
       instanceService.deleteOutput(taskId, userId);
    }

    public void exit(long taskId, String userId) {
       instanceService.exit(taskId, userId);
    }

    public void fail(long taskId, String userId, Map<String, Object> faultData) {
       instanceService.fail(taskId, userId, faultData);
    }

    public void forward(long taskId, String userId, String targetEntityId) {
       instanceService.forward(taskId, userId, targetEntityId);
    }

    public void release(long taskId, String userId) {
       instanceService.release(taskId, userId);
    }

    public void remove(long taskId, String userId) {
        instanceService.remove(taskId, userId);
    }

    public void resume(long taskId, String userId) {
        instanceService.resume(taskId, userId);
    }

    public void setFault(long taskId, String userId, FaultData fault) {
       instanceService.setFault(taskId, userId, fault);
    }

    public void setOutput(long taskId, String userId, Object outputContentData) {
       instanceService.setOutput(taskId, userId, outputContentData);
    }

    public void setPriority(long taskId,  int priority) {
       instanceService.setPriority(taskId, priority);
    }

    public void skip(long taskId, String userId) {
       instanceService.skip(taskId, userId);
       checkSubTaskStrategies(taskId, userId, null);
    }

    public void start(long taskId, String userId) {
       instanceService.start(taskId, userId);
    }

    public void stop(long taskId, String userId) {
       instanceService.stop(taskId, userId);
    }

    public void suspend(long taskId, String userId) {
       instanceService.suspend(taskId, userId); 
    }

    public void nominate(long taskId, String userId, List<OrganizationalEntity> potentialOwners) {
        instanceService.nominate(taskId, userId, potentialOwners);
    }
    
    private void checkSubTaskStrategies(long taskId, String userId, Map<String, Object> data){
        Task task = queryService.getTaskInstanceById(taskId);
        if(task == null){
            return;
        }
        Task parentTask = null;
        if (task.getTaskData().getParentId() != -1){
            parentTask = pm.find(TaskImpl.class, task.getTaskData().getParentId());
        }
        if (parentTask != null){
            if(((InternalTask) parentTask).getSubTaskStrategy() != null && 
            		((InternalTask) parentTask).getSubTaskStrategy().equals(SubTasksStrategy.EndParentOnAllSubTasksEnd)){
                List<TaskSummary> subTasks = queryService.getSubTasksByParent(parentTask.getId());
                    // If there are no more sub tasks or if the last sub task is the one that we are completing now
                    if (subTasks.isEmpty() || (subTasks.size() == 1 && subTasks.get(0).getId() == taskId)) {
                        // Completing parent task if all the sub task has being completed, including the one that we are completing now
                        complete(parentTask.getId(), "Administrator", data);
                    }
            }
        }
        if (((InternalTask) task).getSubTaskStrategy() != null && 
        		((InternalTask) task).getSubTaskStrategy().equals(SubTasksStrategy.SkipAllSubTasksOnParentSkip)){
            List<TaskSummary> subTasks = queryService.getSubTasksByParent(task.getId());
            for(TaskSummary taskSummary : subTasks){
                Task subTask = queryService.getTaskInstanceById(taskSummary.getId());
                // Exit each sub task because the parent task was aborted
                skip(subTask.getId(), "Administrator");
            }
        }
    }

    public void setExpirationDate(long taskId, Date date) {
        instanceService.setExpirationDate(taskId, date);
    }

    public void setDescriptions(long taskId, List<I18NText> descriptions) {
        instanceService.setDescriptions(taskId, descriptions);
    }

    public void setSkipable(long taskId, boolean skipable) {
        instanceService.setSkipable(taskId, skipable);
    }

    public void setSubTaskStrategy(long taskId, SubTasksStrategy strategy) {
        instanceService.setSubTaskStrategy(taskId, strategy);
    }

    public int getPriority(long taskId) {
        return instanceService.getPriority(taskId);
    }

    public Date getExpirationDate(long taskId) {
        return instanceService.getExpirationDate(taskId);
    }

    public List<I18NText> getDescriptions(long taskId) {
        return instanceService.getDescriptions(taskId);
    }

    public boolean isSkipable(long taskId) {
        return instanceService.isSkipable(taskId);
    }

    public SubTasksStrategy getSubTaskStrategy(long taskId) {
        return instanceService.getSubTaskStrategy(taskId);
    }

    public void setTaskNames(long taskId, List<I18NText> taskNames) {
        instanceService.setTaskNames(taskId, taskNames);
    }
    
	public <T> T execute(Command<T> command) {
		return instanceService.execute(command);
	}

}
