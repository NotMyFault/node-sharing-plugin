/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.redhat.jenkins.nodesharingfrontend;

import com.redhat.jenkins.nodesharing.NodeDefinition;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.SystemCommandLanguage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * An extension point turning {@link com.redhat.jenkins.nodesharing.NodeDefinition}s into {@link SharedNode}s.
 * @author ogondza.
 */
@Restricted(NoExternalUse.class)
public abstract class SharedNodeFactory implements ExtensionPoint {
    @Nonnull
    public static SharedNode transform(@Nonnull NodeDefinition def) throws IllegalArgumentException {
        for (SharedNodeFactory factory : ExtensionList.lookup(SharedNodeFactory.class)) {
            SharedNode node = factory.create(def);
            if (node != null) return decorate(node);
        }

        throw new IllegalArgumentException("No SharedNodeFactory to process " + def.getDeclaringFileName());
    }

    @Nonnull
    private static SharedNode decorate(@Nonnull SharedNode node) throws IllegalArgumentException {
        node.setRetentionStrategy(new SharedOnceRetentionStrategy(1));
        node.setMode(Node.Mode.EXCLUSIVE);

        // Approve command launcher's script. It is script from orchestrator, that we trust.
        ComputerLauncher launcher = node.getLauncher();
        if (launcher instanceof CommandLauncher) {
            CommandLauncher cl = (CommandLauncher) launcher;
            ScriptApproval.get().preapprove(cl.getCommand(), SystemCommandLanguage.get());
        }

        if (node.getNumExecutors() != 1) {
            throw new IllegalArgumentException("Shared Nodes must have exactly 1 executor");
        }
        return node;
    }

    /**
     * Instantiate the Node.
     *
     * @param def Node definition.
     * @return Node or null in case the definition is not handled by this implementation.
     * @throws IllegalArgumentException If the definition is invalid.
     */
    @CheckForNull
    public abstract SharedNode create(@Nonnull NodeDefinition def) throws IllegalArgumentException;

    @Extension
    public static final class XStreamFactory extends SharedNodeFactory {

        @Override
        @CheckForNull
        public SharedNode create(@Nonnull NodeDefinition def) throws IllegalArgumentException {
            if (def instanceof NodeDefinition.Xml) {
                SharedNode node;
                try {
                    node = (SharedNode) Jenkins.XSTREAM2.fromXML(def.getDefinition());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid node definition", e);
                }
                if (node == null) {
                    throw new IllegalArgumentException("Invalid node definition");
                }
                return node;
            }
            return null;
        }
    }
}
