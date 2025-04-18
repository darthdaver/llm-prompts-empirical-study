package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.IfStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the 
 * {@link com.github.javaparser.ast.stmt.IfStmt} found.
 *
 */
public class IfStmtVisitor extends BaseStmtVisitor<IfStmt> {

    /**
     * Visit a node and collects all the if statements found within is AST.
     *
     * @param ifStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(IfStmt ifStmt, List<IfStmt> collection) {
        super.visit(ifStmt, collection);
        addToCollection(ifStmt, collection);
    }
}