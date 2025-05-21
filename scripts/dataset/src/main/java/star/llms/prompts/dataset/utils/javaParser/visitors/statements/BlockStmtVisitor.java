package star.llms.prompts.dataset.utils.javaParser.visitors.statements;

import com.github.javaparser.ast.stmt.BlockStmt;
import star.llms.prompts.dataset.utils.javaParser.visitors.base.BaseStmtVisitor;

import java.util.List;

/**
 * The class visit a node (and all its descendants within the AST) and collects all the
 * {@link com.github.javaparser.ast.stmt.BlockStmt} found.
 *
 */
public class BlockStmtVisitor extends BaseStmtVisitor<BlockStmt> {

    /**
     * Visit a node and collects all the block statements found within is AST.
     *
     * @param blockStmt the type of node to collect, anytime it is found within the AST of the analyzed node.
     * @param collection the collection to add any occurrence of the searched node found.
     */
    @Override
    public void visit(BlockStmt blockStmt, List<BlockStmt> collection) {
        addToCollection(blockStmt, collection);
        super.visit(blockStmt, collection);
    }
}
