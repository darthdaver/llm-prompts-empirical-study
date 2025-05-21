package star.llms.prompts.dataset.utils.javaParser.visitors.declarations;

import com.github.javaparser.ast.body.MethodDeclaration;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseDeclarationVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.body.MethodDeclaration} found.
 *
 */
public class MethodDeclarationVisitor extends BaseDeclarationVisitor<MethodDeclaration> {

    /**
     * Visit a node and collects all the method declarations found within is AST.
     *
     * @param methodDeclaration the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(MethodDeclaration methodDeclaration, List<MethodDeclaration> collection) {
        addToCollection(methodDeclaration, collection);
        super.visit(methodDeclaration, collection);
    }
}