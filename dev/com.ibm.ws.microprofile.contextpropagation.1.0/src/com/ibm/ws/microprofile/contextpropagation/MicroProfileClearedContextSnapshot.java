/*******************************************************************************
 * Copyright (c) 2018,2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.contextpropagation;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.context.spi.ThreadContextController;
import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.microprofile.context.EmptyHandleListContextProvider;
import com.ibm.ws.microprofile.context.ThreadIdentityContextProvider;
import com.ibm.ws.microprofile.context.WLMContextProvider;

/**
 * Multi-context snapshot that clears MicroProfile context types when server config is used.
 */
@Trivial // this class does its own tracing when clearing and restoring context
public class MicroProfileClearedContextSnapshot implements com.ibm.wsspi.threadcontext.ThreadContext {
    private static final long serialVersionUID = 1L;

    private static final TraceComponent tc = Tr.register(MicroProfileClearedContextSnapshot.class);

    /**
     * Most of these are skipped because Liberty already provides most these (they inherit from
     * ContainerContextProvider). CDI is also skipped for compatibility purposes.
     */
    private static final HashSet<String> DO_NOT_CLEAR = new HashSet<String>(Arrays.asList //
    (
     ThreadContext.APPLICATION,
     ThreadContext.CDI,
     EmptyHandleListContextProvider.EMPTY_HANDLE_LIST,
     ThreadContext.SECURITY,
     ThreadIdentityContextProvider.SYNC_TO_OS_THREAD,
     ThreadContext.TRANSACTION,
     WLMContextProvider.CLASSIFICATION //
    ));

    private final ArrayList<ThreadContextController> contextRestorers = new ArrayList<ThreadContextController>();
    private final ArrayList<ThreadContextSnapshot> contextSnapshots;

    MicroProfileClearedContextSnapshot(ContextManagerImpl contextMgr) {
        contextSnapshots = new ArrayList<ThreadContextSnapshot>();
        for (ThreadContextProvider provider : contextMgr.contextProviders)
            if (!DO_NOT_CLEAR.contains(provider.getThreadContextType()))
                contextSnapshots.add(provider.clearedContext(Collections.emptyMap()));
    }

    // constructor for clone method
    private MicroProfileClearedContextSnapshot(ArrayList<ThreadContextSnapshot> contextSnapshots) {
        this.contextSnapshots = contextSnapshots; // shallow copy is okay here
    }

    @Override
    public com.ibm.wsspi.threadcontext.ThreadContext clone() {
        return new MicroProfileClearedContextSnapshot(contextSnapshots);
    }

    @Override
    public void taskStarting() throws RejectedExecutionException {
        final boolean trace = TraceComponent.isAnyTracingEnabled();

        try {
            for (ThreadContextSnapshot snapshot : contextSnapshots) {
                if (trace && tc.isDebugEnabled())
                    Tr.debug(this, tc, "clearing " + snapshot);

                contextRestorers.add(snapshot.begin());
            }
        } catch (Error | RuntimeException x) {
            taskStopping();
            throw x;
        }
    }

    @Override
    public void taskStopping() {
        final boolean trace = TraceComponent.isAnyTracingEnabled();

        Throwable failure = null;
        for (int i = contextRestorers.size() - 1; i >= 0; i--)
            try {
                ThreadContextController contextRestorer = contextRestorers.remove(i);

                if (trace && tc.isDebugEnabled())
                    Tr.debug(this, tc, "restoring " + contextRestorer);

                contextRestorer.endContext();
            } catch (Error | RuntimeException x) {
                if (failure != null)
                    failure = x;
            }

        if (failure instanceof Error)
            throw (Error) failure;
        if (failure instanceof RuntimeException)
            throw (RuntimeException) failure;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('@').append(Integer.toHexString(hashCode())).toString();
    }

    private void writeObject(ObjectOutputStream outStream) throws IOException {
        outStream.putFields();
        // nothing to write because the clearing context is recreated at deserialization time
        outStream.writeFields();
    }
}