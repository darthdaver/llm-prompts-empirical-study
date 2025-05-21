package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ExpressionStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ExpressionStmt} found.
 *
 */
public class ExpressionStmtVisitor extends BaseStmtVisitor<ExpressionStmt> {

    /**
     * Visit a node and collects all the expression statements found within is AST.
     *
     * @param expressionStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ExpressionStmt expressionStmt, List<ExpressionStmt> collection) {
        addToCollection(expressionStmt, collection);
        super.visit(expressionStmt, collection);
    }
}
