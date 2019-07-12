/*
 * Copyright 2014 Brandon Arp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.tsdcore.sinks;

import com.arpnetworking.tsdcore.model.PeriodicData;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface to describe a class that publishes <code>PeriodicData</code>.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
public interface Sink {

    /**
     * Called when an additional <code>PeriodicData</code> instance is
     * available for publication.
     *
     * @param data The <code>PeriodicData</code> to be published.
     */
    void recordAggregateData(PeriodicData data);

    /**
     * Called to allow the publisher to clean-up. No further calls to
     * recordAggregation will be made after a call to close.
     */
    void close();
}
