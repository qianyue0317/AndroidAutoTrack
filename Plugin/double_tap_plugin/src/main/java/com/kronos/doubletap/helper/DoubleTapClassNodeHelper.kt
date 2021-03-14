package com.kronos.doubletap.helper

import com.kronos.doubletap.DoubleTabConfig
import com.kronos.plugin.base.AsmHelper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.io.IOException

class DoubleTapClassNodeHelper : AsmHelper {

    private val classNodeMap = hashMapOf<String, ClassNode>()

    @Throws(IOException::class)
    override fun modifyClass(srcClass: ByteArray): ByteArray {
        val classNode = ClassNode(ASM5)
        val classReader = ClassReader(srcClass)
        //1 将读入的字节转为classNode
        classReader.accept(classNode, 0)
        classNodeMap[classNode.name] = classNode
        // 判断当前类是否实现了OnClickListener接口
        val field = classNode.getField()
        val className = classNode.outerClass
        val parentNode = classNodeMap[className]
        val hasKeepAnnotation = if (field) {
            true
        } else {
            parentNode?.getField() ?: false
        }
        if (!hasKeepAnnotation) {
            classNode.interfaces?.forEach {
                if (it == "android/view/View\$OnClickListener") {
                    classNode.methods?.forEach { method ->
                        // 找到onClick 方法
                        if (method.name == "<init>") {
                            initFunction(classNode, method)
                        }
                        if (method.name == "onClick" && method.desc == "(Landroid/view/View;)V") {
                            insertTrack(classNode, method)
                        }
                    }
                }
            }
            /*       classNode.lambdaHelper {
                       (it.name == "onClick" && it.desc.contains(")Landroid/view/View\$OnClickListener;"))
                   }.forEach { method ->
                       val field = classNode.getField()
                    //   insertLambda(classNode, method, field)
                   }*/
        }
        //调用Fragment的onHiddenChange方法
        val classWriter = ClassWriter(0)
        //3  将classNode转为字节数组
        classNode.accept(classWriter)
        return classWriter.toByteArray()
    }


    private fun insertLambda(node: ClassNode, method: MethodNode, field: FieldNode?) {
        // 根据outClassName 获取到外部类的Node
        val instructions = method.instructions
        instructions?.iterator()?.forEach {
            // 判断是不是代码的截止点
            if ((it.opcode >= Opcodes.IRETURN && it.opcode <= Opcodes.RETURN) || it.opcode == Opcodes.ATHROW) {
                instructions.insertBefore(it, VarInsnNode(Opcodes.ALOAD, 0))
                instructions.insertBefore(it, VarInsnNode(Opcodes.ALOAD, 0))
                // 获取到数据参数
                /*if (parentField != null) {
                    parentField.apply {
                        instructions.insertBefore(it, VarInsnNode(Opcodes.ALOAD, 0))
                        instructions.insertBefore(
                                it, FieldInsnNode(Opcodes.GETFIELD, node.name, parentField.name, parentField.desc)
                        )
                    }*/
            } else {
                instructions.insertBefore(it, LdcInsnNode("1234"))
            }
            instructions.insertBefore(
                    it, MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/wallstreetcn/sample/ToastHelper",
                    "toast",
                    "(Ljava/lang/Object;Landroid/view/View;Ljava/lang/Object;)V",
                    false)
            )
        }
    }
}

private fun initFunction(node: ClassNode, method: MethodNode) {
    node.visitField(ACC_PRIVATE + ACC_FINAL, "doubleTap", String.format("L%s;",
            DoubleTabConfig.ByteCodeInjectClassName), node.signature, null)
    val instructions = method.instructions
    val forEach = method.instructions?.iterator()?.forEach {
        if ((it.opcode >= Opcodes.IRETURN && it.opcode <= Opcodes.RETURN) || it.opcode == Opcodes.ATHROW) {
            instructions.insertBefore(it, VarInsnNode(ALOAD, 0))
            instructions.insertBefore(it, TypeInsnNode(NEW, DoubleTabConfig.ByteCodeInjectClassName))
            instructions.insertBefore(it, InsnNode(DUP))
            instructions.insertBefore(it, MethodInsnNode(INVOKESPECIAL, DoubleTabConfig.ByteCodeInjectClassName,
                    "<init>", "()V", false))
            instructions.insertBefore(it, FieldInsnNode(PUTFIELD, node.name, "doubleTap",
                    String.format("L%s;", DoubleTabConfig.ByteCodeInjectClassName)))
        }
    }
}


private fun insertTrack(node: ClassNode, method: MethodNode) {
    // 判断方法名和方法描述
    // 根据outClassName 获取到外部类的Node

    val instructions = method.instructions
    val firstNode = instructions.first
    instructions?.insertBefore(firstNode, VarInsnNode(ALOAD, 0))
    instructions?.insertBefore(firstNode, FieldInsnNode(GETFIELD, node.name,
            "doubleTap", String.format("L%s;", DoubleTabConfig.ByteCodeInjectClassName)))
    instructions?.insertBefore(firstNode, MethodInsnNode(INVOKEVIRTUAL, DoubleTabConfig.ByteCodeInjectClassName,
            DoubleTabConfig.ByteCodeInjectFunctionName, "()Z", false))
    val labelNode = LabelNode(Label())
    instructions?.insertBefore(firstNode, JumpInsnNode(IFNE, labelNode))
    instructions?.insertBefore(firstNode, InsnNode(RETURN))
    instructions?.insertBefore(firstNode, labelNode)
}


// 判断Field是否包含注解
private fun ClassNode.getField(): Boolean {
    var hasAnnotation = false
    this.visibleAnnotations.forEach { annotation ->
        if (annotation.desc == "Landroidx/annotation/Keep;") {
            hasAnnotation = true
        }
    }
    return hasAnnotation
}


fun isFragment(superName: String): Boolean {
    return superName == "androidx/fragment/app/Fragment" || superName == "android/support/v4/app/Fragment"
}


fun isRecyclerViewHolder(superName: String): Boolean {
    return superName == "androidx/recyclerview/widget/RecyclerView\$ViewHolder"
}