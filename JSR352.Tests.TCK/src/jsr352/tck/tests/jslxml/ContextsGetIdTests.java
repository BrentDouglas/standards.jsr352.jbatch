/*
 * Copyright 2013 International Business Machines Corp.
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
package jsr352.tck.tests.jslxml;

import static jsr352.tck.utils.AssertionUtils.assertWithMessage;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.batch.operations.JobOperator.BatchStatus;
import javax.batch.operations.exception.JobStartException;
import javax.batch.runtime.JobExecution;

import jsr352.tck.utils.JobOperatorBridge;

import org.junit.Before;
import org.testng.Reporter;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ContextsGetIdTests {

	private JobOperatorBridge jobOp = null;
	
	/**
	 * @testName: testJobContextGetId
	 * @assertion: Section 7.7.2 JobContext
	 * @test_Strategy: 1. setup a simple job with one step
	 * 				   2. start job 
	 * 				   3. set job exit status equals job id from JobContext in batchlet
	 * 				   4. compare job id 'job1' to job exit status
	 * 
	 * 	<job id="job1" xmlns="http://batch.jsr352/jsl">
	 * 		<step id="step1">
	 *			<batchlet ref="contextsGetIdJobContextTestBatchlet"/>
	 *		</step>
	 *	</job>
	 *
	 * @throws JobStartException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
    @Test
    @org.junit.Test
	public void testJobContextGetId() throws JobStartException, FileNotFoundException, IOException, InterruptedException {

    	String jobId = "job1";
    	
		Reporter.log("starting job");
		JobExecution jobExec = jobOp.startJobAndWaitForResult("contexts_getid_jobcontext", null);
		Reporter.log("Job Status = " + jobExec.getBatchStatus());
		
		assertWithMessage("job id equals job1", jobExec.getExitStatus().equals(jobId));
		
		assertWithMessage("Job completed", jobExec.getBatchStatus().equals(BatchStatus.COMPLETED));
		Reporter.log("job completed");
	}
    
	/**
	 * @testName: testStepContextGetId
	 * @assertion: Section 7.7.2 StepContext
	 * @test_Strategy: 1. setup a simple job with one step
	 * 				   2. start job 
	 * 				   3. set job exit status equals step id from StepContext in batchlet
	 * 				   4. compare step id 'step1' to job exit status
	 * 
	 * 	<job id="job1" xmlns="http://batch.jsr352/jsl">
	 * 		<step id="step1">
	 *			<batchlet ref="contextsGetIdStepContextTestBatchlet"/>
	 *		</step>
	 *	</job>
	 *
	 * @throws JobStartException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
    @Test
    @org.junit.Test
	public void testStepContextGetId() throws JobStartException, FileNotFoundException, IOException, InterruptedException {

    	String stepId = "step1";
    	
		Reporter.log("starting job");
		JobExecution jobExec = jobOp.startJobAndWaitForResult("contexts_getid_stepcontext", null);
		Reporter.log("Job Status = " + jobExec.getBatchStatus());
		
		assertWithMessage("job id equals job1", jobExec.getExitStatus().equals(stepId));
		
		assertWithMessage("Job completed", jobExec.getBatchStatus().equals(BatchStatus.COMPLETED));
		Reporter.log("job completed");
	}
	
	@BeforeTest
    @Before
	public void beforeTest() throws ClassNotFoundException {
		jobOp = new JobOperatorBridge(); 
	}

	@AfterTest
	public void afterTest() {
		jobOp = null;
	}
}
