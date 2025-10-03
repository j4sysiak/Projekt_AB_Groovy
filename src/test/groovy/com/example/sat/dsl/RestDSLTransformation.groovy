package com.example.sat.dsl

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

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

        // Parametr `configClosure` jest najważniejszy. Dodajemy do niego adnotację
        // @DelegatesTo(ServerConfig), aby IDE i kompilator wiedziały,
        // co jest dozwolone wewnątrz tego bloku kodu w teście.
        def configClosureParam = new Parameter(ClassHelper.CLOSURE_TYPE, "configClosure")

        def delegatesToAnnotation = new AnnotationNode(new ClassNode(DelegatesTo))
        delegatesToAnnotation.setMember("value", new ClassExpression(new ClassNode(ServerConfig)))
        configClosureParam.addAnnotation(delegatesToAnnotation)

        def parameters = [pathParam, configClosureParam] as Parameter[]

        // --- 2. Definiujemy ciało wstrzykiwanej metody ---
        // Ciało będzie wywoływać naszą logikę wykonawczą:
        // return RestTestExecutor.execute(configClosure, "get", path)

        def methodCall = new MethodCallExpression(
                // Wywołaj metodę statyczną na klasie RestTestExecutor
                new ClassExpression(new ClassNode(RestTestExecutor)),
                // o nazwie "execute"
                "execute",
                // z następującymi argumentami:
                new ArgumentListExpression(
                        new VariableExpression("configClosure"), // 1. przekazane closure
                        new ConstantExpression(methodName),     // 2. nazwa metody jako string (np. "get")
                        new VariableExpression("path")          // 3. przekazana ścieżka
                )
        )

        def methodBody = new ReturnStatement(methodCall)

        // --- 3. Składamy wszystko w jedną całość ---
        def newMethod = new MethodNode(
                methodName,                 // Nazwa metody, np. "get"
                ACC_PUBLIC,                 // Modyfikator dostępu: public
                ClassHelper.MAP_TYPE,       // Typ zwracany: Map
                parameters,                 // Zdefiniowane parametry
                ClassNode.EMPTY_ARRAY,      // Lista wyjątków (pusta)
                methodBody                  // Zdefiniowane ciało metody
        )

        // --- 4. Dodajemy gotową metodę do klasy testowej ---
        classNode.addMethod(newMethod)
    }
}