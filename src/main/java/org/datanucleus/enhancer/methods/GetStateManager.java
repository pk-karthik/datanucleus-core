/**********************************************************************
Copyright (c) 2016 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
    ...
**********************************************************************/
package org.datanucleus.enhancer.methods;

import org.datanucleus.enhancer.ClassEnhancer;
import org.datanucleus.enhancer.ClassMethod;
import org.datanucleus.enhancer.asm.Label;
import org.datanucleus.enhancer.asm.Opcodes;

/**
 * Method to generate the method "dnGetStateManager" using ASM.
 * <pre>
 * public final StateManager dnGetStateManager()
 * {
 *     return dnStateManager;
 * }
 * </pre>
 */
public class GetStateManager extends ClassMethod
{
    public static GetStateManager getInstance(ClassEnhancer enhancer)
    {
        return new GetStateManager(enhancer, enhancer.getNamer().getGetStateManagerMethodName(), 
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
            enhancer.getNamer().getStateManagerClass(), null, null);
    }

    /**
     * Constructor.
     * @param enhancer ClassEnhancer
     * @param name Name of method
     * @param access Access type
     * @param returnType Return type
     * @param argTypes Argument types
     * @param argNames Argument names
     */
    public GetStateManager(ClassEnhancer enhancer, String name, int access, 
        Object returnType, Object[] argTypes, String[] argNames)
    {
        super(enhancer, name, access, returnType, argTypes, argNames);
    }

    /**
     * Method to add the contents of the class method.
     */
    public void execute()
    {
        visitor.visitCode();

        Label l0 = new Label();
        visitor.visitLabel(l0);
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitFieldInsn(Opcodes.GETFIELD, getClassEnhancer().getASMClassName(),
            getNamer().getStateManagerFieldName(), getNamer().getStateManagerDescriptor());
        visitor.visitInsn(Opcodes.ARETURN);

        Label l1 = new Label();
        visitor.visitLabel(l1);
        visitor.visitLocalVariable("this", getClassEnhancer().getClassDescriptor(), null, l0, l1, 0);
        visitor.visitMaxs(1, 1);

        visitor.visitEnd();
    }
}
