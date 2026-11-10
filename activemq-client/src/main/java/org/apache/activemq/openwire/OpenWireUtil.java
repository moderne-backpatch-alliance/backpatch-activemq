/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.openwire;

import java.io.IOException;
import org.apache.activemq.util.IOExceptionSupport;

public class OpenWireUtil {

    /**
     * Verify that the provided class extends {@link Throwable} and throw an
     * {@link IllegalArgumentException} if it does not.
     *
     * @param clazz
     */
    public static void validateIsThrowable(Class<?> clazz) {
        if (!Throwable.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + clazz + " is not assignable to Throwable");
        }
    }

    public static void validateBufferSize(OpenWireFormat wireFormat, int size) throws IOException {
        validateLessThanFrameSize(wireFormat, size);

        // Also needed when no frame size is known, as with sizePrefixDisabled.
        if (size > wireFormat.getMaxFrameSize()) {
            throw IOExceptionSupport.createFrameSizeException(size,  wireFormat.getMaxFrameSize());
        }
    }

    private static void validateLessThanFrameSize(OpenWireFormat wireFormat, int size)
        throws IOException {
        final OpenWireFormat.MarshallingContext context = wireFormat.getMarshallingContext();
        if (context == null || context.getFrameSize() < 0) {
            return;
        }

        context.increment(size);

        if (context.getEstimatedAllocated() > context.getFrameSize()) {
            throw IOExceptionSupport.createFrameSizeBufferException(
                context.getEstimatedAllocated(), context.getFrameSize());
        }
    }
}
