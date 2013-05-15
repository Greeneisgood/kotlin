/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.lazy.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.utils.WrappedValues;

import java.util.HashMap;
import java.util.Map;

public abstract class MemoizedFunctionToNullableImpl<K, V> implements MemoizedFunctionToNullable<K, V> {
    private final Map<K, Object> cache;

    public MemoizedFunctionToNullableImpl() {
        this(new HashMap<K, Object>());
    }

    public MemoizedFunctionToNullableImpl(@NotNull Map<K, Object> map) {
        this.cache = map;
    }

    @Nullable
    @Override
    public V fun(K input) {
        Object value = cache.get(input);
        if (value != null) return WrappedValues.unescapeNull(value);

        V typedValue = doCompute(input);

        Object oldValue = cache.put(input, WrappedValues.escapeNull(typedValue));
        assert oldValue == null : "Race condition detected";

        return typedValue;
    }

    @Nullable
    protected abstract V doCompute(K input);
}
