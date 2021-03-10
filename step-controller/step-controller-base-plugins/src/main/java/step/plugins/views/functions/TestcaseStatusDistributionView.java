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
package step.plugins.views.functions;

import java.util.HashMap;
import java.util.Map;

import step.artefacts.TestCase;
import step.artefacts.reports.CallFunctionReportNode;
import step.artefacts.reports.TestCaseReportNode;
import step.core.artefacts.reports.ReportNode;
import step.core.artefacts.reports.ReportNodeStatus;
import step.plugins.views.AbstractView;
import step.plugins.views.View;

@View
public class TestcaseStatusDistributionView extends AbstractView<ReportNodeStatusDistribution> {	

	@Override
	public void afterReportNodeSkeletonCreation(ReportNodeStatusDistribution model, ReportNode node) {
		if(node instanceof TestCaseReportNode && node.persistNode()) {
			model.countForecast++;
		}
	}

	@Override
	public void beforeReportNodeExecution(ReportNodeStatusDistribution model, ReportNode node) {
	}

	@Override
	public void afterReportNodeExecution(ReportNodeStatusDistribution model, ReportNode node) {
		if(node instanceof TestCaseReportNode && node.persistNode()) {
			model.distribution.get(node.getStatus()).count++;
			model.count++;
			if(model.countForecast<model.count) {
				model.countForecast=model.count;
			}
		}
	}

	@Override
	public ReportNodeStatusDistribution init() {
		Map<ReportNodeStatus, ReportNodeStatusDistribution.Entry> progress = new HashMap<>();
		for(ReportNodeStatus status:ReportNodeStatus.values()) {
			progress.put(status, new ReportNodeStatusDistribution.Entry(status));
		}
		ReportNodeStatusDistribution distribution = new ReportNodeStatusDistribution(progress);
		distribution.setLabel("Test cases: ");
		return distribution;
	}

	@Override
	public String getViewId() {
		return "statusDistributionForTestcases";
	}

	@Override
	public void rollbackReportNode(ReportNodeStatusDistribution model, ReportNode node) {
		if(node instanceof TestCaseReportNode && node.persistNode()) {
			model.distribution.get(node.getStatus()).count++;
			model.count++;
			model.countForecast--;
		}
	}
}
