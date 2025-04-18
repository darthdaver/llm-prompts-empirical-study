package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.YieldStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.YieldStmt} found.
 *
 */
public class YieldStmtVisitor extends BaseStmtVisitor<YieldStmt> {

    /**
     * Visit a node and collects all the yield statements found within is AST.
     *
     * @param yieldStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(YieldStmt yieldStmt, List<YieldStmt> collection) {
        super.visit(yieldStmt, collection);
        addToCollection(yieldStmt, collection);
    }
}
