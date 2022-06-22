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
package step.core.plugins;

import step.core.GlobalContext;
import step.engine.plugins.ExecutionEnginePlugin;

public abstract class AbstractControllerPlugin extends AbstractPlugin implements ControllerPlugin {
	
	@Override
	public ExecutionEnginePlugin getExecutionEnginePlugin() {
		return null;
	}
	
	public WebPlugin getWebPlugin() {
		return null;
	}

	@Override
	public boolean canBeDisabled() {
		return true;
	}

	@Override
	public void serverStart(GlobalContext context) throws Exception {

	}

	@Override
	public void serverStop(GlobalContext context) {

	}

	@Override
	public void migrateData(GlobalContext context) throws Exception {

	}

	@Override
	public void initializeData(GlobalContext context) throws Exception {

	}

	@Override
	public void afterInitializeData(GlobalContext context) throws Exception {

	}

}
