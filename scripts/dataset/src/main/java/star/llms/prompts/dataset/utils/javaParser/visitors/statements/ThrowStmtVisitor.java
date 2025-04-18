package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ThrowStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ThrowStmt} found.
 *
 */
public class ThrowStmtVisitor extends BaseStmtVisitor<ThrowStmt> {

    /**
     * Visit a node and collects all the throw statements found within is AST.
     *
     * @param throwStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ThrowStmt throwStmt, List<ThrowStmt> collection) {
        super.visit(throwStmt, collection);
        addToCollection(throwStmt, collection);
    }
}
