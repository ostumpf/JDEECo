package cz.cuni.mff.d3s.deeco.runtime;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import cz.cuni.mff.d3s.deeco.executor.Executor;
import cz.cuni.mff.d3s.deeco.executor.SameThreadExecutor;
import cz.cuni.mff.d3s.deeco.knowledge.CloningKnowledgeManagerFactory;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeManagerContainer;
import cz.cuni.mff.d3s.deeco.model.runtime.api.RuntimeMetadata;
import cz.cuni.mff.d3s.deeco.model.runtime.custom.RuntimeMetadataFactoryExt;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeConfiguration.Distribution;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeConfiguration.Execution;
import cz.cuni.mff.d3s.deeco.runtime.RuntimeConfiguration.Scheduling;
import cz.cuni.mff.d3s.deeco.scheduler.Scheduler;
import cz.cuni.mff.d3s.deeco.scheduler.SingleThreadedScheduler;

/**
 * 
 * @author Jaroslav Keznikl <keznikl@d3s.mff.cuni.cz>
 *
 */
public class RuntimeFrameworkBuilderTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	
	@Test
	public void testRuntimeFrameworkBuilderValidConfiguration()
		throws Exception {
		RuntimeConfiguration configuration = new RuntimeConfiguration(Scheduling.WALL_TIME, Distribution.LOCAL, Execution.SINGLE_THREADED);

		RuntimeFrameworkBuilder result = new RuntimeFrameworkBuilder(configuration, new CloningKnowledgeManagerFactory());

		// add additional test code here
		assertNotNull(result);
	}

	
	@Test
	public void testInitNullConfiguration() {
		thrown.expect(IllegalArgumentException.class);
		
		// WHEN a builder is created with a null configuration
		new RuntimeFrameworkBuilder(null, new CloningKnowledgeManagerFactory());
		// THEN an IllegalArgumentException is thrown	
	}
	
	@Test
	public void testBuildNullModel() {		
		RuntimeConfiguration configuration = mock(RuntimeConfiguration.class);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(configuration, new CloningKnowledgeManagerFactory());

		thrown.expect(IllegalArgumentException.class);

		// WHEN a build(...) is called with a null model		
		tested.build(null);
		// THEN an IllegalArgumentException is thrown	
	}
	
	@Test
	public void testConnect() {
		// GIVEN a builder that built proper scheduler, executor and km registry 
		RuntimeConfiguration configuration = mock(RuntimeConfiguration.class);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(configuration, new CloningKnowledgeManagerFactory());
		tested.scheduler = mock(Scheduler.class);
		tested.executor = mock(Executor.class);
		tested.kmContainer = mock(KnowledgeManagerContainer.class);		
		
		// THEN the connect() interconnects the scheduler and executor properly
		tested.connect();
		verify(tested.scheduler).setExecutor(tested.executor);
		verify(tested.executor).setExecutionListener(tested.scheduler);		
	}
	
	@Test
	public void testBuildRuntime() {
		// GIVEN a builder that built proper scheduler, executor and km registry 
		RuntimeConfiguration configuration = mock(RuntimeConfiguration.class);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(configuration, new CloningKnowledgeManagerFactory());
		tested.scheduler = mock(Scheduler.class);
		tested.executor = mock(Executor.class);
		tested.kmContainer = mock(KnowledgeManagerContainer.class);		
		
		RuntimeMetadata model = RuntimeMetadataFactoryExt.eINSTANCE.createRuntimeMetadata();
		tested.model = model;
		
		// WHEN buildRuntime() is called
		tested.buildRuntime();
		// THEN it creates a runtime with the corresponding
		// scheduler, executor, and knowledge registry
		assertNotNull(tested.runtime);
		assertTrue(tested.runtime instanceof RuntimeFrameworkImpl);
		RuntimeFrameworkImpl runtime = (RuntimeFrameworkImpl) tested.runtime;
		assertSame(tested.scheduler, runtime.scheduler);
		assertSame(tested.executor, runtime.executor);
		assertSame(tested.kmContainer, runtime.kmContainer);
		assertSame(model, runtime.model);
	}
	
	

	@Test
	public void testGetWallTimeScheduler() {
		// GIVEN a configuration with WALL_TIME scheduling
		RuntimeConfiguration cnf = new RuntimeConfiguration(Scheduling.WALL_TIME, null, null);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(cnf, new CloningKnowledgeManagerFactory());
		// THEN the builder creates an instance of LocalTimeScheduler
		tested.buildScheduler();
		assertNotNull(tested.scheduler);
		assertTrue(tested.scheduler instanceof SingleThreadedScheduler);		
	}
	

	@Test
	public void testGetUnsupportedScheduler() {
		// GIVEN a configuration with null scheduling
		RuntimeConfiguration cnf = new RuntimeConfiguration(null, null, null);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(cnf, new CloningKnowledgeManagerFactory());
		// THEN buildScheduler() throws an UnsupportedOperationException
		thrown.expect(UnsupportedOperationException.class);
		tested.buildScheduler();
	}	
	
	
	@Test
	public void testGetSingleThreadedExecutor() {
		// GIVEN a configuration with SINGLE_THREADED execution
		RuntimeConfiguration cnf = new RuntimeConfiguration(null, null, Execution.SINGLE_THREADED);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(cnf, new CloningKnowledgeManagerFactory());
		// THEN the builder creates an instance of SameThreadExecutor
		tested.buildExecutor();
		assertNotNull(tested.executor);
		assertTrue(tested.executor instanceof SameThreadExecutor);		
	}
	
	@Test
	public void testGetUnsupportedExecutor() {
		// GIVEN a configuration with null execution
		RuntimeConfiguration cnf = new RuntimeConfiguration(null, null, null);
		RuntimeFrameworkBuilder tested = new RuntimeFrameworkBuilder(cnf, new CloningKnowledgeManagerFactory());

		// THEN buildExecutor() throws an UnsupportedOperationException
		thrown.expect(UnsupportedOperationException.class);
		tested.buildExecutor();				
	}	
}
