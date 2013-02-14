/*
 * Copyright 2012 International Business Machines Corp.
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jsr352.tck.tests.xmlinheritance;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;

import jsr352.tck.utils.IOHelper;

import org.junit.Before;
import org.testng.Reporter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ibm.batch.tck.spi.BatchContainerServiceProvider;
import com.ibm.batch.tck.spi.JobEndCallback;
import com.ibm.batch.tck.spi.JobEndCallbackManager;

public class JobInheritanceTests {

	private int sleepTime = Integer.parseInt(System.getProperty("junit.jobOperator.sleep.time", "900000"));
	private JobOperator jobOp = null;
    private final static Logger logger = Logger.getLogger(JobInheritanceTests.class.getName());
    
	
	public void setup(String[] args, Properties props) {
		
		jobOp = BatchRuntime.getJobOperator();
	}
	
    @BeforeMethod
    @Before
	public void setUp() {
		
		jobOp = BatchRuntime.getJobOperator();
	}
	
	/* cleanup */
	public void  cleanup()
	{		
	
	}
	
	/** Pending discussion of Bug 4393, let's ignore this for now **/
	@Test(enabled=false) @org.junit.Test @org.junit.Ignore
	public void testAll() throws Exception {
		
		// Jobs inheriting jobs
		Reporter.log("Test job inheriting jobs<p>");
		executeJob("job1.xml");
		
		// steps inheriting steps
		Reporter.log("Test teps inheriting steps<p>");
		executeJob("job2-step.xml");
		
	}
	
	public void executeJob(String jobXmlFile) throws Exception {
		
		String METHOD = "executeJob";
		JobEndCallback callback = null;
		
		try {
		Reporter.log("Get job XML file: " + jobXmlFile +"<p>");
		String jobXml = getJobXml(jobXmlFile);
		
		Reporter.log("Get JobEndCallbackManager instance<p>");
		JobEndCallbackManager callbackManager = getServices().getCallbackManager();
		
		callback = new JobEndCallback() {

			@Override
			public void done(long jobExecutionId) {
	            synchronized(this) {
//	            	assert(BatchStatus.COMPLETED == jobExecution.getBatchStatus());
	                this.notify();
	            }
			}
		};
		
		Reporter.log("Register job end callback<p>");
		callbackManager.registerJobEndCallback(callback);
		
		Reporter.log("Invoke JobOperator.start for jobXml " + jobXml + "<p>");
		Long executionId = jobOp.start(jobXml, null);
		
		Reporter.log(Long.toString(jobOp.getJobExecution(executionId).getInstanceId()) + "<p>");
		} catch (Exception e) {
			handleException(METHOD, e);
		}
		
		synchronized (callback) {
			
			try {
				callback.wait(sleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				handleException(METHOD, e);
			}
		}
	}
	
	private BatchContainerServiceProvider getServices() {
		BatchContainerServiceProvider services = null;
        ServiceLoader<BatchContainerServiceProvider> loader = ServiceLoader.load(BatchContainerServiceProvider.class);
        for (BatchContainerServiceProvider provider : loader) {
            if (provider != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Loaded BatchContainerServiceProvider with className = " + provider.getClass().getCanonicalName());
                }
                // Use first one
                services = provider;
                break;
            }
        }
        if (services == null) {
            throw new IllegalStateException("Bad TCK classpath; check your run/debug config.  Probably the remedy is to add the 'Runtime' project as a classpath entry to the TCK run configuration (on the other hand it should NOT" +
            		" be present on the build time clsspath.  The low level problem is that we couldn't find/load a BatchContainerServiceProvider instance.  ");
        }
        return services;
	}
	
	private String getJobXml(String name) throws FileNotFoundException, IOException {
		
		URL jobXMLURL = this.getClass().getResource("/" + name);
		
		return IOHelper.readJobXML(jobXMLURL.getFile());
	}

	private static void handleException(String methodName, Exception e) throws Exception {
		Reporter.log("Caught exception: " + e.getMessage()+"<p>");
		Reporter.log(methodName + " failed<p>");
		throw e;
	}
}
