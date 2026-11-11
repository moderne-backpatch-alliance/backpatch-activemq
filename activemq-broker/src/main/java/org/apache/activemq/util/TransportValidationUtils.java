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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TransportValidationUtils {

    public static final Set<String> DENIED_TRANSPORT_SCHEMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("vm", "http", "https",
            "multicast", "zeroconf", "discovery", "fanout", "mock", "peer", "failover",
            "proxy", "reliable", "simple", "udp", "masterslave")));

    public static void validateAllowedUrl(String uriString) throws URISyntaxException {
        validateAllowedUri(new URI(uriString));
    }

    public static void validateAllowedUri(URI uri) throws URISyntaxException {
        validateAllowedUri(uri, 0);
    }

    // Validate the URI does not contain a denied transport scheme
    private static void validateAllowedUri(URI uri, int depth) throws URISyntaxException {
        // Don't allow more than 5 nested URIs to prevent blowing the stack
        if (depth > 5) {
            throw new IllegalArgumentException("URI can't contain more than 5 nested composite URIs");
        }

        // First check the main URI scheme
        validateAllowedScheme(uri.getScheme());

        // isCompositeURI misses nested URIs and URIs without parentheses, so parse as
        // the transports do and check each parsed component.
        final URISupport.CompositeData data;
        try {
            data = URISupport.parseComposite(uri);
        } catch (URISyntaxException e) {
            // If this is not a valid URI then we can stop checking
            // This can happen when parsing a nested URI and at the last portion
            return;
        }

        if (data.getComponents() != null) {
            depth++;
            for (URI component : data.getComponents()) {
                // Each URI could be a nested and/or composite URI so call validateAllowedUri()
                // to validate it. If the scheme is null then the original URI is not composite
                // or nested so we can skip the check, and we are finished.
                if (component.getScheme() != null) {
                    validateAllowedUri(component, depth);
                }
            }
        }
    }

    // Check all denied schemes
    private static void validateAllowedScheme(String scheme) {
        for (String denied : DENIED_TRANSPORT_SCHEMES) {
            // The schemes should be case-insensitive but ignore case as a precaution
            if (scheme.equalsIgnoreCase(denied)) {
                throw new IllegalArgumentException("Transport scheme '" + scheme + "' is not allowed");
            }
        }
    }
}
