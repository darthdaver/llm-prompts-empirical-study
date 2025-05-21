package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.SynchronizedStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.SynchronizedStmt} found.
 *
 */
public class SynchronizedStmtVisitor extends BaseStmtVisitor<SynchronizedStmt> {

    /**
     * Visit a node and collects all the syncronized statements found within is AST.
     *
     * @param synchronizedStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(SynchronizedStmt synchronizedStmt, List<SynchronizedStmt> collection) {
        addToCollection(synchronizedStmt, collection);
        super.visit(synchronizedStmt, collection);
    }
}
