/*******************************************************************************
 * (C) Copyright 2016 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.core.execution;

import org.bson.types.ObjectId;

import step.core.GlobalContext;
import step.core.execution.model.Execution;
import step.core.execution.model.ExecutionParameters;
import step.core.execution.model.ExecutionStatus;
import step.core.scheduler.ExecutionTaskAccessor;
import step.core.scheduler.ExecutiontTaskParameters;
import step.threadpool.ThreadPool;

public class ExecutionRunnableFactory {
		
	private GlobalContext globalContext;
	private ExecutionTaskAccessor taskAccessor;
	
	public ExecutionRunnableFactory(GlobalContext globalContext) {
		super();
		this.globalContext = globalContext;
		taskAccessor = globalContext.getScheduleAccessor();
	}
	
	public ExecutionRunnable newExecutionRunnable(Execution execution) {		
		ExecutionContext context = createExecutionContext(execution);
		ExecutionRunnable task = new ExecutionRunnable(globalContext.getRepositoryObjectManager(), globalContext.getExecutionAccessor(), context);
		return task;
	}

	private ExecutionContext createExecutionContext(Execution execution) {
		boolean isolatedContext = execution.getExecutionParameters().isIsolatedExecution();
		String executionId = execution.getId().toString();
		ExecutionContext context;
		if(isolatedContext) {
			context = ContextBuilder.createLocalExecutionContext(executionId);
			context.setReportNodeAccessor(globalContext.getReportAccessor());
			context.setEventManager(globalContext.getEventManager());
			context.setExecutionCallbacks(globalContext.getPluginManager().getProxy());
			
			ExecutionManager executionManager = new ExecutionManagerImpl(globalContext.getExecutionAccessor());
			context.put(ExecutionManager.class, executionManager);
		} else {
			context = new ExecutionContext(executionId);
			context.setExpressionHandler(globalContext.getExpressionHandler());
			context.setDynamicBeanResolver(globalContext.getDynamicBeanResolver());
			context.setConfiguration(globalContext.getConfiguration());
			context.setArtefactAccessor(globalContext.getArtefactAccessor());
			context.setReportNodeAccessor(globalContext.getReportAccessor());
			context.setEventManager(globalContext.getEventManager());
			context.setExecutionCallbacks(globalContext.getPluginManager().getProxy());
			ExecutionManager executionManager = new ExecutionManagerImpl(globalContext.getExecutionAccessor());
			context.put(ExecutionManager.class, executionManager);
			context.setExecutionTypeListener(executionManager);
			context.put(ThreadPool.class, new ThreadPool(context));
		}

		context.setExecutionParameters(execution.getExecutionParameters());
		context.updateStatus(ExecutionStatus.INITIALIZING);
		return context;
	}
	
	public Execution createExecution(ExecutionParameters executionParameters, String taskID) {		
		Execution execution = new Execution();
		execution.setStartTime(System.currentTimeMillis());
		execution.setExecutionParameters(executionParameters);
		execution.setStatus(ExecutionStatus.INITIALIZING);
		
		if(executionParameters.getAttributes() != null) {
			execution.setAttributes(executionParameters.getAttributes());
		}
		
		ExecutiontTaskParameters executiontTaskParameters = taskAccessor.get(new ObjectId(taskID));
		if(executiontTaskParameters != null && executiontTaskParameters.getAttributes() != null)
			execution.setAttributes(executiontTaskParameters.getAttributes());
		
		if(taskID!=null) {
			execution.setExecutionTaskID(taskID);
		}
		
		if(executionParameters.getDescription()!=null) {
			execution.setDescription(executionParameters.getDescription());
		}
		
		globalContext.getExecutionAccessor().save(execution);		
		return execution;
	}

}
