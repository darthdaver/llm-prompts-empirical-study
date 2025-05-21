package star.llms.prompts.dataset.utils.javaParser.visitors.expressions;

import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseExprVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.expr.VariableDeclarationExpr} found.
 *
 */
public class VariableDeclarationExprVisitor extends BaseExprVisitor<VariableDeclarationExpr> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param variableDeclarationExpr the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(VariableDeclarationExpr variableDeclarationExpr, List<VariableDeclarationExpr> collection) {
        addToCollection(variableDeclarationExpr, collection);
        super.visit(variableDeclarationExpr, collection);
    }
}