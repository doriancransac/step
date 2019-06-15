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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import java.util.ArrayList;

import org.junit.Test;

import step.artefacts.CheckArtefact;
import step.core.GlobalContext;
import step.core.GlobalContextBuilder;
import step.core.artefacts.ArtefactAccessor;
import step.core.artefacts.reports.ReportNode;
import step.core.artefacts.reports.ReportNodeStatus;
import step.core.execution.model.Execution;
import step.core.execution.model.ExecutionMode;
import step.core.execution.model.ExecutionParameters;
import step.core.execution.model.ExecutionStatus;
import step.core.plugins.AbstractControllerPlugin;
import step.core.repositories.ImportResult;
import step.core.repositories.RepositoryObjectManager;
import step.core.repositories.RepositoryObjectReference;

public class ExecutionRunnableTest {

	private static class TestRepositoryObjectManager extends RepositoryObjectManager {

		private ImportResult result;
		
		public TestRepositoryObjectManager(ImportResult result, ArtefactAccessor artefactAccessor) {
			super(artefactAccessor);
			this.result = result;
		}

		@Override
		public ImportResult importArtefact(ExecutionContext context, RepositoryObjectReference artefact) throws Exception {
			return result;
		}
		
	}
	
	@Test 
	public void test() throws Exception {
		GlobalContext globalContext = GlobalContextBuilder.createGlobalContext();
		globalContext.getPluginManager().register(new AbstractControllerPlugin() {

			@Override
			public void executionStart(ExecutionContext context) {
				context.getVariablesManager().putVariable(context.getReport(), "tec.execution.exports", "[]");
				super.executionStart(context);
			}
		});
		
		CheckArtefact artefact = new CheckArtefact(c->c.getCurrentReportNode().setStatus(ReportNodeStatus.PASSED));
		globalContext.getArtefactAccessor().save(artefact);
		
		ImportResult result = new ImportResult();
		result.setSuccessful(true);
		result.setArtefactId(artefact.getId().toString());
		
		RepositoryObjectManager repo = new TestRepositoryObjectManager(result, globalContext.getArtefactAccessor());
		
		globalContext.setRepositoryObjectManager(repo);
		
		ExecutionRunnableFactory f = new ExecutionRunnableFactory(globalContext);
		
		ExecutionParameters p = new ExecutionParameters("user",null, ExecutionMode.RUN);
		p.setArtefact(new RepositoryObjectReference());
		p.setExports(new ArrayList<>());
		Execution e = f.createExecution(p, null);
		
		ExecutionRunnable r = f.newExecutionRunnable(e);

		r.run();
		
		Execution execution = globalContext.getExecutionAccessor().get(e.getId().toString());
		assertNotNull(execution);
		assertNotNull(execution.getStartTime());
		assertNotNull(execution.getEndTime());
		assertEquals(ExecutionStatus.ENDED,execution.getStatus());
		assertNull(execution.getExecutionTaskID());
		
		ReportNode node = globalContext.getReportAccessor().getReportNodesByExecutionIDAndArtefactID(e.getId().toString(), artefact.getId().toString()).next();
		assertNotNull(node);
		assertEquals(ReportNodeStatus.PASSED, node.getStatus());
		
	}
	
}
