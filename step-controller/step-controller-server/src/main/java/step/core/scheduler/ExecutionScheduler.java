/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
 *  
 * This file is part of STEP
 *  
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package step.core.scheduler;

import java.util.Iterator;
import java.util.List;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.core.GlobalContext;
import step.core.controller.ControllerSetting;
import step.core.controller.ControllerSettingAccessor;
import step.core.execution.ExecutionContext;
import step.core.execution.model.ExecutionParameters;

public class ExecutionScheduler {
	
	private final Logger logger = LoggerFactory.getLogger(ExecutionScheduler.class);
	
	private ControllerSettingAccessor controllerSettingAccessor;
	
	private GlobalContext context;
		
	private Executor executor;
	
	public static final String SETTING_SCHEDULER_ENABLED = "scheduler_enabled";
	
	public ExecutionScheduler(GlobalContext globalContext) {
		super();
		
		this.context = globalContext;
		this.executor = new Executor(globalContext);
		this.controllerSettingAccessor = context.require(ControllerSettingAccessor.class);
	}

	public void shutdown() {
		executor.shutdown();
	}

	public void start() {
		executor.start();
		loadExecutionTasks();
	}
	
	private void loadExecutionTasks() {
		if(isSchedulerEnabled()) {
			Iterator<ExecutiontTaskParameters> it = getActiveExecutionTasks();
			while(it.hasNext()) {
				ExecutiontTaskParameters task = it.next();
				logger.info("Loading schedule for task " + task.getName() + " having for id : " + task.getId());
				try {
					boolean mayFireAgain = executor.schedule(task);
					if(!mayFireAgain) {
						removeExecutionTask(task.getId().toString());
					}
				} catch (Exception e) {
					logger.error("An error occurred while scheduling task. "+ task.toString()+ ". Disabling task.", e);
					disableExecutionTask(task.getId().toString());
				}
			}
		}		
	}
	
	public Iterator<ExecutiontTaskParameters> getActiveExecutionTasks() {
		return context.getScheduleAccessor().getActiveExecutionTasks();
	}
	
	public Iterator<ExecutiontTaskParameters> getActiveAndInactiveExecutionTasks() {
		return context.getScheduleAccessor().getAll();
	}
	
	public void removeExecutionTask(String executionTaskID) {
		ExecutiontTaskParameters task = get(executionTaskID);
		remove(task);
		executor.deleteSchedule(task);
	}
	
	public void enableExecutionTask(String executionTaskID) {
		ExecutiontTaskParameters task = get(executionTaskID);
		task.setActive(true);
		save(task);
		if(isSchedulerEnabled()) {
			executor.schedule(task);			
		} 
	}
	
	public void disableExecutionTask(String executionTaskID) {
		ExecutiontTaskParameters task = get(executionTaskID);
		task.setActive(false);
		save(task);
		executor.deleteSchedule(task);
	}

	public boolean addExecutionTask(ExecutiontTaskParameters task) {
		executor.validate(task);
		task.setActive(true);
		save(task);
		
		if(isSchedulerEnabled()) {
			return executor.schedule(task);			
		} else {		
			return true;
		}
	}
	
	private boolean isSchedulerEnabled() {
		return controllerSettingAccessor.getSettingAsBoolean(SETTING_SCHEDULER_ENABLED);
	}
	
	public String execute(ExecutionParameters executionParameters) {
		return executor.execute(executionParameters);
	}

	public String executeExecutionTask(String executionTaskID, String user) {
		ExecutiontTaskParameters task = get(executionTaskID);
		task.getExecutionsParameters().setUserID(user);
		return executor.execute(task);
	}
	
	public ExecutiontTaskParameters get(String id) {
		return context.getScheduleAccessor().get(new ObjectId(id));
	}
	
	private void save(ExecutiontTaskParameters schedule) {
		context.getScheduleAccessor().save(schedule);
	}
	
	private void remove(ExecutiontTaskParameters schedule) {
		context.getScheduleAccessor().remove(schedule.getId());
	}
	
	public List<ExecutionContext> getCurrentExecutions() {
		return executor.getCurrentExecutions();
	}

	public void enableAllExecutionTasksSchedule() {
		// Save setting
		controllerSettingAccessor.updateOrCreateSetting(SETTING_SCHEDULER_ENABLED, Boolean.TRUE.toString());
		
		logger.info("Enabling Scheduler...");
		loadExecutionTasks();	
	}
	
	public void disableAllExecutionTasksSchedule() {
		// Save setting
		controllerSettingAccessor.updateOrCreateSetting(SETTING_SCHEDULER_ENABLED, Boolean.FALSE.toString());
		
		logger.info("Disabling Scheduler...");
		Iterator<ExecutiontTaskParameters> it = getActiveExecutionTasks();
		while(it.hasNext()) {
			ExecutiontTaskParameters task = it.next();
			logger.info("Disabling schedule for task " + task.getName() + " having for id : " + task.getId());
			executor.deleteSchedule(task);
		}
	}
}
