package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.BreakStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.BreakStmt} found.
 *
 */
public class BreakStmtVisitor extends BaseStmtVisitor<BreakStmt> {

    /**
     * Visit a node and collects all the break statements found within is AST.
     *
     * @param breakStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(BreakStmt breakStmt, List<BreakStmt> collection) {
        addToCollection(breakStmt, collection);
        super.visit(breakStmt, collection);
    }
}
