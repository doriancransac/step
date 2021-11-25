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
package step.core;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.exense.commons.app.Configuration;
import step.artefacts.handlers.PlanLocator;
import step.artefacts.handlers.SelectorHelper;
import step.attachments.FileResolver;
import step.core.access.User;
import step.core.access.UserAccessor;
import step.core.access.UserAccessorImpl;
import step.core.accessors.AbstractAccessor;
import step.core.artefacts.reports.ReportNode;
import step.core.artefacts.reports.ReportNodeAccessor;
import step.core.artefacts.reports.ReportNodeAccessorImpl;
import step.core.collections.Collection;
import step.core.collections.CollectionFactory;
import step.core.controller.ControllerSettingAccessor;
import step.core.deployment.WebApplicationConfigurationManager;
import step.core.dynamicbeans.DynamicBeanResolver;
import step.core.dynamicbeans.DynamicJsonObjectResolver;
import step.core.dynamicbeans.DynamicJsonValueResolver;
import step.core.dynamicbeans.DynamicValueResolver;
import step.core.entities.Bean;
import step.core.entities.Entity;
import step.core.entities.EntityDependencyTreeVisitor.EntityTreeVisitorContext;
import step.core.entities.EntityManager;
import step.core.entities.DependencyTreeVisitorHook;
import step.core.execution.model.Execution;
import step.core.execution.model.ExecutionAccessor;
import step.core.execution.model.ExecutionAccessorImpl;
import step.core.execution.model.ExecutionStatus;
import step.core.plans.Plan;
import step.core.plans.PlanAccessor;
import step.core.plans.PlanAccessorImpl;
import step.core.plans.PlanEntity;
import step.core.plugins.ControllerInitializationPlugin;
import step.core.plugins.ControllerPlugin;
import step.core.plugins.ControllerPluginManager;
import step.core.plugins.ModuleChecker;
import step.core.plugins.PluginManager;
import step.core.plugins.PluginManager.Builder;
import step.core.repositories.RepositoryObjectManager;
import step.core.scheduler.ExecutionScheduler;
import step.core.scheduler.ExecutionTaskAccessor;
import step.core.scheduler.ExecutionTaskAccessorImpl;
import step.core.scheduler.ExecutiontTaskParameters;
import step.core.scheduler.Executor;
import step.core.tables.AbstractTable;
import step.core.tables.TableRegistry;
import step.core.tasks.AsyncTaskManager;
import step.dashboards.DashboardSession;
import step.engine.execution.ExecutionManagerImpl;
import step.expressions.ExpressionHandler;
import step.resources.Resource;
import step.resources.ResourceAccessor;
import step.resources.ResourceAccessorImpl;
import step.resources.ResourceEntity;
import step.resources.ResourceImporter;
import step.resources.ResourceManager;
import step.resources.ResourceManagerControllerPlugin;
import step.resources.ResourceManagerImpl;
import step.resources.ResourceRevision;
import step.resources.ResourceRevisionAccessor;
import step.resources.ResourceRevisionAccessorImpl;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;

public class Controller {
	
	private static final Logger logger = LoggerFactory.getLogger(Controller.class);
	
	private Configuration configuration;
	
	private GlobalContext context;
		
	private ControllerPluginManager pluginManager;
	
	private ExecutionScheduler scheduler;
	
	private ServiceRegistrationCallback serviceRegistrationCallback;
	
	public Controller(Configuration configuration) {
		super();
		this.configuration = configuration;
	}

	public void init(ServiceRegistrationCallback serviceRegistrationCallback) throws Exception {			
		this.serviceRegistrationCallback = serviceRegistrationCallback;
		
		initContext();
		context.setServiceRegistrationCallback(serviceRegistrationCallback);
		
		recover();
		
		ControllerPlugin pluginProxy = pluginManager.getProxy();
		logger.info("Starting controller...");
		pluginProxy.executionControllerStart(context);
		logger.info("Executing migration tasks...");
		pluginProxy.migrateData(context);
		logger.info("Initializing data...");
		pluginProxy.initializeData(context);
		logger.info("Calling post data initialization scripts...");
		pluginProxy.afterInitializeData(context);
			
		scheduler = new ExecutionScheduler(context.require(ControllerSettingAccessor.class), context.getScheduleAccessor(), new Executor(context));
		scheduler.start();
	}
	
