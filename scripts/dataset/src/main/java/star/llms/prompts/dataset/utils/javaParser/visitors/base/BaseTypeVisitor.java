package star.llms.prompts.dataset.utils.javaParser.visitors.base;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the nodes <T>
 * that extends {@link com.github.javaparser.ast.type.Type} found.
 *
 */
public abstract class BaseTypeVisitor<T extends Type> extends VoidVisitorAdapter<List<T>> {

    public List<T> visit(Type type) {
        List<T> collection = new java.util.ArrayList<>();
        if (type.isArrayType()) {
            visit(type.asArrayType(), collection);
        } else if (type.isClassOrInterfaceType()) {
            visit(type.asClassOrInterfaceType(), collection);
        } else if (type.isIntersectionType()) {
            visit(type.asIntersectionType(), collection);
        } else if (type.isPrimitiveType()) {
            visit(type.asPrimitiveType(), collection);
        } else if (type.isTypeParameter()) {
            visit(type.asTypeParameter(), collection);
        } else if (type.isUnionType()) {
            visit(type.asUnionType(), collection);
        } else if (type.isUnknownType()) {
            visit(type.asUnionType(), collection);
        } else if (type.isVarType()) {
            visit(type.asVarType(), collection);
        } else if (type.isVoidType()) {
            visit(type.asVoidType(), collection);
        } else if (type.isWildcardType()) {
            visit(type.asWildcardType(), collection);
        }
        return collection;
    }

    // Generic method to add the statement to the collection
    protected void addToCollection(T type, List<T> collection) {
        collection.add(type);
    }
}
