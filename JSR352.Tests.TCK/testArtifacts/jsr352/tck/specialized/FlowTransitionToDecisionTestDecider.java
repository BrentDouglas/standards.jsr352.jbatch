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
package jsr352.tck.specialized;

import javax.batch.api.Decider;
import javax.batch.runtime.StepExecution;

import jsr352.tck.common.StatusConstants;

@javax.inject.Named
public class FlowTransitionToDecisionTestDecider implements Decider<Object>, StatusConstants {
	
	public final static String DECIDER_EXIT_STATUS = "DECIDER_EXIT_STATUS";


	@Override
	public String decide(StepExecution stepExecution) throws Exception {
		
		// for our test 
		// <end exit-status="ThatsAllFolks" on="DECIDER_EXIT_STATUS*VERY GOOD INVOCATION" />
		return DECIDER_EXIT_STATUS + "*" + stepExecution.getExitStatus();
	}


	@Override
	public String decide(StepExecution[] stepExecutions) throws Exception {
		
		// this method should be only invoked for split transition to decision
		return "INVALID_THIS_TEST_IS_FOR_FLOW_NOT_SPLIT";
	}

   
	



	
}