	private void initContext() throws Exception {
		context = new GlobalContext();
		context.setConfiguration(configuration);
		
		Builder<ControllerInitializationPlugin> builder = new PluginManager.Builder<ControllerInitializationPlugin>(ControllerInitializationPlugin.class);
		PluginManager<ControllerInitializationPlugin> controllerInitializationPluginManager = builder.withPluginsFromClasspath().build();
		logger.info("Checking preconditions...");
		controllerInitializationPluginManager.getProxy().checkPreconditions(context);
		
		ModuleChecker moduleChecker = context.get(ModuleChecker.class);
		pluginManager = new ControllerPluginManager(configuration, moduleChecker);
		context.setPluginManager(pluginManager);
		
		CollectionFactory collectionFactory = CollectionFactoryConfigurationParser.parseConfiguration(configuration);
		context.setCollectionFactory(collectionFactory);
		
		ResourceAccessor resourceAccessor = new ResourceAccessorImpl(collectionFactory.getCollection("resources", Resource.class));
		ResourceRevisionAccessor resourceRevisionAccessor = new ResourceRevisionAccessorImpl(
				collectionFactory.getCollection("resourceRevisions", ResourceRevision.class));
		String resourceRootDir = ResourceManagerControllerPlugin.getResourceDir(configuration);
		ResourceManager resourceManager = new ResourceManagerImpl(new File(resourceRootDir), resourceAccessor, resourceRevisionAccessor);
		FileResolver fileResolver = new FileResolver(resourceManager);
		
		context.setResourceAccessor(resourceAccessor);
		context.setResourceManager(resourceManager);
		context.setFileResolver(fileResolver);
		
		TableRegistry tableRegistry = new TableRegistry();
		context.put(TableRegistry.class, tableRegistry);		
		ExecutionAccessorImpl executionAccessor = new ExecutionAccessorImpl(
				collectionFactory.getCollection("executions", Execution.class));
		context.setExecutionAccessor(executionAccessor);		
		context.setExecutionManager(new ExecutionManagerImpl(executionAccessor));
		
		context.setPlanAccessor(new PlanAccessorImpl(collectionFactory.getCollection("plans", Plan.class)));
		context.setReportNodeAccessor(
				new ReportNodeAccessorImpl(collectionFactory.getCollection("reports", ReportNode.class)));
		context.setScheduleAccessor(new ExecutionTaskAccessorImpl(
				collectionFactory.getCollection("tasks", ExecutiontTaskParameters.class)));

		Collection<User> userCollection = collectionFactory.getCollection("users", User.class);
		context.setUserAccessor(new UserAccessorImpl(userCollection));
		tableRegistry.register("users", new AbstractTable<User>(userCollection, false));
		
		
		context.setRepositoryObjectManager(new RepositoryObjectManager());
		context.setExpressionHandler(new ExpressionHandler(configuration.getProperty("tec.expressions.scriptbaseclass"), 
				configuration.getPropertyAsInteger("tec.expressions.warningthreshold",200),
				configuration.getPropertyAsInteger("tec.expressions.pool.maxtotal",1000),
				configuration.getPropertyAsInteger("tec.expressions.pool.maxidle",-1)));
		context.setDynamicBeanResolver(new DynamicBeanResolver(new DynamicValueResolver(context.getExpressionHandler())));
		
		context.setEntityManager(new EntityManager());
		DynamicJsonObjectResolver dynamicJsonObjectResolver = new DynamicJsonObjectResolver(new DynamicJsonValueResolver(getContext().getExpressionHandler()));
		SelectorHelper selectorHelper = new SelectorHelper(dynamicJsonObjectResolver);
		PlanLocator planLocator = new PlanLocator(getContext().getPlanAccessor(), selectorHelper);
		
		EntityManager entityManager = context.getEntityManager();
		entityManager
				// Bean entity used for remote test
				.register(new Entity<Bean, AbstractAccessor<Bean>>("beans",
						new AbstractAccessor(context.getCollectionFactory().getCollection("beans", Bean.class)),
						Bean.class))
				.register(new Entity<>(EntityManager.executions, context.getExecutionAccessor(), Execution.class))
				.register(new PlanEntity(context.getPlanAccessor(), planLocator, entityManager))
				.register(new Entity<>(EntityManager.reports, context.getReportAccessor(), ReportNode.class))
				.register(new Entity<>(EntityManager.tasks, context.getScheduleAccessor(), ExecutiontTaskParameters.class))
				.register(new Entity<>(EntityManager.users, context.getUserAccessor(), User.class))
				.register(new ResourceEntity(resourceAccessor, resourceManager, fileResolver, entityManager))
				.register(new Entity<>(EntityManager.resourceRevisions, resourceRevisionAccessor, ResourceRevision.class))
				.register(new Entity<>("sessions",
						new AbstractAccessor<>(
								collectionFactory.getCollection("sessions", DashboardSession.class)),
						DashboardSession.class));
		
		entityManager.registerImportHook(new ResourceImporter(context.getResourceManager()));
		entityManager.getEntityByName("sessions").setByPassObjectPredicate(true);

		context.put(AsyncTaskManager.class, new AsyncTaskManager());
		context.put(WebApplicationConfigurationManager.class, new WebApplicationConfigurationManager());

		createOrUpdateIndexes();

	}

	private void createOrUpdateIndexes() {
		long dataTTL = context.getConfiguration().getPropertyAsInteger("db.datattl", 0);
		context.getReportAccessor().createIndexesIfNeeded(dataTTL);
		context.getExecutionAccessor().createIndexesIfNeeded(dataTTL);
	}

	public void destroy() {
		// waits for executions to terminate
		scheduler.shutdown();

		serviceRegistrationCallback.stop();

		// call shutdown hooks
		pluginManager.getProxy().executionControllerDestroy(context);
		
		try {
			context.close();
		} catch (IOException e) {
			logger.error("Error while closing global context", e);
		}
	}
	
	private void recover() {
		ExecutionAccessor accessor = context.getExecutionAccessor();
		List<Execution> executions = accessor.getActiveTests();
		if(executions!=null && executions.size()>0) {
			logger.warn("Found " + executions.size() + " executions in an inconsistent state. The system might not have been shutdown cleanly or crashed."
					+ "Starting recovery...");
			for(Execution e:executions) {
				logger.warn("Recovering test execution " + e.toString());
				logger.debug("Setting status to ENDED. TestExecutionID:"+ e.getId().toString()); 
				e.setStatus(ExecutionStatus.ENDED);
				e.setEndTime(System.currentTimeMillis());
				accessor.save(e);
			}
			logger.debug("Recovery ended.");
		}
	}
		
	public GlobalContext getContext() {
		return context;
	}

	public ExecutionScheduler getScheduler() {
		return scheduler;
	}
	
	public interface ServiceRegistrationCallback {

		public void register(Object component);
		
		public void registerService(Class<?> serviceClass);
		
		public void registerHandler(Handler handler);

		public void registerServlet(ServletHolder servletHolder, String subPath);

        public FilterHolder registerServletFilter(Class<? extends Filter> filterClass, String pathSpec, EnumSet<DispatcherType> dispatches);

        public void stop();
		
	}
	
}
