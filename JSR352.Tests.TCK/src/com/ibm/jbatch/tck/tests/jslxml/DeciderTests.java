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
package com.ibm.jbatch.tck.tests.jslxml;

import static com.ibm.jbatch.tck.utils.AssertionUtils.assertObjEquals;

import java.util.Properties;
import java.util.logging.Logger;

import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;

import com.ibm.jbatch.tck.artifacts.common.StatusConstants;
import com.ibm.jbatch.tck.artifacts.specialized.DeciderTestsBatchlet;
import com.ibm.jbatch.tck.utils.JobOperatorBridge;

import org.junit.BeforeClass;
import org.testng.Reporter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeciderTests implements StatusConstants {
	private final static Logger logger = Logger.getLogger(DeciderTests.class.getName());
	private static JobOperatorBridge jobOp = null;


	public static void setup(String[] args, Properties props) throws Exception {
		String METHOD = "setup";

		try {
			jobOp = new JobOperatorBridge();
		} catch (Exception e) {
			handleException(METHOD, e);
		}
	}

	@BeforeMethod
	@BeforeClass
	public static void setUp() throws Exception {
		jobOp = new JobOperatorBridge();                              
	}

	/* cleanup */
	public void  cleanup()
	{		

	}

	/*
	 * @testName: testDeciderEndNormal
	 * @assertion: Tests that decision child elements: next, step, end, fail, all work as expected (by verifying batch status)
	 *             Tests exit status globbing ('*' and '?') 
	 *             Tests StepExecution from previous step passed to Decider
	 *             Tests that decider can be configured via property
	 *             Tests that JobContext can be utilized in decider (the same instance as that of the earlier steps).
	 *             Tests setting of <stop>, <end>, <fail> @exit-status attribute value.
	 *             
	 * @test_Strategy: The test methods in this class have a tighter coupling than usual.  That's because they all use the
	 *                 same artifacts, the same basic JSL, and the same basic <decision> element (with the same set of glob 
	 *                 patterns). We set up different "branches" in the job execution sequence depending on exit status, 
	 *                 (e.g. stop vs. fail, end with one exit status vs. another), and we take the various branches in the
	 *                 various test methods.
	 *                    
	 *                 1. Batchlet is coded to end with one of two different exit status values depending on @BatchProperty populated by job param set either to:
	 *                       jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.NORMAL_VALUE);
	 *                              OR
	 *                       jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.SPECIAL_VALUE);
	 *                    Batch status is tested as well
	 *                 2. Decider returns value based on configured property, step execution exit status, and job context transient data. 
	 *                 3. JobContext utilized to adjust "core exit status" by prefixing the number of times the step has run.
	 *                 4. Special globbing chars '*' and '?' are used in the @on values to test exit status globbing.
	 *                 5. Test asserts specific exit status as well as batch status.  In addition to testing the overall test flow this 
	 *                    tests the @exit-status attributes of fail, stop, end.
	 *                    
	 */
	@Test
	@org.junit.Test
	public void testDeciderEndNormal() throws Exception {

		String METHOD = "testDeciderEndNormal";

		try {
			// 1. Here "EndSpecial" is the exit status the decider will return if the step exit status
			// is the "special" exit status value.  It is set as a property on the decider.
			Reporter.log("Build job parameters for EndSpecial exit status<p>");

			Properties jobParameters = new Properties();

			// 2. Here "EndNormal" is the exit status the decider will return if the step exit status
			// is the "normal" exit status value.  It is set as a property on the batchlet and passed
			// along to the decider via stepContext.setTransientUserData().
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value EndNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "EndNormal");
			// 3. This "ACTUAL_VALUE" is a property set on the batchlet.  It will either indicate to end
			// the step with a "normal" or "special" exit status.            
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.NORMAL_VALUE + "<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.NORMAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value EndSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "EndSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()=EndNormal<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getStatus()=COMPLETED<p>");
			Reporter.log("Actual JobExecution getStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("EndNormal", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.COMPLETED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}

	}

	/*
	 * @testName: testDeciderEndSpecial
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderEndSpecial() throws Exception {
		String METHOD = "testDeciderEndSpecial";

		try {
			Reporter.log("Build job parameters for EndSpecial exit status<p>");	    

			Properties jobParameters = new Properties();        
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "EndNormal");
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value EndNormal<p>");
			// 1. This is the only test parameter that differs from testDeciderEndNormal().
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.SPECIAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.SPECIAL_VALUE);
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value EndSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "EndSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			// 2. And the job exit status differs accordingly.
			Reporter.log("Expected JobExecution getExitStatus()=EndSpecial<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=COMPLETED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("EndSpecial", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.COMPLETED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}

	// See the first two test methods for an explanation of parameter values.
	/*
	 * @testName: testDeciderStopNormal
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderStopNormal() throws Exception {
		String METHOD = " testDeciderStopNormal";

		try {
			Reporter.log("Build job parameters for StopSpecial exit status<p>");

			Properties jobParameters = new Properties();    
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value StopNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "StopNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.NORMAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.NORMAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value StopSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "StopSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()=StopNormal<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=STOPPED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("StopNormal", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.STOPPED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}

	}

	// See the first two test methods for an explanation of parameter values.
	/*
	 * @testName: testDeciderStopSpecial
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderStopSpecial() throws Exception {
		String METHOD = "testDeciderStopSpecial";

		try {
			Reporter.log("Build job parameters for StopSpecial exit status<p>");

			Properties jobParameters = new Properties();     
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value StopNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "StopNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.SPECIAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.SPECIAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value StopSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "StopSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()=StopSpecial<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=STOPPED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("StopSpecial", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.STOPPED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}

	// See the first two test methods for an explanation of parameter values.
	/*
	 * @testName: testDeciderFailNormal
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderFailNormal() throws Exception {

		String METHOD = "testDeciderFailNormal";

		try {
			Reporter.log("Build job parameters for FailSpecial exit status<p>");	    	

			Properties jobParameters = new Properties();        
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value FailNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "FailNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.NORMAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.NORMAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value FailSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "FailSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()=FailNormal<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=FAILED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("FailNormal", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.FAILED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}

	// See the first two test methods for an explanation of parameter values.
	/*
	 * @testName: testDeciderFailSpecial
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderFailSpecial() throws Exception {
		String METHOD = "testDeciderFailSpecial";

		try {
			Reporter.log("Build job parameters for FailSpecial exit status<p>");

			Properties jobParameters = new Properties();        
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value FailNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "FailNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.SPECIAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.SPECIAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value FailSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "FailSpecial");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_incompleterun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()=FailSpecial<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=FAILED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals("FailSpecial", jobExec.getExitStatus());
			assertObjEquals(BatchStatus.FAILED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}

	/*
	 * @testName: testDeciderNextNormal
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderNextNormal() throws Exception {
		String METHOD = "testDeciderNextNormal";

		try {
			Reporter.log("Build job parameters for NextSpecial exit status<p>");

			Properties jobParameters = new Properties();        
			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value NextNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "NextNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.NORMAL_VALUE +"<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.NORMAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value NextSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "NextSpecial");

			Reporter.log("Create single job listener deciderTestJobListener and get JSL<p>");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_completerun", jobParameters); 

			Reporter.log("Expected JobExecution getExitStatus()="+GOOD_JOB_EXIT_STATUS+"<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=COMPLETED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals(GOOD_JOB_EXIT_STATUS, jobExec.getExitStatus());
			assertObjEquals(BatchStatus.COMPLETED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}

	/*
	 * @testName: testDeciderNextSpecial
	 * @assertion: see testDeciderEndNormal
	 * @test_Strategy: see testDeciderEndNormal
	 */
	@Test
	@org.junit.Test
	public void testDeciderNextSpecial() throws Exception {
		String METHOD = "testDeciderNextSpecial";

		try {
			Reporter.log("Build job parameters for NextSpecial exit status<p>");	

			Properties jobParameters = new Properties();        

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTION + " with value NextNormal<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTION, "NextNormal");

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.ACTUAL_VALUE + " with value " + DeciderTestsBatchlet.SPECIAL_VALUE + "<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.ACTUAL_VALUE, DeciderTestsBatchlet.SPECIAL_VALUE);

			Reporter.log("Set job parameters property " + DeciderTestsBatchlet.SPECIAL_EXIT_STATUS + " with value NextSpecial<p>");
			jobParameters.setProperty(DeciderTestsBatchlet.SPECIAL_EXIT_STATUS, "NextSpecial");

			Reporter.log("Create single job listener deciderTestJobListener and get JSL<p>");

			Reporter.log("Invoke startJobAndWaitForResult<p>");
			JobExecution jobExec = jobOp.startJobAndWaitForResult("job_decider_completerun", jobParameters); 

			// This actually exits with the exact same status as the "...NextNormal" test.
			Reporter.log("Expected JobExecution getExitStatus()="+GOOD_JOB_EXIT_STATUS+"<p>");
			Reporter.log("Actual JobExecution getExitStatus()="+jobExec.getExitStatus()+"<p>");
			Reporter.log("Expected JobExecution getBatchStatus()=COMPLETED<p>");
			Reporter.log("Actual JobExecution getBatchStatus()="+jobExec.getBatchStatus()+"<p>");
			assertObjEquals(GOOD_JOB_EXIT_STATUS, jobExec.getExitStatus());
			assertObjEquals(BatchStatus.COMPLETED, jobExec.getBatchStatus());
		} catch(Exception e) {
			handleException(METHOD, e);
		}
	}   

	private static void handleException(String methodName, Exception e) throws Exception {
		Reporter.log("Caught exception: " + e.getMessage()+"<p>");
		Reporter.log(methodName + " failed<p>");
		throw e;
	}

}
