package com.example.sat.dsl2

import groovy.lang.DelegatesTo
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.syntax.Token

import static org.codehaus.groovy.ast.ClassNode.ACC_PUBLIC // <-- DODAJ TĘ LINIĘ

/**
 * Globalna transformacja AST, która jest aktywowana przez adnotację @RestDSL.
 * Jej zadaniem jest wstrzyknięcie do klasy testowej metod pomocniczych (np. 'get', 'post'),
 * które upraszczają pisanie testów REST-owych.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class RestDSLTransformation implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        // Podstawowa weryfikacja, czy transformacja jest nałożona na klasę
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof ClassNode)) {
            return
        }

        ClassNode classNode = (ClassNode) nodes[1]

        // Wstrzykujemy metody dla różnych typów żądań HTTP
        addHttpMethod(classNode, "get")
        addHttpMethod(classNode, "post")
        // W przyszłości można tu dodać 'put', 'delete' itd.
    }

    /**
     * Buduje i wstrzykuje metodę (np. 'get') do podanej klasy.
     * @param classNode Klasa, do której metoda zostanie dodana.
     * @param methodName Nazwa metody do wstrzyknięcia (np. "get").
     */
    private void addHttpMethod(ClassNode classNode, String methodName) {
        // --- 1. Definiujemy parametry wstrzykiwanej metody ---
        // Metoda będzie miała sygnaturę: Map get(String path, Closure configClosure)

        def pathParam = new Parameter(ClassHelper.STRING_TYPE, "path")
        def configClosureParam = new Parameter(new ClassNode(Closure), "configClosure")

        def delegatesToAnnotation = new AnnotationNode(new ClassNode(DelegatesTo))
        // ZMIANA: Teraz delegujemy do głównej klasy ServerConfig
        delegatesToAnnotation.setMember("value", new ClassExpression(new ClassNode(ServerConfig)))
        configClosureParam.addAnnotation(delegatesToAnnotation)

        def parameters = [pathParam, configClosureParam] as Parameter[]

        // --- 2. Definiujemy ciało wstrzykiwanej metody ---
        // Tworzymy ServerConfig, wywołujemy closure z parametrem, przekazujemy config do RestTestExecutor
        def configVar = new VariableExpression("config")
        def configInit = new DeclarationExpression(
            configVar,
            Token.newSymbol("=", -1, -1),
            new ConstructorCallExpression(new ClassNode(ServerConfig), ArgumentListExpression.EMPTY_ARGUMENTS)
        )
        def printAst = new ExpressionStatement(
            new MethodCallExpression(
                new VariableExpression("this"),
                "println",
                new ConstantExpression("AST: przekazuję do closure: ")
            )
        )
        def printConfig = new ExpressionStatement(
            new MethodCallExpression(
                new VariableExpression("this"),
                "println",
                configVar
            )
        )
        def callClosure = new ExpressionStatement(
            new MethodCallExpression(
                new VariableExpression("configClosure"),
                "call",
                new ArgumentListExpression(configVar)
            )
        )
        def methodCall = new MethodCallExpression(
            new ClassExpression(new ClassNode(RestTestExecutor)),
            "executeWithConfig",
            new ArgumentListExpression(configVar, new ConstantExpression(methodName), new VariableExpression("path"))
        )
        def methodBody = new BlockStatement([
            new ExpressionStatement(configInit),
            printAst,
            printConfig,
            callClosure,
            new ReturnStatement(methodCall)
        ], new VariableScope())

        // --- 3. Składamy wszystko w jedną całość ---
        def newMethod = new MethodNode(
                methodName,                 // Nazwa metody, np. "get"
                ACC_PUBLIC,                 // Modyfikator dostępu: public
                new ClassNode(Map),         // Typ zwracany: Map (poprawka dla Groovy 3+)
                parameters,                 // Zdefiniowane parametry
                ClassNode.EMPTY_ARRAY,      // Lista wyjątków (pusta)
                methodBody                  // Zdefiniowane ciało metody
        )

        // --- 4. Dodajemy gotową metodę do klasy testowej ---
        classNode.addMethod(newMethod)
    }
}