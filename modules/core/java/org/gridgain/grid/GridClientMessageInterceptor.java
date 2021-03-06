/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;

/**
 * Interface for user-defined object interceptors.
 * <p>
 * Interceptors allow user to transform objects send and received via REST protocols.
 * For example they could be used for customized multi-language marshalling by
 * converting binary object representation received from client to java object.
 *
 * @author @java.author
 * @version @java.version
 */
public interface GridClientMessageInterceptor {
    /**
     * Intercepts received objects.
     *
     * @param obj Original incoming object.
     * @return Object which should replace original in later processing.
     */
    @Nullable public Object onReceive(@Nullable Object obj);

    /**
     * Intercepts received objects.
     *
     * @param obj Original incoming object.
     * @return Object which should be send to remote client instead of original.
     */
    @Nullable public Object onSend(Object obj);
}
