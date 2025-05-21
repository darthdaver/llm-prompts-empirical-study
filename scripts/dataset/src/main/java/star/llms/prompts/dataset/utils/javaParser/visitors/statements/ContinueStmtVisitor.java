package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ContinueStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ContinueStmt} found.
 *
 */
public class ContinueStmtVisitor extends BaseStmtVisitor<ContinueStmt> {

    /**
     * Visit a node and collects all the continue statements found within is AST.
     *
     * @param continueStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ContinueStmt continueStmt, List<ContinueStmt> collection) {
        addToCollection(continueStmt, collection);
        super.visit(continueStmt, collection);
    }
}
