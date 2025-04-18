package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt} found.
 *
 */
public class ExplicitConstructorInvocationStmtVisitor extends BaseStmtVisitor<ExplicitConstructorInvocationStmt> {

    /**
     * Visit a node and collects all the explicit constructor invocation statements found within is AST.
     *
     * @param explicitConstructorInvocationStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ExplicitConstructorInvocationStmt explicitConstructorInvocationStmt, List<ExplicitConstructorInvocationStmt> collection) {
        super.visit(explicitConstructorInvocationStmt, collection);
        addToCollection(explicitConstructorInvocationStmt, collection);
    }
}
