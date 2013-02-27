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
package com.ibm.jbatch.container.jobinstance;

import java.io.Externalizable;
import java.io.Serializable;
import java.sql.Timestamp;

import javax.batch.api.PartitionPlan;
import javax.batch.operations.JobOperator.BatchStatus;
import javax.batch.runtime.Metric;
import javax.batch.runtime.StepExecution;

import com.ibm.jbatch.container.context.impl.MetricImpl;
import com.ibm.jbatch.container.context.impl.StepContextImpl;

public class StepExecutionImpl implements StepExecution, Serializable {

    
    private long commitCount = 0;
    private Timestamp endTime = null;
    private String exitStatus = null;
    private BatchStatus batchStatus = null;
    
    private long filterCount = 0;
    private long jobExecutionId = 0;
    private Timestamp lastUpdateTime = null;
    private long processSkipCount = 0;
    private long readCount = 0;
    private long readSkipCount = 0;
    private long rollbackCount = 0;
    private Timestamp startTime = null;
    private long stepExecutionId = 0;
    private String stepName = null;
    long ExecutionId = 0;

    private long writeCount = 0;
    private long writeSkipCount = 0;
    
    private PartitionPlan plan = null;
    
    private Externalizable persistentUserData = null;
    
    private StepContextImpl<?, ? extends Externalizable> stepContext = null;
    
    public StepExecutionImpl(long jobExecutionId, long stepExecutionId) {
    	this.jobExecutionId = jobExecutionId;
    	this.stepExecutionId = stepExecutionId;
    }
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public Timestamp getEndTime() {
        
    	if (stepContext != null){
    		return this.stepContext.getEndTimeTS();
    	}
    	else {
    		return endTime;
    	}
    }

    @Override
    public long getJobExecutionId(){
    	return this.jobExecutionId;
    }
    
    @Override
    public String getExitStatus() {
    	if (stepContext != null){
    		return this.stepContext.getExitStatus();
    	}
    	else {
    		return exitStatus;
    	}
    }

    @Override
    public Timestamp getStartTime() {
       	if (stepContext != null){
    		return this.stepContext.getStartTimeTS();
    	}
    	else {
    		return startTime;
    	}
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
		buf.append("---------------------------------------------------------------------------------");
		//buf.append("getStepName(): " + this.getStepName() + "\n");
		buf.append("getJobExecutionId(): " + this.getJobExecutionId() + "\n");
		//buf.append("getStepExecutionId(): " + this.getStepExecutionId() + "\n");			
		//buf.append("getCommitCount(): " + this.getCommitCount() + "\n");
		//buf.append("getFilterCount(): " + this.getFilterCount() + "\n");
		//buf.append("getProcessSkipCount(): " + this.getProcessSkipCount() + "\n");
		//buf.append("getReadCount(): " + this.getReadCount() + "\n");
		//buf.append("getReadSkipCount(): " + this.getReadSkipCount() + "\n");
		//buf.append("getRollbackCount(): " + this.getRollbackCount() + "\n");
		//buf.append("getWriteCount(): " + this.getWriteCount() + "\n");
		//buf.append("getWriteSkipCount(): " + this.getWriteSkipCount() + "\n");
		buf.append("getStartTime(): " + this.getStartTime() + "\n");
		buf.append("getEndTime(): " + this.getEndTime() + "\n");
		//buf.append("getLastUpdateTime(): " + this.getLastUpdateTime() + "\n");
		buf.append("getBatchStatus(): " + this.getBatchStatus().name() + "\n");
		buf.append("getExitStatus(): " + this.getExitStatus());
		buf.append("---------------------------------------------------------------------------------");
        return buf.toString();
    }

	@Override
	public Metric[] getMetrics() {
		
		
		if (stepContext != null){
			return stepContext.getMetrics();
		}
		else {
			Metric[] metrics = new MetricImpl[8];
			metrics[0] = new MetricImpl(MetricImpl.MetricName.READCOUNT, readCount);
			metrics[1] = new MetricImpl(MetricImpl.MetricName.WRITECOUNT, writeCount);
			metrics[2] = new MetricImpl(MetricImpl.MetricName.COMMITCOUNT, commitCount);
			metrics[3] = new MetricImpl(MetricImpl.MetricName.ROLLBACKCOUNT, rollbackCount);
			metrics[4] = new MetricImpl(MetricImpl.MetricName.READSKIPCOUNT, readSkipCount);
			metrics[5] = new MetricImpl(MetricImpl.MetricName.PROCESSSKIPCOUNT, processSkipCount);
			metrics[6] = new MetricImpl(MetricImpl.MetricName.FILTERCOUNT, filterCount);
			metrics[7] = new MetricImpl(MetricImpl.MetricName.WRITESKIPCOUNT, writeSkipCount);
			
			return metrics;
		}
	}

	@Override
	public BatchStatus getBatchStatus() {
		
		if (stepContext != null){
			return this.stepContext.getBatchStatus();
		}
		else {
			return batchStatus;
		}
	}

	@Override
	public Externalizable getUserPersistentData() {
		if (stepContext != null){
			return this.stepContext.getPersistentUserData();
		}
		else {
			return this.persistentUserData;
		}
	}
    
	
	// impl specific setters
    public void setFilterCount(long filterCnt) {
        this.filterCount = filterCnt;
    }

    public void setLastUpdateTime(Timestamp lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setProcessSkipCount(long processSkipCnt) {
        this.processSkipCount = processSkipCnt;
    }

    public void setReadCount(long readCnt) {
        this.readCount = readCnt;
    }

    public void setReadSkipCount(long readSkipCnt) {
        this.readSkipCount = readSkipCnt;
    }

    public void setRollbackCount(long rollbackCnt) {
        this.rollbackCount = rollbackCnt;
    }
    
    public void setJobExecutionId(long jobexecID){
    	this.jobExecutionId = jobexecID;
    }
    
    public void setStepExecutionId(long stepexecID){
    	this.ExecutionId = stepexecID;
    }
        
    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public void setWriteCount(long writeCnt) {     
        this.writeCount = writeCnt;
    }

    public void setWriteSkipCount(long writeSkipCnt) {        
        this.writeSkipCount = writeSkipCnt;
    }  
    
    public <T> void setStepContext(StepContextImpl<?, ? extends Externalizable> stepContext) {
        this.stepContext = stepContext;
    }
    
    public void setCommitCount(long commitCnt) {
        this.commitCount = commitCnt;
    }
    
    public void setBatchStatus(BatchStatus batchstatus){
    	this.batchStatus = batchstatus;
    }
    
    public void setExitStatus(String exitstatus){
    	this.exitStatus = exitstatus;
    }
    
    public void setStartTime(Timestamp startts){
    	this.startTime = startts;
    }
    
    public void setEndTime(Timestamp endts){
    	this.endTime = endts;
    }
    
    public void setpersistentUserData(Externalizable data){
    	this.persistentUserData = data;
    }

	@Override
	public String getStepId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
    	if (stepContext != null){
    		return this.stepContext.getId();
    	}
    	else {
    		return stepName;
    	}
	}

	public void setPlan(PartitionPlan plan) {
		this.plan = plan;
	}

	public PartitionPlan getPlan() {
		return plan;
	}
}
