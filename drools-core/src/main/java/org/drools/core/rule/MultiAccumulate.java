/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.core.rule;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;

import org.drools.core.WorkingMemory;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.AccumulateNode.AccumulateContextEntry;
import org.drools.core.reteoo.AccumulateNode.GroupByContext;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.spi.Accumulator;
import org.drools.core.spi.MvelAccumulator;
import org.drools.core.spi.Tuple;
import org.drools.core.spi.Wireable;
import org.drools.core.util.index.TupleList;
import org.kie.internal.security.KiePolicyHelper;

public class MultiAccumulate extends Accumulate {
    private Accumulator[] accumulators;

    public MultiAccumulate() { }

    public MultiAccumulate(final RuleConditionElement source,
                           final Declaration[] requiredDeclarations,
                           final Accumulator[] accumulators ) {
        super(source, requiredDeclarations);
        this.accumulators = accumulators;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {
        super.readExternal(in);
        this.accumulators = new Accumulator[in.readInt()];
        for ( int i = 0; i < this.accumulators.length; i++ ) {
            this.accumulators[i] = (Accumulator) in.readObject();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeInt( accumulators.length );
        for (Accumulator acc : accumulators) {
            if (Accumulator.isCompiledInvoker(acc)) {
                out.writeObject(null);
            } else {
                out.writeObject(acc);
            }
        }
    }

    public boolean isMultiFunction() {
        return true;
    }

    public Accumulator[] getAccumulators() {
        return this.accumulators;
    }

    public Serializable[] createFunctionContext() {
        Serializable[] ctxs = new Serializable[this.accumulators.length];
        for ( int i = 0; i < ctxs.length; i++ ) {
            ctxs[i] = this.accumulators[i].createContext();
        }
        return ctxs;
    }

    public void init(final Object workingMemoryContext,
                     final Object context,
                     final Tuple leftTuple,
                     final WorkingMemory workingMemory) {
        try {
            for ( int i = 0; i < this.accumulators.length; i++ ) {
                Object[] functionContext = (Object[]) ((AccumulateContextEntry)context).getFunctionContext();
                this.accumulators[i].init( ((Object[])workingMemoryContext)[i],
                                           functionContext[i],
                                           leftTuple,
                                           this.requiredDeclarations,
                                           workingMemory );
            }
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
    }

    public Object accumulate(final Object workingMemoryContext,
                             final Object context,
                             final Tuple match,
                             final InternalFactHandle handle,
                             final WorkingMemory workingMemory) {
        try {
            Object[] values = new Object[accumulators.length];
            for ( int i = 0; i < this.accumulators.length; i++ ) {
                Object[] functionContext = (Object[]) ((AccumulateContextEntry)context).getFunctionContext();
                values[i] = this.accumulators[i].accumulate( ((Object[])workingMemoryContext)[i],
                                                             functionContext[i],
                                                             match,
                                                             handle,
                                                             this.requiredDeclarations,
                                                             getInnerDeclarationCache(),
                                                             workingMemory );
            }
            return values;
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
    }

    @Override
    public Object accumulate(Object workingMemoryContext, LeftTuple match, InternalFactHandle childHandle,
                             GroupByContext groupByContext, TupleList<AccumulateContextEntry> tupleList, WorkingMemory wm) {
        throw new UnsupportedOperationException("This should never be called, it's for LambdaGroupByAccumulate only.");
    }

    @Override
    public boolean tryReverse(final Object workingMemoryContext,
                              final Object context,
                              final Tuple leftTuple,
                              final InternalFactHandle handle,
                              final RightTuple rightParent,
                              final LeftTuple match,
                              final WorkingMemory workingMemory) {
        try {
            Object[] values = (Object[]) match.getContextObject();
            for ( int i = 0; i < this.accumulators.length; i++ ) {
                Object[] functionContext = (Object[]) ((AccumulateContextEntry)context).getFunctionContext();
                boolean reversed = this.accumulators[i].tryReverse( ((Object[])workingMemoryContext)[i],
                                                                    functionContext[i],
                                                                    leftTuple,
                                                                    handle,
                                                                    values[i],
                                                                    this.requiredDeclarations,
                                                                    getInnerDeclarationCache(),
                                                                    workingMemory );
                if (!reversed) {
                    return false;
                }
            }
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }

        return true;
    }

    public boolean supportsReverse() {
        for ( Accumulator acc : this.accumulators ) {
            if ( ! acc.supportsReverse() ) {
                return false;
            }
        }
        return true;
    }

    public Object[] getResult(final Object workingMemoryContext,
                              final Object context,
                              final Tuple leftTuple,
                              final WorkingMemory workingMemory) {
        try {
            Object[] results = new Object[this.accumulators.length];
            for ( int i = 0; i < this.accumulators.length; i++ ) {
                Object[] functionContext = (Object[]) ((AccumulateContextEntry)context).getFunctionContext();
                results[i] = this.accumulators[i].getResult( ((Object[])workingMemoryContext)[i],
                                                             functionContext[i],
                                                             leftTuple,
                                                             this.requiredDeclarations,
                                                             workingMemory );
            }
            return results;
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
    }

    public void replaceAccumulatorDeclaration(Declaration declaration, Declaration resolved) {
        for (Accumulator accumulator : accumulators) {
            if ( accumulator instanceof MvelAccumulator ) {
                ( (MvelAccumulator) accumulator ).replaceDeclaration( declaration, resolved );
            }
        }
    }

    public MultiAccumulate clone() {
        RuleConditionElement clonedSource = source instanceof GroupElement ? ((GroupElement) source).cloneOnlyGroup() : source.clone();
        MultiAccumulate clone = new MultiAccumulate( clonedSource,
                                                     this.requiredDeclarations,
                                                     this.accumulators );
        registerClone(clone);
        return clone;
    }

    public Object[] createWorkingMemoryContext() {
        Object[] ctx = new Object[ this.accumulators.length ];
        for( int i = 0; i < this.accumulators.length; i++ ) {
            ctx[i] = this.accumulators[i].createWorkingMemoryContext();
        }
        return ctx;
    }

    public final class Wirer implements Wireable.Immutable, Serializable {
        private static final long serialVersionUID = -9072646735174734614L;

        private transient boolean initialized;

        private final int index;

        public Wirer( int index ) {
            this.index = index;
        }

        public void wire( Object object ) {
            Accumulator accumulator = KiePolicyHelper.isPolicyEnabled() ? new Accumulator.SafeAccumulator((Accumulator) object) : (Accumulator) object;
            accumulators[index] = accumulator;
            for ( Accumulate clone : cloned ) {
                ((MultiAccumulate)clone).accumulators[index] = accumulator;
            }
            initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(accumulators);
        result = prime * result + Arrays.hashCode( requiredDeclarations );
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    public boolean equals(Object obj) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass() != obj.getClass() ) return false;
        MultiAccumulate other = (MultiAccumulate) obj;
        if ( !Arrays.equals( accumulators, other.accumulators ) ) return false;
        if ( !Arrays.equals( requiredDeclarations, other.requiredDeclarations ) ) return false;
        if ( source == null ) {
            if ( other.source != null ) return false;
        } else if ( !source.equals( other.source ) ) return false;
        return true;
    }
}
