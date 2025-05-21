package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ForStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ForStmt} found.
 *
 */
public class ForStmtVisitor extends BaseStmtVisitor<ForStmt> {

    /**
     * Visit a node and collects all the for statements found within is AST.
     *
     * @param forStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ForStmt forStmt, List<ForStmt> collection) {
        addToCollection(forStmt, collection);
        super.visit(forStmt, collection);
    }
}