/*
 * IterNodeCompiler.java
 *
 * Created on January 3, 2007, 11:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.jruby.compiler;

import org.jruby.ast.Node;

/**
 *
 * @author headius
 */
public class IterNodeCompiler implements NodeCompiler{
    
    /** Creates a new instance of IterNodeCompiler */
    public IterNodeCompiler() {
    }
    
    public void compile(Node node, Compiler context) {
        // FIXME: This should get caller from different location than iter since we revered 
        // relationship
        // FIXME: handle other iternode cases
    }
    
}
