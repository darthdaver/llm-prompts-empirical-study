package star.llms.prompts.dataset.utils.javaParser.visitors.types;

import com.github.javaparser.ast.type.VoidType;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseTypeVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.type.VoidType} found.
 *
 */
public class VoidTypeVisitor extends BaseTypeVisitor<VoidType> {

    /**
     * Visit a node and collects all the method call expressions found within is AST.
     *
     * @param voidType the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(VoidType voidType, List<VoidType> collection) {
        addToCollection(voidType, collection);
        super.visit(voidType, collection);
    }
}