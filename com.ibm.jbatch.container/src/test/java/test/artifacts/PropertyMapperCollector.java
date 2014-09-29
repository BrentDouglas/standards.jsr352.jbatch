/*
 * Copyright 2014 International Business Machines Corp.
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
package test.artifacts;

import java.io.Serializable;

import javax.batch.api.partition.PartitionCollector;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;

public class PropertyMapperCollector implements PartitionCollector {

	@Inject
	StepContext stepCtx;
		
	@Override
	public Serializable collectPartitionData() throws Exception {
		String stepPropValue = stepCtx.getProperties().getProperty("stepProp");
		String data = (String) stepCtx.getPersistentUserData() + "?" + stepPropValue;
		
		return data;
	}

}
