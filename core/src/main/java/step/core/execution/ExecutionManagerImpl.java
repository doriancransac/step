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

import java.util.HashMap;
import java.util.Map.Entry;

import step.core.artefacts.AbstractArtefact;
import step.core.artefacts.reports.ReportNodeStatus;
import step.core.execution.model.Execution;
import step.core.execution.model.ExecutionAccessor;
import step.core.execution.model.ExecutionStatus;
import step.core.repositories.ImportResult;

public class ExecutionManagerImpl implements ExecutionManager {
	
	private final ExecutionAccessor accessor;
	
	public ExecutionManagerImpl(ExecutionAccessor accessor) {
		super();
		this.accessor = accessor;
	}

	@Override
	public void updateParameters(ExecutionContext context) {
		Execution execution = getExecution(context);
		
		execution.setResult(ReportNodeStatus.RUNNING);
		
		HashMap<String, String> params = new HashMap<>();
		for(Entry<String, Object> entry:context.getVariablesManager().getAllVariables().entrySet()) {
			params.put(entry.getKey(), entry.getValue().toString());
		}
		execution.setParameters(params);
		
		saveExecution(execution);
	}
	
	@Override
	public void persistStatus(ExecutionContext context) {
		Execution execution = getExecution(context);
		if(context.getStatus()==ExecutionStatus.ENDED) {
			execution.setEndTime(System.currentTimeMillis());
		}
		execution.setStatus(context.getStatus());
		if(context.getArtefact()!=null) {
			execution.setArtefactID(context.getArtefact().getId().toString());
			if(execution.getDescription()==null) {
				AbstractArtefact artefact = context.getArtefact();
				execution.setDescription(artefact.getAttributes()!=null?artefact.getAttributes().get("name"):null);
			}
		}
		saveExecution(execution);
	}
	
	@Override
	public void persistImportResult(ExecutionContext context, ImportResult importResult) {
		Execution execution = getExecution(context);
		execution.setImportResult(importResult);
		saveExecution(execution);
	}
	
	@Override
	public void updateStatus(ExecutionContext context, ExecutionStatus status) {
		context.updateStatus(status);
		persistStatus(context);
	}
	
	private void saveExecution(Execution execution) {
		accessor.save(execution);
	}

	private Execution getExecution(ExecutionContext context) {
		Execution execution = accessor.get(context.getExecutionId());
		return execution;
	}
	
	@Override
	public void updateExecutionType(ExecutionContext context, String newType) {
		Execution execution = getExecution(context);
		execution.setExecutionType(newType);
		saveExecution(execution);
	}
	
	@Override
	public void updateExecutionResult(ExecutionContext context, ReportNodeStatus resultStatus) {
		Execution execution = getExecution(context);
		execution.setResult(resultStatus);
		saveExecution(execution);
	}
}
