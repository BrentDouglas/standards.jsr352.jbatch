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
package com.ibm.jbatch.container.api.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.exception.JobExecutionAlreadyCompleteException;
import javax.batch.operations.exception.JobExecutionIsRunningException;
import javax.batch.operations.exception.JobExecutionNotMostRecentException;
import javax.batch.operations.exception.JobExecutionNotRunningException;
import javax.batch.operations.exception.JobInstanceAlreadyCompleteException;
import javax.batch.operations.exception.JobRestartException;
import javax.batch.operations.exception.JobStartException;
import javax.batch.operations.exception.NoSuchJobException;
import javax.batch.operations.exception.NoSuchJobExecutionException;
import javax.batch.operations.exception.NoSuchJobInstanceException;
import javax.batch.operations.exception.SecurityException;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.JobInstance;
import javax.batch.runtime.StepExecution;
import com.ibm.jbatch.container.services.IBatchKernelService;
import com.ibm.jbatch.container.services.IPersistenceManagerService;
import com.ibm.jbatch.container.servicesmanager.ServicesManager;
import com.ibm.jbatch.container.servicesmanager.ServicesManagerImpl;
import com.ibm.jbatch.spi.BatchSecurityHelper;
import com.ibm.jbatch.spi.services.IJobXMLLoaderService;


public class JobOperatorImpl implements JobOperator {

    private final static String sourceClass = JobOperatorImpl.class.getName();
    private final static Logger logger = Logger.getLogger(sourceClass);
    
    private ServicesManager servicesManager = null; 
    private IBatchKernelService batchKernel = null;
    private IPersistenceManagerService persistenceService = null;
    private IJobXMLLoaderService jobXMLLoaderService = null;
	
    public JobOperatorImpl() {
        servicesManager = ServicesManagerImpl.getInstance();
        batchKernel = servicesManager.getBatchKernelService();
        persistenceService = servicesManager.getPersistenceManagerService();
        jobXMLLoaderService =  servicesManager.getDelegatingJobXMLLoaderService();
    }
    
	@Override
	public long start(String jobXMLName, Properties submittedProps)	throws JobStartException {
	    
	    String jobXML = jobXMLLoaderService.loadJob(jobXMLName);
	    
		long executionId = 0;
        
        if (logger.isLoggable(Level.FINE)) {            
            int concatLen = jobXML.length() > 200 ? 200 : jobXML.length();
            logger.fine("Starting job: " + jobXML.substring(0, concatLen) + "... truncated ...");
        }
        
        JobExecution execution = batchKernel.startJob(jobXML, submittedProps);
        executionId = execution.getExecutionId();
        
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("JobOperator start received executionId: " + executionId);
        }

