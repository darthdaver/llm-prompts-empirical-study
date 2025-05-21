package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.UnparsableStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.UnparsableStmt} found.
 *
 */
public class UnparsableStmtVisitor extends BaseStmtVisitor<UnparsableStmt> {

    /**
     * Visit a node and collects all the unparsable statements found within is AST.
     *
     * @param unparsableStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(UnparsableStmt unparsableStmt, List<UnparsableStmt> collection) {
        addToCollection(unparsableStmt, collection);
        super.visit(unparsableStmt, collection);
    }
}
