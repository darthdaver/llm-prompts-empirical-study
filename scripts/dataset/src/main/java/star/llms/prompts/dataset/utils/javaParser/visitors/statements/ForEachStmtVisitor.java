package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.ForEachStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.ForEachStmt} found.
 *
 */
public class ForEachStmtVisitor extends BaseStmtVisitor<ForEachStmt> {

    /**
     * Visit a node and collects all the forEach statements found within is AST.
     *
     * @param forEachStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ForEachStmt forEachStmt, List<ForEachStmt> collection) {
        super.visit(forEachStmt, collection);
        addToCollection(forEachStmt, collection);
    }
}