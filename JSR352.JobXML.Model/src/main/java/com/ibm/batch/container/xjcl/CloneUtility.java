/**
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
package com.ibm.batch.container.xjcl;

import java.util.List;
import java.util.Properties;

import jsr352.batch.jsl.Batchlet;
import jsr352.batch.jsl.CheckpointAlgorithm;
import jsr352.batch.jsl.Chunk;
import jsr352.batch.jsl.End;
import jsr352.batch.jsl.ExceptionClassFilter;
import jsr352.batch.jsl.Fail;
import jsr352.batch.jsl.ItemProcessor;
import jsr352.batch.jsl.ItemReader;
import jsr352.batch.jsl.ItemWriter;
import jsr352.batch.jsl.JSLProperties;
import jsr352.batch.jsl.Listener;
import jsr352.batch.jsl.Listeners;
import jsr352.batch.jsl.Next;
import jsr352.batch.jsl.ObjectFactory;
import jsr352.batch.jsl.Property;
import jsr352.batch.jsl.Stop;

public class CloneUtility {
    
    private static  ObjectFactory jslFactory = new ObjectFactory();

    public static Batchlet cloneBatchlet(Batchlet batchlet){
    	Batchlet newBatchlet = jslFactory.createBatchlet();
    	
    	newBatchlet.setRef(batchlet.getRef());
    	newBatchlet.setProperties(cloneJSLProperties(batchlet.getProperties()));
    	
    	return newBatchlet;
    }
    
    public static JSLProperties cloneJSLProperties(JSLProperties jslProps) {
    	if (jslProps == null) {
    		return null;
    	}

    	
    	JSLProperties newJSLProps = jslFactory.createJSLProperties();
    	
    	newJSLProps.setPartition(jslProps.getPartition());;
    	
    	for(Property jslProp : jslProps.getPropertyList()) {
    		Property newProperty = jslFactory.createProperty();
    		
    		newProperty.setName(jslProp.getName());
    		newProperty.setValue(jslProp.getValue());
    		
    		newJSLProps.getPropertyList().add(newProperty);
    	}

    	return newJSLProps;
    }

    public static void cloneControlElements(List<ControlElement> controlElements, List<ControlElement> newControlElements) {
        
        newControlElements.clear();
        
        for (ControlElement controlElement : controlElements) {
            if (controlElement instanceof End){
                End endElement = (End)controlElement;
                End newEnd = jslFactory.createEnd();
                newEnd.setExitStatus(endElement.getExitStatus());
                newEnd.setOn(endElement.getOn());
                
                newControlElements.add(newEnd);
            }   
            else if (controlElement instanceof Fail){
                Fail failElement = (Fail)controlElement;
                Fail newFail = jslFactory.createFail();
                newFail.setExitStatus(failElement.getExitStatus());
                newFail.setOn(failElement.getOn());
                
                newControlElements.add(newFail);
            }
            else if (controlElement instanceof Next){
                Next nextElement = (Next)controlElement;
                Next newNext = jslFactory.createNext();
                newNext.setOn(nextElement.getOn());
                newNext.setTo(nextElement.getTo());
                
                newControlElements.add(newNext);
            }
            
            else if (controlElement instanceof Stop){
                Stop stopElement = (Stop)controlElement;
                Stop newStop = jslFactory.createStop();
                newStop.setExitStatus(stopElement.getExitStatus());
                newStop.setOn(stopElement.getOn());
                newStop.setRestart(stopElement.getRestart());
                
                newControlElements.add(newStop);
            }
        }
        
        
    }
    
    public static Listeners cloneListeners(Listeners listeners) {
    	if (listeners == null) {
    		return null;
    	}
    	
    	Listeners newListeners = jslFactory.createListeners();
    	
    	for(Listener listener : listeners.getListenerList()) {
    		Listener newListener = jslFactory.createListener();
    		
    		newListener.setRef(listener.getRef());
    		newListener.setProperties(cloneJSLProperties(listener.getProperties()));
    	}

    	return newListeners;
    }
    
    public static Chunk cloneChunk(Chunk chunk) {
    	Chunk newChunk = jslFactory.createChunk();
    	
        newChunk.setItemCount(chunk.getItemCount());
        newChunk.setRetryLimit(chunk.getRetryLimit());
        newChunk.setSkipLimit(chunk.getSkipLimit());
        newChunk.setTimeLimit(chunk.getTimeLimit());
        newChunk.setCheckpointPolicy(chunk.getCheckpointPolicy());

        newChunk.setCheckpointAlgorithm(cloneCheckpointAlorithm(chunk.getCheckpointAlgorithm()));
    	newChunk.setProcessor(cloneItemProcessor(chunk.getProcessor()));
    	newChunk.setReader(cloneItemReader(chunk.getReader()));
    	newChunk.setWriter(cloneItemWriter(chunk.getWriter()));
    	newChunk.setNoRollbackExceptionClasses(cloneExceptionClassFilter(chunk.getNoRollbackExceptionClasses()));
    	newChunk.setRetryableExceptionClasses(cloneExceptionClassFilter(chunk.getRetryableExceptionClasses()));
    	newChunk.setSkippableExceptionClasses(cloneExceptionClassFilter(chunk.getSkippableExceptionClasses()));
    	
    	return newChunk;
    }
	
    private static CheckpointAlgorithm cloneCheckpointAlorithm(CheckpointAlgorithm checkpointAlgorithm){
        if (checkpointAlgorithm == null) {
            return null;
        }
        
        CheckpointAlgorithm newCheckpointAlgorithm = jslFactory.createCheckpointAlgorithm();
        newCheckpointAlgorithm.setRef(checkpointAlgorithm.getRef());
        newCheckpointAlgorithm.setProperties(cloneJSLProperties(checkpointAlgorithm.getProperties()));
        
        return newCheckpointAlgorithm;
        
    }
        
    private static ItemProcessor cloneItemProcessor(ItemProcessor itemProcessor) {
        if (itemProcessor == null) {
            return null;
        }
        
        ItemProcessor newItemProcessor = jslFactory.createItemProcessor();
        newItemProcessor.setRef(itemProcessor.getRef());
        newItemProcessor.setProperties(cloneJSLProperties(itemProcessor.getProperties()));
        
        return newItemProcessor;
    }

    private static ItemReader cloneItemReader(ItemReader itemReader) {
        if (itemReader == null) {
            return null;
        }
        
        ItemReader newItemReader = jslFactory.createItemReader();
        newItemReader.setRef(itemReader.getRef());
        newItemReader.setProperties(cloneJSLProperties(itemReader.getProperties()));
        
        return newItemReader;
    }
    
    private static ItemWriter cloneItemWriter(ItemWriter itemWriter) {
        ItemWriter newItemWriter = jslFactory.createItemWriter();
        newItemWriter.setRef(itemWriter.getRef());
        newItemWriter.setProperties(cloneJSLProperties(itemWriter.getProperties()));
        
        return newItemWriter;
    }
    
    private static ExceptionClassFilter cloneExceptionClassFilter(ExceptionClassFilter exceptionClassFilter) {

        if (exceptionClassFilter == null) {
            return null;
        }
        
        ExceptionClassFilter newExceptionClassFilter = jslFactory.createExceptionClassFilter();
        
        newExceptionClassFilter.setInclude(cloneExceptionClassFilterInclude(newExceptionClassFilter.getInclude()));
        newExceptionClassFilter.setExclude(cloneExceptionClassFilterExclude(newExceptionClassFilter.getExclude()));
        
        return newExceptionClassFilter;
        
    }
    
    private static ExceptionClassFilter.Include cloneExceptionClassFilterInclude(ExceptionClassFilter.Include include) {
        if (include == null) {
            return null;
        }
        
        ExceptionClassFilter.Include newInclude = jslFactory.createExceptionClassFilterInclude();
        
        newInclude.setClazz(include.getClazz());
        
        return newInclude;
        
    }
    
    private static ExceptionClassFilter.Exclude cloneExceptionClassFilterExclude(ExceptionClassFilter.Exclude exclude) {
        
        if (exclude == null) {
            return null;
        }
        
        ExceptionClassFilter.Exclude newExclude = jslFactory.createExceptionClassFilterExclude();
        
        newExclude.setClazz(exclude.getClazz());
        
        return newExclude;
                
    }
    
    
    /**
     * Creates a java.util.Properties map from a jsr352.batch.jsl.Properties
     * object.
     * 
     * @param xmlProperties
     * @return
     */
    public static Properties jslPropertiesToJavaProperties(
            final JSLProperties xmlProperties) {

        final Properties props = new Properties();

        for (final Property prop : xmlProperties.getPropertyList()) {
            props.setProperty(prop.getName(), prop.getValue());
        }

        return props;

    }
}
