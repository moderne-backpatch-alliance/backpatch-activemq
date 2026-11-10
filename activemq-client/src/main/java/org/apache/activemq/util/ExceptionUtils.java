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
package org.apache.activemq.util;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;

import java.util.ArrayList;
import java.util.List;
import org.apache.activemq.ActiveMQMessageFormatException;

public class ExceptionUtils {


    public static ActiveMQMessageFormatException createMessageFormatException(Throwable error) {
        if (error instanceof ActiveMQMessageFormatException) {
            return (ActiveMQMessageFormatException) error;
        } else if (containsMessageFormatError(error)) {
            return new ActiveMQMessageFormatException(error);
        }
        return null;
    }

    private static boolean containsMessageFormatError(Throwable error) {
        if (error == null) {
            return false;
        }

        Throwable cause = ExceptionUtils.getRootCause(error);
        return isMessageFormatError(cause) ||
                error instanceof JMSException && isMessageFormatError(((JMSException) error).getLinkedException());
    }

    private static boolean isMessageFormatError(Throwable error) {
        if (error == null) {
            return false;
        }

        return error instanceof MarshallingSupport.ActiveMQUnmarshalEOFException ||
                error instanceof MessageFormatException ||
                error instanceof MessageEOFException;
    }

    public static Throwable getRootCause(final Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        final List<Throwable> list = getThrowableList(throwable);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    static List<Throwable> getThrowableList(Throwable throwable) {
        final List<Throwable> list = new ArrayList<>();
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable);
            throwable = throwable.getCause();
        }
        return list;
    }
}
