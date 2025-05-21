package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.WhileStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.WhileStmt} found.
 *
 */
public class WhileStmtVisitor extends BaseStmtVisitor<WhileStmt> {

    /**
     * Visit a node and collects all the while statements found within is AST.
     *
     * @param whileStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(WhileStmt whileStmt, List<WhileStmt> collection) {
        addToCollection(whileStmt, collection);
        super.visit(whileStmt, collection);
    }
}