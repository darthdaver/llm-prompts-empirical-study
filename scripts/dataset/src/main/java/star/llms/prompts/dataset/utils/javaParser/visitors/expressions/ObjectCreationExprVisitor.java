package star.llms.prompts.dataset.utils.javaParser.visitors.expressions;

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseExprVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.expr.ObjectCreationExpr} found.
 *
 */
public class ObjectCreationExprVisitor extends BaseExprVisitor<ObjectCreationExpr> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param objCreationExprStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ObjectCreationExpr objCreationExprStmt, List<ObjectCreationExpr> collection) {
        super.visit(objCreationExprStmt, collection);
        addToCollection(objCreationExprStmt, collection);
    }
}