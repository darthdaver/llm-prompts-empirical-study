package star.llms.prompts.dataset.utils.javaParser.visitors.types;

import com.github.javaparser.ast.type.ArrayType;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseTypeVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.type.ArrayType} found.
 *
 */
public class ArrayTypeVisitor extends BaseTypeVisitor<ArrayType> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param arrayType the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(ArrayType arrayType, List<ArrayType> collection) {
        super.visit(arrayType, collection);
        addToCollection(arrayType, collection);
    }
}