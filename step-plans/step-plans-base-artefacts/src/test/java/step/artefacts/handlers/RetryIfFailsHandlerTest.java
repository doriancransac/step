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
package step.artefacts.handlers;

import static junit.framework.Assert.assertEquals;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import step.artefacts.IfBlock;
import step.artefacts.RetryIfFails;
import step.artefacts.Set;
import step.artefacts.reports.RetryIfFailsReportNode;
import step.core.artefacts.CheckArtefact;
import step.core.artefacts.reports.ReportNode;
import step.core.artefacts.reports.ReportNodeStatus;
import step.core.dynamicbeans.DynamicValue;

public class RetryIfFailsHandlerTest extends AbstractArtefactHandlerTest {
	
	@Test
	public void testSuccess() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setMaxRetries(new DynamicValue<Integer>(2));
		
		CheckArtefact check1 = new CheckArtefact(c->context.getCurrentReportNode().setStatus(ReportNodeStatus.PASSED));
		block.addChild(check1);
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		assertEquals(child.getStatus(), ReportNodeStatus.PASSED);
		
		assertEquals(1, getChildren(child).size());
	}
	
	@Test
	public void testMaxRetry() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setMaxRetries(new DynamicValue<Integer>(3));
		block.setGracePeriod(new DynamicValue<Integer>(1000));
		
		CheckArtefact check1 = new CheckArtefact(c->context.getCurrentReportNode().setStatus(ReportNodeStatus.FAILED));
		block.addChild(check1);		
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		Assert.assertTrue(child.getDuration()>=2000);
		assertEquals(child.getStatus(), ReportNodeStatus.FAILED);
		
		assertEquals(3, getChildren(child).size());
	}
	
	@Test
	public void testReportLastNodeOnly() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setMaxRetries(new DynamicValue<Integer>(3));
		block.setGracePeriod(new DynamicValue<Integer>(1000));
		block.setReportLastTryOnly(new DynamicValue<Boolean>(true));
		
		CheckArtefact check1 = new CheckArtefact(c->{
			context.getCurrentReportNode().setStatus(ReportNodeStatus.FAILED);
		});
		block.addChild(check1);		
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		Assert.assertTrue(child.getDuration()>=2000);
		assertEquals(child.getStatus(), ReportNodeStatus.FAILED);
		
		assertEquals(1, getChildren(child).size());
	}
	
	@Test
	public void testReportLastNodeOnlySuccess() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setMaxRetries(new DynamicValue<Integer>(3));
		block.setGracePeriod(new DynamicValue<Integer>(1000));
		block.setReportLastTryOnly(new DynamicValue<Boolean>(true));
		
		CheckArtefact check1 = new CheckArtefact(c->{
			context.getCurrentReportNode().setStatus(ReportNodeStatus.PASSED);
		});
		block.addChild(check1);		
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		List<ReportNode> children = getChildren(child);
		assertEquals(child.getStatus(), ReportNodeStatus.PASSED);		
		assertEquals(1, getChildren(child).size());
	}
	
	@Test
	public void testReportLastNodeOnlyTimeout() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setMaxRetries(new DynamicValue<Integer>(3));
		block.setGracePeriod(new DynamicValue<Integer>(1000));
		block.setReportLastTryOnly(new DynamicValue<Boolean>(true));
		block.setTimeout(new DynamicValue<Integer>(500));
		
		CheckArtefact check1 = new CheckArtefact(c->{
			context.getCurrentReportNode().setStatus(ReportNodeStatus.FAILED);
		});
		block.addChild(check1);		
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		System.out.println("Assert child.getDuration()<=2000 with value: " + child.getDuration());
		Assert.assertTrue(child.getDuration()<2000);
		Assert.assertTrue(child instanceof RetryIfFailsReportNode);
		RetryIfFailsReportNode retryIfFailsReportNode = (RetryIfFailsReportNode) child;
		Assert.assertEquals(2, retryIfFailsReportNode.getTries());
		Assert.assertEquals(1, retryIfFailsReportNode.getSkipped());
		assertEquals(child.getStatus(), ReportNodeStatus.FAILED);
		
		assertEquals(1, getChildren(child).size());
	}
	
	@Test
	public void testTimeout() {
		setupContext();
		
		RetryIfFails block = new RetryIfFails();
		block.setTimeout(new DynamicValue<Integer>(200));
		block.setGracePeriod(new DynamicValue<Integer>(50));
		
		CheckArtefact check1 = new CheckArtefact(c-> {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
		});
		block.addChild(check1);		
		
		execute(block);
		
		ReportNode child = getFirstReportNode();
		Assert.assertTrue(child.getDuration()>=250);
		assertEquals(child.getStatus(), ReportNodeStatus.FAILED);
	}
	
	@Test
	public void testFalse() {
		setupContext();
		
		IfBlock block = new IfBlock("false");
		block.addChild(new Set());

		execute(block);
		
		ReportNode child = getFirstReportNode();
		assertEquals(child.getStatus(), ReportNodeStatus.PASSED);	
		assertEquals(0, getChildren(child).size());
	}
}

