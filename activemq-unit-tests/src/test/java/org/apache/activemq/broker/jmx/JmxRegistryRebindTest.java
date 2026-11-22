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
package org.apache.activemq.broker.jmx;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.ServerSocket;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;

import javax.management.remote.rmi.RMIServer;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The RMI registry the broker creates for its JMX connector must not let a
 * client replace or remove the connector's stub.
 */
public class JmxRegistryRebindTest {

    public interface Placeholder extends Remote {
    }

    public static class PlaceholderImpl implements Placeholder {
    }

    private BrokerService broker;
    private int connectorPort;

    @Before
    public void setUp() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        connectorPort = socket.getLocalPort();
        socket.close();

        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(true);
        broker.getManagementContext().setCreateConnector(true);
        broker.getManagementContext().setConnectorPort(connectorPort);
        broker.start();
        broker.waitUntilStarted();
    }

    @After
    public void tearDown() throws Exception {
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    @Test(timeout = 60000)
    public void testRebindDoesNotReplaceConnectorStub() throws Exception {
        final Registry registry = LocateRegistry.getRegistry("localhost", connectorPort);
        assertTrue("connector stub bound", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                try {
                    return registry.lookup("jmxrmi") instanceof RMIServer;
                } catch (Exception e) {
                    return false;
                }
            }
        }));

        PlaceholderImpl placeholder = new PlaceholderImpl();
        Remote placeholderStub = UnicastRemoteObject.exportObject(placeholder, 0);
        try {
            registry.rebind("jmxrmi", placeholderStub);
            Remote bound = registry.lookup("jmxrmi");
            assertNotNull(bound);
            assertTrue("jmxrmi still names the connector, got " + bound, bound instanceof RMIServer);

            registry.rebind("other", placeholderStub);
            assertFalse("other name was bound", Arrays.asList(registry.list()).contains("other"));

            try {
                registry.unbind("jmxrmi");
            } catch (Exception ignored) {
            }
            assertTrue("jmxrmi still bound after unbind", registry.lookup("jmxrmi") instanceof RMIServer);
        } finally {
            UnicastRemoteObject.unexportObject(placeholder, true);
        }
    }
}
