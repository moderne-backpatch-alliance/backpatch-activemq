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
package org.apache.activemq.network;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;
import org.apache.activemq.broker.BrokerService;
import org.junit.Test;

public class LdapNetworkConnectorTest {

    // A copy of TransportValidationUtils.DENIED_TRANSPORT_SCHEMES, kept here so this test also
    // compiles and runs against a broker that does not have it yet.
    private static final Set<String> DENIED_TRANSPORT_SCHEMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("vm", "http",
            "multicast", "zeroconf", "discovery", "fanout", "mock", "peer", "failover",
            "proxy", "reliable", "simple", "udp", "masterslave")));

    @Test
    public void testValidation() throws Exception {
        LdapNetworkConnector connector = new LdapNetworkConnector();
        connector.setBrokerService(new BrokerService());

        for (String deniedScheme : DENIED_TRANSPORT_SCHEMES) {
            try {
                connector.addConnector(newSearchResult(deniedScheme, "localhost"));
                fail("Should have failed trying to add connector with scheme: " + deniedScheme);
            } catch (IllegalArgumentException e) {
                assertEquals("Transport scheme '" + deniedScheme + "' is not allowed", e.getMessage());
            }
        }
    }

    @Test
    public void testCompositeValidation() throws Exception {
        LdapNetworkConnector connector = new LdapNetworkConnector();
        connector.setBrokerService(new BrokerService());

        for (String deniedScheme : DENIED_TRANSPORT_SCHEMES) {
            try {
                connector.addConnector(newSearchResult("tcp", "static:(tcp://localhost," +
                                deniedScheme + "://localhost)"));
                fail("Should have failed trying to add connector with scheme: " + deniedScheme);
            } catch (IllegalArgumentException e) {
                assertEquals("Transport scheme '" + deniedScheme + "' is not allowed", e.getMessage());
            }
        }
    }

    private SearchResult newSearchResult(String protocol, String host) {
        BasicAttributes attrs = new BasicAttributes();
        attrs.put("ipserviceprotocol", protocol);
        attrs.put("iphostnumber", host);
        attrs.put("ipserviceport", "0");
        SearchResult result = new SearchResult("name", null, attrs);
        result.setNameInNamespace("fullname");
        return result;
    }

}
