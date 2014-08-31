/*
 * Copyright 2012,2013 International Business Machines Corp.
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
package com.ibm.jbatch.tck.utils;

import javax.batch.runtime.JobExecution;

public class AssertionUtils {

    public static void assertWithMessage(final String message, final Object expected, final Object actual) {
        assertWithMessage(null, message, expected, actual);
    }

    public static void assertWithMessage(final String message, final boolean result) {
        assertWithMessage(null, message, result);
    }

    public static void assertWithMessage(final String message, final int expected, final int actual) {
        assertWithMessage(null, message, expected, actual);
    }

    public static void assertWithMessage(final JobExecution ex, final Object expected, final Object actual) {
        assertWithMessage(ex, null, expected, actual);
    }

    public static void assertWithMessage(final JobExecution ex, final String message, final Object expected, final Object actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null) {
            if (message == null)
                throw new AssertionError("Expected 'null' but found value: " + actual + _je(ex));
            else
                throw new AssertionError(message + "; Expected 'null' but found value: " + actual + _je(ex));
        } else if (!expected.equals(actual)) {
            if (message == null)
                throw new AssertionError("Expected value: " + expected + ", but found value: " + actual + _je(ex));
            else
                throw new AssertionError(message + "; Expected value: " + expected + ", but found value: " + actual + _je(ex));
        }
    }

    public static void assertWithMessage(final JobExecution ex, final String message, final boolean result) {
        if (!result) {
            if (message == null)
                throw new AssertionError(_je(ex));
            else
                throw new AssertionError(message + _je(ex));
        }
    }

    public static void assertWithMessage(final JobExecution ex, final String message, final int expected, final int actual) {
        boolean result = (expected == actual);

        if (!result) {
            if (message == null)
                throw new AssertionError("Expected value: " + expected + ", but found value: " + actual + _je(ex));
            else
                throw new AssertionError(message + "; Expected value: " + expected + ", but found value: " + actual + _je(ex));
        }
    }

    private static String _je(final JobExecution ex) {
        return ex == null ? "" : "\nJobExecution {" +
                "jobExecutionId=" + ex.getExecutionId() +
                ", jobName='" + ex.getJobName() +
                "', batchStatus=" + ex.getBatchStatus() +
                ", exitStatus='" + ex.getExitStatus() +
                "', parameters=" + ex.getJobParameters() +
                '}';
    }
}
