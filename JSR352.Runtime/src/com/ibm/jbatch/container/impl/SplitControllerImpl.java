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
package com.ibm.jbatch.container.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.batch.operations.JobExecutionAlreadyCompleteException;
import javax.batch.operations.JobExecutionNotMostRecentException;
import javax.batch.operations.JobRestartException;
import javax.batch.operations.JobStartException;
import javax.batch.runtime.BatchStatus;

import com.ibm.jbatch.container.AbortedBeforeStartException;
import com.ibm.jbatch.container.IExecutionElementController;
import com.ibm.jbatch.container.context.impl.StepContextImpl;
import com.ibm.jbatch.container.exception.BatchContainerRuntimeException;
import com.ibm.jbatch.container.jobinstance.RuntimeJobContextJobExecutionBridge;
import com.ibm.jbatch.container.services.IBatchKernelService;
import com.ibm.jbatch.container.servicesmanager.ServicesManager;
import com.ibm.jbatch.container.servicesmanager.ServicesManagerImpl;
import com.ibm.jbatch.container.status.InternalExecutionElementStatus;
import com.ibm.jbatch.container.util.BatchWorkUnit;
import com.ibm.jbatch.container.util.PartitionDataWrapper;
import com.ibm.jbatch.jsl.model.Flow;
import com.ibm.jbatch.jsl.model.JSLJob;
import com.ibm.jbatch.jsl.model.Split;

public class SplitControllerImpl implements IExecutionElementController {

	private final static String sourceClass = SplitControllerImpl.class.getName();
	private final static Logger logger = Logger.getLogger(sourceClass);

	private final RuntimeJobContextJobExecutionBridge jobExecutionImpl;

	private volatile List<BatchWorkUnit> parallelBatchWorkUnits;

	private final ServicesManager servicesManager;
	private final IBatchKernelService batchKernel;

	final List<JSLJob> subJobs = new ArrayList<JSLJob>();

	protected Split split;

	public SplitControllerImpl(RuntimeJobContextJobExecutionBridge jobExecutionImpl, Split split) {
		this.jobExecutionImpl = jobExecutionImpl;
		this.split = split;

		servicesManager = ServicesManagerImpl.getInstance();
		batchKernel = servicesManager.getBatchKernelService();

	}

	@Override
	public void stop() { 

		// It's possible we may try to stop a split before any
		// sub steps have been started.

		synchronized (subJobs) {

			if (parallelBatchWorkUnits != null) {
				for (BatchWorkUnit subJob : parallelBatchWorkUnits) {
					try {
						batchKernel.stopJob(subJob.getJobExecutionImpl().getExecutionId());
					} catch (Exception e) {
						// TODO - Is this what we want to know.  
						// Blow up if it happens to force the issue.
						throw new IllegalStateException(e);
					}
				}
			}
		}

	}

	@Override
	public InternalExecutionElementStatus execute(RuntimeJobContextJobExecutionBridge rootJobExecution) throws AbortedBeforeStartException, JobRestartException, JobStartException, JobExecutionAlreadyCompleteException, JobExecutionNotMostRecentException {
		String sourceMethod = "execute";
		if (logger.isLoggable(Level.FINER)) {
			logger.entering(sourceClass, sourceMethod);
		}

		BlockingQueue<BatchWorkUnit> completedWorkQueue = new LinkedBlockingQueue<BatchWorkUnit>();
		List<Flow> flows = this.split.getFlows();

		parallelBatchWorkUnits = new ArrayList<BatchWorkUnit>();

		// Build all sub jobs from flows in split
		synchronized (subJobs) {

			for (Flow flow : flows) {
				subJobs.add(PartitionedStepBuilder.buildSubJob(jobExecutionImpl.getExecutionId(), jobExecutionImpl.getJobContext(), this.split, flow, null));
			}

			for (JSLJob job : subJobs) {
				int count = batchKernel.getJobInstanceCount(job.getId());
				if (count == 0) {
					parallelBatchWorkUnits.add(batchKernel.buildNewBatchWorkUnit(job, null, null, completedWorkQueue, rootJobExecution));
				} else if (count == 1) {
					parallelBatchWorkUnits.add(batchKernel.buildRestartableBatchWorkUnit(job, null, null, completedWorkQueue, rootJobExecution));
				} else {
					throw new IllegalStateException("There is an inconsistency somewhere in the internal subjob creation");
				}
			}

		}

		// Then start or restart all subjobs in parallel
		for (BatchWorkUnit work : parallelBatchWorkUnits) {
			int count = batchKernel.getJobInstanceCount(work.getJobExecutionImpl().getJobInstance().getJobName());

			assert (count <= 1);

			if (count == 1) {
				batchKernel.startGeneratedJob(work);
			} else if (count > 1) {
				batchKernel.restartGeneratedJob(work);
			} else {
				throw new IllegalStateException("There is an inconsistency somewhere in the internal subjob creation");
			}
		}

		//check the batch status of each subJob after it's done to see if it stopped or failed
		boolean someFlowFailed = false;
		boolean someFlowStopped = false;

		for (int i=0; i < subJobs.size(); i++) {
			BatchWorkUnit batchWork;
			try {
				batchWork = completedWorkQueue.take(); //wait for each thread to finish and then look at it's status
			} catch (InterruptedException e) {
				throw new BatchContainerRuntimeException(e);
			}
			BatchStatus batchStatus = batchWork.getJobExecutionImpl().getJobContext().getBatchStatus();
			if (batchStatus.equals(BatchStatus.FAILED)) {
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Subjob " + batchWork.getJobExecutionImpl().getExecutionId() + "ended with status '" + batchStatus + "'" );
				}
				someFlowFailed = true;
			} else if (batchStatus.equals(BatchStatus.STOPPED)){
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Subjob " + batchWork.getJobExecutionImpl().getExecutionId() + "ended with status '" + batchStatus + "'" );
				}
				someFlowStopped = true;
			}
		}

		// Remember it's only an implementation detail that there even is an exit status, but maybe it will make our
		// code be more consistent?

		InternalExecutionElementStatus splitStatus = null;

		// First in order of precedence is to fail
		if (someFlowFailed) {
			splitStatus = new InternalExecutionElementStatus(BatchStatus.FAILED);
		} else if (someFlowStopped) {
			splitStatus = new InternalExecutionElementStatus(BatchStatus.STOPPED);
		} else {
			splitStatus = new InternalExecutionElementStatus(BatchStatus.COMPLETED);
		}

		if (logger.isLoggable(Level.FINER)) {
			logger.finer("Exiting with: " + splitStatus);
		}

		return splitStatus;
	}

	public void setStepContext(StepContextImpl stepContext) {
		throw new BatchContainerRuntimeException("Incorrect usage: step context is not in scope within a flow.");
	}

	@Override
	public void setAnalyzerQueue(BlockingQueue<PartitionDataWrapper> analyzerQueue) {
		// no-op
	}

	public List<BatchWorkUnit> getParallelJobExecs() {
		return parallelBatchWorkUnits;
	}

}