        return executionId;
	}


	@Override
	public void abandon(JobExecution jobExecution)
			throws NoSuchJobExecutionException, JobExecutionIsRunningException, SecurityException {
		// TODO Auto-generated method stub
		
		boolean abandoned = false;
		long executionId = jobExecution.getExecutionId();
		
		// get the job executions associated with the job instance
		//List<JobExecution> jobExecutions = persistenceService.jobOperatorGetJobExecutionsByJobInstanceID(instanceId);
		
		JobExecution jobEx = persistenceService.jobOperatorGetJobExecution(executionId);
		
		// if there are none found, throw exception saying so
		if (jobEx == null){
			throw new NoSuchJobInstanceException(null, "Job Execution: " + executionId + " not found");
		}
			
		if (isAuthorized(jobEx.getInstanceId())) {
			// for every job execution associated with the job
			// if it is not in STARTED state, mark it as ABANDONED
			//for (JobExecution jobEx : jobExecutions){
				if (!jobEx.getBatchStatus().equals(BatchStatus.STARTED) || !jobEx.getBatchStatus().equals(BatchStatus.STARTING)){
					// update table to reflect ABANDONED state
			        long time = System.currentTimeMillis();
			    	Timestamp timestamp = new Timestamp(time);
					persistenceService.jobOperatorUpdateBatchStatusWithSTATUSandUPDATETSonly(jobEx.getExecutionId(), "batchstatus", BatchStatus.ABANDONED.name(), timestamp);
				}
				else {
					// If one of the JobExecutions is still running, throw an exception
					throw new JobExecutionIsRunningException(null, "Job Execution: " + executionId + " is still running");
				}
			//}
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}
	}

	@Override
	public List<JobExecution> getExecutions(JobInstance instance)
			throws NoSuchJobInstanceException, SecurityException {
		
		List<JobExecution> executions = new ArrayList<JobExecution>();
		
		if (isAuthorized(instance.getInstanceId())) {
			executions = persistenceService.jobOperatorGetJobExecutionsByJobInstanceID(instance.getInstanceId());
			if (executions.size() == 0){
				throw new NoSuchJobInstanceException(null, "Job: " + instance.getJobName() + " has no executions");
			}
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}

		return executions;
	}

	@Override
	public JobExecution getJobExecution(long executionId)
			throws NoSuchJobExecutionException, SecurityException {
		
		JobExecution theJobExecution = null;
		JobExecution execution = persistenceService.jobOperatorGetJobExecution(executionId);
		if (execution == null){
			throw new NoSuchJobExecutionException(null, "No job execution exists for job execution id: " + executionId);
		}

		if(isAuthorized(execution.getInstanceId())) {
			theJobExecution = batchKernel.getJobExecution(executionId);
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}

		return theJobExecution;
	}

	@Override
	public List<JobExecution> getJobExecutions(JobInstance instance)
			throws NoSuchJobInstanceException, SecurityException {
		List<JobExecution> executions = new ArrayList<JobExecution>();
		
		if (isAuthorized(instance.getInstanceId())) {
			executions = persistenceService.jobOperatorGetJobExecutions(instance.getInstanceId());
			if (executions.size() == 0 ){
				throw new NoSuchJobInstanceException(null, "Job: " + instance.getJobName() + " does not exist");
			}
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}
	
		return executions;
	}

	@Override
	public JobInstance getJobInstance(long executionId)
			throws NoSuchJobExecutionException {
		// will have to look at t he persistence layer - 
		// this used to take in an instanceid, now takes 
		// in an executionId. Will have to adapt to that fact
		return this.batchKernel.getJobInstance(executionId);
	}

	@Override
	public int getJobInstanceCount(String jobName) throws NoSuchJobException {
				
    	int jobInstanceCount = 0;
    	
    	jobInstanceCount = persistenceService.jobOperatorGetJobInstanceCount(jobName);
    	
    	if (jobInstanceCount > 0) {
    		return jobInstanceCount;
    	}
    	else throw new NoSuchJobException(null, "Job " + jobName + " not found");
	}

	@Override
	public List<JobInstance> getJobInstances(String jobName, int start,
			int count) throws NoSuchJobException {
		
		List<JobInstance> jobInstances = new ArrayList<JobInstance>();
		
		// get the jobinstance ids associated with this job name
		List<Long> instanceIds = persistenceService.jobOperatorgetJobInstanceIds(jobName, start, count);
		
		if (instanceIds.size() > 0){
			// for every job instance id
			for (long id : instanceIds){
				// get the job instance obj, add it to the list
				JobInstance jobInstance = batchKernel.getJobInstance(id);
				if(isAuthorized(jobInstance.getInstanceId())) {
					jobInstances.add(jobInstance);	
				}
			}
			// send the list of objs back to caller
			return jobInstances;
		}
		else throw new NoSuchJobException(null, "Job Name " + jobName + " not found");
	}

	@Override
	public Set<String> getJobNames() {
		
		Set<String> jobNames = new HashSet<String>();
		HashMap data = persistenceService.jobOperatorGetJobInstanceData();
		Iterator it = data.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			long instanceId = (Long) entry.getKey();
			if(isAuthorized(instanceId)) {
				String name = (String)entry.getValue();
				jobNames.add(name);
			}
		}
		return jobNames;
	}

	@Override
	public Properties getParameters(JobInstance instance)
			throws NoSuchJobExecutionException, SecurityException{
		
		Properties props = null;
		
		if (isAuthorized(instance.getInstanceId())) {

			props = persistenceService.getParameters(instance.getInstanceId());
			if (props == null) {
				throw new NoSuchJobExecutionException(null, "");
			}
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}

		return props;
	}

	@Override
	public List<JobExecution> getRunningExecutions(String jobName)
			throws NoSuchJobException {
		
		List<JobExecution> jobExecutions = new ArrayList<JobExecution>();

		// get the jobexecution ids associated with this job name
		Set<Long> executionIds = persistenceService.jobOperatorGetRunningExecutions(jobName);
		
		if (executionIds.size() > 0){
			// for every job instance id
			for (long id : executionIds){
				
				JobExecution jobEx = batchKernel.getJobExecution(id);
				if(isAuthorized(jobEx.getInstanceId())) {
					// get the job instance obj, add it to the list
					jobExecutions.add(jobEx);	
				}
			}
			// send the list of objs back to caller
			return jobExecutions;
		}
		else throw new NoSuchJobException(null, "Job Name " + jobName + " not found");
	}

	@Override
	public List<StepExecution> getStepExecutions(long executionId)
			throws NoSuchJobExecutionException, SecurityException {
		
		List<StepExecution> stepExecutions = new ArrayList<StepExecution>();

		JobExecution jobEx = batchKernel.getJobExecution(executionId);
		if (isAuthorized(jobEx.getInstanceId())) {
			stepExecutions = persistenceService.getStepExecutionIDListQueryByJobID(executionId);
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}

		return stepExecutions;
		
	}

	@Override
	public long restart(long executionId)
			throws JobExecutionAlreadyCompleteException,
			NoSuchJobExecutionException, JobExecutionNotMostRecentException,
			JobRestartException, SecurityException {
		
		long newExecutionId = -1;
		
        if (logger.isLoggable(Level.FINE)) {            
            logger.fine("Restarting job with instanceID: " + executionId);
        }
        
		JobExecution jobEx = batchKernel.getJobExecution(executionId);
		if (isAuthorized(jobEx.getInstanceId())) {
			JobExecution execution = batchKernel.restartJob(executionId);
	        newExecutionId = execution.getExecutionId();	
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}
        
        if (logger.isLoggable(Level.FINE)) {            
            logger.fine("Restarted job with instanceID: " + executionId + ", and new executionID: " + newExecutionId);
        }
        
        return newExecutionId;
	}
	
    @Override
    public long restart(long executionId, Properties restartParameters) throws JobInstanceAlreadyCompleteException,
            NoSuchJobExecutionException, NoSuchJobException, JobRestartException, SecurityException {

    	long newExecutionId = -1;
    	
        if (logger.isLoggable(Level.FINE)) {            
            logger.fine("Restarting job with instanceID: " + executionId);
        }
        
        JobExecution jobEx = batchKernel.getJobExecution(executionId);
        if (isAuthorized(jobEx.getInstanceId())) {
            JobExecution execution = batchKernel.restartJob(executionId, restartParameters);
            newExecutionId = execution.getExecutionId();
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}
        
        if (logger.isLoggable(Level.FINE)) {            
            logger.fine("Restarted job with instanceID: " + executionId + ", and new executionID: " + newExecutionId);
        }
        
        return newExecutionId;
    }
	
	@Override
	public void stop(long executionId) throws NoSuchJobExecutionException,
			JobExecutionNotRunningException, SecurityException {
		
		JobExecution jobEx = batchKernel.getJobExecution(executionId);
		
		if (isAuthorized(jobEx.getInstanceId())) {
			batchKernel.stopJob(executionId);	
		} else {
			throw new SecurityException("The current user is not authorized to perform this operation");
		}
		
	}
		
	@Override
	public void purge(String apptag) {
		
		if (batchKernel.getBatchSecurityHelper().isAdmin(apptag)) {
			persistenceService.purge(apptag);
		}
	}

	private boolean isAuthorized(long instanceId) {
		
		String apptag = persistenceService.getJobCurrentTag(instanceId);
		BatchSecurityHelper bsh = batchKernel.getBatchSecurityHelper();
		if (bsh.isAdmin(apptag) || bsh.getCurrentTag().equals(apptag)) {
			return true;
		}
		return false;
	}
	
}