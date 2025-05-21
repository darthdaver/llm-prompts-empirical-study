package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.TryStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.TryStmt} found.
 *
 */
public class TryStmtVisitor extends BaseStmtVisitor<TryStmt> {

    /**
     * Visit a node and collects all the try statements found within is AST.
     *
     * @param tryStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(TryStmt tryStmt, List<TryStmt> collection) {
        addToCollection(tryStmt, collection);
        super.visit(tryStmt, collection);
    }
}
