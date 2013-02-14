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
package com.ibm.batch.container.artifact.proxy;

import javax.batch.api.Batchlet;
import javax.batch.api.CheckpointAlgorithm;
import javax.batch.api.Decider;
import javax.batch.api.ItemProcessor;
import javax.batch.api.ItemReader;
import javax.batch.api.ItemWriter;
import javax.batch.api.PartitionAnalyzer;
import javax.batch.api.PartitionCollector;
import javax.batch.api.PartitionMapper;
import javax.batch.api.PartitionReducer;

import com.ibm.batch.container.services.IBatchArtifactFactory;
import com.ibm.batch.container.services.ServicesManager;
import com.ibm.batch.container.services.ServicesManager.ServiceType;
import com.ibm.batch.container.validation.ArtifactValidationException;

/*
 * Introduce a level of indirection so proxies are not instantiated directly by newing them up.
 */
public class ProxyFactory {

    protected static ServicesManager servicesManager = ServicesManager.getInstance();

    private static ThreadLocal<InjectionReferences> injectionContext = new ThreadLocal<InjectionReferences>();
    
    protected static IBatchArtifactFactory batchArtifactFactory = 
        (IBatchArtifactFactory) servicesManager.getService(ServiceType.DELEGATING_ARTIFACT_FACTORY_SERVICE);

    protected static Object loadArtifact(String id, InjectionReferences injectionReferences) {
        injectionContext.set(injectionReferences);
        
        Object loadedArtifact = null;
        try {
            loadedArtifact = batchArtifactFactory.load(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loadedArtifact;
    }
    
    public static InjectionReferences getInjectionReferences() {
        return injectionContext.get();
    }
    
    /*
     * Decider
     */
    public static DeciderProxy createDeciderProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        Decider loadedArtifact = (Decider)loadArtifact(id, injectionRefs);
        DeciderProxy proxy = new DeciderProxy(loadedArtifact);

        return proxy;
    }

    /*
     * Batchlet artifact
     */
    public static BatchletProxy createBatchletProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        Batchlet loadedArtifact = (Batchlet)loadArtifact(id, injectionRefs);
        BatchletProxy proxy = new BatchletProxy(loadedArtifact);

        return proxy;
    }
    
    /*
     * The four main chunk-related artifacts
     */    
    
    public static CheckpointAlgorithmProxy createCheckpointAlgorithmProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        CheckpointAlgorithm loadedArtifact = (CheckpointAlgorithm)loadArtifact(id, injectionRefs);
        CheckpointAlgorithmProxy proxy = new CheckpointAlgorithmProxy(loadedArtifact);

        return proxy;
    }
    
    public static ItemReaderProxy createItemReaderProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        ItemReader loadedArtifact = (ItemReader)loadArtifact(id, injectionRefs);
        ItemReaderProxy proxy = new ItemReaderProxy(loadedArtifact);

        return proxy;
    }
    
    public static ItemProcessorProxy createItemProcessorProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        ItemProcessor loadedArtifact = (ItemProcessor)loadArtifact(id, injectionRefs);
        ItemProcessorProxy proxy = new ItemProcessorProxy(loadedArtifact);

        return proxy;
    }
    
    public static ItemWriterProxy createItemWriterProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        ItemWriter loadedArtifact = (ItemWriter)loadArtifact(id, injectionRefs);
        return new ItemWriterProxy(loadedArtifact);
    }
        
    /*
     * The four partition-related artifacts
     */
    
    public static PartitionReducerProxy createPartitionReducerProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        PartitionReducer loadedArtifact = (PartitionReducer)loadArtifact(id, injectionRefs);
        PartitionReducerProxy proxy = new PartitionReducerProxy(loadedArtifact);

        return proxy;
    }
    
    public static PartitionMapperProxy createPartitionMapperProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        PartitionMapper loadedArtifact = (PartitionMapper)loadArtifact(id, injectionRefs);
        PartitionMapperProxy proxy = new PartitionMapperProxy(loadedArtifact);

        return proxy;
    }
    
    public static PartitionAnalyzerProxy createPartitionAnalyzerProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        PartitionAnalyzer loadedArtifact = (PartitionAnalyzer)loadArtifact(id, injectionRefs);
        PartitionAnalyzerProxy proxy = new PartitionAnalyzerProxy(loadedArtifact);

        return proxy;
    }
    
    public static PartitionCollectorProxy createPartitionCollectorProxy(String id, InjectionReferences injectionRefs) throws ArtifactValidationException {
        PartitionCollector loadedArtifact = (PartitionCollector)loadArtifact(id, injectionRefs);
        PartitionCollectorProxy proxy = new PartitionCollectorProxy(loadedArtifact);

        return proxy;
    }
}
