/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.codegen.optimization

import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.inline.ReifiedTypeInliner
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.common.StrictBasicValue
import org.jetbrains.kotlin.codegen.optimization.fixStack.top
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue

class RedundantCheckCastEliminationMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        val insns = methodNode.instructions.toArray()
        if (!insns.any { it.opcode == Opcodes.CHECKCAST }) return

        val redundantCheckCasts = ArrayList<TypeInsnNode>()

        val frames = analyze(internalClassName, methodNode, ReificationTrackerInterpreter())
        for (i in insns.indices) {
            val value = frames[i]?.top()
            val valueType = value?.type ?: continue
            val insn = insns[i]

            if (ReifiedTypeInliner.isOperationReifiedMarker(insn.previous)) continue // if checkcast itself reification instruction keep it
            if (value is ReifiedBasicValue && value.isReified) continue  // if we try to checkcast marked value then keep cast

            if (insn is TypeInsnNode) {
                val insnType = Type.getObjectType(insn.desc)
                if (!isTrivialSubtype(insnType, valueType)) continue

                //Keep casts to multiarray types cause dex doesn't recognize ANEWARRAY [Ljava/lang/Object; as Object [][], but Object [] type
                //It's not clear is it bug in dex or not and maybe best to distinguish such types from MULTINEWARRRAY ones in method analyzer
                if (isMultiArrayType(insnType)) continue

                if (insn.opcode == Opcodes.CHECKCAST) {
                    redundantCheckCasts.add(insn)
                }
            }
        }

        redundantCheckCasts.forEach {
            methodNode.instructions.remove(it)
        }
    }

    private fun isTrivialSubtype(superType: Type, subType: Type) =
        superType == subType

    private fun isMultiArrayType(type: Type) = type.sort == Type.ARRAY && type.dimensions != 1
}

class ReifiedBasicValue(type: Type?, val isReified: Boolean) : StrictBasicValue(type) {
    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) {
            return isReified == (other as ReifiedBasicValue).isReified
        }

        return false
    }

    override fun hashCode(): Int = 31 * super.hashCode() + isReified.hashCode()
}

class ReificationTrackerInterpreter : OptimizationBasicInterpreter() {

    private var isReified = false

    override fun naryOperation(insn: AbstractInsnNode, values: MutableList<out BasicValue>?): BasicValue? {
        if (insn is MethodInsnNode && AsmTypes.ENUM_TYPE.internalName == insn.owner) {
            if (insn.name == "valueOf") {
                isReified = insn.previous?.previous?.previous?.let { ReifiedTypeInliner.isOperationReifiedMarker(it) } ?: false
            }
        }
        return super.naryOperation(insn, values)
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        if (CHECKCAST == insn.opcode) {
            if ((value as? ReifiedBasicValue)?.isReified == true) {
                isReified = true
            }
        }
        if (insn.opcode == Opcodes.ANEWARRAY && insn is TypeInsnNode && insn.desc == AsmTypes.ENUM_TYPE.internalName) {
            //reified check for enumValuesOf
            insn.previous?.previous?.let {
                isReified = ReifiedTypeInliner.isOperationReifiedMarker(it)
            }
        }
        //in most cases it's previous instruction with marker
        isReified = isReified or ReifiedTypeInliner.isOperationReifiedMarker(insn.previous)


        return super.unaryOperation(insn, value)
    }

    override fun newValue(type: Type?): StrictBasicValue? {
        val value = if (Type.OBJECT == type?.sort || Type.ARRAY == type?.sort) {
            ReifiedBasicValue(type, isReified)
        } else super.newValue(type);
        isReified = false
        return value
    }

    override fun newValueFrom(value: BasicValue): StrictBasicValue? {
        if (value.type.sort == Type.OBJECT || value.type.sort == Type.ARRAY) {
            return ReifiedBasicValue(value.type, (value as? ReifiedBasicValue)?.isReified == true)
        }
        return super.newValueFrom(value)
    }

}