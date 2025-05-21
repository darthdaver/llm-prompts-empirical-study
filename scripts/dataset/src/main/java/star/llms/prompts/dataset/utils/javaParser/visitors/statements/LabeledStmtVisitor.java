package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.LabeledStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.LabeledStmt} found.
 *
 */
public class LabeledStmtVisitor extends BaseStmtVisitor<LabeledStmt> {

    /**
     * Visit a node and collects all the labeled statements found within is AST.
     *
     * @param labeledStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(LabeledStmt labeledStmt, List<LabeledStmt> collection) {
        addToCollection(labeledStmt, collection);
        super.visit(labeledStmt, collection);
    }
}
