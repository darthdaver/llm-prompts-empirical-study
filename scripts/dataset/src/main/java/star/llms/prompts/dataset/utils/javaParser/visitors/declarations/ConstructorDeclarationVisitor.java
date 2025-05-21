package star.llms.prompts.dataset.utils.javaParser.visitors.declarations;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseDeclarationVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.body.ConstructorDeclaration} found.
 *
 */
public class ConstructorDeclarationVisitor extends BaseDeclarationVisitor<ConstructorDeclaration> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param constructorDeclaration the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ConstructorDeclaration constructorDeclaration, List<ConstructorDeclaration> collection) {
        addToCollection(constructorDeclaration, collection);
        super.visit(constructorDeclaration, collection);
    }
}
