package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.DoStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.DoStmt} found.
 *
 */
public class DoStmtVisitor extends BaseStmtVisitor<DoStmt> {

    /**
     * Visit a node and collects all the do statements found within is AST.
     *
     * @param doStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(DoStmt doStmt, List<DoStmt> collection) {
        addToCollection(doStmt, collection);
        super.visit(doStmt, collection);
    }
}