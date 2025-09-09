package com.example.sat.test.dsl

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class RestDSLTransformation implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof ClassNode)) return

        ClassNode classNode = (ClassNode) nodes[1]

        // Wstrzykujemy metodę 'get'
        addHttpMethod(classNode, "get")
    }

    private void addHttpMethod(ClassNode classNode, String methodName) {
        def pathParam = new Parameter(ClassHelper.STRING_TYPE, "path")
        def configClosureParam = new Parameter(ClassHelper.CLOSURE_TYPE, "configClosure")
        def parameters = new Parameter[2]
        parameters[0] = pathParam
        parameters[1] = configClosureParam

        // Tworzymy ciało metody:
        // return RestTestExecutor.execute(params.from, "get", path)
        def methodCall = new MethodCallExpression(
                new ClassExpression(new ClassNode(RestTestExecutor)), // Wywołaj na klasie RestTestExecutor
                "execute", // Metodę 'execute'
                new ArgumentListExpression(
                        new VariableExpression("configClosure"), // <-- Przekazujemy closure
                        new ConstantExpression(methodName),
                        new VariableExpression("path")
                )
        )

        def methodBody = new ReturnStatement(methodCall)

        // Tworzymy całą metodę
        def newMethod = new MethodNode(
                methodName, // Nazwa metody: "get"
                ACC_PUBLIC, // Modyfikator dostępu
                ClassHelper.MAP_TYPE, // Typ zwracany
                parameters, // Parametry
                ClassNode.EMPTY_ARRAY, // Wyjątki
                methodBody // Ciało
        )

        // Dodajemy nową metodę do klasy testowej
        classNode.addMethod(newMethod)
    }
}