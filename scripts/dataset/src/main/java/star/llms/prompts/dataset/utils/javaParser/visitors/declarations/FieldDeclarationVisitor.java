package star.llms.prompts.dataset.utils.javaParser.visitors.declarations;

import com.github.javaparser.ast.body.FieldDeclaration;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseDeclarationVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.body.FieldDeclaration} found.
 *
 */
public class FieldDeclarationVisitor extends BaseDeclarationVisitor<FieldDeclaration> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param fieldDeclaration the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(FieldDeclaration fieldDeclaration, List<FieldDeclaration> collection) {
        addToCollection(fieldDeclaration, collection);
        super.visit(fieldDeclaration, collection);
    }
}