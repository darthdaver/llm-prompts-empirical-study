package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.SwitchStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.SwitchStmt} found.
 *
 */
public class SwitchStmtVisitor extends BaseStmtVisitor<SwitchStmt> {

    /**
     * Visit a node and collects all the switch statements found within is AST.
     *
     * @param switchStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(SwitchStmt switchStmt, List<SwitchStmt> collection) {
        addToCollection(switchStmt, collection);
        super.visit(switchStmt, collection);
    }
}