package star.llms.prompts.dataset.utils.javaParser.visitors.base;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the nodes <B>
 * that extends {@link com.github.javaparser.ast.body.BodyDeclaration} found.
 *
 */
public abstract class BaseDeclarationVisitor<B extends BodyDeclaration> extends VoidVisitorAdapter<List<B>> {

    public List<B> visit(TypeDeclaration typeDeclaration) {
        List<B> collection = new java.util.ArrayList<>();
        if (typeDeclaration.isClassOrInterfaceDeclaration()) {
            visit(typeDeclaration.asClassOrInterfaceDeclaration(), collection);
        }
        return collection;
    }

    // Generic method to add the statement to the collection
    protected void addToCollection(B declaration, List<B> collection) {
        collection.add(declaration);
    }
}