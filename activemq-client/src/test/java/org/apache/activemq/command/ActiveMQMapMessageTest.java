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

package org.apache.activemq.command;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ActiveMQMapMessageTest {

    @Test
    public void nestedMapsAndListsPreserveValuesAndStringTypes() throws Exception {
        Map<String, Object> leaf = new HashMap<>();
        leaf.put("leaf", "value");
        Map<String, Object> inner = new HashMap<>();
        List<Object> list = Arrays.<Object>asList("inner", leaf);
        inner.put("list", list);
        Map<String, Object> nested = new HashMap<>();
        nested.put("text", "Ω中");
        nested.put("map", inner);
        nested.put("emptyMap", Collections.emptyMap());
        nested.put("emptyList", Collections.emptyList());
        ActiveMQMapMessage message = new ActiveMQMapMessage();
        message.setObject("nested", nested);
        message.storeContentAndClear();
        message.setReadOnlyBody(true);

        assertEquals(nested, message.getObject("nested"));
        assertEquals(nested, message.getContentMap().get("nested"));
    }
}
