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

package org.gridgain.grid.lang;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.util.lang.*;

/**
 * Defines a predicate which accepts a parameter and returns {@code true} or {@code false}. In
 * GridGain, predicates are generally used for filtering nodes within grid projections, or for
 * providing atomic filters when performing cache operation, like in
 * {@link GridCache#put(Object, Object, GridPredicate[])} method.
 *
 * @param <E> Type of predicate parameter.
 */
public abstract class GridPredicate<E> extends GridLambdaAdapter {
    /**
     * Predicate body.
     *
     * @param e Predicate parameter.
     * @return Return value.
     */
    public abstract boolean apply(E e);
}
