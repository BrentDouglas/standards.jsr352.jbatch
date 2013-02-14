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

public class SplitTransitioningTests {

	private JobOperatorBridge jobOp = null;
	
	/**
	 * @testName: testSplitTransitionToStep
	 * @assertion: Section 5.4 Split
	 * @test_Strategy: 1. setup a job consisting of one split (w/ 2 flows) and one step
	 * 				   2. start job 
	 * 				   3. add step id from step context to job context exit status
	 * 				   4. verify that the split indeed transitioned to the step
	 * 
	 * @throws JobStartException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test @org.junit.Test
	public void testSplitTransitionToStep() throws JobStartException, FileNotFoundException, IOException, InterruptedException {

		Reporter.log("starting job");
		JobExecution jobExec = jobOp.startJobAndWaitForResult("split_transition_to_step", null);
		Reporter.log("Job Status = " + jobExec.getBatchStatus());
		
		assertWithMessage("Split transitioned to step", jobExec.getExitStatus().equals("step1"));
		
		assertWithMessage("Job completed", jobExec.getBatchStatus().equals(BatchStatus.COMPLETED));
		Reporter.log("job completed");
	}
	
	/**
	 * @testName: testSplitTransitionToStepOutOfScope
	 * @assertion: Section 5.4 Split
	 * @test_Strategy: 1. setup a job consisting of one split (w/ 2 flows) and one step
	 * 				   2. start job 
	 * 				   3. this job should fail because the split flow 'flow1' next to outside the split
	 * 
     *	<split id="split1">
	 *	   <flow id="flow1" next="step1">
	 *			<step id="flow1step1" next="flow1step2">
	 *				<batchlet ref="splitTransitionToDecisionTestBatchlet"/>
	 *			</step>
	 *			<step id="flow1step2">
	 *				<batchlet ref="splitTransitionToDecisionTestBatchlet"/>
	 *			</step>
	 *		</flow>
	 *		<flow id="flow2">
	 *			<step id="flow1step3" next="flow1step4">
	 *				<batchlet ref="splitTransitionToDecisionTestBatchlet"/>
	 *			</step>
	 *			<step id="flow1step4">
	 *				<batchlet ref="splitTransitionToDecisionTestBatchlet"/>
	 *			</step>
	 *		</flow>
	 *	</split>
	 *
	 *	<step id="step1">
	 *		<batchlet ref="splitTransitionToStepTestBatchlet"/>
	 *	</step>
	 *
	 * @throws JobStartException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
    @Test(enabled = false)
    @org.junit.Test
	public void testSplitTransitionToStepOutOfScope() {

		Reporter.log("starting job");
		JobExecution jobExec = null;
		try {
			jobExec = jobOp.startJobAndWaitForResult("split_transition_to_step_out_of_scope", null);
		} catch (JobStartException e) {
			Reporter.log("job failed to start " + e.getLocalizedMessage());
		}
		
		Reporter.log("Job Status = " + jobExec.getBatchStatus());
		
		assertWithMessage("Job should have failed because of out of scope execution elements.", jobExec.getBatchStatus().equals(BatchStatus.FAILED));
	}
	
	/**
	 * @testName: testFlowTransitionToDecision
	 * @assertion: Section 5.4 Split
	 * @test_Strategy: 1. setup a job consisting of one split (w/ 2 flows) and one decision
	 * 				   2. start job 
	 * 				   3. split will transition to decider which will change the exit status
	 * 				   4. compare that the exit status set by the decider matches that of the job
	 * 
	 * @throws JobStartException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test @org.junit.Test
	public void testFlowTransitionToDecision() throws JobStartException, FileNotFoundException, IOException, InterruptedException {

		String exitStatus = "ThatsAllFolks";
		// based on our decider exit status
		/*
		<decision id="decider1" ref="flowTransitionToDecisionTestDecider">
			<end exit-status="ThatsAllFolks" on="DECIDER_EXIT_STATUS*2" />
		</decision>
		*/
		Reporter.log("starting job");
		JobExecution jobExec = jobOp.startJobAndWaitForResult("split_transition_to_decision", null);
		Reporter.log("Job Status = " + jobExec.getBatchStatus());
		
		assertWithMessage("Job Exit Status is from decider", jobExec.getExitStatus().equals(exitStatus));
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
