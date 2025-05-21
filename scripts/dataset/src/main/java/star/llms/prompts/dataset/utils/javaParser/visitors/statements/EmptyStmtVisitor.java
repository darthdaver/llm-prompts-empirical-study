package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.EmptyStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.EmptyStmt} found.
 *
 */
public class EmptyStmtVisitor extends BaseStmtVisitor<EmptyStmt> {

    /**
     * Visit a node and collects all the empty statements found within is AST.
     *
     * @param emptyStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(EmptyStmt emptyStmt, List<EmptyStmt> collection) {
        addToCollection(emptyStmt, collection);
        super.visit(emptyStmt, collection);
    }
}
